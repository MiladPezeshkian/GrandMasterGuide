package com.zorix.chess.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.zorix.chess.controller.PendingPromotion
import com.zorix.chess.core.Move
import com.zorix.chess.core.Piece
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Position
import com.zorix.chess.core.Squares
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Order of the promotion picker, as on lichess: queen closest to the promotion square. */
val PROMOTION_ORDER = listOf(PieceType.QUEEN, PieceType.KNIGHT, PieceType.ROOK, PieceType.BISHOP)

private data class DragState(val from: Int, val piece: Piece, val pointer: Offset)

private data class PieceAnim(val piece: Piece, val from: Int, val to: Int)

/**
 * Interactive chess board: tap-tap or drag-and-drop moves, legal move hints, last move and
 * check highlights, animated moves, engine arrows and the promotion picker.
 *
 * When [onSquareTap] is set (board editor) every tap is reported raw and pieces cannot be moved.
 */
@Composable
fun ChessBoard(
    position: Position,
    lastMove: Move?,
    flipped: Boolean,
    colors: BoardColors,
    pieces: PieceImages,
    modifier: Modifier = Modifier,
    arrows: List<BoardArrow> = emptyList(),
    showCoordinates: Boolean = true,
    showLegalMoves: Boolean = true,
    animateMoves: Boolean = true,
    interactive: Boolean = true,
    promotion: PendingPromotion? = null,
    /** Extra square highlights (puzzle hints, lesson targets). */
    highlights: Map<Int, Color> = emptyMap(),
    contentDescription: String = "",
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    onPromotion: (PieceType?) -> Unit = {},
    onSquareTap: ((Int) -> Unit)? = null,
) {
    val fen = position.fen()
    var selected by remember(fen) { mutableStateOf<Int?>(null) }
    var drag by remember { mutableStateOf<DragState?>(null) }
    var dropTarget by remember { mutableStateOf<Int?>(null) }

    // Move animation: compare the previous and the new board and slide what moved.
    var shownBoard by remember { mutableStateOf(position.pieces()) }
    val progress = remember { Animatable(1f) }
    var anims by remember { mutableStateOf<List<PieceAnim>>(emptyList()) }
    LaunchedEffect(fen) {
        val old = shownBoard
        val new = position.pieces()
        shownBoard = new
        val moving = if (animateMoves) diffMoves(old, new, skip = dropTarget) else emptyList()
        dropTarget = null
        if (moving.isNotEmpty()) {
            anims = moving
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
        }
        anims = emptyList()
    }

    val onMoveState by rememberUpdatedState(onMove)
    val onPromotionState by rememberUpdatedState(onPromotion)
    val onTapState by rememberUpdatedState(onSquareTap)
    val textMeasurer = rememberTextMeasurer()

    fun col(square: Int) = if (flipped) 7 - Squares.file(square) else Squares.file(square)
    fun row(square: Int) = if (flipped) Squares.rank(square) else 7 - Squares.rank(square)

    Canvas(
        modifier
            .aspectRatio(1f)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(fen, flipped, interactive, promotion, onSquareTap != null) {
                val sq = size.width / 8f

                fun squareAt(o: Offset): Int? {
                    val c = floor(o.x / sq).toInt()
                    val r = floor(o.y / sq).toInt()
                    if (c !in 0..7 || r !in 0..7) return null
                    return if (flipped) Squares.of(7 - c, r) else Squares.of(c, 7 - r)
                }

                fun tap(square: Int) {
                    val sel = selected
                    if (sel != null && sel != square && position.legalMovesFrom(sel).any { it.to == square }) {
                        selected = null
                        onMoveState(sel, square)
                        return
                    }
                    val p = position[square]
                    selected = if (p != null && p.side == position.sideToMove && sel != square) square else null
                }

                fun drop(from: Int, to: Int?) {
                    if (to != null && to != from && position.legalMovesFrom(from).any { it.to == to }) {
                        dropTarget = to
                        selected = null
                        onMoveState(from, to)
                    } else {
                        selected = from
                    }
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = squareAt(down.position)

                    val pending = promotion
                    if (pending != null) {
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        val target = squareAt(up.position)
                        val choice = target?.let {
                            val r0 = row(pending.to)
                            val index = abs(row(it) - r0)
                            val sameColumn = col(it) == col(pending.to)
                            val towardsCentre = if (r0 == 0) row(it) >= r0 else row(it) <= r0
                            if (sameColumn && towardsCentre && index < PROMOTION_ORDER.size) PROMOTION_ORDER[index] else null
                        }
                        onPromotionState(choice)
                        return@awaitEachGesture
                    }

                    val rawTap = onTapState
                    if (rawTap != null) {
                        val up = waitForUpOrCancellation()
                        if (up != null) squareAt(up.position)?.let(rawTap)
                        return@awaitEachGesture
                    }

                    if (!interactive || start == null) return@awaitEachGesture
                    val piece = position[start]
                    val draggable = piece != null && piece.side == position.sideToMove
                    var dragging = false
                    var pointer = down.position
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp()) {
                                change.consume()
                                if (dragging) drop(start, squareAt(pointer)) else tap(start)
                                break
                            }
                            pointer = change.position
                            if (!dragging && draggable && (pointer - down.position).getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                                selected = start
                            }
                            if (dragging && piece != null) {
                                drag = DragState(start, piece, pointer)
                                change.consume()
                            }
                        }
                    } finally {
                        drag = null
                    }
                }
            },
    ) {
        val sq = size.width / 8f
        fun topLeft(square: Int) = Offset(col(square) * sq, row(square) * sq)
        fun centre(square: Int) = Offset(col(square) * sq + sq / 2, row(square) * sq + sq / 2)

        // Squares
        for (r in 0..7) for (c in 0..7) {
            drawRect(if ((r + c) % 2 == 0) colors.light else colors.dark, Offset(c * sq, r * sq), Size(sq, sq))
        }

        // Last move, selection and check highlights
        lastMove?.let {
            drawRect(colors.lastMove, topLeft(it.from), Size(sq, sq))
            drawRect(colors.lastMove, topLeft(it.to), Size(sq, sq))
        }
        for ((square, color) in highlights) drawRect(color, topLeft(square), Size(sq, sq))
        val sel = selected
        if (sel != null) drawRect(colors.selected, topLeft(sel), Size(sq, sq))
        if (position.isCheck) {
            val king = position.kingSquare(position.sideToMove)
            if (king != Squares.NONE) {
                drawRect(
                    Brush.radialGradient(
                        0f to Color(0xFFFF0000),
                        0.25f to Color(0xFFE70000),
                        0.9f to Color(0x00A90000),
                        center = centre(king),
                        radius = sq * 0.75f,
                    ),
                    topLeft(king),
                    Size(sq, sq),
                )
            }
        }

        // Legal move hints for the selected piece
        if (sel != null && showLegalMoves && onSquareTap == null) {
            for (to in position.legalMovesFrom(sel).map { it.to }.distinct()) {
                if (position[to] == null) {
                    drawCircle(colors.moveHint, radius = sq * 0.16f, center = centre(to))
                } else {
                    drawCircle(colors.moveHint, radius = sq * 0.44f, center = centre(to), style = Stroke(width = sq * 0.09f))
                }
            }
        }

        // Hovered square while dragging
        drag?.let { d ->
            val c = floor(d.pointer.x / sq).toInt()
            val r = floor(d.pointer.y / sq).toInt()
            if (c in 0..7 && r in 0..7) {
                drawCircle(colors.hover, radius = sq * 0.75f, center = Offset(c * sq + sq / 2, r * sq + sq / 2))
            }
        }

        // Coordinates
        if (showCoordinates) {
            val style = TextStyle(fontSize = (sq * 0.2f).toSp(), fontWeight = FontWeight.SemiBold)
            for (c in 0..7) {
                val file = if (flipped) 7 - c else c
                val light = (7 + c) % 2 == 0
                val label = textMeasurer.measure(('a' + file).toString(), style.copy(color = if (light) colors.coordOnLight else colors.coordOnDark))
                drawText(label, topLeft = Offset(c * sq + sq - label.size.width - sq * 0.06f, 8 * sq - label.size.height - sq * 0.02f))
            }
            for (r in 0..7) {
                val rank = if (flipped) r else 7 - r
                val light = r % 2 == 0
                val label = textMeasurer.measure(('1' + rank).toString(), style.copy(color = if (light) colors.coordOnLight else colors.coordOnDark))
                drawText(label, topLeft = Offset(sq * 0.06f, r * sq + sq * 0.03f))
            }
        }

        // Pieces
        val animTargets = anims.map { it.to }.toSet()
        for (square in 0 until 64) {
            val p = position[square] ?: continue
            if (square in animTargets) continue
            if (drag?.from == square) continue
            drawPiece(pieces[p], topLeft(square), sq)
        }
        val t = progress.value
        for (a in anims) {
            val from = topLeft(a.from)
            val to = topLeft(a.to)
            drawPiece(pieces[a.piece], Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t), sq)
        }

        // Engine arrows
        for (arrow in arrows) {
            drawArrow(centre(arrow.from), centre(arrow.to), arrow.color, arrow.width * sq, sq)
        }

        // Dragged piece follows the finger, lifted a little so it stays visible.
        drag?.let { d ->
            val s = sq * 1.3f
            drawPiece(pieces[d.piece], Offset(d.pointer.x - s / 2, d.pointer.y - s * 0.75f), s)
        }

        // Promotion picker: Queen, Knight, Rook, Bishop stacked from the promotion square.
        promotion?.let { p ->
            drawRect(Color.Black.copy(alpha = 0.6f))
            val c = col(p.to)
            val r0 = row(p.to)
            val dir = if (r0 == 0) 1 else -1
            PROMOTION_ORDER.forEachIndexed { i, type ->
                val r = r0 + dir * i
                val centre = Offset(c * sq + sq / 2, r * sq + sq / 2)
                drawCircle(
                    Brush.radialGradient(listOf(Color.White, Color(0xFFC9C9CF)), center = centre, radius = sq * 0.5f),
                    radius = sq * 0.47f,
                    center = centre,
                )
                if (i == 0) {
                    drawCircle(Color(0xFFE3202B), radius = sq * 0.47f, center = centre, style = Stroke(width = sq * 0.05f))
                }
                drawPiece(pieces[Piece.of(p.side, type)], Offset(c * sq + sq * 0.1f, r * sq + sq * 0.1f), sq * 0.8f)
            }
        }
    }
}

