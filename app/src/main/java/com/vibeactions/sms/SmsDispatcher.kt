package com.vibeactions.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.vibeactions.scheduler.SmsDeliveredReceiver
import com.vibeactions.scheduler.SmsSentReceiver
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

    /**
     * Hands the message to the radio. A successful Result only means "accepted by SmsManager";
     * the actual radio outcome arrives asynchronously at [SmsSentReceiver] via the sent receipt,
     * which is armed when [logId]/[macroId] identify the log entry to correct on failure.
     */
    suspend fun send(
        recipient: String,
        body: String,
        logId: Long? = null,
        macroId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasSmsPermission()) {
            return@withContext Result.failure(SecurityException("SEND_SMS permission not granted"))
        }
        try {
            val sms = smsManager()
            val sentIntent = if (logId != null && macroId != null)
                sentReceipt(logId, macroId, recipient, body) else null
            // Delivery report (best-effort — many carriers never send one) flips the log's
            // delivery status to DELIVERED/FAILED so the log can show "Delivered ✓✓".
            val deliveryIntent = if (logId != null)
                deliveryReceipt(logId, macroId, recipient) else null
            val parts = sms.divideMessage(body)
            if (parts.size == 1) {
                sms.sendTextMessage(recipient, null, body, sentIntent, deliveryIntent)
            } else {
                // One receipt per part; any failing part flips the log entry to FAILED.
                val sentReceipts = sentIntent?.let { ArrayList(parts.map { _ -> sentIntent }) }
                val deliveryReceipts = deliveryIntent?.let { ArrayList(parts.map { _ -> deliveryIntent }) }
                sms.sendMultipartTextMessage(recipient, null, parts, sentReceipts, deliveryReceipts)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deliveryReceipt(logId: Long, macroId: String?, recipient: String): PendingIntent {
        val intent = Intent(context, SmsDeliveredReceiver::class.java).apply {
            action = SmsDeliveredReceiver.ACTION_SMS_DELIVERED
            putExtra(SmsDeliveredReceiver.EXTRA_LOG_ID, logId)
            macroId?.let { putExtra(SmsDeliveredReceiver.EXTRA_MACRO_ID, it) }
        }
        return PendingIntent.getBroadcast(
            context, ("sms_delivered|$logId|$recipient").hashCode() and 0x7FFFFFFF, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun sentReceipt(logId: Long, macroId: String, recipient: String, body: String): PendingIntent {
        val intent = Intent(context, SmsSentReceiver::class.java).apply {
            action = SmsSentReceiver.ACTION_SMS_SENT
            putExtra(SmsSentReceiver.EXTRA_LOG_ID, logId)
            putExtra(SmsSentReceiver.EXTRA_MACRO_ID, macroId)
            putExtra(SmsSentReceiver.EXTRA_RECIPIENT, recipient)
            putExtra(SmsSentReceiver.EXTRA_BODY, body)
        }
        // Request code must differ per (log, recipient): extras alone don't distinguish
        // PendingIntents, and a multi-recipient send arms one receipt per recipient.
        return PendingIntent.getBroadcast(
            context, ("sms_sent|$logId|$recipient").hashCode() and 0x7FFFFFFF, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
}
