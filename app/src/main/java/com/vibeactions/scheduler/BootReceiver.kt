package com.vibeactions.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.vibeactions.domain.usecase.RescheduleAllUseCase
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var rescheduleAll: RescheduleAllUseCase
    @Inject lateinit var workScheduler: WorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleAll()
                workScheduler.ensurePeriodicCatchUp()
            } finally {
                pending.finish()
            }
        }
    }
}
