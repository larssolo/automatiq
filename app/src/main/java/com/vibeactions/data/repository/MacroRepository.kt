package com.vibeactions.data.repository

import com.vibeactions.data.db.MacroDao
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.toDomain
import com.vibeactions.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroRepository @Inject constructor(private val dao: MacroDao) {
    fun observeAll(): Flow<List<Macro>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    suspend fun getById(id: String): Macro? = dao.getById(id)?.toDomain()
    suspend fun getEnabledScheduled(): List<Macro> = dao.getEnabledScheduled().map { it.toDomain() }
    suspend fun upsert(macro: Macro) = dao.upsert(macro.toEntity())
    suspend fun delete(macro: Macro) = dao.delete(macro.toEntity())
    suspend fun updateStatus(id: String, at: Long, status: MacroStatus) =
        dao.updateStatus(id, at, status.name)

    suspend fun updateScheduledFireAt(id: String, at: Long) = dao.updateScheduledFireAt(id, at)

    /** Persist a new manual ordering: each id's sort_order becomes its index in [orderedIds]. */
    suspend fun persistOrder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> dao.updateSortOrder(id, index) }
    }
}
