package com.vibeactions.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUtilsTest {
    @Test fun validNumbers() {
        assertTrue(isValidPhone("+4512345678"))
        assertTrue(isValidPhone("12345678"))
        assertTrue(isValidPhone("+45 12 34 56 78"))
    }
    @Test fun invalidNumbers() {
        assertFalse(isValidPhone(""))
        assertFalse(isValidPhone("abc"))
        assertFalse(isValidPhone("12"))
    }
    @Test fun masksAllButLastTwo() {
        assertEquals("+45 ×× ×× ×× 78", maskPhone("+4512345678"))
    }
    @Test fun maskShortNumber() {
        assertEquals("×× 78", maskPhone("5678"))
    }
}
