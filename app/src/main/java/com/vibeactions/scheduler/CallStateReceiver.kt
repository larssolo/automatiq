package com.vibeactions.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.vibeactions.data.AppSettings
import com.vibeactions.util.CallWatchState
import com.vibeactions.util.advanceCallState
import com.vibeactions.util.isWithinQuietHours
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * Auto-replies to missed calls (MISSED_CALL macros). PHONE_STATE is a protected system broadcast
 * exempt from the implicit-broadcast restrictions, so a manifest receiver works — no service
 * needed. The ring/answer state machine is pure ([advanceCallState]) and persisted between
 * broadcasts. Requires READ_PHONE_STATE; the caller's number additionally needs READ_CALL_LOG
 * (API 28+) — without it missed calls are detected but can't be replied to (no target number).
 */
@AndroidEntryPoint
class CallStateReceiver : BroadcastReceiver() {
    @Inject lateinit var router: IncomingReplyRouter

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val phoneState = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val advance = advanceCallState(
            CallWatchState(
                ringing = prefs.getBoolean(KEY_RINGING, false),
                number = prefs.getString(KEY_NUMBER, null),
                answered = prefs.getBoolean(KEY_ANSWERED, false)
            ),
            phoneState, number
        )
        prefs.edit()
            .putBoolean(KEY_RINGING, advance.state.ringing)
            .putString(KEY_NUMBER, advance.state.number)
            .putBoolean(KEY_ANSWERED, advance.state.answered)
            .apply()

        val missed = advance.missedNumber ?: return
        // A withheld/anonymous caller has no textable number.
        if (missed.none { it.isDigit() }) return
        // Quiet hours: defer the reply to just after the window, like SMS auto-replies.
        if (AppSettings.quietHoursEnabled(context) && isWithinQuietHours(
                LocalTime.now(),
                AppSettings.quietStartMinute(context),
                AppSettings.quietEndMinute(context)
            )
        ) {
            DeferredReplyWorker.enqueue(context, DeferredReplyWorker.KIND_CALL, missed, body = "")
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                router.handleMissedCall(missed)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val PREFS = "call_state"
        private const val KEY_RINGING = "ringing"
        private const val KEY_NUMBER = "number"
        private const val KEY_ANSWERED = "answered"
    }
}
