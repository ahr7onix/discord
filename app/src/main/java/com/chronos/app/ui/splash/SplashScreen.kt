package com.chronos.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chronos.app.ui.theme.Violet300
import com.chronos.app.ui.theme.Violet400
import com.chronos.app.ui.theme.Violet500
import com.chronos.app.ui.theme.Violet600
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated launch screen. A clock assembles itself — the ring sweeps in with a
 * glowing head, tick marks fade up, the hands spin and settle on a 10:10 watch
 * pose, a violet glow pulses out, then the CHRONOS wordmark reveals letter by
 * letter. Calls [onFinished] once when the sequence ends, or earlier on tap.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val ringSweep = remember { Animatable(0f) }
    val ticks = remember { Animatable(0f) }
    val hourAngle = remember { Animatable(0f) }
    val minuteAngle = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }

    val orbit = rememberInfiniteTransition(label = "orbit")
    val orbitAngle by orbit.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "orbitAngle",
    )

    val done = remember { AtomicBoolean(false) }
    val finish = { if (done.compareAndSet(false, true)) onFinished() }

    LaunchedEffect(Unit) {
        launch { delay(250); ticks.animateTo(1f, tween(650, easing = LinearOutSlowInEasing)) }
        ringSweep.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        launch { minuteAngle.animateTo(360f * 3 + 60f, tween(1150, easing = FastOutSlowInEasing)) }
        hourAngle.animateTo(360f * 2 + 300f, tween(1150, easing = FastOutSlowInEasing))
        launch { glow.animateTo(1f, tween(620, easing = FastOutSlowInEasing)) }
        reveal.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
        delay(650)
        finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(listOf(Color(0xFF1B1030), Color(0xFF0C0718), Color(0xFF070410)))
            )
            .pointerInput(Unit) { detectTapGestures { finish() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.28f
            val stroke = radius * 0.085f

            drawOrbit(center, radius * 1.42f, orbitAngle, ticks.value)
            drawGlowPulse(center, radius, glow.value)
            drawTicks(center, radius, ticks.value)
            drawRing(center, radius, stroke, ringSweep.value)
            drawHands(center, radius, hourAngle.value, minuteAngle.value, ringSweep.value)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer { translationY = size.minDimension * 0.17f },
        ) {
            Wordmark(reveal.value)
            val subtitleAlpha = ((reveal.value - 0.6f) / 0.4f).coerceIn(0f, 1f) * 0.85f
            Text(
                text = "agenda · lembretes",
                color = Violet300.copy(alpha = subtitleAlpha),
                fontSize = 13.sp,
                letterSpacing = 4.sp,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun Wordmark(reveal: Float) {
    val text = "CHRONOS"
    val n = text.length
    val stagger = 0.5f
    Row {
        text.forEachIndexed { i, ch ->
            val start = i.toFloat() / n * stagger
            val local = ((reveal - start) / (1f - stagger)).coerceIn(0f, 1f)
            Text(
                text = ch.toString(),
                color = Violet400,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = local
                    translationY = (1f - local) * 26f
                    val s = 0.85f + 0.15f * local
                    scaleX = s; scaleY = s
                },
            )
        }
    }
}

// ---- canvas pieces ------------------------------------------------------------

private fun DrawScope.drawRing(center: Offset, radius: Float, stroke: Float, progress: Float) {
    if (progress <= 0f) return
    val sweep = 360f * progress
    drawArc(
        brush = Brush.sweepGradient(
            0f to Violet600, 0.5f to Violet400, 1f to Violet300, center = center,
        ),
        startAngle = -90f,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    val headRad = Math.toRadians((-90f + sweep).toDouble())
    val head = Offset(
        center.x + radius * cos(headRad).toFloat(),
        center.y + radius * sin(headRad).toFloat(),
    )
    drawCircle(Violet300.copy(alpha = 0.35f), radius = stroke * 1.9f, center = head)
    drawCircle(Color.White, radius = stroke * 0.7f, center = head)
}

private fun DrawScope.drawTicks(center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0f) return
    for (i in 0 until 12) {
        val ang = Math.toRadians((i * 30 - 90).toDouble())
        val cardinal = i % 3 == 0
        val outer = radius * 0.80f
        val inner = radius * (if (cardinal) 0.66f else 0.72f)
        val c = cos(ang).toFloat(); val s = sin(ang).toFloat()
        drawLine(
            color = (if (cardinal) Violet300 else Violet500)
                .copy(alpha = alpha * (if (cardinal) 0.9f else 0.5f)),
            start = Offset(center.x + inner * c, center.y + inner * s),
            end = Offset(center.x + outer * c, center.y + outer * s),
            strokeWidth = if (cardinal) radius * 0.028f else radius * 0.018f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawHands(
    center: Offset, radius: Float, hour: Float, minute: Float, appear: Float,
) {
    if (appear < 0.15f) return
    val a = ((appear - 0.15f) / 0.85f).coerceIn(0f, 1f)
    rotate(hour, center) {
        drawLine(
            color = Violet300.copy(alpha = a),
            start = Offset(center.x, center.y + radius * 0.10f),
            end = Offset(center.x, center.y - radius * 0.42f),
            strokeWidth = radius * 0.055f, cap = StrokeCap.Round,
        )
    }
    rotate(minute, center) {
        drawLine(
            color = Color.White.copy(alpha = a),
            start = Offset(center.x, center.y + radius * 0.12f),
            end = Offset(center.x, center.y - radius * 0.60f),
            strokeWidth = radius * 0.038f, cap = StrokeCap.Round,
        )
    }
    drawCircle(Violet400.copy(alpha = 0.4f * a), radius = radius * 0.11f, center = center)
    drawCircle(Color.White.copy(alpha = a), radius = radius * 0.045f, center = center)
}

private fun DrawScope.drawGlowPulse(center: Offset, radius: Float, g: Float) {
    if (g <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            0f to Violet500.copy(alpha = 0.30f * g), 1f to Color.Transparent,
            center = center, radius = radius * 1.9f,
        ),
        radius = radius * 1.9f, center = center,
    )
    drawCircle(
        color = Violet400.copy(alpha = (1f - g) * 0.45f),
        radius = radius * (1.0f + g * 0.7f),
        center = center,
        style = Stroke(width = radius * 0.02f),
    )
}

private fun DrawScope.drawOrbit(center: Offset, radius: Float, angle: Float, alpha: Float) {
    if (alpha <= 0f) return
    for (i in 0 until 8) {
        val ang = Math.toRadians((angle + i * 45f).toDouble())
        val p = Offset(center.x + radius * cos(ang).toFloat(), center.y + radius * sin(ang).toFloat())
        drawCircle(Violet500.copy(alpha = alpha * 0.25f), radius = radius * 0.012f, center = p)
    }
}
