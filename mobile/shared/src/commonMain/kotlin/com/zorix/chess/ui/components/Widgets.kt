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
) {
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
                        IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                            Icon(AppIcons.VolumeUp, stringResource(Res.string.action_listen), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                AnimatedContent(
                    targetState = if (busy && message == null) null else message,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "coach",
                ) { m ->
                    if (m == null) {
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
