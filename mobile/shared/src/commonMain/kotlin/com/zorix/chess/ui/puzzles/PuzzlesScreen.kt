package com.zorix.chess.ui.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.zorix.chess.controller.PendingPromotion
import com.zorix.chess.controller.Profile
import com.zorix.chess.controller.PuzzleMode
import com.zorix.chess.controller.PuzzleOutcome
import com.zorix.chess.controller.PuzzleState
import com.zorix.chess.controller.PuzzleTrainer
import com.zorix.chess.controller.Settings
import com.zorix.chess.resources.*
import com.zorix.chess.ui.board.ChessBoard
import com.zorix.chess.ui.board.PieceImages
import com.zorix.chess.ui.board.boardColors
import com.zorix.chess.ui.components.AppIcons
import com.zorix.chess.ui.components.CoachBubble
import com.zorix.chess.ui.components.HelpButton
import com.zorix.chess.ui.components.HelpTopic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.zorix.chess.ui.components.PrimaryButton
import com.zorix.chess.ui.components.ScreenHeader
import com.zorix.chess.ui.components.SecondaryButton
import com.zorix.chess.ui.themeLabel
import com.zorix.chess.ui.theme.ZorixColors
import org.jetbrains.compose.resources.stringResource

/** Rated puzzles near the player's level, a puzzle streak mode and theme training. */
@Composable
fun PuzzlesScreen(
    state: PuzzleState,
    trainer: PuzzleTrainer,
    profile: Profile,
    settings: Settings,
    pieces: PieceImages,
    onSpeak: (String) -> Unit,
    onBuild: () -> Unit,
) {
    LaunchedEffect(state.loaded) { if (state.loaded && state.puzzle == null) trainer.start(PuzzleMode.RATED) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            stringResource(Res.string.tab_puzzles),
            subtitle = if (state.mode == PuzzleMode.STREAK) stringResource(Res.string.puzzle_streak_now, state.streak)
            else stringResource(Res.string.puzzle_solved_line, profile.puzzlesSolved),
            actions = {
                IconButton(onClick = onBuild) { Icon(AppIcons.Edit, stringResource(Res.string.action_build)) }
                HelpButton(HelpTopic.PUZZLES)
            },
        )
        LazyRow(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = state.mode == PuzzleMode.RATED && state.theme == null, onClick = { trainer.start(PuzzleMode.RATED) }, label = { Text(stringResource(Res.string.puzzle_mode_rated)) })
            }
            item {
                FilterChip(selected = state.mode == PuzzleMode.STREAK, onClick = { trainer.start(PuzzleMode.STREAK) }, label = { Text(stringResource(Res.string.puzzle_mode_streak)) })
            }
            items(trainer.themes) { t ->
                FilterChip(selected = state.theme == t && state.mode == PuzzleMode.RATED, onClick = { trainer.start(PuzzleMode.RATED, t) }, label = { Text(themeLabel(t)) })
            }
        }
        val p = state.puzzle
        val pos = state.position
        if (p == null || pos == null) return@Column
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val boardSize = min(maxWidth - 24.dp, maxHeight * 0.62f)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                val sideText = stringResource(if (!state.flipped) Res.string.puzzle_white_to_move else Res.string.puzzle_black_to_move)
                val (msg, color) = when (state.outcome) {
                    PuzzleOutcome.SOLVING -> sideText to MaterialTheme.colorScheme.onSurface
                    PuzzleOutcome.CORRECT_STEP -> stringResource(Res.string.puzzle_correct) to ZorixColors.Best
                    PuzzleOutcome.WRONG -> stringResource(Res.string.puzzle_wrong) to MaterialTheme.colorScheme.error
                    PuzzleOutcome.SOLVED -> stringResource(Res.string.puzzle_solved) to ZorixColors.Best
                    PuzzleOutcome.FAILED -> stringResource(Res.string.puzzle_streak_over, state.streak) to MaterialTheme.colorScheme.error
                }
                Text(msg, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = color, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ChessBoard(
                        position = pos,
                        lastMove = state.lastMove,
                        flipped = state.flipped,
                        colors = boardColors(settings.boardTheme),
                        pieces = pieces,
                        modifier = Modifier.size(boardSize).clip(RoundedCornerShape(6.dp)),
                        showCoordinates = settings.showCoordinates,
                        showLegalMoves = settings.showLegalMoves,
                        animateMoves = settings.animateMoves,
                        interactive = state.interactive,
                        promotion = state.pendingPromotion?.let { (f, t) -> PendingPromotion(f, t, pos.sideToMove) },
                        highlights = state.hintSquare?.let { mapOf(it to ZorixColors.Best.copy(alpha = 0.6f)) } ?: emptyMap(),
                        onMove = trainer::onMove,
                        onPromotion = trainer::onPromotion,
                    )
                }
                Spacer(Modifier.height(10.dp))
                val themes = p.themes.filter { it in PuzzleTrainer.TRAINABLE }.take(3).map { themeLabel(it) }
                if (themes.isNotEmpty() && state.outcome != PuzzleOutcome.SOLVING) {
                    Text(themes.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.explanation?.let { e ->
                    Spacer(Modifier.height(10.dp))
                    CoachBubble(e, onSpeak = { onSpeak(e) })
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state.outcome) {
                PuzzleOutcome.SOLVED, PuzzleOutcome.FAILED -> {
                    if (state.outcome == PuzzleOutcome.FAILED) {
                        PrimaryButton(stringResource(Res.string.puzzle_try_again), { trainer.start(PuzzleMode.STREAK) }, Modifier.weight(1f), icon = AppIcons.Replay)
                    } else {
                        PrimaryButton(stringResource(Res.string.puzzle_next), trainer::next, Modifier.weight(1f), icon = AppIcons.ArrowForward)
                    }
                }
                else -> {
                    SecondaryButton(stringResource(Res.string.puzzle_hint), trainer::hint, Modifier.weight(1f), icon = AppIcons.Bulb)
                    SecondaryButton(stringResource(Res.string.puzzle_solution), trainer::showSolution, Modifier.weight(1f), icon = AppIcons.Play)
                    if (state.mode == PuzzleMode.RATED) SecondaryButton(stringResource(Res.string.puzzle_skip), trainer::next, Modifier.weight(1f))
                }
            }
        }
    }
}
