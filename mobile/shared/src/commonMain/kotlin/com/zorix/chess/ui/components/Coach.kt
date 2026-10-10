package com.zorix.chess.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorix.chess.controller.ChessUiState
import com.zorix.chess.controller.MoveFeedback
import com.zorix.chess.resources.*
import com.zorix.chess.ui.color
import com.zorix.chess.ui.label
import com.zorix.chess.ui.symbol
import com.zorix.chess.ui.theme.ZorixColors
import com.zorix.chess.ui.theme.notation
import org.jetbrains.compose.resources.stringResource

/** Coach verdict for the last move: quality badge, explanation and the better move. */
@Composable
fun CoachCard(state: ChessUiState, modifier: Modifier = Modifier) {
    if (!state.settings.coachMode || state.game.plies.isEmpty()) return
    val feedback = state.lastFeedback
    if (feedback == null && !state.coachBusy) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        AnimatedContent(
            targetState = feedback,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "coach",
        ) { f ->
            if (f == null) CoachWorking() else CoachVerdict(f)
        }
    }
}

@Composable
private fun CoachWorking() {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(Res.string.coach_evaluating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CoachVerdict(f: MoveFeedback) {
    val q = f.quality
    // The explanation is read aloud: it shows when the voice starts, "Zorix is thinking…" until then.
    val revealed = rememberCoachReveal(f.message.ifBlank { null })
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(q.color),
            contentAlignment = Alignment.Center,
        ) {
            Text(q.symbol, color = Color(0xFF101014), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.School, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(Res.string.coach_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                VoiceIndicator(showPreparing = revealed)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    f.san,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold).notation(),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(q.label()),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = q.color,
                )
                Spacer(Modifier.weight(1f))
                f.scoreAfter?.let { ScoreChip(it) }
            }
            Spacer(Modifier.height(4.dp))
            if (!revealed) {
                CoachThinking(Modifier.padding(vertical = 2.dp))
            } else if (f.message.isNotBlank()) {
                Text(
                    f.message,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            val best = f.bestSan
            if (best != null && best != f.san) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Bulb, null, tint = ZorixColors.Best, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(Res.string.coach_best_was, best),
                        style = MaterialTheme.typography.labelLarge,
                        color = ZorixColors.Best,
                    )
                }
            }
        }
    }
}
