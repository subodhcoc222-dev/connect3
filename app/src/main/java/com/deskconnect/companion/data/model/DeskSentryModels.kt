package com.deskconnect.companion.data.model

import com.google.gson.annotations.SerializedName

/**
 * Root schema matching Firebase node: /desk_sentry/{device_id}
 */
data class DeskSentryDevice(
    @SerializedName("battery_level")
    val batteryLevel: Int = 0,
    @SerializedName("is_charging")
    val isCharging: Boolean = false,
    @SerializedName("last_heartbeat")
    val lastHeartbeat: Long = 0L,
    @SerializedName("alarm_active")
    val alarmActive: Boolean = false,
    @SerializedName("status")
    val status: String = "OFFLINE",
    @SerializedName("latest_snapshot_base64")
    val latestSnapshotBase64: String? = null,
    @SerializedName("latest_snap_time")
    val latestSnapTime: Long = 0L,
    @SerializedName("commands")
    val commands: Map<String, Any>? = null,
    @SerializedName("available_dates")
    val availableDates: List<String> = emptyList(),
    @SerializedName("events")
    val events: Map<String, String>? = null // Stringified JSON per date
)

/**
 * Parsed payload of the inner JSON string inside events/{date}
 */
data class DailyEventPayload(
    @SerializedName("date")
    val date: String = "",
    @SerializedName("dayName")
    val dayName: String = "",
    @SerializedName("slots")
    val slots: Map<String, SlotDetail> = emptyMap()
)

data class SlotDetail(
    @SerializedName("presentSec")
    val presentSec: Long = 0L,
    @SerializedName("absentSec")
    val absentSec: Long = 0L,
    @SerializedName("officialBreakSec")
    val officialBreakSec: Long = 0L,
    @SerializedName("absences")
    val absences: List<AbsenceRecord> = emptyList(),
    @SerializedName("breaks")
    val breaks: List<BreakRecord> = emptyList()
)

data class AbsenceRecord(
    @SerializedName("start")
    val start: String = "",
    @SerializedName("end")
    val end: String = "",
    @SerializedName("durationSec")
    val durationSec: Long = 0L,
    @SerializedName("reason")
    val reason: String = ""
)

data class BreakRecord(
    @SerializedName("start")
    val start: String = "",
    @SerializedName("end")
    val end: String = "",
    @SerializedName("durationSec")
    val durationSec: Long = 0L
)
