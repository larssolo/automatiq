package com.vibeactions.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.util.nextFireAt
import com.vibeactions.util.formatRecurrence
import com.vibeactions.util.parseHhMmOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One enabled macro's readiness: its trigger summary and, for scheduled macros, the next fire time
 *  (or a problem note like an expired/invalid schedule). */
data class MacroHealth(
    val name: String,
    val trigger: String,
    val nextFireAt: Long?,
    val note: String? = null
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    repo: MacroRepository
) : ViewModel() {
    val macroHealth: StateFlow<List<MacroHealth>> =
        repo.observeAll().map { macros ->
            macros.filter { it.enabled }.map { m ->
                when (m.triggerType) {
                    TriggerType.SCHEDULED -> scheduledHealth(m)
                    TriggerType.LOCATION -> MacroHealth(m.name, "Location", null)
                    TriggerType.INCOMING -> MacroHealth(m.name, "Auto-reply", null)
                    TriggerType.MANUAL -> MacroHealth(m.name, "Manual", null)
                    TriggerType.CHARGING -> MacroHealth(m.name, "On charging", null)
                    TriggerType.BLUETOOTH -> MacroHealth(m.name, "On Bluetooth", null)
                    TriggerType.WIFI -> MacroHealth(m.name, "On Wi-Fi", null)
                    TriggerType.MISSED_CALL -> MacroHealth(m.name, "Missed call", null)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun scheduledHealth(m: com.vibeactions.domain.model.Macro): MacroHealth {
        val time = m.scheduledTime
        val trigger = "${time ?: "--:--"} · ${formatRecurrence(m.daysOfWeek, m.weekInterval, m.randomDaysMin, m.randomDaysMax)}"
        if (time == null || parseHhMmOrNull(time) == null) {
            return MacroHealth(m.name, trigger, null, note = "Invalid time — won't fire")
        }
        // Same calculation as the armed alarm, incl. random days and varied time.
        val next = m.nextFireAt() ?: return MacroHealth(m.name, trigger, null, note = "Ended — no more runs")
        return MacroHealth(m.name, trigger, next)
    }
}
