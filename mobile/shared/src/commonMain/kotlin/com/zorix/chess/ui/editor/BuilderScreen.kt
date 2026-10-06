package com.zorix.chess.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zorix.chess.core.Piece
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Position
import com.zorix.chess.core.Side
import com.zorix.chess.core.Squares
import com.zorix.chess.resources.*
import com.zorix.chess.ui.board.BoardColors
import com.zorix.chess.ui.board.ChessBoard
import com.zorix.chess.ui.board.PieceImages
import com.zorix.chess.ui.components.AppIcons
import com.zorix.chess.ui.components.HelpButton
import com.zorix.chess.ui.components.HelpTopic
import com.zorix.chess.ui.components.PrimaryButton
import com.zorix.chess.ui.components.ScreenHeader
import com.zorix.chess.ui.label
import com.zorix.chess.ui.theme.ZorixColors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Most pieces a position may hold: the engine's evaluation handles at most 32. */
const val BUILDER_MAX_PIECES = 32

/** What the builder reports about the current arrangement (null = ready to analyse). */
enum class BuilderIssue { OPPONENT_IN_CHECK, TOO_MANY_PIECES }

/**
 * "Build a puzzle": both kings are always on the board (they can be moved, never removed) and any
 * other pieces can be placed anywhere, in any number, up to [BUILDER_MAX_PIECES] in total.
 * Pawns cannot stand on the first or last rank, as in real chess.
 */
