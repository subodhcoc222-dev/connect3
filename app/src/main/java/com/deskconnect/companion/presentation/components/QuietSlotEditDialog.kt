package com.deskconnect.companion.presentation.components

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskconnect.companion.data.local.QuietSlot
import com.deskconnect.companion.presentation.theme.*
import java.util.Locale

@Composable
fun QuietSlotEditDialog(
    slot: QuietSlot,
    onDismiss: () -> Unit,
    onSave: (QuietSlot) -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(slot.isEnabled) }
    var startHour by remember { mutableIntStateOf(slot.startHour) }
    var startMinute by remember { mutableIntStateOf(slot.startMinute) }
    var endHour by remember { mutableIntStateOf(slot.endHour) }
    var endMinute by remember { mutableIntStateOf(slot.endMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = {
            Text("Edit Silent Slot ${slot.id}", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable This Slot", color = TextWhite, fontSize = 14.sp)
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = SlateDark, checkedTrackColor = CyanAccent)
                    )
                }

                HorizontalDivider(color = SlateBorder)

                // Start Time Picker
                TimeSelectRow(
                    label = "Mute From (Start)",
                    timeText = formatTime12H(startHour, startMinute),
                    onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            startHour = h
                            startMinute = m
                        }, startHour, startMinute, false).show()
                    }
                )

                // End Time Picker
                TimeSelectRow(
                    label = "Unmute At (End)",
                    timeText = formatTime12H(endHour, endMinute),
                    onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            endHour = h
                            endMinute = m
                        }, endHour, endMinute, false).show()
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(slot.copy(
                        isEnabled = isEnabled,
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Slot", color = SlateDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
fun TimeSelectRow(label: String, timeText: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateSurfaceLight, RoundedCornerShape(8.dp))
            .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, color = TextMuted, fontSize = 12.sp)
            Text(timeText, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, tint = CyanAccent)
    }
}

fun formatTime12H(hour: Int, minute: Int): String {
    val amPm = if (hour >= 12) "PM" else "AM"
    val h = if (hour % 12 == 0) 12 else hour % 12
    return String.format(Locale.getDefault(), "%02d:%02d %s", h, minute, amPm)
}
