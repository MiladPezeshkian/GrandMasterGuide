package com.zorix.chess.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.zorix.chess.platform.LocalAppLocale
import com.zorix.chess.platform.PlatformBackHandler
import com.zorix.chess.resources.*
import com.zorix.chess.controller.ChessController
import com.zorix.chess.controller.ChessUiState
import com.zorix.chess.controller.HintState
import com.zorix.chess.controller.UiMessage
import com.zorix.chess.ui.board.BoardArrow
import com.zorix.chess.ui.board.ChessBoard
import com.zorix.chess.ui.board.PieceImages
import com.zorix.chess.ui.board.boardColors
import com.zorix.chess.ui.board.rememberPieceImages
import com.zorix.chess.ui.components.ActionBar
import com.zorix.chess.ui.components.CoachCard
import com.zorix.chess.ui.components.EnginePanel
import com.zorix.chess.ui.components.EvalBar
import com.zorix.chess.ui.components.MenuAction
import com.zorix.chess.ui.components.MoveStrip
import com.zorix.chess.ui.components.ScoreChip
import com.zorix.chess.ui.components.TurnIndicator
import com.zorix.chess.ui.components.ZorixTopBar
import com.zorix.chess.ui.theme.ZorixColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Things only the Android layer can do. */
interface PlatformActions {
    val versionName: String
    val maxThreads: Int
    fun sharePgn(pgn: String)

    /** Dark status-bar icons for the light app style. */
    fun setLightSystemBars(light: Boolean) {}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(state: ChessUiState, c: ChessController, platform: PlatformActions, onBuild: () -> Unit) {
    val pieces = rememberPieceImages()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    var showFen by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    val messageTexts = UiMessage.entries.associateWith { stringResource(it.label()) }
    val texts by rememberUpdatedState(messageTexts)
    fun show(message: UiMessage) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(texts.getValue(message))
        }
    }
    LaunchedEffect(c) { c.messages.collect { show(it) } }
    LaunchedEffect(state.moveCounter) {
        if (state.moveCounter > 0 && state.settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight && maxWidth >= 560.dp
        val actionBar: @Composable (Boolean) -> Unit = { insets ->
            ActionBar(
                canUndo = state.game.canUndo,
                canRedo = state.game.canRedo,
                thinking = state.isThinking,
                onUndo = c::undo,
                onRedo = c::redo,
                onBestMove = c::requestBestMove,
                onStop = c::stopThinking,
                onFlip = c::flipBoard,
                onBuild = onBuild,
                applyNavigationInsets = insets,
            )
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ZorixTopBar(analysisOn = state.analysisOn, onToggleAnalysis = c::setAnalysis, onLearn = null, onMenu = { action ->
                    when (action) {
                        MenuAction.NEW_GAME -> c.newGame()
                        MenuAction.EDIT_POSITION -> onBuild()
                        MenuAction.LOAD_FEN -> showFen = true
                        MenuAction.COPY_FEN -> {
                            clipboard.setText(AnnotatedString(c.fen()))
                            show(UiMessage.FEN_COPIED)
                        }
                        MenuAction.COPY_PGN -> {
                            clipboard.setText(AnnotatedString(c.pgn()))
                            show(UiMessage.PGN_COPIED)
                        }
                        MenuAction.SHARE_PGN -> platform.sharePgn(c.pgn())
                        MenuAction.ABOUT -> showAbout = true
                    }
                })
            },
            bottomBar = { if (!landscape) actionBar(false) },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
                if (landscape) {
                    val boardSize = min(maxHeight - 16.dp, maxWidth * 0.56f)
                    Row(Modifier.fillMaxSize().padding(8.dp)) {
                        BoardWithEval(state, c, pieces, boardSize)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).fillMaxSize()) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                StatusRow(state)
                                MoveStrip(state.game, c::goTo, feedback = state.feedback)
                                CoachCard(state, Modifier.padding(bottom = 10.dp))
                                Panel(state, c)
                            }
                            actionBar(false)
                        }
                    }
                } else {
                    val boardSize = min(maxWidth - 32.dp, maxHeight * 0.64f)
                    // When the best move arrives, scroll its card (with the "play" button) fully into view.
                    val panelInView = remember { BringIntoViewRequester() }
                    val hintReady = state.hint is HintState.Ready
                    LaunchedEffect(hintReady) {
                        if (hintReady) {
                            delay(120) // let the card lay out first
                            panelInView.bringIntoView()
                        }
                    }
                    Column(Modifier.fillMaxSize()) {
                        StatusRow(state, Modifier.padding(horizontal = 8.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            BoardWithEval(state, c, pieces, boardSize)
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            MoveStrip(state.game, c::goTo, Modifier.padding(horizontal = 4.dp), state.feedback)
                            CoachCard(state, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp))
                            Panel(state, c, Modifier.padding(horizontal = 12.dp).bringIntoViewRequester(panelInView))
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }

    if (showFen) FenDialog(initial = "", onLoad = c::loadFen, onDismiss = { showFen = false })
    if (showAbout) AboutDialog(platform.versionName) { showAbout = false }
}

@Composable
private fun StatusRow(state: ChessUiState, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TurnIndicator(state.game.position.sideToMove)
        Spacer(Modifier.weight(1f))
        state.currentView?.score?.let { ScoreChip(it) }
    }
}

