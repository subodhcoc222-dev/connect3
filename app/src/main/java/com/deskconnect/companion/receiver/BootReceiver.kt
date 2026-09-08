package com.deskconnect.companion.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.deskconnect.companion.data.local.PreferencesManager
import com.deskconnect.companion.service.DeskWatchdogService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            val prefs = PreferencesManager(context)
            val now = System.currentTimeMillis()

            if (prefs.isMasterSwitchOn) {
                // Check if app was paused until a specific timestamp
                val pauseUntil = prefs.pauseUntilTimestamp
                if (pauseUntil > 0 && now < pauseUntil) {
                    // Still within pause duration; do not start
                    return
                }

                val serviceIntent = Intent(context, DeskWatchdogService::class.java).apply {
                    action = DeskWatchdogService.ACTION_START_WATCHDOG
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
