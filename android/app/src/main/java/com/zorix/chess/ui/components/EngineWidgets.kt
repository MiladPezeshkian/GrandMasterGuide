package com.zorix.chess.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorix.chess.R
import com.zorix.chess.controller.ChessUiState
import com.zorix.chess.controller.EngineLine
import com.zorix.chess.controller.EngineStatus
import com.zorix.chess.controller.EngineView
import com.zorix.chess.controller.HintState
import com.zorix.chess.engine.uci.Score
import com.zorix.chess.ui.label
import com.zorix.chess.ui.theme.NumberStyle
import com.zorix.chess.ui.theme.ZorixColors
import com.zorix.chess.ui.theme.notation
import java.util.Locale

/** Vertical evaluation bar: white share = White's winning chances. */
@Composable
fun EvalBar(score: Score?, flipped: Boolean, modifier: Modifier = Modifier) {
    val target = score?.winChance()?.toFloat() ?: 0.5f
    val white by animateFloatAsState(target, tween(durationMillis = 450), label = "eval")
    val active = score != null
    Canvas(modifier.clip(RoundedCornerShape(3.dp))) {
        drawRect(if (active) ZorixColors.EvalBlack else Color(0xFF34343C))
        val h = size.height * white
        val top = if (flipped) 0f else size.height - h
        drawRect(if (active) ZorixColors.EvalWhite else Color(0xFF8A8A94), Offset(0f, top), Size(size.width, h))
        drawLine(ZorixColors.Red.copy(alpha = 0.7f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
    }
}

/** Small evaluation pill, light when White is better, dark when Black is. */
@Composable
fun ScoreChip(score: Score, modifier: Modifier = Modifier) {
    val whiteBetter = score.pawns() >= 0
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (whiteBetter) ZorixColors.EvalWhite else Color(0xFF0A0A0C))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            score.format(),
            style = NumberStyle.copy(fontSize = 13.sp).notation(),
            color = if (whiteBetter) Color(0xFF111114) else Color.White,
        )
    }
}

fun formatNodes(n: Long): String = when {
    n >= 1_000_000_000 -> String.format(Locale.US, "%.1fG", n / 1e9)
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1e6)
    n >= 1_000 -> String.format(Locale.US, "%.0fk", n / 1e3)
    else -> n.toString()
}

fun formatSeconds(ms: Int): String = String.format(Locale.US, "%.1f", ms / 1000.0)

/** The card under the board: hint, live analysis, game result or engine status. */
@Composable
fun EnginePanel(
    state: ChessUiState,
    onStop: () -> Unit,
    onPlaySuggestion: () -> Unit,
    onDismissSuggestion: () -> Unit,
    onPlayLine: (Int) -> Unit,
    onRetryEngine: () -> Unit,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            EngineStatusRow(state.engine, onRetryEngine)
            val status = state.game.status
            val hint = state.hint
            val mode = when {
                status.isOver -> 0
                hint is HintState.Thinking -> 1
                hint is HintState.Ready -> 2
                state.analysisOn -> 3
                else -> 4
            }
            AnimatedContent(targetState = mode, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "panel") { m ->
                when (m) {
                    0 -> GameOverContent(state, onUndo, onNewGame)
                    1 -> (state.hint as? HintState.Thinking)?.let { ThinkingContent(it, state, onStop) }
                    2 -> (state.hint as? HintState.Ready)?.let { SuggestionContent(it.view, onPlaySuggestion, onDismissSuggestion) }
                    3 -> AnalysisContent(state.analysis?.takeIf { it.fen == state.fen }, onPlayLine)
                    else -> IdleContent(state)
                }
            }
        }
    }
}

