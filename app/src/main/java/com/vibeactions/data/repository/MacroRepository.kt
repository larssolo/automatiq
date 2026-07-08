package com.vibeactions.data.repository

import com.vibeactions.data.db.MacroDao
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
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
    suspend fun getEnabledByTrigger(type: TriggerType): List<Macro> =
        dao.getEnabledByTrigger(type.name).map { it.toDomain() }
    suspend fun upsert(macro: Macro) = dao.upsert(macro.toEntity())
    suspend fun delete(macro: Macro) = dao.delete(macro.toEntity())
    suspend fun updateStatus(id: String, at: Long, status: MacroStatus) =
        dao.updateStatus(id, at, status.name)

    /** Status-only update for late radio-level failure reports (keeps lastTriggeredAt intact). */
    suspend fun updateLastStatus(id: String, status: MacroStatus) =
        dao.updateLastStatus(id, status.name)

    /** Atomically claim today's scheduled fire; true if this caller won the claim (see DAO). */
    suspend fun tryClaimScheduledFire(id: String, at: Long, startOfDay: Long): Boolean =
        dao.claimScheduledFire(id, at, startOfDay) > 0

    /** Persist a new manual ordering: each id's sort_order becomes its index in [orderedIds]. */
    suspend fun persistOrder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> dao.updateSortOrder(id, index) }
    }
}
