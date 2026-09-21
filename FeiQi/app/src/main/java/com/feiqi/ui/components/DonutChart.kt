package com.feiqi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.feiqi.data.model.CategorySum
import com.feiqi.utils.ChartUtils
import kotlin.math.min

@Composable
fun DonutChart(
    categories: List<CategorySum>,
    total: Double,
    centerText: String,
    modifier: Modifier = Modifier
) {
    val segments = ChartUtils.calculateDonutSegments(categories, total)
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = min(size.width, size.height) * 0.82f
            val offset = (min(size.width, size.height) - diameter) / 2f
            val rect = Rect(offset, offset, offset + diameter, offset + diameter)
            drawArc(
                color = outlineColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = rect.topLeft,
                size = Size(rect.width, rect.height),
                style = Stroke(width = 28.dp.toPx())
            )
            segments.forEach { segment ->
                drawArc(
                    color = segment.category.color,
                    startAngle = segment.startAngle,
                    sweepAngle = segment.sweepAngle,
                    useCenter = false,
                    topLeft = rect.topLeft,
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = 28.dp.toPx())
                )
            }
        }
        Text(
            text = centerText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
