package com.zorix.chess.ui.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.zorix.chess.coach.CoachQuality
import com.zorix.chess.controller.Settings
import com.zorix.chess.core.Side
import com.zorix.chess.play.ReviewController
import com.zorix.chess.play.ReviewState
import com.zorix.chess.play.ReviewedMove
import com.zorix.chess.resources.*
import com.zorix.chess.ui.board.BoardArrow
import com.zorix.chess.ui.board.ChessBoard
import com.zorix.chess.ui.board.PieceImages
import com.zorix.chess.ui.board.boardColors
import com.zorix.chess.ui.color
import com.zorix.chess.ui.components.AppIcons
import com.zorix.chess.ui.components.CoachBubble
import com.zorix.chess.ui.components.Pill
import com.zorix.chess.ui.components.PrimaryButton
import com.zorix.chess.ui.components.Progress
import com.zorix.chess.ui.components.ScreenHeader
import com.zorix.chess.ui.components.SecondaryButton
import com.zorix.chess.ui.components.SectionTitle
import com.zorix.chess.ui.components.ZCard
import com.zorix.chess.ui.label
import com.zorix.chess.ui.symbol
import com.zorix.chess.ui.theme.ZorixColors
import com.zorix.chess.ui.theme.notation
import com.zorix.chess.ui.formatPercent
import org.jetbrains.compose.resources.stringResource

