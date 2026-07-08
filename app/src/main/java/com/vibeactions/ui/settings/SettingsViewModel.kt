package com.vibeactions.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.usecase.SaveMacroUseCase
import com.vibeactions.util.DEFAULT_GEMINI_MODEL
import com.vibeactions.util.exportMacros
import com.vibeactions.util.importMacros
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: MacroRepository,
    private val save: SaveMacroUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    fun getApiKey(): String = prefs.getString("gemini_api_key", "") ?: ""
    fun saveApiKey(key: String) { prefs.edit().putString("gemini_api_key", key.trim()).apply() }
    fun getSystemPrompt(): String = prefs.getString("gemini_system_prompt", "") ?: ""
    fun saveSystemPrompt(p: String) { prefs.edit().putString("gemini_system_prompt", p.trim()).apply() }
    fun getModel(): String =
        prefs.getString("gemini_model", DEFAULT_GEMINI_MODEL)?.ifBlank { DEFAULT_GEMINI_MODEL }
            ?: DEFAULT_GEMINI_MODEL
    fun saveModel(m: String) { prefs.edit().putString("gemini_model", m.trim()).apply() }

    fun export(onReady: (String) -> Unit) = viewModelScope.launch {
        onReady(exportMacros(repo.observeAll().first()))
    }

    /** Imports macros from JSON; the result carries the count or the parse failure for the UI. */
    fun import(json: String, onDone: (Result<Int>) -> Unit) = viewModelScope.launch {
        val result = runCatching { importMacros(json) }
        result.getOrNull()?.forEach { save(it) }
        onDone(result.map { it.size })
    }
}
