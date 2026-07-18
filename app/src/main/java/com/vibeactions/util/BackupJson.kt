package com.vibeactions.util

import com.vibeactions.domain.model.Macro
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Full app backup: every macro plus a flat map of settings (system prompt, model, appearance,
 * quiet hours, and — only when the user opts in — the Gemini API key). Kept in one file so a user
 * migrating phones restores everything, not just macros.
 */
@Serializable
internal data class Backup(
    val version: Int = 1,
    val macros: List<MacroDto> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

private val backupJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

fun exportBackup(macros: List<Macro>, settings: Map<String, String>): String =
    backupJson.encodeToString(
        Backup.serializer(),
        Backup(version = 1, macros = macros.map { it.toDto() }, settings = settings)
    )

/** Parsed backup: macros plus the settings map to re-apply. */
class ParsedBackup(val macros: List<Macro>, val settings: Map<String, String>)

/**
 * Parses either format: a bare macro array (legacy `exportMacros` output) or a full [Backup] object.
 * The first non-whitespace character disambiguates — `[` = legacy array, `{` = full backup — so old
 * export files keep importing unchanged.
 */
fun parseBackup(text: String): ParsedBackup {
    val firstChar = text.trimStart().firstOrNull()
    return if (firstChar == '[') {
        ParsedBackup(importMacros(text), emptyMap())
    } else {
        val backup = backupJson.decodeFromString(Backup.serializer(), text)
        ParsedBackup(backup.macros.map { it.toMacro() }, backup.settings)
    }
}
