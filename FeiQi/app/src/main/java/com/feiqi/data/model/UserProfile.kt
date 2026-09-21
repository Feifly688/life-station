package com.feiqi.data.model

/**
 * 个人基本信息，用于健康页展示与 BMI 计算。
 * 通过 DataStore 以 JSON 形式持久化。
 */
data class UserProfile(
    val gender: String = "",
    val age: Int = 0,
    val heightCm: Double = 0.0
) {
    val hasHeight: Boolean get() = heightCm > 0
    val isEmpty: Boolean get() = gender.isBlank() && age <= 0 && heightCm <= 0

    /** 根据身高与传入体重计算 BMI；信息不全时返回 null。 */
    fun bmi(weightKg: Double?): Double? {
        if (weightKg == null || weightKg <= 0 || heightCm <= 0) return null
        val meter = heightCm / 100.0
        return weightKg / (meter * meter)
    }
}

/** 中国成人 BMI 参考区间。 */
object BmiScale {

    fun label(bmi: Double): String = when {
        bmi < 18.5 -> "偏瘦"
        bmi < 24.0 -> "正常"
        bmi < 28.0 -> "超重"
        else -> "肥胖"
    }

    fun hint(bmi: Double): String = when {
        bmi < 18.5 -> "适当增加营养摄入"
        bmi < 24.0 -> "保持得不错，继续维持"
        bmi < 28.0 -> "注意饮食与规律运动"
        else -> "建议咨询专业意见并调整作息"
    }
}
