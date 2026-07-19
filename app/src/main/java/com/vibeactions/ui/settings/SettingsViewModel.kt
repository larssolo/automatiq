package com.vibeactions.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.FolderRepository
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.usecase.SaveMacroUseCase
import com.vibeactions.ui.common.BackgroundSetting
import com.vibeactions.util.DEFAULT_GEMINI_MODEL
import com.vibeactions.util.parseBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: MacroRepository,
    private val save: SaveMacroUseCase,
    private val folderRepo: FolderRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    private val uiPrefs = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
    private val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun getApiKey(): String = prefs.getString("gemini_api_key", "") ?: ""
    fun saveApiKey(key: String) { prefs.edit().putString("gemini_api_key", key.trim()).apply() }
    fun getSystemPrompt(): String = prefs.getString("gemini_system_prompt", "") ?: ""
    fun saveSystemPrompt(p: String) { prefs.edit().putString("gemini_system_prompt", p.trim()).apply() }
    fun getModel(): String =
        prefs.getString("gemini_model", DEFAULT_GEMINI_MODEL)?.ifBlank { DEFAULT_GEMINI_MODEL }
            ?: DEFAULT_GEMINI_MODEL
    fun saveModel(m: String) { prefs.edit().putString("gemini_model", m.trim()).apply() }

    /** Full backup: every macro plus settings. [includeApiKey] opts into embedding the Gemini key
     *  (off by default — the key is otherwise kept out of any exported/backed-up file). */
    fun exportBackup(includeApiKey: Boolean, onReady: (String) -> Unit) = viewModelScope.launch {
        val macros = repo.observeAll().first()
        val folders = folderRepo.observeAll().first()
        // Fully qualified: the top-level util function is otherwise shadowed by this member's name.
        onReady(com.vibeactions.util.exportBackup(macros, folders, collectSettings(includeApiKey)))
    }

    /**
     * Imports a full backup or a legacy macro-only file; the result carries the macro count or the
     * parse failure for the UI. Settings present in the file are restored too.
     */
    fun importBackup(json: String, onDone: (Result<Int>) -> Unit) = viewModelScope.launch {
        val result = runCatching { parseBackup(json) }
        result.getOrNull()?.let { parsed ->
            // Folders restored before macros: membership (already resolved by the orphan guard at
            // parse time) needs them present, and upserting first keeps the list coherent while
            // macro saves stream in.
            parsed.folders.forEach { folderRepo.upsert(it) }
            parsed.macros.forEach { save(it) }
            applySettings(parsed.settings)
        }
        onDone(result.map { it.macros.size })
    }

    /** Flattens the three settings stores into one map, prefixed by store so restore can route each
     *  key back. The API key is included only when the user explicitly opts in. */
    private fun collectSettings(includeApiKey: Boolean): Map<String, String> {
        val m = linkedMapOf<String, String>()
        prefs.getString("gemini_system_prompt", "")?.takeIf { it.isNotBlank() }
            ?.let { m["ai:gemini_system_prompt"] = it }
        prefs.getString("gemini_model", "")?.takeIf { it.isNotBlank() }
            ?.let { m["ai:gemini_model"] = it }
        if (includeApiKey) prefs.getString("gemini_api_key", "")?.takeIf { it.isNotBlank() }
            ?.let { m["ai:gemini_api_key"] = it }
        m["ui:bg_hue"] = uiPrefs.getFloat("bg_hue", 0f).toString()
        m["ui:bg_saturation"] = uiPrefs.getFloat("bg_saturation", 1f).toString()
        m["ui:card_opacity"] = uiPrefs.getFloat("card_opacity", 0.93f).toString()
        m["app:quiet_hours_enabled"] = appPrefs.getBoolean("quiet_hours_enabled", false).toString()
        m["app:quiet_start_minute"] = appPrefs.getInt("quiet_start_minute", 22 * 60).toString()
        m["app:quiet_end_minute"] = appPrefs.getInt("quiet_end_minute", 7 * 60).toString()
        return m
    }

    private fun applySettings(settings: Map<String, String>) {
        settings.forEach { (key, value) ->
            when {
                key.startsWith("ai:") ->
                    prefs.edit().putString(key.removePrefix("ai:"), value).apply()
                key.startsWith("ui:") ->
                    value.toFloatOrNull()?.let {
                        uiPrefs.edit().putFloat(key.removePrefix("ui:"), it).apply()
                    }
                key.startsWith("app:") -> applyAppSetting(key.removePrefix("app:"), value)
            }
        }
        // Reload the in-memory appearance state so restored hue/saturation/opacity show immediately.
        BackgroundSetting.load(context)
    }

    private fun applyAppSetting(key: String, value: String) {
        val editor = appPrefs.edit()
        when (key) {
            "quiet_hours_enabled" -> editor.putBoolean(key, value.toBoolean())
            "quiet_start_minute", "quiet_end_minute" ->
                value.toIntOrNull()?.let { editor.putInt(key, it) }
        }
        editor.apply()
    }
}
