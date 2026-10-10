package com.zorix.chess.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.zorix.chess.controller.SpeechStatus
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorix.chess.resources.*
import com.zorix.chess.ui.theme.ZorixColors
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Title row for full screens, with an optional back button and actions. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, stringResource(Res.string.action_back)) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        actions()
    }
}

/** Rounded card used across the app. */
@Composable
fun ZCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = color,
    ) {
        Box(Modifier.padding(padding)) { content() }
    }
}

/** Card with a red gradient, for the most important action on a screen. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(ZorixColors.palette.heroGradient))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
    ) { content() }
}

@Composable
fun StarRow(stars: Int, max: Int = 3, size: Dp = 16.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(max) { i ->
            Icon(
                if (i < stars) AppIcons.Star else AppIcons.StarBorder,
                null,
                tint = if (i < stars) Color(0xFFFFC53D) else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier, tint: Color = ZorixColors.RedBright) {
    ZCard(modifier, padding = 14.dp) {
        Column {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ZorixColors.Red, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        icon?.let {
            Icon(it, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        icon?.let {
            Icon(it, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Progress(fraction: Float, modifier: Modifier = Modifier, color: Color = ZorixColors.Best) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

/** Round Zorix emblem, used as the coach's avatar. */
@Composable
fun CoachAvatar(size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0xFF0B0B0E)).border(1.5.dp, ZorixColors.Red.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(Res.drawable.zc_emblem), null, Modifier.size(size * 0.78f))
    }
}

/** What the coach's voice is doing (provided at the root of the app). */
val LocalSpeechStatus = compositionLocalOf { SpeechStatus.IDLE }

/** Whether the coach reads new explanations aloud (voice switched on and available for the language). */
val LocalVoiceOn = compositionLocalOf { false }

/**
 * Whether the coach text [message] may be shown yet. With the voice on, a new explanation waits
 * until the voice starts reading it, so text and voice arrive together; until then the screen shows
 * "Zorix is thinking…". If no voice is asked for within a moment (or it fails), the text shows anyway.
 */
@Composable
fun rememberCoachReveal(message: String?, waitForVoice: Boolean = true): Boolean {
    val voiceOn = LocalVoiceOn.current && waitForVoice
    val status = rememberUpdatedState(LocalSpeechStatus.current)
    var revealed by remember(message) { mutableStateOf(!voiceOn || message.isNullOrBlank()) }
    LaunchedEffect(message, voiceOn) {
        if (revealed) return@LaunchedEffect
        if (!voiceOn) { revealed = true; return@LaunchedEffect }
        val asked = withTimeoutOrNull(VOICE_REQUEST_WAIT_MS) { snapshotFlow { status.value }.first { it == SpeechStatus.PREPARING } }
        if (asked != null) withTimeoutOrNull(VOICE_READY_WAIT_MS) { snapshotFlow { status.value }.first { it != SpeechStatus.PREPARING } }
        revealed = true
    }
    return revealed
}

/** How long a new explanation waits for the voice to be asked for it (lessons start reading after a short pause). */
private const val VOICE_REQUEST_WAIT_MS = 1_200L

/** The longest a text waits for its voice (the voice itself gives up after 20 s). */
private const val VOICE_READY_WAIT_MS = 21_000L

/** "Zorix is thinking…" with a spinner, shown in place of a coach text until its voice is ready. */
@Composable
fun CoachThinking(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ZorixColors.RedBright)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(Res.string.zorix_thinking), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Next to a coach text: a small spinner with "Preparing voice…" until the voice starts, then a
 * pulsing speaker while it speaks. Nothing when the voice is idle.
 */
@Composable
fun VoiceIndicator(modifier: Modifier = Modifier, showPreparing: Boolean = true) {
    when (LocalSpeechStatus.current) {
        SpeechStatus.PREPARING -> if (showPreparing) Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = ZorixColors.RedBright)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.voice_preparing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpeechStatus.SPEAKING -> {
            val pulse = rememberInfiniteTransition(label = "voice")
            val alpha by pulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "voiceAlpha")
            Icon(AppIcons.VolumeUp, null, tint = ZorixColors.RedBright.copy(alpha = alpha), modifier = modifier.size(18.dp))
        }
        SpeechStatus.IDLE -> Unit
    }
}

/**
 * The coach speaking: avatar, title (with an optional verdict badge), the explanation and a
 * button to hear it again.
 */
@Composable
fun CoachBubble(
    message: String?,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.coach_name),
    busy: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    onSpeak: (() -> Unit)? = null,
    /** The text is read aloud automatically: show it together with the voice (see [rememberCoachReveal]). */
    waitForVoice: Boolean = false,
) {
    val revealed = rememberCoachReveal(message, waitForVoice)
    ZCard(modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            CoachAvatar()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = ZorixColors.RedBright, modifier = Modifier.weight(1f, fill = false))
                    badge?.let {
                        Spacer(Modifier.width(8.dp))
                        it()
                    }
                    Spacer(Modifier.weight(1f))
                    if (onSpeak != null && !message.isNullOrBlank()) {
                        if (LocalSpeechStatus.current == SpeechStatus.IDLE) {
                            IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                                Icon(AppIcons.VolumeUp, stringResource(Res.string.action_listen), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            VoiceIndicator(Modifier.padding(horizontal = 6.dp), showPreparing = revealed)
                        }
                    }
                }
                AnimatedContent(
                    targetState = if (busy && message == null) null else if (!revealed) THINKING else message,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "coach",
                ) { m ->
                    if (m == THINKING) {
                        CoachThinking(Modifier.padding(top = 6.dp))
                    } else if (m == null) {
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(Res.string.coach_evaluating), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(
                            m,
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 23.sp),
                        )
                    }
                }
            }
        }
    }
}

/** Placeholder text standing for "waiting for the voice" in the coach bubble. */
private const val THINKING = "\u0000thinking"

/** Small rounded label. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = Color.White) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = textColor, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Section title inside scrolling screens. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 18.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}
