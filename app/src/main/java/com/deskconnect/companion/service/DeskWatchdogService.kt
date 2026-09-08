package com.deskconnect.companion.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.deskconnect.companion.DeskConnectApp
import com.deskconnect.companion.R
import com.deskconnect.companion.data.local.PreferencesManager
import com.deskconnect.companion.data.model.DeskSentryDevice
import com.deskconnect.companion.presentation.MainActivity
import com.deskconnect.companion.presentation.alarm.AlarmOverlayActivity
import com.google.firebase.database.*
import kotlinx.coroutines.*
import java.util.Locale

class DeskWatchdogService : Service(), TextToSpeech.OnInitListener {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var prefs: PreferencesManager
    private lateinit var audioPlayer: AlarmAudioPlayer
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var databaseRef: DatabaseReference? = null
    private var firebaseListener: ValueEventListener? = null

    private var lastRecordedHeartbeat: Long = 0L
    private var lastLocalHeartbeatUpdate: Long = 0L
    private var isHeartbeatMissAlerted = false
    private var heartbeatMissStartTime: Long = 0L

    private var isAlarmActiveOnFirebase = false
    private var isFailSafeAlarmTriggered = false
    private var isSnoozed = false
    private var snoozeEndTime: Long = 0L

    companion object {
        const val ACTION_START_WATCHDOG = "action_start_watchdog"
        const val ACTION_STOP_WATCHDOG = "action_stop_watchdog"
        const val ACTION_SNOOZE_ALARM = "action_snooze_alarm"
        const val NOTIFICATION_ID = 1001
        const val HEARTBEAT_TIMEOUT_MS = 15_000L
        const val GRACE_PERIOD_MS = 120_000L
        const val FIREBASE_RTDB_URL = "https://desk-sentry-default-rtdb.firebaseio.com/"
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        audioPlayer = AlarmAudioPlayer(this)
        tts = TextToSpeech(this, this)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_WATCHDOG -> {
                stopWatchdog()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE_ALARM -> {
                handleSnooze()
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildForegroundNotification("Monitoring DeskConnect..."))
                startWatchdog()
            }
        }
        return START_STICKY
    }

    private fun startWatchdog() {
        if (!prefs.isMasterSwitchOn) {
            stopSelf()
            return
        }
        connectFirebase()
        startWatchdogTicker()
    }

    private fun connectFirebase() {
        val deviceId = prefs.pairedDeviceId.ifEmpty { "349806" }
        val database = FirebaseDatabase.getInstance(FIREBASE_RTDB_URL)
        databaseRef = database.getReference("desk_sentry").child(deviceId)

        firebaseListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val deviceData = snapshot.getValue(DeskSentryDevice::class.java) ?: return
                handleFirebaseTelemetry(deviceData)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DeskWatchdog", "Firebase error: ${error.message}")
            }
        }
        databaseRef?.addValueEventListener(firebaseListener!!)
    }

    private fun handleFirebaseTelemetry(data: DeskSentryDevice) {
        if (data.lastHeartbeat != lastRecordedHeartbeat) {
            lastRecordedHeartbeat = data.lastHeartbeat
            lastLocalHeartbeatUpdate = System.currentTimeMillis()

            if (isHeartbeatMissAlerted) {
                isHeartbeatMissAlerted = false
                heartbeatMissStartTime = 0L
                if (isFailSafeAlarmTriggered) {
                    isFailSafeAlarmTriggered = false
                    stopAlarm()
                }
            }
        }

        isAlarmActiveOnFirebase = data.alarmActive

        if (isAlarmActiveOnFirebase) {
            evaluateAndTriggerAlarm(reason = "DESK_ALARM")
        } else {
            if (!isFailSafeAlarmTriggered) {
                stopAlarm()
            }
        }
    }

    private fun startWatchdogTicker() {
        serviceScope.launch {
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()

                if (!prefs.isMasterSwitchOn) {
                    stopSelf()
                    break
                }

                if (isSnoozed && now >= snoozeEndTime) {
                    isSnoozed = false
                    if (isAlarmActiveOnFirebase || isFailSafeAlarmTriggered) {
                        evaluateAndTriggerAlarm(reason = if (isFailSafeAlarmTriggered) "FAIL_SAFE" else "DESK_ALARM")
                    }
                }

                if (lastLocalHeartbeatUpdate > 0L) {
                    val diff = now - lastLocalHeartbeatUpdate
                    if (diff > HEARTBEAT_TIMEOUT_MS) {
                        if (!isHeartbeatMissAlerted) {
                            isHeartbeatMissAlerted = true
                            heartbeatMissStartTime = now
                            speakTtsAlert("Firebase disconnected. Please connect to Firebase.")
                        }

                        val graceDiff = now - heartbeatMissStartTime
                        if (graceDiff >= GRACE_PERIOD_MS && !isFailSafeAlarmTriggered) {
                            isFailSafeAlarmTriggered = true
                            evaluateAndTriggerAlarm(reason = "FAIL_SAFE_HEARTBEAT_LOST")
                        }
                    }
                }
            }
        }
    }

    private fun evaluateAndTriggerAlarm(reason: String) {
        val quietSlots = prefs.getQuietSlots()
        if (QuietSlotChecker.isCurrentTimeInQuietSlot(quietSlots)) {
            stopAlarm()
            return
        }

        if (isSnoozed) return

        audioPlayer.play()

        val overlayIntent = Intent(this, AlarmOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("EXTRA_ALARM_REASON", reason)
        }
        startActivity(overlayIntent)
    }

    private fun handleSnooze() {
        isSnoozed = true
        val snoozeMins = prefs.snoozeMinutes
        snoozeEndTime = System.currentTimeMillis() + (snoozeMins * 60 * 1000L)
        stopAlarm()
    }

    private fun stopAlarm() {
        audioPlayer.stop()
    }

    private fun speakTtsAlert(message: String) {
        if (isTtsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "DISCONNECT_ALERT_ID")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DeskConnect::WatchdogWakeLock").apply {
            acquire(24 * 60 * 60 * 1000L)
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, DeskConnectApp.CHANNEL_WATCHDOG_SERVICE)
            .setContentTitle("DeskConnect Companion Running")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopWatchdog() {
        firebaseListener?.let { databaseRef?.removeEventListener(it) }
        serviceScope.cancel()
        stopAlarm()
        tts?.stop()
        tts?.shutdown()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWatchdog()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
