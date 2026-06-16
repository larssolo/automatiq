package com.vibeactions.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsDispatcher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun send(recipient: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasSmsPermission()) {
            return@withContext Result.failure(SecurityException("SEND_SMS permission not granted"))
        }
        try {
            val sms = smsManager()
            val parts = sms.divideMessage(body)
            if (parts.size == 1) {
                sms.sendTextMessage(recipient, null, body, null, null)
            } else {
                sms.sendMultipartTextMessage(recipient, null, parts, null, null)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
}
