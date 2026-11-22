package com.vibeactions.billing

/** One purchase offer from Play, cut down to what the app needs. Keeps the choice below testable. */
data class OfferInfo(val token: String, val tags: List<String>, val price: String)

/**
 * The early-bird code. One shared code unlocks a discount offer that lives in Play Console
 * (one-time product `textfox_unlock` -> offer tagged [OFFER_TAG]). Play does the pricing and the
 * payment; the code only decides whether the app asks for that offer.
 *
 * This is not a secret: the app is open source, so anyone can read [CODE]. Play's per-user purchase
 * limit on the offer is what stops one account from using it twice.
 */
object EarlyBird {
    /** The tag on the discount offer in Play Console. Must match it exactly. */
    const val OFFER_TAG = "earlybird"

    /** What the user types. Compared without case or surrounding spaces. */
    const val CODE = "TEXTFOX50"

    fun accepts(input: String): Boolean = input.trim().equals(CODE, ignoreCase = true)

    /** The normal-price offer: the first one that isn't the early-bird offer. */
    fun regularOffer(offers: List<OfferInfo>): OfferInfo? = offers.firstOrNull { OFFER_TAG !in it.tags }

    /** The discount offer, if Play returned it for this account. */
    fun earlyBirdOffer(offers: List<OfferInfo>): OfferInfo? = offers.firstOrNull { OFFER_TAG in it.tags }

    /**
     * The offer the purchase should use: the early-bird one only when the code is redeemed and Play
     * has it, otherwise the normal one. Null when Play returned no offers at all.
     */
    fun offerFor(redeemed: Boolean, offers: List<OfferInfo>): OfferInfo? =
        (if (redeemed) earlyBirdOffer(offers) else null) ?: regularOffer(offers)
}
