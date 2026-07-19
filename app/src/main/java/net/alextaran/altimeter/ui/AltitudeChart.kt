package net.alextaran.altimeter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.alextaran.altimeter.storage.AltitudePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val ChartLineColor = Color(0xFF81C784)
private val ChartGradientTop = Color(0x4081C784)
private val ChartGradientBottom = Color(0x0081C784)
private val GridLineColor = Color(0x33FFFFFF)
private val AxisLabelColor = Color(0xAAFFFFFF)

@Composable
fun AltitudeChart(
    points: List<AltitudePoint>,
    windowStartMs: Long,
    windowEndMs: Long,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No altitude data for this period",
                color = Color(0xAAFFFFFF),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val paddingLeft = 56f
    val paddingRight = 16f
    val paddingTop = 20f
    val paddingBottom = 40f

    val minAlt = points.minOf { it.altitude }
    val maxAlt = points.maxOf { it.altitude }

    // Add some vertical breathing room and round to nice values
    val altRange = maxAlt - minAlt
    val niceStep = niceStep(altRange)
    val yMin = floor(minAlt / niceStep) * niceStep - niceStep
    val yMax = ceil(maxAlt / niceStep) * niceStep + niceStep
    val effectiveYRange = yMax - yMin

    val timeRange = (windowEndMs - windowStartMs).toFloat()

    Box(
        modifier = modifier
            .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val chartWidth = size.width - paddingLeft - paddingRight
            val chartHeight = size.height - paddingTop - paddingBottom

            // --- Draw horizontal grid lines and Y-axis labels ---
            val ySteps = generateYLabels(yMin, yMax, niceStep)
            for (yVal in ySteps) {
                val yPos = paddingTop + chartHeight * (1f - ((yVal - yMin) / effectiveYRange).toFloat())
                // Dashed grid line
                drawLine(
                    color = GridLineColor,
                    start = Offset(paddingLeft, yPos),
                    end = Offset(paddingLeft + chartWidth, yPos),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )
                // Y label
                drawContext.canvas.nativeCanvas.drawText(
                    "${yVal.roundToInt()} m",
                    paddingLeft - 8f,
                    yPos + 4f,
                    android.graphics.Paint().apply {
                        color = 0xAAFFFFFF.toInt()
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.RIGHT
                        isAntiAlias = true
                    }
                )
            }

            // --- Draw vertical grid lines and X-axis time labels ---
            val timeStepMs = niceTimeStep(timeRange.toLong())
            val firstTimeTick = ((windowStartMs / timeStepMs) + 1) * timeStepMs
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            var tickTime = firstTimeTick
            while (tickTime <= windowEndMs) {
                val xPos = paddingLeft + chartWidth * ((tickTime - windowStartMs).toFloat() / timeRange)
                drawLine(
                    color = GridLineColor,
                    start = Offset(xPos, paddingTop),
                    end = Offset(xPos, paddingTop + chartHeight),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )
                drawContext.canvas.nativeCanvas.drawText(
                    timeFormat.format(Date(tickTime)),
                    xPos,
                    paddingTop + chartHeight + 30f,
                    android.graphics.Paint().apply {
                        color = 0xAAFFFFFF.toInt()
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                )
                tickTime += timeStepMs
            }

            // --- Build the line path ---
            if (points.size >= 2) {
                val linePath = Path()
                val fillPath = Path()

                for (i in points.indices) {
                    val p = points[i]
                    val x = paddingLeft + chartWidth * ((p.timestamp - windowStartMs).toFloat() / timeRange)
                    val y = paddingTop + chartHeight * (1f - ((p.altitude - yMin) / effectiveYRange).toFloat())
                    if (i == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, paddingTop + chartHeight)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                // Close fill path
                val lastPt = points.last()
                val lastX = paddingLeft + chartWidth * ((lastPt.timestamp - windowStartMs).toFloat() / timeRange)
                fillPath.lineTo(lastX, paddingTop + chartHeight)
                fillPath.close()

                // Gradient fill under the line
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(ChartGradientTop, ChartGradientBottom),
                        startY = paddingTop,
                        endY = paddingTop + chartHeight
                    )
                )

                // Draw the line
                drawPath(
                    path = linePath,
                    color = ChartLineColor,
                    style = Stroke(
                        width = 3f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            } else if (points.size == 1) {
                // Single point — draw a dot
                val p = points[0]
                val x = paddingLeft + chartWidth * ((p.timestamp - windowStartMs).toFloat() / timeRange)
                val y = paddingTop + chartHeight * (1f - ((p.altitude - yMin) / effectiveYRange).toFloat())
                drawCircle(
                    color = ChartLineColor,
                    radius = 6f,
                    center = Offset(x, y)
                )
            }
        }
    }
}

/**
 * Pick a "nice" step value for the altitude axis depending on the data range.
 */
private fun niceStep(range: Double): Double {
    if (range <= 0) return 10.0
    val rough = range / 4.0
    val magnitude = Math.pow(10.0, floor(Math.log10(rough)))
    val residual = rough / magnitude
    return when {
        residual <= 1.0 -> magnitude
        residual <= 2.0 -> 2.0 * magnitude
        residual <= 5.0 -> 5.0 * magnitude
        else -> 10.0 * magnitude
    }
}

/**
 * Generate Y-axis label values from min to max with the given step.
 */
private fun generateYLabels(min: Double, max: Double, step: Double): List<Double> {
    val labels = mutableListOf<Double>()
    var current = min
    while (current <= max + step * 0.01) {
        labels.add(current)
        current += step
    }
    return labels
}

/**
 * Pick a "nice" time step for the X-axis in milliseconds.
 */
private fun niceTimeStep(rangeMs: Long): Long {
    val candidates = longArrayOf(
        60_000L,         // 1 min
        5 * 60_000L,     // 5 min
        10 * 60_000L,    // 10 min
        15 * 60_000L,    // 15 min
        30 * 60_000L,    // 30 min
        60 * 60_000L     // 1 hour
    )
    val targetTicks = 5
    for (c in candidates) {
        if (rangeMs / c in 2..targetTicks.toLong() * 2) return c
    }
    return candidates.last()
}
