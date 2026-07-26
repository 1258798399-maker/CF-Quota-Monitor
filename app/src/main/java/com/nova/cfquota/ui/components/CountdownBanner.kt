package com.nova.cfquota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.cfquota.ui.theme.LocalCfExtraColors

/**
 * Bottom hint + reset countdown banner (soft cream background, blue info icon).
 */
@Composable
fun CountdownBanner(
    countdownText: String,
    totalUsedText: String,
    modifier: Modifier = Modifier
) {
    val extra = LocalCfExtraColors.current
    val onSurface = MaterialTheme.colorScheme.onSurface

    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = onSurface)) {
            append("每日请求数重置清零：")
        }
        withStyle(SpanStyle(color = onSurface.copy(alpha = 0.85f))) {
            append("距离重置还有 ")
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = extra.quota)) {
            append(countdownText)
        }
        withStyle(SpanStyle(color = onSurface.copy(alpha = 0.85f))) {
            append("，北京时间 (UTC+8) ")
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = extra.quota)) {
            append("8:00重置")
        }
        withStyle(SpanStyle(color = onSurface.copy(alpha = 0.85f))) {
            append("，今日使用情况总计：")
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = extra.quota)) {
            append(totalUsedText)
        }
        withStyle(SpanStyle(color = onSurface.copy(alpha = 0.85f))) {
            append("。")
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(extra.bannerBackground)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "info",
            tint = extra.brand,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 1.dp)
        )
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
