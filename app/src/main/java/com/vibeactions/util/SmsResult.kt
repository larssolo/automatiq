package com.vibeactions.util

/**
 * Maps a radio-level SMS send result code (delivered to the sentIntent's BroadcastReceiver) to a
 * short human-readable error, or null when the send succeeded. Codes mirror
 * SmsManager.RESULT_ERROR_* / Activity.RESULT_OK; kept as literals so this stays pure JVM.
 */
fun smsResultErrorText(resultCode: Int): String? = when (resultCode) {
    -1 -> null                                   // Activity.RESULT_OK
    1 -> "send failed"                           // RESULT_ERROR_GENERIC_FAILURE
    2 -> "radio off (flight mode?)"              // RESULT_ERROR_RADIO_OFF
    3 -> "message could not be encoded"          // RESULT_ERROR_NULL_PDU
    4 -> "no service"                            // RESULT_ERROR_NO_SERVICE
    5 -> "SMS limit exceeded"                    // RESULT_ERROR_LIMIT_EXCEEDED
    6 -> "blocked by fixed dialing numbers"      // RESULT_ERROR_FDN_CHECK_FAILURE
    7 -> "short code not allowed"                // RESULT_ERROR_SHORT_CODE_NOT_ALLOWED
    else -> "send failed (code $resultCode)"
}
