package com.deskconnect.companion.presentation.viewmodel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.deskconnect.companion.data.local.PreferencesManager
import com.deskconnect.companion.data.local.QuietSlot
import com.deskconnect.companion.data.model.DailyEventPayload
import com.deskconnect.companion.data.model.DeskSentryDevice
import com.deskconnect.companion.receiver.AutoResumeReceiver
import com.deskconnect.companion.service.DeskWatchdogService
import com.google.firebase.database.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DashboardUiState(
    val isMasterSwitchOn: Boolean = false,
    val isPinSet: Boolean = false,
    val pairedDeviceId: String = "349806",
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val lastHeartbeatMs: Long = 0L,
    val isHeartbeatAlive: Boolean = true,
    val pauseUntilTimestamp: Long = 0L,
    val latestSnapshotBase64: String? = null,
    val latestSnapTime: Long = 0L,
    val isRequestingSnap: Boolean = false,
    val availableDates: List<String> = emptyList(),
    val selectedDatePayload: DailyEventPayload? = null,
    val snoozeMinutes: Int = 1,
    val quietSlots: List<QuietSlot> = emptyList()
)

class MainViewModel(private val context: Context) : ViewModel() {

    val prefs = PreferencesManager(context)
    private val gson = Gson()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var dbRef: DatabaseReference? = null
    private var dbListener: ValueEventListener? = null
    private var currentLoadedDeviceData: DeskSentryDevice? = null

    companion object {
        const val FIREBASE_RTDB_URL = "https://desk-sentry-default-rtdb.firebaseio.com/"
    }

    init {
        loadLocalSettings()
        initFirebase()
    }

    fun loadLocalSettings() {
        _uiState.value = _uiState.value.copy(
            isMasterSwitchOn = prefs.isMasterSwitchOn,
            isPinSet = prefs.isPinSet(),
            pairedDeviceId = prefs.pairedDeviceId.ifEmpty { "349806" },
            snoozeMinutes = prefs.snoozeMinutes,
            pauseUntilTimestamp = prefs.pauseUntilTimestamp,
            quietSlots = prefs.getQuietSlots()
        )
    }

    private fun initFirebase() {
        val deviceId = prefs.pairedDeviceId.ifEmpty { "349806" }
        val database = FirebaseDatabase.getInstance(FIREBASE_RTDB_URL)
        dbRef = database.getReference("desk_sentry").child(deviceId)

        dbListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.getValue(DeskSentryDevice::class.java) ?: return
                currentLoadedDeviceData = data

                val isAlive = (System.currentTimeMillis() - data.lastHeartbeat) < 15_000L

                _uiState.value = _uiState.value.copy(
                    batteryLevel = data.batteryLevel,
                    isCharging = data.isCharging,
                    lastHeartbeatMs = data.lastHeartbeat,
                    isHeartbeatAlive = isAlive,
                    latestSnapshotBase64 = data.latestSnapshotBase64,
                    latestSnapTime = data.latestSnapTime,
                    availableDates = data.availableDates
                )
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        dbRef?.addValueEventListener(dbListener!!)
    }

    fun toggleMasterSwitch(turnOn: Boolean) {
        prefs.isMasterSwitchOn = turnOn
        if (turnOn) {
            cancelScheduledAutoResume()
            prefs.pauseUntilTimestamp = 0L
            startWatchdogService()
        } else {
            stopWatchdogService()
        }
        loadLocalSettings()
    }

    fun pauseUntilDate(timestampMillis: Long) {
        prefs.pauseUntilTimestamp = timestampMillis
        toggleMasterSwitch(false)
        scheduleAutoResumeAlarm(timestampMillis)
    }

    private fun scheduleAutoResumeAlarm(triggerMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutoResumeReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 2002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
    }

    private fun cancelScheduledAutoResume() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutoResumeReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 2002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun startWatchdogService() {
        val intent = Intent(context, DeskWatchdogService::class.java).apply {
            action = DeskWatchdogService.ACTION_START_WATCHDOG
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun stopWatchdogService() {
        val intent = Intent(context, DeskWatchdogService::class.java).apply {
            action = DeskWatchdogService.ACTION_STOP_WATCHDOG
        }
        context.startService(intent)
    }

    fun requestSnapshot() {
        dbRef?.child("commands")?.child("request_snap")?.setValue(true)
    }

    fun loadEventDatePayload(date: String) {
        val rawJson = currentLoadedDeviceData?.events?.get(date) ?: return
        try {
            val payload = gson.fromJson(rawJson, DailyEventPayload::class.java)
            _uiState.value = _uiState.value.copy(selectedDatePayload = payload)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateSnoozeMinutes(minutes: Int) {
        prefs.snoozeMinutes = minutes
        loadLocalSettings()
    }

    override fun onCleared() {
        super.onCleared()
        dbListener?.let { dbRef?.removeEventListener(it) }
    }
}