private fun DrawScope.drawPiece(image: ImageBitmap, topLeft: Offset, size: Float) {
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(size.roundToInt(), size.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, width: Float, square: Float) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy)
    if (length < 1f) return
    val ux = dx / length
    val uy = dy / length
    val tip = Offset(to.x - ux * square * 0.12f, to.y - uy * square * 0.12f)
    val headLength = width * 2.3f
    val headHalf = width * 1.45f
    val base = Offset(tip.x - ux * headLength, tip.y - uy * headLength)
    val px = -uy
    val py = ux
    val half = width / 2
    val path = Path().apply {
        moveTo(from.x + px * half, from.y + py * half)
        lineTo(base.x + px * half, base.y + py * half)
        lineTo(base.x + px * headHalf, base.y + py * headHalf)
        lineTo(tip.x, tip.y)
        lineTo(base.x - px * headHalf, base.y - py * headHalf)
        lineTo(base.x - px * half, base.y - py * half)
        lineTo(from.x - px * half, from.y - py * half)
        close()
    }
    drawPath(path, color)
}

/** Works out which pieces slid between two boards (normal moves, captures, castling, undo). */
private fun diffMoves(old: List<Piece?>, new: List<Piece?>, skip: Int?): List<PieceAnim> {
    val vanished = (0 until 64).filter { old[it] != null && old[it] !== new[it] }
    val appeared = (0 until 64).filter { new[it] != null && old[it] !== new[it] }
    if (appeared.isEmpty() || appeared.size > 2 || vanished.size > 3) return emptyList()
    fun distance(a: Int, b: Int) = abs(Squares.file(a) - Squares.file(b)) + abs(Squares.rank(a) - Squares.rank(b))
    val used = HashSet<Int>()
    val result = ArrayList<PieceAnim>()
    for (to in appeared) {
        if (to == skip) continue
        val piece = new[to] ?: continue
        val from = vanished.filter { it !in used && old[it] === piece }.minByOrNull { distance(it, to) }
            ?: vanished.filter { it !in used && old[it]?.side == piece.side && old[it]?.type == PieceType.PAWN }
                .minByOrNull { distance(it, to) }
            ?: continue
        used += from
        result += PieceAnim(piece, from, to)
    }
    return result
}
