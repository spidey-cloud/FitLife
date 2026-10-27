package com.fitlife.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitlife.app.data.ride.GeoPoint
import kotlin.math.cos
import kotlin.math.max

/**
 * Stylised route drawing (no map tiles, no network, no API key): the GPS trace is
 * projected equirectangularly and fitted to the canvas over a faint grid.
 */
@Composable
fun RouteTrace(
    points: List<GeoPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    startColor: Color = MaterialTheme.colorScheme.secondary,
    showLiveDot: Boolean = false
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val step = 28.dp.toPx()
        var gx = 0f
        while (gx < size.width) { drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), 1f); gx += step }
        var gy = 0f
        while (gy < size.height) { drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), 1f); gy += step }

        if (points.size < 2) {
            if (points.size == 1) drawCircle(color, 7.dp.toPx(), Offset(size.width / 2, size.height / 2))
            return@Canvas
        }
        val midLat = points.sumOf { it.lat } / points.size
        val kx = cos(Math.toRadians(midLat))
        val xs = points.map { it.lng * kx }
        val ys = points.map { it.lat }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val pad = 20.dp.toPx()
        val spanX = max(maxX - minX, 1e-6); val spanY = max(maxY - minY, 1e-6)
        val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val offX = (size.width - spanX * scale) / 2
        val offY = (size.height - spanY * scale) / 2
        fun project(i: Int) = Offset(
            (offX + (xs[i] - minX) * scale).toFloat(),
            (size.height - (offY + (ys[i] - minY) * scale)).toFloat()
        )
        val path = Path().apply {
            val p0 = project(0); moveTo(p0.x, p0.y)
            for (i in 1 until points.size) { val p = project(i); lineTo(p.x, p.y) }
        }
        // glow + line
        drawPath(path, color.copy(alpha = 0.25f), style = Stroke(12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, color, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val start = project(0); val end = project(points.lastIndex)
        drawCircle(Color.White, 7.dp.toPx(), start); drawCircle(startColor, 5.dp.toPx(), start)
        if (showLiveDot) {
            drawCircle(color.copy(alpha = 0.25f), 14.dp.toPx(), end)
            drawCircle(color, 7.dp.toPx(), end)
        } else {
            drawCircle(Color.White, 7.dp.toPx(), end); drawCircle(color, 5.dp.toPx(), end)
        }
    }
}

/** Filled elevation profile; values are altitudes in metres along the route. */
@Composable
fun ElevationChart(
    altitudes: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 90.dp
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (altitudes.size < 2) return@Canvas
        val min = altitudes.min(); val maxA = altitudes.max()
        val span = max(maxA - min, 10.0)   // flat rides don't look mountainous
        val dx = size.width / (altitudes.size - 1)
        fun y(a: Double) = (size.height - ((a - min) / span) * size.height * 0.9f).toFloat()
        val line = Path().apply {
            moveTo(0f, y(altitudes[0]))
            altitudes.forEachIndexed { i, a -> if (i > 0) lineTo(dx * i, y(a)) }
        }
        val fill = Path().apply {
            addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f))))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Minimal line for live speed history. */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) return@Canvas
        val maxV = max(values.max(), 5f)
        val dx = size.width / (values.size - 1)
        val path = Path().apply {
            values.forEachIndexed { i, v ->
                val p = Offset(dx * i, size.height - (v / maxV) * size.height)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
