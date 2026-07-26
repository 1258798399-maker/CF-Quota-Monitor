package com.nova.cfquota.core

import java.text.DecimalFormat
import java.util.Locale

object Formatters {
    private val grouping = DecimalFormat("#,###")

    /** 46373 -> "46,373" */
    fun thousands(value: Long): String = grouping.format(value)

    /** 46.373 -> "46.37" */
    fun percent(value: Double): String = String.format(Locale.US, "%.2f", value)

    /** seconds -> "04小时08分56秒" */
    fun countdown(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return String.format(Locale.US, "%02d小时%02d分%02d秒", h, m, sec)
    }
}
