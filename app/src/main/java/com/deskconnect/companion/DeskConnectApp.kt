package com.deskconnect.companion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DeskConnectApp : Application() {

    companion object {
        const val CHANNEL_WATCHDOG_SERVICE = "desk_watchdog_channel"
        const val CHANNEL_ALARM_HIGH_PRIORITY = "desk_alarm_channel"
        const val CHANNEL_ALERT_NOTIFICATIONS = "desk_alert_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                CHANNEL_WATCHDOG_SERVICE,
                "DeskConnect 24/7 Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps connection alive with DeskConnect"
            }

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_HIGH_PRIORITY,
                "DeskConnect Critical Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Triggers full screen alarms and bypass alerts"
                setBypassDnd(true)
                enableVibration(true)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_NOTIFICATIONS,
                "DeskConnect Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heartbeat miss and disconnection warnings"
            }

            manager?.createNotificationChannels(listOf(serviceChannel, alarmChannel, alertChannel))
        }
    }
}
