package com.feiqi.utils

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.feiqi.data.model.CategorySum
import kotlin.math.min

object ChartUtils {

    data class ArcSegment(
        val startAngle: Float,
        val sweepAngle: Float,
        val category: CategorySum
    )

    fun calculateDonutSegments(
        categories: List<CategorySum>,
        total: Double
    ): List<ArcSegment> {
        if (total <= 0 || categories.isEmpty()) return emptyList()
        val safeTotal = categories.sumOf { it.amount }
        if (safeTotal <= 0) return emptyList()
        val segments = mutableListOf<ArcSegment>()
        var currentAngle = -90f
        categories.forEach { sum ->
            val sweep = (sum.amount / safeTotal * 360f).toFloat()
            segments.add(
                ArcSegment(
                    startAngle = currentAngle,
                    sweepAngle = sweep,
                    category = sum
                )
            )
            currentAngle += sweep
        }
        return segments
    }

    fun DrawScope.drawDonut(
        segments: List<ArcSegment>,
        strokeWidth: Float,
        sizeRatio: Float = 0.8f
    ) {
        val diameter = min(size.width, size.height) * sizeRatio
        val topLeftOffset = (min(size.width, size.height) - diameter) / 2f
        val rect = Rect(
            topLeftOffset,
            topLeftOffset,
            topLeftOffset + diameter,
            topLeftOffset + diameter
        )
        segments.forEach { segment ->
            drawArc(
                color = segment.category.color,
                startAngle = segment.startAngle,
                sweepAngle = segment.sweepAngle,
                useCenter = false,
                topLeft = rect.topLeft,
                size = Size(rect.width, rect.height),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}
