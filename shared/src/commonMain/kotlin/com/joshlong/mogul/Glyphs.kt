package com.joshlong.mogul

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser

// the attach-to icons from the popup's svgs, drawn on the same 24 by 24 grid

private fun path(d: String) = PathParser().parsePathString(d).toPath()

private val globeMeridian = path("M3 12h18")
private val globeLongitude = path("M12 3a14 14 0 0 1 0 18a14 14 0 0 1 0-18")
private val microphone = path("M8.5 10.5a3.5 3.5 0 0 0 7 0M12 14v3.5M10 17.5h4M6.3 7.5a6 6 0 0 0 0 5.5M17.7 7.5a6 6 0 0 1 0 5.5")
private val feedWaves = path("M6 11.2a6.8 6.8 0 0 1 6.8 6.8M6 6.4A11.6 11.6 0 0 1 17.6 18")

private fun stroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.onGrid(draw: DrawScope.() -> Unit) = scale(size.minDimension / 24f, pivot = Offset.Zero) { draw() }

@Composable
fun Glyph(kind: AttachKind, color: Color, modifier: Modifier = Modifier) = when (kind) {
    AttachKind.GLOBAL -> Canvas(modifier) {
        onGrid {
            drawCircle(color, radius = 9f, center = Offset(12f, 12f), style = stroke(2f))
            drawPath(globeMeridian, color, style = stroke(2f))
            drawPath(globeLongitude, color, style = stroke(2f))
        }
    }

    AttachKind.PODCASTS -> Canvas(modifier) {
        onGrid {
            drawRoundRect(color, Offset(2.5f, 2.5f), Size(19f, 19f), CornerRadius(4.5f), style = stroke(2f))
            drawRoundRect(color, Offset(10.4f, 6f), Size(3.2f, 6f), CornerRadius(1.6f))
            drawRoundRect(color, Offset(10.4f, 6f), Size(3.2f, 6f), CornerRadius(1.6f), style = stroke(1.4f))
            drawPath(microphone, color, style = stroke(1.7f))
        }
    }

    // a filled tile with the feed glyph cut out of it, so it shows whatever is behind
    AttachKind.BLOGS -> Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        onGrid {
            drawRoundRect(color, Offset(2f, 2f), Size(20f, 20f), CornerRadius(4.5f))
            drawCircle(Color.Black, radius = 1.9f, center = Offset(7.3f, 16.7f), blendMode = BlendMode.Clear)
            drawPath(feedWaves, Color.Black, style = stroke(2.4f), blendMode = BlendMode.Clear)
        }
    }
}
