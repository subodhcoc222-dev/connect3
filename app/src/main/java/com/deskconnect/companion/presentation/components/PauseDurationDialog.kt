package com.deskconnect.companion.presentation.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskconnect.companion.presentation.theme.*
import java.util.Calendar

@Composable
fun PauseDurationDialog(
    onDismiss: () -> Unit,
    onSelectUnlimited: () -> Unit,
    onSelectSpecificDate: (Long) -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = {
            Text(text = "Pause DeskConnect", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = "Select how long to suspend background monitoring:", color = TextMuted, fontSize = 14.sp)

                PauseOptionCard(
                    icon = Icons.Default.CalendarMonth,
                    title = "Until Specific Date & Time",
                    subtitle = "Auto-resumes at the exact chosen moment",
                    onClick = {
                        showDateTimePicker(context) { chosenMillis ->
                            onSelectSpecificDate(chosenMillis)
                        }
                    }
                )

                PauseOptionCard(
                    icon = Icons.Default.HourglassEmpty,
                    title = "Unlimited / Indefinite",
                    subtitle = "App stays off until you manually turn switch ON",
                    onClick = onSelectUnlimited
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
fun PauseOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
            .background(SlateSurfaceLight, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(text = subtitle, color = TextMuted, fontSize = 12.sp)
        }
    }
}

private fun showDateTimePicker(context: Context, onTimeSelected: (Long) -> Unit) {
    val cal = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val chosen = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                    }
                    onTimeSelected(chosen.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}
