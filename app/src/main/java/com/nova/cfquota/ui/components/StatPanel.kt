package com.nova.cfquota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.cfquota.ui.theme.LocalCfExtraColors

/**
 * Three-column statistics panel on a soft light-blue background.
 */
@Composable
fun StatPanel(
    workersText: String,
    pagesText: String,
    quotaText: String,
    modifier: Modifier = Modifier
) {
    val extra = LocalCfExtraColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extra.panelBackground)
            .padding(vertical = 16.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatColumn(
                label = "WORKERS 请求",
                value = workersText,
                valueColor = extra.workers,
                labelColor = extra.label,
                modifier = Modifier.weight(1f)
            )
            VDivider(extra.label.copy(alpha = 0.25f))
            StatColumn(
                label = "PAGES 请求",
                value = pagesText,
                valueColor = extra.pages,
                labelColor = extra.label,
                modifier = Modifier.weight(1f)
            )
            VDivider(extra.label.copy(alpha = 0.25f))
            StatColumn(
                label = "日配额",
                value = quotaText,
                valueColor = extra.quota,
                labelColor = extra.label,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VDivider(color: Color) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .width(1.dp)
            .background(color)
    )
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            // Keep the 6-digit quota value on a single line on portrait screens:
            // no wrapping, no overflow onto a second line that would break the
            // three-column grid.
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
