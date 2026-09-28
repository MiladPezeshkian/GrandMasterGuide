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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zorix.chess.resources.*
import org.jetbrains.compose.resources.StringResource
import com.zorix.chess.core.Piece
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Position
import com.zorix.chess.core.Side
import com.zorix.chess.ui.board.BoardColors
import com.zorix.chess.ui.board.ChessBoard
import com.zorix.chess.ui.board.PieceImages
import com.zorix.chess.ui.components.AppIcons
import com.zorix.chess.ui.label
import com.zorix.chess.ui.theme.ZorixColors

/**
 * Board editor: set up any position (e.g. from a real game) and let Stockfish analyse it.
 * Validation (kings, pawns on back ranks, side not to move in check) happens before loading.
 */
@Composable
fun PositionEditor(
    initial: Position,
    flipped: Boolean,
    colors: BoardColors,
    pieces: PieceImages,
    onCancel: () -> Unit,
    onDone: (Position) -> Unit,
) {
    val board = remember { mutableStateListOf<Piece?>().apply { addAll(initial.pieces()) } }
    var side by remember { mutableStateOf(initial.sideToMove) }
    var castling by remember { mutableIntStateOf(initial.castling) }
    var tool by remember { mutableStateOf<Piece?>(Piece.of(Side.WHITE, PieceType.PAWN)) }
    var error by remember { mutableStateOf<StringResource?>(null) }

    val position = Position.of(board.toList(), side, castling)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(AppIcons.Close, stringResource(Res.string.action_cancel)) }
                Text(stringResource(Res.string.editor_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val candidate = Position.of(board.toList(), side, castling)
                    val problem = candidate.problem()
                    if (problem == null) onDone(candidate) else error = problem.label()
                }) {
                    Icon(AppIcons.Check, null)
                    Text(stringResource(Res.string.action_done))
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
                    onSquareTap = { square ->
                        error = null
                        val current = board[square]
                        board[square] = if (tool == null || current === tool) null else tool
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.editor_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (s in Side.entries) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (type in listOf(PieceType.KING, PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN)) {
                                    val piece = Piece.of(s, type)
                                    PaletteButton(selected = tool === piece, onClick = { tool = piece }) {
                                        Image(BitmapPainter(pieces[piece]), contentDescription = null, modifier = Modifier.fillMaxSize().padding(3.dp))
                                    }
                                }
                                if (s == Side.WHITE) {
                                    PaletteButton(selected = tool == null, onClick = { tool = null }) {
                                        Icon(AppIcons.Delete, stringResource(Res.string.editor_eraser), tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.editor_side_to_move), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    for (s in Side.entries) {
                        FilterChip(selected = side == s, onClick = { side = s; error = null }, label = { Text(stringResource(s.label())) }, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.editor_castling), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
                CastleRow(Res.string.editor_white_oo, Position.CASTLE_WK, Res.string.editor_white_ooo, Position.CASTLE_WQ, castling) { castling = it }
                CastleRow(Res.string.editor_black_oo, Position.CASTLE_BK, Res.string.editor_black_ooo, Position.CASTLE_BQ, castling) { castling = it }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        board.clear()
                        repeat(64) { board.add(null) }
                        castling = 0
                        error = null
                    }, modifier = Modifier.weight(1f)) {
                        Icon(AppIcons.Delete, null, modifier = Modifier.size(18.dp))
                        Text(" " + stringResource(Res.string.editor_clear))
                    }
                    OutlinedButton(onClick = {
                        val start = Position.initial()
                        board.clear()
                        board.addAll(start.pieces())
                        side = Side.WHITE
                        castling = start.castling
                        error = null
                    }, modifier = Modifier.weight(1f)) {
                        Icon(AppIcons.Refresh, null, modifier = Modifier.size(18.dp))
                        Text(" " + stringResource(Res.string.editor_start))
                    }
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text(" " + stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PaletteButton(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) ZorixColors.RedDeep else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if (selected) 2.dp else 0.dp, if (selected) ZorixColors.Red else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun CastleRow(firstLabel: StringResource, firstFlag: Int, secondLabel: StringResource, secondFlag: Int, castling: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for ((label, flag) in listOf(firstLabel to firstFlag, secondLabel to secondFlag)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = castling and flag != 0, onCheckedChange = { on -> onChange(if (on) castling or flag else castling and flag.inv()) })
                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