@Composable
fun BuilderScreen(
    initial: Position,
    colors: BoardColors,
    pieces: PieceImages,
    onCancel: () -> Unit,
    onAnalyze: (Position) -> Unit,
) {
    val board = remember { mutableStateListOf<Piece?>().apply { addAll(startingBoard(initial)) } }
    var side by remember { mutableStateOf(initial.sideToMove) }
    var castling by remember { mutableIntStateOf(initial.castling) }
    var tool by remember { mutableStateOf<Piece?>(Piece.of(Side.WHITE, PieceType.QUEEN)) }
    var flipped by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<StringResource?>(null) }

    val position = Position.of(board.toList(), side, castling)
    val count = board.count { it != null }
    val issue = when {
        count > BUILDER_MAX_PIECES -> BuilderIssue.TOO_MANY_PIECES
        position.problem() == com.zorix.chess.core.PositionProblem.OPPONENT_IN_CHECK -> BuilderIssue.OPPONENT_IN_CHECK
        else -> null
    }

    fun tap(square: Int) {
        notice = null
        val current = board[square]
        val placing = tool
        when {
            // The eraser removes anything but a king.
            placing == null -> if (current?.type == PieceType.KING) notice = Res.string.builder_king_stays else board[square] = null
            placing.type == PieceType.KING -> {
                if (current?.type == PieceType.KING) return
                val from = board.indexOfFirst { it === placing }
                if (from >= 0) board[from] = null
                board[square] = placing
            }
            current?.type == PieceType.KING -> notice = Res.string.builder_king_stays
            current === placing -> board[square] = null
            placing.type == PieceType.PAWN && Squares.rank(square) in setOf(0, 7) -> notice = Res.string.builder_pawn_rank
            current == null && count >= BUILDER_MAX_PIECES -> notice = Res.string.builder_full
            else -> board[square] = placing
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            stringResource(Res.string.builder_title),
            onBack = onCancel,
            subtitle = stringResource(Res.string.builder_count, count, BUILDER_MAX_PIECES),
            actions = {
                androidx.compose.material3.IconButton(onClick = { flipped = !flipped }) { Icon(AppIcons.Flip, stringResource(Res.string.action_flip)) }
                HelpButton(HelpTopic.BUILDER)
            },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                ChessBoard(
                    position = position,
                    lastMove = null,
                    flipped = flipped,
                    colors = colors,
                    pieces = pieces,
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                    showLegalMoves = false,
                    animateMoves = false,
                    interactive = false,
                    contentDescription = stringResource(Res.string.board_description),
                    onSquareTap = ::tap,
                )
            }
            Spacer(Modifier.height(10.dp))
            Banner(
                ok = issue == null && notice == null,
                text = stringResource(
                    notice ?: when (issue) {
                        BuilderIssue.TOO_MANY_PIECES -> Res.string.builder_full
                        BuilderIssue.OPPONENT_IN_CHECK -> Res.string.builder_check
                        null -> Res.string.builder_ready
                    },
                ),
            )
            Spacer(Modifier.height(12.dp))

            // Palette: every piece but the kings, which are already on the board and only move.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (s in Side.entries) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (type in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN)) {
                                val piece = Piece.of(s, type)
                                PaletteButton(selected = tool === piece, onClick = { tool = piece }) {
                                    Image(BitmapPainter(pieces[piece]), contentDescription = null, modifier = Modifier.fillMaxSize().padding(3.dp))
                                }
                            }
                            val king = Piece.of(s, PieceType.KING)
                            PaletteButton(selected = tool === king, onClick = { tool = king }, outlined = true) {
                                Image(BitmapPainter(pieces[king]), contentDescription = stringResource(Res.string.builder_move_king), modifier = Modifier.fillMaxSize().padding(5.dp))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PaletteButton(selected = tool == null, onClick = { tool = null }) {
                            Icon(AppIcons.Delete, stringResource(Res.string.editor_eraser), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            stringResource(
                                when {
                                    tool == null -> Res.string.builder_tool_eraser
                                    tool?.type == PieceType.KING -> Res.string.builder_tool_king
                                    else -> Res.string.builder_tool_piece
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.editor_side_to_move), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                for (s in Side.entries) {
                    FilterChip(selected = side == s, onClick = { side = s; notice = null }, label = { Text(stringResource(s.label())) }, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.editor_castling), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
            CastleRow(Res.string.editor_white_oo, Position.CASTLE_WK, Res.string.editor_white_ooo, Position.CASTLE_WQ, castling, position.castling) { castling = it }
            CastleRow(Res.string.editor_black_oo, Position.CASTLE_BK, Res.string.editor_black_ooo, Position.CASTLE_BQ, castling, position.castling) { castling = it }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    board.clear(); board.addAll(kingsOnly()); castling = 0; notice = null
                }, modifier = Modifier.weight(1f)) {
                    Icon(AppIcons.Delete, null, modifier = Modifier.size(18.dp))
                    Text(" " + stringResource(Res.string.editor_clear))
                }
                OutlinedButton(onClick = {
                    val start = Position.initial()
                    board.clear(); board.addAll(start.pieces()); side = Side.WHITE; castling = start.castling; notice = null
                }, modifier = Modifier.weight(1f)) {
                    Icon(AppIcons.Refresh, null, modifier = Modifier.size(18.dp))
                    Text(" " + stringResource(Res.string.editor_start))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(12.dp)) {
            PrimaryButton(
                stringResource(Res.string.builder_analyze),
                onClick = {
                    when (issue) {
                        null -> onAnalyze(position)
                        BuilderIssue.OPPONENT_IN_CHECK -> notice = Res.string.builder_check
                        BuilderIssue.TOO_MANY_PIECES -> notice = Res.string.builder_full
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = AppIcons.Analysis,
            )
        }
    }
}

/** The position to start from: the analysis board when it has one king each, otherwise two lone kings. */
private fun startingBoard(initial: Position): List<Piece?> {
    val p = initial.pieces()
    val whiteKings = p.count { it == Piece.of(Side.WHITE, PieceType.KING) }
    val blackKings = p.count { it == Piece.of(Side.BLACK, PieceType.KING) }
    return if (whiteKings == 1 && blackKings == 1 && p.count { it != null } <= BUILDER_MAX_PIECES) p else kingsOnly()
}

private fun kingsOnly(): List<Piece?> = MutableList<Piece?>(64) { null }.apply {
    this[Squares.parse("e1")] = Piece.of(Side.WHITE, PieceType.KING)
    this[Squares.parse("e8")] = Piece.of(Side.BLACK, PieceType.KING)
}

@Composable
private fun Banner(ok: Boolean, text: String) {
    val color = if (ok) ZorixColors.Best else MaterialTheme.colorScheme.error
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (ok) AppIcons.Check else AppIcons.Warning, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun PaletteButton(selected: Boolean, onClick: () -> Unit, outlined: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) ZorixColors.RedDeep else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                if (selected) 2.dp else if (outlined) 1.dp else 0.dp,
                if (selected) ZorixColors.Red else if (outlined) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun CastleRow(firstLabel: StringResource, firstFlag: Int, secondLabel: StringResource, secondFlag: Int, castling: Int, possible: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for ((label, flag) in listOf(firstLabel to firstFlag, secondLabel to secondFlag)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = possible and flag != 0,
                    onCheckedChange = { on -> onChange(if (on) castling or flag else castling and flag.inv()) },
                )
                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
