package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import javax.inject.Inject

/** Enables/disables every macro in a folder at once (the folder card's master switch). Each member
 *  goes through ToggleMacroUseCase so alarms, geofences and the state monitor stay in sync. */
class ToggleFolderUseCase @Inject constructor(
    private val macroRepo: MacroRepository,
    private val toggleMacro: ToggleMacroUseCase
) {
    suspend operator fun invoke(folderId: String, enabled: Boolean) {
        macroRepo.getByFolder(folderId).forEach { toggleMacro(it, enabled) }
    }
}
