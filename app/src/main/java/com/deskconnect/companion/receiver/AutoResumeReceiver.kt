package com.deskconnect.companion.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.deskconnect.companion.data.local.PreferencesManager
import com.deskconnect.companion.service.DeskWatchdogService

class AutoResumeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PreferencesManager(context)
        prefs.isMasterSwitchOn = true
        prefs.pauseUntilTimestamp = 0L

        val serviceIntent = Intent(context, DeskWatchdogService::class.java).apply {
            action = DeskWatchdogService.ACTION_START_WATCHDOG
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
