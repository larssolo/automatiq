package com.vibeactions.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MacroAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var firer: MacroFirer

    override fun onReceive(context: Context, intent: Intent) {
        val macroId = intent.getStringExtra(EXTRA_MACRO_ID) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val force = intent.getBooleanExtra("force", false)
                firer.fire(macroId, enforceOncePerDay = !force)
            } finally {
                pending.finish()
            }
        }
    }

    companion object { const val EXTRA_MACRO_ID = "macro_id" }
}
