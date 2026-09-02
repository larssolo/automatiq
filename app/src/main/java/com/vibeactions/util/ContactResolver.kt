package com.vibeactions.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a phone number to a saved contact's display name, so the UI and personalised messages
 * can show "Peter" instead of a raw number. Results are cached in memory (the contact list rarely
 * changes within a session) and every query degrades gracefully to null when READ_CONTACTS isn't
 * granted, so callers just fall back to the number.
 */
@Singleton
class ContactResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // number (as typed) -> resolved name, or null when there's no match. `null` values are cached
    // too, so a miss isn't re-queried on every recomposition.
    private val cache = ConcurrentHashMap<String, Optional>()

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Display name for [number], or null when there's no contact / no permission / a blank number. */
    suspend fun displayName(number: String): String? = withContext(Dispatchers.IO) {
        val key = number.trim()
        if (key.isEmpty()) return@withContext null
        cache[key]?.let { return@withContext it.value }
        if (!hasPermission()) return@withContext null
        val resolved = runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(key)
            )
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
        }.getOrNull()
        cache[key] = Optional(resolved)
        resolved
    }

    /** First name only (for the {modtager} token) — the leading word of the display name. */
    suspend fun firstName(number: String): String? =
        displayName(number)?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }

    /**
     * Best-effort check of whether [number] is this device's own line. Depends on the SIM/carrier
     * actually exposing the line number (often blank), so a false result never means "definitely not
     * you" — it's only used to add a helpful "your own phone" hint, never to block anything.
     */
    fun isOwnNumber(number: String): Boolean {
        val own = runCatching {
            @Suppress("DEPRECATION", "MissingPermission")
            context.getSystemService(TelephonyManager::class.java)?.line1Number
        }.getOrNull()?.let { digitsOnly(it) } ?: return false
        if (own.isEmpty()) return false
        val other = digitsOnly(number)
        // Compare on the last 8 digits so +45 prefixes vs. bare local numbers still match.
        return other.isNotEmpty() && own.takeLast(8) == other.takeLast(8)
    }

    private fun digitsOnly(s: String) = s.filter { it.isDigit() }

    private class Optional(val value: String?)
}