@Composable
private fun Panel(state: ChessUiState, c: ChessController, modifier: Modifier = Modifier) {
    EnginePanel(
        state = state,
        onStop = c::stopThinking,
        onPlaySuggestion = c::playSuggestion,
        onDismissSuggestion = c::dismissSuggestion,
        onPlayLine = c::playLine,
        onRetryEngine = c::retryEngine,
        onUndo = c::undo,
        onNewGame = c::newGame,
        modifier = modifier,
    )
}

private val arrowColors get() = listOf(ZorixColors.Best, ZorixColors.Line2, ZorixColors.Line3)

private fun arrowsFor(state: ChessUiState): List<BoardArrow> {
    if (!state.settings.showArrows || state.game.status.isOver) return emptyList()
    val hint = state.hint
    val result = ArrayList<BoardArrow>()
    when (hint) {
        is HintState.Ready -> hint.view.takeIf { it.fen == state.fen }?.bestMove?.let {
            result += BoardArrow(it.from, it.to, ZorixColors.Best.copy(alpha = 0.9f), 0.18f)
        }
        is HintState.Thinking -> hint.view?.bestMove?.let {
            result += BoardArrow(it.from, it.to, ZorixColors.Best.copy(alpha = 0.45f), 0.16f)
        }
        HintState.Idle -> state.analysis?.takeIf { it.fen == state.fen }?.lines?.forEachIndexed { i, line ->
            val move = line.firstMove ?: return@forEachIndexed
            if (result.none { it.from == move.from && it.to == move.to }) {
                result += BoardArrow(move.from, move.to, arrowColors[i % arrowColors.size].copy(alpha = 0.85f - i * 0.15f), 0.17f - i * 0.03f)
            }
        }
    }
    // Draw the best arrow last so it stays on top.
    return result.asReversed()
}

@Composable
private fun BoardWithEval(state: ChessUiState, c: ChessController, pieces: PieceImages, boardSize: Dp) {
    val settings = state.settings
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row {
            EvalBar(
                score = state.currentView?.score,
                flipped = state.flipped,
                modifier = Modifier.width(10.dp).height(boardSize),
            )
            Spacer(Modifier.width(6.dp))
            ChessBoard(
                position = state.game.position,
                lastMove = state.game.lastMove,
                flipped = state.flipped,
                colors = boardColors(settings.boardTheme),
                pieces = pieces,
                modifier = Modifier
                    .size(boardSize)
                    .shadow(10.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp)),
                arrows = arrowsFor(state),
                showCoordinates = settings.showCoordinates,
                showLegalMoves = settings.showLegalMoves,
                animateMoves = settings.animateMoves,
                interactive = !state.game.status.isOver,
                promotion = state.pendingPromotion,
                contentDescription = stringResource(
                    if (state.pendingPromotion != null) Res.string.promotion_hint else Res.string.board_description,
                ),
                onMove = c::onUserMove,
                onPromotion = { type -> if (type == null) c.onPromotionCancelled() else c.onPromotionChosen(type) },
            )
        }
    }
}
