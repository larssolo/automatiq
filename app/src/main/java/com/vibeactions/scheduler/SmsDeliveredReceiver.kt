package com.vibeactions.scheduler

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import com.vibeactions.data.repository.MacroLogRepository
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.DeliveryStatus
import com.vibeactions.util.deliveryStatusForReport
import com.vibeactions.widget.WidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the carrier's SMS delivery report (armed by SmsDispatcher) and records DELIVERED/FAILED
 * on the log row, so the log can show "Delivered ✓✓" rather than just "Sent". Many carriers/devices
 * never send a report — then this simply never fires and the delivery status stays unknown.
 */
@AndroidEntryPoint
class SmsDeliveredReceiver : BroadcastReceiver() {
    @Inject lateinit var logRepo: MacroLogRepository
    @Inject lateinit var macroRepo: MacroRepository
    @Inject lateinit var widgets: WidgetRefresher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_DELIVERED) return
        val logId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
        if (logId == -1L) return
        val macroId = intent.getStringExtra(EXTRA_MACRO_ID)
        val status = deliveryStatusFrom(intent, resultCode) ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                logRepo.updateDelivery(logId, status)
                if (macroId != null) widgets.refreshFor(macroId)
            } finally {
                pending.finish()
            }
        }
    }

    /** Reads the delivery outcome from the report PDU's TP-Status; falls back to the result code
     *  on devices that don't attach a PDU. Returns null for a non-final "still trying" report. */
    @Suppress("DEPRECATION")
    private fun deliveryStatusFrom(intent: Intent, resultCode: Int): DeliveryStatus? {
        val pdu = intent.getByteArrayExtra("pdu")
        if (pdu != null) {
            val format = intent.getStringExtra("format")
            val message = runCatching {
                if (format != null) SmsMessage.createFromPdu(pdu, format)
                else SmsMessage.createFromPdu(pdu)
            }.getOrNull()
            if (message != null) return deliveryStatusForReport(message.status)
        }
        return when (resultCode) {
            Activity.RESULT_OK -> DeliveryStatus.DELIVERED
            0 -> null                       // no report info — leave delivery status unknown
            else -> DeliveryStatus.FAILED
        }
    }

    companion object {
        const val ACTION_SMS_DELIVERED = "com.vibeactions.SMS_DELIVERED"
        const val EXTRA_LOG_ID = "log_id"
        const val EXTRA_MACRO_ID = "macro_id"
    }
}
