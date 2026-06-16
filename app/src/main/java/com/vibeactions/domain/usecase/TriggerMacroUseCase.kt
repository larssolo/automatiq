package com.vibeactions.domain.usecase

import com.vibeactions.scheduler.MacroFirer
import javax.inject.Inject

class TriggerMacroUseCase @Inject constructor(private val firer: MacroFirer) {
    /** Manual tap — always sends, no once-per-day guard. */
    suspend operator fun invoke(macroId: String) = firer.fire(macroId, enforceOncePerDay = false)
}
