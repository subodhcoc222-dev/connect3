package com.deskconnect.companion.presentation.eventlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventLogScreen(
    availableDates: List<String>,
    dailyPayload: DailyEventPayload?,
    onDateSelected: (String) -> Unit,
    onBackToDashboard: () -> Unit
) {
    var activeDate by remember { mutableStateOf<String?>(null) }

    // Sort dates in descending order (newest on top)
    val sortedDates = remember(availableDates) {
        availableDates.sortedWith { d1, d2 ->
            val date1 = parseFlexibleDate(d1)
            val date2 = parseFlexibleDate(d2)
            when {
                date1 == null && date2 == null -> 0
                date1 == null -> 1
                date2 == null -> -1
                else -> date2.compareTo(date1) // Newest first
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (activeDate == null) "Study Event History" else "$activeDate Report",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeDate != null) {
                            activeDate = null // Go back to Date list
                        } else {
                            onBackToDashboard() // Go back to Dashboard
                        }
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateDark)
            )
        },
        containerColor = SlateDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (activeDate == null) {
                // STEP 1: Date & Day Selection List (Newest first)
                if (sortedDates.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No study logs available in Firebase.", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = "SELECT DATE FOR DETAILED REPORT",
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        items(sortedDates) { dateStr ->
                            val dayName = getDayNameFromDate(dateStr)
                            DateRowItem(
                                dateStr = dateStr,
                                dayName = dayName,
                                onClick = {
                                    activeDate = dateStr
                                    onDateSelected(dateStr)
                                }
                            )
                        }
                    }
                }
            } else {
                // STEP 2: All-Day Report & Slot Details View
                if (dailyPayload == null || dailyPayload.slots.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No slot records recorded for $activeDate", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 1. ALL-DAY REPORT CARD (सबसे ऊपर पूरे दिन की समरी)
                        item {
                            AllDaySummaryCard(date = activeDate!!, payload = dailyPayload)
                        }

                        // 2. SLOTS TITLE
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "INDIVIDUAL SLOT DETAILS",
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // 3. EACH SLOT BREAKDOWN
                        items(dailyPayload.slots.entries.toList()) { (slotKey, slotDetail) ->
                            SlotReportCard(slotId = slotKey, detail = slotDetail)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DateRowItem(dateStr: String, dayName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateSurface, RoundedCornerShape(12.dp))
            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SlateSurfaceLight, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = dateStr, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = dayName, color = CyanAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
    }
}

@Composable
fun AllDaySummaryCard(date: String, payload: DailyEventPayload) {
    var totalPresent = 0L
    var totalBreak = 0L
    var totalAbsent = 0L

    payload.slots.values.forEach {
        totalPresent += it.presentSec
        totalBreak += it.officialBreakSec
        totalAbsent += it.absentSec
    }

    val totalTime = (totalPresent + totalBreak + totalAbsent).coerceAtLeast(1L)
    val studyEfficiency = ((totalPresent.toFloat() / totalTime.toFloat()) * 100).toInt()

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, CyanAccent, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "ALL-DAY REPORT", color = CyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text(text = "${payload.dayName}, $date", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .background(if (studyEfficiency >= 75) EmeraldPresent.copy(alpha = 0.2f) else CrimsonAbsent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .border(1.dp, if (studyEfficiency >= 75) EmeraldPresent else CrimsonAbsent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$studyEfficiency% Efficiency",
                        color = if (studyEfficiency >= 75) EmeraldPresent else CrimsonAbsent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Proportional Multi-color Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
            ) {
                if (totalPresent > 0) Box(modifier = Modifier.weight(totalPresent.toFloat()).fillMaxHeight().background(EmeraldPresent))
                if (totalBreak > 0) Box(modifier = Modifier.weight(totalBreak.toFloat()).fillMaxHeight().background(AmberBreak))
                if (totalAbsent > 0) Box(modifier = Modifier.weight(totalAbsent.toFloat()).fillMaxHeight().background(CrimsonAbsent))
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AllDayMetricBox(label = "Total Present", timeSec = totalPresent, color = EmeraldPresent)
                AllDayMetricBox(label = "Total Break", timeSec = totalBreak, color = AmberBreak)
                AllDayMetricBox(label = "Total Absent", timeSec = totalAbsent, color = CrimsonAbsent)
            }
        }
    }
}

@Composable
fun AllDayMetricBox(label: String, timeSec: Long, color: Color) {
    Column {
        Text(text = label, color = TextMuted, fontSize = 11.sp)
        Text(
            text = formatDurationClean(timeSec),
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SlotReportCard(slotId: String, detail: SlotDetail) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "SLOT $slotId", color = CyanAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "Present", timeSec = detail.presentSec, color = EmeraldPresent)
                MetricItem(label = "Break", timeSec = detail.officialBreakSec, color = AmberBreak)
                MetricItem(label = "Absent", timeSec = detail.absentSec, color = CrimsonAbsent)
            }

            if (detail.absences.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SlateBorder)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Unscheduled Absences:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
    Column {
        Text(text = label, color = TextMuted, fontSize = 11.sp)
        Text(text = formatDurationClean(timeSec), color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

fun formatDurationClean(totalSec: Long): String {
    val hrs = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return when {
        hrs > 0 -> "${hrs}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}

fun parseFlexibleDate(dateStr: String): Date? {
    val formats = listOf(
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    )
    for (f in formats) {
        try { return f.parse(dateStr) } catch (_: Exception) {}
    }
    return null
}

fun getDayNameFromDate(dateStr: String): String {
    val date = parseFlexibleDate(dateStr) ?: return "Day"
    return SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
}
