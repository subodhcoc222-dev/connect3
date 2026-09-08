package com.deskconnect.companion.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class QuietSlot(
    val id: Int,
    val isEnabled: Boolean,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)

class PreferencesManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "deskconnect_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val standardPrefs: SharedPreferences =
        context.getSharedPreferences("deskconnect_standard_prefs", Context.MODE_PRIVATE)

    private val gson = Gson()

    companion object {
        private const val KEY_MASTER_PIN = "key_master_pin"
        private const val KEY_MASTER_SWITCH = "key_master_switch"
        private const val KEY_PAUSE_UNTIL_TIMESTAMP = "key_pause_until_timestamp"
        private const val KEY_SNOOZE_SECONDS = "key_snooze_seconds"
        private const val KEY_QUIET_SLOTS = "key_quiet_slots"
        private const val KEY_PAIR_DEVICE_ID = "key_pair_device_id"
    }

    fun isPinSet(): Boolean = securePrefs.getString(KEY_MASTER_PIN, null) != null

    fun setMasterPin(pin: String) {
        securePrefs.edit().putString(KEY_MASTER_PIN, pin).apply()
    }

    fun verifyPin(inputPin: String): Boolean {
        val storedPin = securePrefs.getString(KEY_MASTER_PIN, null) ?: return false
        return storedPin == inputPin
    }

    var isMasterSwitchOn: Boolean
        get() = standardPrefs.getBoolean(KEY_MASTER_SWITCH, false)
        set(value) = standardPrefs.edit().putBoolean(KEY_MASTER_SWITCH, value).apply()

    var pauseUntilTimestamp: Long
        get() = standardPrefs.getLong(KEY_PAUSE_UNTIL_TIMESTAMP, 0L)
        set(value) = standardPrefs.edit().putLong(KEY_PAUSE_UNTIL_TIMESTAMP, value).apply()

    // Snooze stored in seconds: default 60s (1 min), options include 30s, 60s, 120s, 180s, 300s
    var snoozeSeconds: Int
        get() = standardPrefs.getInt(KEY_SNOOZE_SECONDS, 60)
        set(value) = standardPrefs.edit().putInt(KEY_SNOOZE_SECONDS, value).apply()

    var pairedDeviceId: String
        get() = standardPrefs.getString(KEY_PAIR_DEVICE_ID, "349806") ?: "349806"
        set(value) = standardPrefs.edit().putString(KEY_PAIR_DEVICE_ID, value).apply()

    fun getQuietSlots(): List<QuietSlot> {
        val json = standardPrefs.getString(KEY_QUIET_SLOTS, null)
        if (json.isNullOrEmpty()) {
            return listOf(
                QuietSlot(1, false, 22, 0, 5, 0),
                QuietSlot(2, false, 0, 0, 0, 0),
                QuietSlot(3, false, 0, 0, 0, 0),
                QuietSlot(4, false, 0, 0, 0, 0)
            )
        }
        val type = object : TypeToken<List<QuietSlot>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveQuietSlots(slots: List<QuietSlot>) {
        val json = gson.toJson(slots)
        standardPrefs.edit().putString(KEY_QUIET_SLOTS, json).apply()
    }
}