@Composable
private fun EngineStatusRow(status: EngineStatus, onRetry: () -> Unit) {
    when (status) {
        is EngineStatus.Ready -> Unit
        is EngineStatus.Preparing -> {
            Text(stringResource(R.string.engine_preparing), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
            Spacer(Modifier.height(12.dp))
        }
        EngineStatus.Starting -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.engine_starting), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
        }
        is EngineStatus.Failed -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.engine_failed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                    Text(status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onRetry) { Text(stringResource(R.string.engine_retry)) }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun IdleContent(state: ChessUiState) {
    val side = stringResource(state.game.position.sideToMove.label())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Bulb, null, tint = ZorixColors.Red, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.hint_idle, side),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        ThinkTimeBadge(state.settings.thinkTimeMs)
    }
}

@Composable
fun ThinkTimeBadge(thinkTimeMs: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Timer, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.think_time_value, formatSeconds(thinkTimeMs)),
            style = NumberStyle.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThinkingContent(hint: HintState.Thinking, state: ChessUiState, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(hint.startedAtMs) {
        while (true) withFrameMillis { now = System.currentTimeMillis() }
    }
    val fraction = ((now - hint.startedAtMs).toFloat() / hint.budgetMs).coerceIn(0f, 1f)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ZorixColors.Red)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.hint_thinking), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onStop) {
                Icon(AppIcons.Stop, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_stop))
            }
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = ZorixColors.Red,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(10.dp))
        val view = hint.view
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                view?.bestSan ?: "…",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold).notation(),
            )
            Spacer(Modifier.width(10.dp))
            view?.score?.let { ScoreChip(it) }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.hint_depth, view?.depth ?: 0) +
                    "  ·  " + formatSeconds(minOf(hint.budgetMs.toLong(), now - hint.startedAtMs).toInt()) + " / " +
                    stringResource(R.string.think_time_value, formatSeconds(state.settings.thinkTimeMs)),
                style = NumberStyle.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuggestionContent(view: EngineView, onPlay: () -> Unit, onDismiss: () -> Unit) {
    val side = stringResource(view.sideToMove.label())
    Column {
        Text(
            stringResource(R.string.hint_best_move, side),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                view.bestSan ?: "—",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold).notation(),
                color = ZorixColors.Best,
            )
            Spacer(Modifier.width(12.dp))
            view.score?.let { ScoreChip(it) }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.hint_depth, view.depth),
                style = NumberStyle.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        view.lines.firstOrNull()?.let { line ->
            Spacer(Modifier.height(4.dp))
            Text(
                line.text,
                style = MaterialTheme.typography.bodySmall.notation(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onPlay, enabled = view.bestMove != null) {
                Icon(AppIcons.Play, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_play) + (view.bestSan?.let { " $it" } ?: ""))
            }
        }
    }
}

private val lineColors = listOf(ZorixColors.Best, ZorixColors.Line2, ZorixColors.Line3)

@Composable
private fun AnalysisContent(view: EngineView?, onPlayLine: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Analysis, null, tint = ZorixColors.Red, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.analysis_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (view != null) {
                Text(
                    stringResource(R.string.hint_depth, view.depth) + "  ·  " + formatNodes(view.nps) + " n/s",
                    style = NumberStyle.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (view == null || view.lines.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.analysis_waiting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            view.lines.forEachIndexed { index, line -> AnalysisLineRow(line, lineColors[index % lineColors.size]) { onPlayLine(index) } }
        }
    }
}

@Composable
private fun AnalysisLineRow(line: EngineLine, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 4.dp, height = 22.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        ScoreChip(line.score, Modifier.widthIn(min = 58.dp))
        Spacer(Modifier.width(10.dp))
        Text(line.text, style = MaterialTheme.typography.bodyMedium.notation(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GameOverContent(state: ChessUiState, onUndo: () -> Unit, onNewGame: () -> Unit) {
    val label = state.game.status.label()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(ZorixColors.Red))
            Spacer(Modifier.width(10.dp))
            Text(label?.let { stringResource(it) } ?: "", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onUndo) {
                Icon(AppIcons.Undo, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_undo))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onNewGame) {
                Icon(AppIcons.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_new_game))
            }
        }
    }
}
