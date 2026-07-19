package com.vibeactions.data.repository

import com.vibeactions.data.db.FolderDao
import com.vibeactions.domain.model.Folder
import com.vibeactions.domain.model.toDomain
import com.vibeactions.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(private val dao: FolderDao) {
    fun observeAll(): Flow<List<Folder>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    suspend fun getById(id: String): Folder? = dao.getById(id)?.toDomain()
    suspend fun upsert(folder: Folder) = dao.upsert(folder.toEntity())
    suspend fun delete(folder: Folder) = dao.delete(folder.toEntity())
    suspend fun setExpanded(id: String, expanded: Boolean) = dao.updateExpanded(id, expanded)
    suspend fun updateSortOrder(id: String, order: Int) = dao.updateSortOrder(id, order)
}
