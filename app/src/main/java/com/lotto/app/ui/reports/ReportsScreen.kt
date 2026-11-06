package com.lotto.app.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotto.app.ui.components.buttons.BackButton

private val monthLabels = listOf(
    "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
    "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
)

@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    onOpenTotalSales: () -> Unit,
    onOpenPerformance: () -> Unit
) {
    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton(onBack = onBack)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "รายงานยอด",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            ReportMenuItem(
                title = "ยอดขายทั้งหมด",
                icon = Icons.Outlined.ReceiptLong,
                onClick = onOpenTotalSales
            )
            ReportMenuItem(
                title = "ยอดผลประกอบการ",
                icon = Icons.Outlined.Assessment,
                onClick = onOpenPerformance
            )
        }
    }
}

@Composable
private fun ReportMenuItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null
            )
        }
    }
}

@Composable
fun ReportDetailScreen(
    title: String,
    onBack: () -> Unit,
    onOpenCurrentSales: () -> Unit = {},
    onOpenSalesHistory: () -> Unit = {}
) {
    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton(onBack = onBack)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    ) { innerPadding ->
        if (title == "ยอดขายทั้งหมด") {
            TotalSalesLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onOpenCurrentSales = onOpenCurrentSales,
                onOpenSalesHistory = onOpenSalesHistory
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "หน้านี้เตรียมโครงสร้างไว้แล้ว รายละเอียดการแสดงผลจะกำหนดในขั้นตอนถัดไป",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TotalSalesLayout(
    modifier: Modifier = Modifier,
    onOpenCurrentSales: () -> Unit,
    onOpenSalesHistory: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "ยอดขายทั้งหมด",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "กราฟยอดขายรายปี",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
            YearlySalesChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "รายการยอดขาย",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            ReportMenuItem(
                title = "ยอดขายงวดปัจจุบัน",
                icon = Icons.Outlined.ReceiptLong,
                onClick = onOpenCurrentSales
            )
            ReportMenuItem(
                title = "ยอดขายทั้งหมด (ย้อนหลัง 24 งวด)",
                icon = Icons.Outlined.Assessment,
                onClick = onOpenSalesHistory
            )
        }
    }
}

@Composable
private fun YearlySalesChart(modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ยอดขาย (บาท)",
                modifier = Modifier.rotate(-90f),
                color = axisColor,
                fontSize = 11.sp,
                maxLines = 1
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val left = 2.dp.toPx()
                    val right = size.width - 2.dp.toPx()
                    val top = 8.dp.toPx()
                    val bottom = size.height - 4.dp.toPx()
                    val rows = 5
                    for (index in 0..rows) {
                        val y = top + (bottom - top) * index / rows
                        drawLine(
                            color = gridColor,
                            start = androidx.compose.ui.geometry.Offset(left, y),
                            end = androidx.compose.ui.geometry.Offset(right, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    drawLine(
                        color = axisColor,
                        start = androidx.compose.ui.geometry.Offset(left, top),
                        end = androidx.compose.ui.geometry.Offset(left, bottom),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawLine(
                        color = axisColor,
                        start = androidx.compose.ui.geometry.Offset(left, bottom),
                        end = androidx.compose.ui.geometry.Offset(right, bottom),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    monthLabels.forEach { month ->
                        Text(
                            text = month,
                            fontSize = 8.sp,
                            color = axisColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Text(
            text = "ยังไม่มีข้อมูลยอดขายสำหรับแสดงกราฟ",
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
