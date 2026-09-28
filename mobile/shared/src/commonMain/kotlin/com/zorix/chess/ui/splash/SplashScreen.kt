package com.zorix.chess.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zorix.chess.resources.*
import com.zorix.chess.controller.EngineStatus
import com.zorix.chess.ui.components.BrandUnderline
import com.zorix.chess.ui.components.ZorixWordmark
import com.zorix.chess.ui.theme.ZorixColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Width of the emblem; matches the Android 12+ system splash icon so the hand-off is seamless. */
private val EMBLEM_WIDTH = 150.dp

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Animated brand intro: the ZC emblem (continuing from the system splash) lifts up with a light
 * sweep, "ZORIX CHESS" tracks in, the red line draws out, then the whole screen fades away.
 * It also covers the one-time copy of the engine's neural network on first launch.
 */
@Composable
fun SplashScreen(engine: EngineStatus, onFinished: () -> Unit) {
    val lift = remember { Animatable(0f) }
    val shine = remember { Animatable(-0.3f) }
    val glow = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val line = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    val status by rememberUpdatedState(engine)
    val finish by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { glow.animateTo(1f, tween(900, easing = Emphasized)) }
            launch { lift.animateTo(1f, tween(700, delayMillis = 150, easing = Emphasized)) }
            launch { shine.animateTo(1.3f, tween(900, delayMillis = 250, easing = LinearEasing)) }
            launch { word.animateTo(1f, tween(800, delayMillis = 450, easing = Emphasized)) }
            launch { line.animateTo(1f, tween(650, delayMillis = 900, easing = Emphasized)) }
            launch { tagline.animateTo(1f, tween(500, delayMillis = 1200)) }
        }
        delay(250)
        // Wait for the first-launch network copy; never block the app on a slow engine start.
        withTimeoutOrNull(60_000) {
            snapshotFlow { status }.first { it !is EngineStatus.Preparing }
        }
        exit.animateTo(0f, tween(380))
        finish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ZorixColors.Background)
            .alpha(exit.value),
        contentAlignment = Alignment.Center,
    ) {
        // Soft red glow behind the emblem.
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2 - 60.dp.toPx() * lift.value)
            drawCircle(
                Brush.radialGradient(
                    listOf(ZorixColors.Red.copy(alpha = 0.22f * glow.value), Color.Transparent),
                    center = c,
                    radius = size.minDimension * 0.55f,
                ),
                radius = size.minDimension * 0.55f,
                center = c,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(Res.drawable.zc_emblem),
                contentDescription = stringResource(Res.string.app_name),
                modifier = Modifier
                    .width(EMBLEM_WIDTH)
                    .graphicsLayer {
                        translationY = -60.dp.toPx() * lift.value
                        val s = 1f - 0.08f * lift.value
                        scaleX = s
                        scaleY = s
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        // Metallic light sweep, clipped to the emblem's own pixels.
                        val x = size.width * shine.value
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                                start = Offset(x - size.width * 0.25f, 0f),
                                end = Offset(x + size.width * 0.25f, size.height),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    },
            )
        }

        Column(
            Modifier.padding(top = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ZorixWordmark(
                fontSize = 24.sp,
                letterSpacing = (0.62f - 0.34f * word.value).em,
                alpha = word.value,
                modifier = Modifier.graphicsLayer { translationY = 18.dp.toPx() * (1f - word.value) },
            )
            Spacer(Modifier.height(14.dp))
            BrandUnderline(Modifier.width(250.dp).height(2.dp), fraction = line.value)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(Res.string.tagline),
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.04.em),
                color = ZorixColors.SilverDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(tagline.value),
            )
        }

        EngineProgress(engine, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp))
    }
}

@Composable
private fun EngineProgress(engine: EngineStatus, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (engine) {
            is EngineStatus.Preparing -> if (engine.progress > 0f) {
                Text(
                    stringResource(Res.string.splash_preparing, "${(engine.progress * 100).toInt()}%"),
                    style = MaterialTheme.typography.labelMedium,
                    color = ZorixColors.SilverDim,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { engine.progress },
                    modifier = Modifier.width(180.dp).height(3.dp).clip(CircleShape),
                    color = ZorixColors.Red,
                    trackColor = ZorixColors.SurfaceHighest,
                )
            }
            else -> Text(
                stringResource(Res.string.offline_badge).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.em),
                color = ZorixColors.SilverDim.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.size(1.dp))
    }
}
