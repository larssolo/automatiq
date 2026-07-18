package com.vibeactions.util

/**
 * Whether a CHARGING/BLUETOOTH/WIFI macro should fire for a device-state event.
 *
 * @param macroTarget  the macro's target (BLUETOOTH device address / WIFI SSID); null/blank = any.
 * @param macroOnConnect  true = the macro fires on connect/plug-in, false = on disconnect/unplug.
 * @param eventTarget  the address/SSID the event carries; null when the platform didn't provide one
 *                     (e.g. a Wi-Fi drop reports no SSID) — then only an "any target" macro matches.
 * @param eventConnected  true = a connect/plug-in event, false = a disconnect/unplug event.
 */
fun stateTriggerMatches(
    macroTarget: String?,
    macroOnConnect: Boolean,
    eventTarget: String?,
    eventConnected: Boolean
): Boolean {
    if (macroOnConnect != eventConnected) return false
    if (macroTarget.isNullOrBlank()) return true
    return eventTarget != null && eventTarget.equals(macroTarget, ignoreCase = true)
}