/** Game review: accuracy, move classification counts, evaluation graph, key moments and retries. */
@Composable
fun ReviewScreen(
    state: ReviewState,
    review: ReviewController,
    settings: Settings,
    pieces: PieceImages,
    playerName: String,
    onBack: () -> Unit,
    onSpeak: (String) -> Unit,
) {
    val game = state.game ?: return
    val data = state.review
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(stringResource(Res.string.review_title), onBack = onBack, subtitle = data?.opening, actions = { com.zorix.chess.ui.components.HelpButton(com.zorix.chess.ui.components.HelpTopic.REVIEW) })
        if (data == null) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.review_analysing), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Progress(state.progress, Modifier.width(240.dp))
            }
            return@Column
        }
        val retry = state.retry
        val ply = state.ply
        val shownPosition = retry?.position ?: if (ply == 0) game.start else game.plies[ply - 1].after
        val moveAtPly = data.moves.getOrNull(ply - 1)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val boardSize = min(maxWidth - 24.dp, maxHeight * 0.55f)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val player = state.player ?: Side.WHITE
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row {
                        EvalColumn(data.evalCurve.getOrElse(ply) { 0.0 }, player == Side.BLACK, Modifier.width(10.dp).height(boardSize))
                        Spacer(Modifier.width(6.dp))
                        ChessBoard(
                            position = shownPosition,
                            lastMove = if (retry != null) retry.tried else game.plies.getOrNull(ply - 1)?.move,
                            flipped = player == Side.BLACK,
                            colors = boardColors(settings.boardTheme),
                            pieces = pieces,
                            modifier = Modifier.size(boardSize).clip(RoundedCornerShape(6.dp)),
                            arrows = if (retry == null) listOfNotNull(moveAtPly?.bestMove?.takeIf { moveAtPly.quality.ordinal >= CoachQuality.INACCURACY.ordinal }
                                ?.let { BoardArrow(it.from, it.to, ZorixColors.Best.copy(alpha = 0.85f), 0.17f) }) else emptyList(),
                            showCoordinates = settings.showCoordinates,
                            interactive = retry != null && retry.solved != true && !retry.checking,
                            onMove = { f, t -> review.onRetryMove(com.zorix.chess.core.Move(f, t, if (shownPosition.isPromotion(f, t)) com.zorix.chess.core.PieceType.QUEEN else null)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (retry != null) {
                    val msg = when {
                        retry.checking -> null
                        retry.message != null -> retry.message
                        else -> stringResource(Res.string.review_retry_prompt)
                    }
                    CoachBubble(msg, busy = retry.checking, badge = retry.solved?.let {
                        { Pill(stringResource(if (it) Res.string.puzzle_correct else Res.string.puzzle_wrong), if (it) ZorixColors.Best else MaterialTheme.colorScheme.error, textColor = Color(0xFF101014)) }
                    }, onSpeak = msg?.let { m -> { onSpeak(m) } })
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(stringResource(Res.string.review_back_to_game), review::endRetry, Modifier.fillMaxWidth())
                } else {
                    NavRow(ply, game.plies.size, review::goTo)
                    moveAtPly?.let { m ->
                        CoachBubble(
                            m.message.ifBlank { null } ?: stringResource(m.quality.label()),
                            title = "${(m.index / 2) + 1}${if (m.side == Side.WHITE) "." else "..."} ${m.san}",
                            badge = { Pill("${m.quality.symbol} ${stringResource(m.quality.label())}", m.quality.color, textColor = Color(0xFF101014)) },
                            onSpeak = m.speech.takeIf { it.isNotBlank() }?.let { s -> { onSpeak(s) } },
                        )
                    }
                    SectionTitle(stringResource(Res.string.review_accuracy))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccuracyCard(if (player == Side.WHITE) playerName else "Zorix", data.accuracy[Side.WHITE] ?: 0.0, Modifier.weight(1f))
                        AccuracyCard(if (player == Side.BLACK) playerName else "Zorix", data.accuracy[Side.BLACK] ?: 0.0, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    EvalGraph(data.evalCurve, ply, Modifier.fillMaxWidth().height(90.dp)) { review.goTo(it) }
                    SectionTitle(stringResource(Res.string.review_moves))
                    CountsTable(data.counts[player].orEmpty())
                    val moments = data.keyMoments(player)
                    if (moments.isNotEmpty()) {
                        SectionTitle(stringResource(Res.string.review_key_moments))
                        moments.forEach { m -> MomentRow(m, onShow = { review.goTo(m.index + 1) }, onRetry = { review.startRetry(m) }) }
                    }
                    Spacer(Modifier.navigationBarsPadding().height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun NavRow(ply: Int, max: Int, goTo: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { goTo(0) }) { Icon(AppIcons.FirstPage, null) }
        IconButton(onClick = { goTo(ply - 1) }) { Icon(AppIcons.ChevronLeft, null) }
        Text("$ply / $max", style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { goTo(ply + 1) }) { Icon(AppIcons.ChevronRight, null) }
        IconButton(onClick = { goTo(max) }) { Icon(AppIcons.LastPage, null) }
    }
}

@Composable
private fun AccuracyCard(name: String, accuracy: Double, modifier: Modifier) {
    ZCard(modifier) {
        Column {
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(formatPercent(accuracy), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = accuracyColor(accuracy))
            Text(stringResource(Res.string.review_accuracy), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun accuracyColor(a: Double) = when {
    a >= 90 -> ZorixColors.Best
    a >= 75 -> Color(0xFF8FD14F)
    a >= 60 -> Color(0xFFF5C542)
    else -> Color(0xFFFF7A6B)
}

@Composable
private fun CountsTable(counts: Map<CoachQuality, Int>) {
    ZCard(Modifier.fillMaxWidth()) {
        Column {
            for (q in CoachQuality.entries) {
                val n = counts[q] ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(q.color), contentAlignment = Alignment.Center) {
                        Text(q.symbol, color = Color(0xFF101014), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(q.label()), Modifier.weight(1f))
                    Text(n.toString(), style = MaterialTheme.typography.titleMedium, color = if (n > 0) q.color else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MomentRow(m: ReviewedMove, onShow: () -> Unit, onRetry: () -> Unit) {
    ZCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = onShow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(m.quality.color), contentAlignment = Alignment.Center) {
                Text(m.quality.symbol, color = Color(0xFF101014), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${(m.index / 2) + 1}${if (m.side == Side.WHITE) "." else "..."} ${m.san}", style = MaterialTheme.typography.titleSmall.notation())
                Text(stringResource(m.quality.label()), style = MaterialTheme.typography.labelMedium, color = m.quality.color)
            }
            if (m.quality.ordinal >= CoachQuality.INACCURACY.ordinal) {
                PrimaryButton(stringResource(Res.string.review_retry), onRetry, icon = AppIcons.Replay)
            }
        }
    }
}

@Composable
private fun EvalColumn(pawns: Double, flipped: Boolean, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(4.dp))) {
        val white = ((pawns.coerceIn(-8.0, 8.0) + 8.0) / 16.0).toFloat()
        val h = size.height
        val whiteH = h * white
        if (!flipped) {
            drawRect(ZorixColors.EvalBlack, size = size)
            drawRect(ZorixColors.EvalWhite, topLeft = Offset(0f, h - whiteH), size = androidx.compose.ui.geometry.Size(size.width, whiteH))
        } else {
            drawRect(ZorixColors.EvalBlack, size = size)
            drawRect(ZorixColors.EvalWhite, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, whiteH))
        }
    }
}

@Composable
private fun EvalGraph(curve: List<Double>, ply: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.clip(RoundedCornerShape(14.dp)).background(ZorixColors.EvalBlack)) {
            val count = curve.size
            Canvas(Modifier.fillMaxSize().clickable { }) {
                if (count < 2) return@Canvas
                val w = size.width
                val h = size.height
                fun y(v: Double) = (h / 2 - (v.coerceIn(-8.0, 8.0) / 8.0 * h / 2)).toFloat()
                val path = Path().apply {
                    moveTo(0f, h / 2)
                    curve.forEachIndexed { i, v -> lineTo(w * i / (count - 1), y(v)) }
                    lineTo(w, h / 2)
                    close()
                }
                drawPath(path, ZorixColors.EvalWhite, style = Fill)
                drawLine(Color(0x55FFFFFF), Offset(0f, h / 2), Offset(w, h / 2), 1f)
                val x = w * ply / (count - 1)
                drawLine(ZorixColors.Red, Offset(x, 0f), Offset(x, h), 3f)
            }
            Row(Modifier.fillMaxSize()) {
                repeat(count) { i ->
                    Box(Modifier.weight(1f).fillMaxSize().clickable { onSelect(i) })
                }
            }
        }
    }
}
