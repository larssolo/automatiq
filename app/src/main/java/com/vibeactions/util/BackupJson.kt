package com.vibeactions.util

import com.vibeactions.domain.model.Folder
import com.vibeactions.domain.model.Macro
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Full app backup: every macro, every folder, plus a flat map of settings (system prompt, model,
 * appearance, quiet hours, and — only when the user opts in — the Gemini API key). Kept in one
 * file so a user migrating phones restores everything, not just macros.
 */
@Serializable
internal data class FolderDto(
    val id: String, val name: String, val cardColor: Long = 0L,
    val sortOrder: Int = 0, val expanded: Boolean = true, val createdAt: Long = 0L
)

internal fun Folder.toDto() = FolderDto(id, name, cardColor, sortOrder, expanded, createdAt)
internal fun FolderDto.toFolder() = Folder(id, name, cardColor, sortOrder, expanded, createdAt)

@Serializable
internal data class Backup(
    val version: Int = 1,
    val macros: List<MacroDto> = emptyList(),
    val folders: List<FolderDto> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

private val backupJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

fun exportBackup(macros: List<Macro>, folders: List<Folder>, settings: Map<String, String>): String =
    backupJson.encodeToString(
        Backup.serializer(),
        Backup(version = 1, macros = macros.map { it.toDto() },
            folders = folders.map { it.toDto() }, settings = settings)
    )

/** Parsed backup: macros, folders, and the settings map to re-apply. */
class ParsedBackup(val macros: List<Macro>, val folders: List<Folder>, val settings: Map<String, String>)

/**
 * Parses either format: a bare macro array (legacy `exportMacros` output) or a full [Backup]
 * object. A macro whose folderId matches no folder in the same file (hand-edited) falls back to
 * root — a macro must never vanish behind a ghost folder.
 */
fun parseBackup(text: String): ParsedBackup {
    val firstChar = text.trimStart().firstOrNull()
    if (firstChar == '[') return ParsedBackup(
        // A bare array carries no folders, so any folderId in it is by definition an orphan.
        importMacros(text).map { if (it.folderId != null) it.copy(folderId = null) else it },
        emptyList(), emptyMap()
    )
    val backup = backupJson.decodeFromString(Backup.serializer(), text)
    val folders = backup.folders.map { it.toFolder() }
    val ids = folders.map { it.id }.toSet()
    val macros = backup.macros.map { it.toMacro() }
        .map { if (it.folderId != null && it.folderId !in ids) it.copy(folderId = null) else it }
    return ParsedBackup(macros, folders, backup.settings)
}
