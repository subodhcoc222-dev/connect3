package com.deskconnect.companion.presentation.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskconnect.companion.data.local.PreferencesManager
import com.deskconnect.companion.presentation.components.PinBottomSheet
import com.deskconnect.companion.presentation.components.PinMode
import com.deskconnect.companion.service.DeskWatchdogService

class AlarmOverlayActivity : ComponentActivity() {

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DeskWatchdogService.ACTION_DISMISS_OVERLAY) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindowBypass()

        val filter = IntentFilter(DeskWatchdogService.ACTION_DISMISS_OVERLAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dismissReceiver, filter)
        }

        val reason = intent.getStringExtra("EXTRA_ALARM_REASON") ?: "DESK_ALARM"
        val prefs = PreferencesManager(this)

        setContent {
            var showPinDialog by remember { mutableStateOf(false) }
            val snoozeLabel = formatSnoozeLabel(prefs.snoozeSeconds)

            AlarmOverlayScreen(
                reason = reason,
                snoozeLabel = snoozeLabel,
                onSnoozeClicked = { triggerSnooze() },
                onDismissClicked = { showPinDialog = true }
            )

            if (showPinDialog) {
                PinBottomSheet(
                    mode = PinMode.AUTHENTICATE,
                    onDismiss = { showPinDialog = false },
                    onPinSuccess = {
                        showPinDialog = false
                        triggerManualDismiss()
                    },
                    verifyOldPin = { prefs.verifyPin(it) },
                    saveNewPin = {}
                )
            }
        }
    }

    private fun configureWindowBypass() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun triggerSnooze() {
        val snoozeIntent = Intent(this, DeskWatchdogService::class.java).apply {
            action = DeskWatchdogService.ACTION_SNOOZE_ALARM
        }
        startService(snoozeIntent)
        finish()
    }

    private fun triggerManualDismiss() {
        val dismissIntent = Intent(this, DeskWatchdogService::class.java).apply {
            action = DeskWatchdogService.ACTION_MANUAL_DISMISS_ALARM
        }
        startService(dismissIntent)
        finish()
    }

    private fun formatSnoozeLabel(secs: Int): String {
        return if (secs < 60) "${secs}S" else "${secs / 60}M"
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(dismissReceiver)
        } catch (_: Exception) {}
    }

    @Deprecated("Prevent accidental back dismiss")
    override fun onBackPressed() {}
}

@Composable
fun AlarmOverlayScreen(
    reason: String,
    snoozeLabel: String,
    onSnoozeClicked: () -> Unit,
    onDismissClicked: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val bgColor by infiniteTransition.animateColor(
        initialValue = Color(0xFFEF4444),
        targetValue = Color(0xFF7F1D1D),
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarmColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert",
                tint = Color.White,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "DESK VIOLATION DETECTED",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (reason.contains("HEARTBEAT"))
                    "Camera offline for >2 mins! Desk verification failed."
                else
                    "Absence threshold exceeded on camera device!",
                color = Color(0xFFFEE2E2),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onSnoozeClicked,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "SNOOZE ($snoozeLabel)",
                    color = Color(0xFFB91C1C),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onDismissClicked,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DISMISS ALARM (PIN)",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
