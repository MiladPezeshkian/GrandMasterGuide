package com.zorix.chess.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.imageResource
import com.zorix.chess.resources.*
import com.zorix.chess.controller.BoardThemeId
import com.zorix.chess.core.Piece
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Side

/** Square colours of a board theme. Highlights are shared overlays that work on every theme. */
data class BoardColors(val light: Color, val dark: Color) {
    val lastMove = Color(0x6BCDD26A)
    val selected = Color(0x8014551E)
    val moveHint = Color(0x6614551E)
    val hover = Color(0x40FFFFFF)
    val coordOnLight: Color get() = dark
    val coordOnDark: Color get() = light
}

fun boardColors(theme: BoardThemeId): BoardColors = when (theme) {
    // Same colours as the desktop GrandMaster Guide board.
    BoardThemeId.CLASSIC -> BoardColors(Color(0xFFF0D9B5), Color(0xFFB58863))
    BoardThemeId.WALNUT -> BoardColors(Color(0xFFE8C99B), Color(0xFF8B5A2B))
    BoardThemeId.GREEN -> BoardColors(Color(0xFFEEEED2), Color(0xFF769656))
    BoardThemeId.BLUE -> BoardColors(Color(0xFFDEE3E6), Color(0xFF8CA2AD))
    BoardThemeId.GRAPHITE -> BoardColors(Color(0xFFCFD0D6), Color(0xFF6B6D78))
}

/** The 12 piece bitmaps (cburnett set, 256 px). */
class PieceImages(private val images: Map<Piece, ImageBitmap>) {
    operator fun get(piece: Piece): ImageBitmap = images.getValue(piece)
}

@Composable
fun rememberPieceImages(): PieceImages {
    val wk = imageResource(Res.drawable.piece_wk)
    val wq = imageResource(Res.drawable.piece_wq)
    val wr = imageResource(Res.drawable.piece_wr)
    val wb = imageResource(Res.drawable.piece_wb)
    val wn = imageResource(Res.drawable.piece_wn)
    val wp = imageResource(Res.drawable.piece_wp)
    val bk = imageResource(Res.drawable.piece_bk)
    val bq = imageResource(Res.drawable.piece_bq)
    val br = imageResource(Res.drawable.piece_br)
    val bb = imageResource(Res.drawable.piece_bb)
    val bn = imageResource(Res.drawable.piece_bn)
    val bp = imageResource(Res.drawable.piece_bp)
    return remember(wk, bk) {
        PieceImages(
            mapOf(
                Piece.of(Side.WHITE, PieceType.KING) to wk,
                Piece.of(Side.WHITE, PieceType.QUEEN) to wq,
                Piece.of(Side.WHITE, PieceType.ROOK) to wr,
                Piece.of(Side.WHITE, PieceType.BISHOP) to wb,
                Piece.of(Side.WHITE, PieceType.KNIGHT) to wn,
                Piece.of(Side.WHITE, PieceType.PAWN) to wp,
                Piece.of(Side.BLACK, PieceType.KING) to bk,
                Piece.of(Side.BLACK, PieceType.QUEEN) to bq,
                Piece.of(Side.BLACK, PieceType.ROOK) to br,
                Piece.of(Side.BLACK, PieceType.BISHOP) to bb,
                Piece.of(Side.BLACK, PieceType.KNIGHT) to bn,
                Piece.of(Side.BLACK, PieceType.PAWN) to bp,
            ),
        )
    }
}

/** An arrow drawn on the board (engine suggestion / analysis line). */
data class BoardArrow(val from: Int, val to: Int, val color: Color, val width: Float = 0.16f)
