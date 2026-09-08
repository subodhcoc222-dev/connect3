package com.deskconnect.companion.presentation.eventlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskconnect.companion.data.model.DailyEventPayload
import com.deskconnect.companion.data.model.SlotDetail
import com.deskconnect.companion.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventLogScreen(
    availableDates: List<String>,
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    dailyPayload: DailyEventPayload?,
    onBackClicked: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Event Logs", color = TextWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateDark)
            )
        },
        containerColor = SlateDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Horizontal Date Selector
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(availableDates) { dateStr ->
                    val isSelected = dateStr == selectedDate
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) CyanAccent else SlateSurface)
                            .border(1.dp, if (isSelected) CyanAccent else SlateBorder, RoundedCornerShape(10.dp))
                            .clickable { onDateSelected(dateStr) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = dateStr,
                            color = if (isSelected) SlateDark else TextWhite,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (dailyPayload == null || dailyPayload.slots.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No slot logs recorded for this date.", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                Text(
                    text = dailyPayload.dayName,
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(dailyPayload.slots.entries.toList()) { (slotId, slotDetail) ->
                        SlotReportCard(slotId = slotId, detail = slotDetail)
                    }
                }
            }
        }
    }
}

@Composable
fun SlotReportCard(slotId: String, detail: SlotDetail) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "SLOT $slotId",
                color = CyanAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Visual Segmented Proportional Bar
            val totalSec = (detail.presentSec + detail.absentSec + detail.officialBreakSec).coerceAtLeast(1L).toFloat()
            val presentWeight = (detail.presentSec / totalSec).coerceIn(0.01f, 1f)
            val breakWeight = (detail.officialBreakSec / totalSec).coerceIn(0.01f, 1f)
            val absentWeight = (detail.absentSec / totalSec).coerceIn(0.01f, 1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                if (detail.presentSec > 0) Box(modifier = Modifier.weight(presentWeight).fillMaxHeight().background(EmeraldPresent))
                if (detail.officialBreakSec > 0) Box(modifier = Modifier.weight(breakWeight).fillMaxHeight().background(AmberBreak))
                if (detail.absentSec > 0) Box(modifier = Modifier.weight(absentWeight).fillMaxHeight().background(CrimsonAbsent))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Key Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "Present", timeSec = detail.presentSec, color = EmeraldPresent)
                MetricItem(label = "Official Break", timeSec = detail.officialBreakSec, color = AmberBreak)
                MetricItem(label = "Absent", timeSec = detail.absentSec, color = CrimsonAbsent)
            }

            // Breakdown of Absences
            if (detail.absences.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SlateBorder)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Unscheduled Absences:", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                detail.absences.forEach { abs ->
                    Text(
                        text = "• ${abs.start} - ${abs.end} (${abs.durationSec}s): ${abs.reason}",
                        color = CrimsonAbsent,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MetricItem(label: String, timeSec: Long, color: Color) {
    val minutes = timeSec / 60
    val seconds = timeSec % 60
    val formatted = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"

    Column {
        Text(text = label, color = TextMuted, fontSize = 11.sp)
        Text(text = formatted, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
