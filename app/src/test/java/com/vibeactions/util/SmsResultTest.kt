package com.vibeactions.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsResultTest {

    @Test fun resultOk_isNotAnError() {
        // Activity.RESULT_OK (-1) is what the radio reports on a successful send.
        assertNull(smsResultErrorText(-1))
    }

    @Test fun radioOff_mapsToReadableText() {
        // SmsManager.RESULT_ERROR_RADIO_OFF = 2
        assertEquals("radio off (flight mode?)", smsResultErrorText(2))
    }

    @Test fun noService_mapsToReadableText() {
        // SmsManager.RESULT_ERROR_NO_SERVICE = 4
        assertEquals("no service", smsResultErrorText(4))
    }

    @Test fun limitExceeded_mapsToReadableText() {
        // SmsManager.RESULT_ERROR_LIMIT_EXCEEDED = 5
        assertEquals("SMS limit exceeded", smsResultErrorText(5))
    }

    @Test fun unknownCode_fallsBackWithCodeNumber() {
        val text = smsResultErrorText(42)
        assertTrue("expected code in message, got: $text", text!!.contains("42"))
    }
}
