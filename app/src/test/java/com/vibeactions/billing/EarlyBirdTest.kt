package com.vibeactions.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarlyBirdTest {
    private val regular = OfferInfo("tok-regular", emptyList(), "$7.00")
    private val early = OfferInfo("tok-early", listOf(EarlyBird.OFFER_TAG), "$3.50")

    @Test fun codeIsMatchedWithoutCaseOrSurroundingSpaces() {
        assertTrue(EarlyBird.accepts("TEXTFOX50"))
        assertTrue(EarlyBird.accepts("textfox50"))
        assertTrue(EarlyBird.accepts("  TextFox50 \n"))
    }

    @Test fun wrongOrEmptyCodeIsRejected() {
        assertFalse(EarlyBird.accepts(""))
        assertFalse(EarlyBird.accepts("   "))
        assertFalse(EarlyBird.accepts("TEXTFOX5"))
        assertFalse(EarlyBird.accepts("TEXTFOX500"))
        assertFalse(EarlyBird.accepts("TEXT FOX50"))
    }

    @Test fun withoutCodeTheRegularOfferIsUsedEvenWhenPlayReturnsBoth() {
        // Order must not matter: Play may list the discount offer first.
        assertEquals(regular, EarlyBird.offerFor(redeemed = false, offers = listOf(early, regular)))
        assertEquals(regular, EarlyBird.offerFor(redeemed = false, offers = listOf(regular, early)))
    }

    @Test fun withCodeTheEarlyBirdOfferIsUsed() {
        assertEquals(early, EarlyBird.offerFor(redeemed = true, offers = listOf(regular, early)))
        assertEquals(early, EarlyBird.offerFor(redeemed = true, offers = listOf(early, regular)))
    }

    @Test fun withCodeButNoEarlyBirdOfferItFallsBackToRegular() {
        assertEquals(regular, EarlyBird.offerFor(redeemed = true, offers = listOf(regular)))
    }

    @Test fun noOffersAtAllGivesNullSoPlayUsesItsDefault() {
        assertNull(EarlyBird.offerFor(redeemed = false, offers = emptyList()))
        assertNull(EarlyBird.offerFor(redeemed = true, offers = emptyList()))
    }

    @Test fun onlyAnEarlyBirdOfferAndNoCodeGivesNoOffer() {
        // Nothing to charge the normal price with; the caller then sends no token.
        assertNull(EarlyBird.offerFor(redeemed = false, offers = listOf(early)))
    }

    @Test fun regularPriceNeverComesFromTheEarlyBirdOffer() {
        assertEquals("$7.00", EarlyBird.regularOffer(listOf(early, regular))?.price)
        assertNull(EarlyBird.regularOffer(listOf(early)))
    }
}
