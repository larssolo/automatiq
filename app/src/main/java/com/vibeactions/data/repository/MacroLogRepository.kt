package com.vibeactions.data.repository

import com.vibeactions.data.db.MacroLogDao
import com.vibeactions.domain.model.MacroLog
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.toDomain
import com.vibeactions.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroLogRepository @Inject constructor(private val dao: MacroLogDao) {
    fun observeAll(): Flow<List<MacroLog>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Inserts and returns the row id, so radio-level send receipts can address this entry later. */
    suspend fun add(log: MacroLog): Long {
        val id = dao.insert(log.toEntity())
        dao.prune(500)
        return id
    }

    suspend fun updateResult(id: Long, status: MacroStatus, error: String?) =
        dao.updateResult(id, status.name, error)

    suspend fun clear() = dao.clear()
}
