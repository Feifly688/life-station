package com.feiqi.data.model

import androidx.compose.ui.graphics.Color

private val categoryColors = listOf(
    Color(0xFFB85C5C),
    Color(0xFF6B8E6B),
    Color(0xFFD4A05E),
    Color(0xFF6B7FA8),
    Color(0xFF8C7B6B),
    Color(0xFFA86B8E),
    Color(0xFF5D8A8A),
    Color(0xFF7A7A6D)
)

fun categoryColor(index: Int): Color = categoryColors[index % categoryColors.size]

data class CategorySum(
    val category: String,
    val amount: Double,
    val color: Color,
    val percent: Float
)
