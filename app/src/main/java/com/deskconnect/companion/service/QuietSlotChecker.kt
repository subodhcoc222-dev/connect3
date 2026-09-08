package com.deskconnect.companion.service

import com.deskconnect.companion.data.local.QuietSlot
import java.util.Calendar

object QuietSlotChecker {

    fun isCurrentTimeInQuietSlot(slots: List<QuietSlot>): Boolean {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        for (slot in slots) {
            if (!slot.isEnabled) continue

            val startMinutes = slot.startHour * 60 + slot.startMinute
            val endMinutes = slot.endHour * 60 + slot.endMinute

            if (startMinutes == endMinutes) continue

            if (startMinutes < endMinutes) {
                // Same-day slot (e.g. 14:00 to 18:00)
                if (currentMinutes in startMinutes until endMinutes) return true
            } else {
                // Overnight slot (e.g. 22:00 to 05:00)
                if (currentMinutes >= startMinutes || currentMinutes < endMinutes) return true
            }
        }
        return false
    }
}
