package com.akillikumanda.app.data.remote

import com.akillikumanda.app.core.database.SavedRemoteDao
import com.akillikumanda.app.core.database.SavedRemoteEntity
import com.akillikumanda.app.core.database.normalizeName
import com.akillikumanda.app.core.model.RemoteProfile
import com.akillikumanda.app.core.model.VerificationStatus
import com.akillikumanda.app.data.catalog.CatalogParser
import kotlinx.coroutines.flow.Flow

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
    data class Duplicate(val existingId: Long) : SaveResult
    data class Invalid(val reason: String) : SaveResult
}

class SavedRemoteRepository(private val dao: SavedRemoteDao, private val clock: () -> Long = System::currentTimeMillis) {

    fun observeAll(): Flow<List<SavedRemoteEntity>> = dao.observeAll()
    fun observe(id: Long): Flow<SavedRemoteEntity?> = dao.observe(id)
    suspend fun get(id: Long) = dao.get(id)

    /** Yalnızca kullanıcının "Çalıştı" dediği profiller kaydedilir (çağıran taraf garanti eder). */
    suspend fun saveConfirmed(profile: RemoteProfile, name: String, room: String?, note: String?): SaveResult {
        val clean = name.trim()
        if (clean.isEmpty()) return SaveResult.Invalid("Kumanda adı boş olamaz")
        if (clean.length > 60) return SaveResult.Invalid("Kumanda adı en fazla 60 karakter olabilir")
        val norm = normalizeName(clean)
        dao.findDuplicate(profile.id, norm)?.let { return SaveResult.Duplicate(it.id) }
        val id = dao.insert(
            SavedRemoteEntity(
                profileId = profile.id, profileVersion = profile.version,
                profileSnapshot = CatalogParser.encodeSnapshot(profile),
                name = clean, normalizedName = norm, category = profile.category.name, brand = profile.brand,
                model = profile.model, connectionType = profile.connectionType.name,
                room = room?.trim()?.ifEmpty { null }, note = note?.trim()?.ifEmpty { null },
                verification = VerificationStatus.USER_CONFIRMED.name, createdAt = clock(), lastUsedAt = clock(),
            )
        )
        return SaveResult.Saved(id)
    }

    suspend fun rename(id: Long, newName: String): SaveResult {
        val e = dao.get(id) ?: return SaveResult.Invalid("Kayıt bulunamadı")
        val clean = newName.trim()
        if (clean.isEmpty()) return SaveResult.Invalid("Ad boş olamaz")
        val norm = normalizeName(clean)
        dao.findDuplicate(e.profileId, norm)?.let { if (it.id != id) return SaveResult.Duplicate(it.id) }
        dao.update(e.copy(name = clean, normalizedName = norm))
        return SaveResult.Saved(id)
    }

    suspend fun updateDetails(id: Long, room: String?, note: String?) {
        val e = dao.get(id) ?: return
        dao.update(e.copy(room = room?.trim()?.ifEmpty { null }, note = note?.trim()?.ifEmpty { null }))
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)
    suspend fun markUsed(id: Long) = dao.touch(id, clock())
    suspend fun setVerification(id: Long, status: VerificationStatus) = dao.setVerification(id, status.name)
    suspend fun delete(entity: SavedRemoteEntity) = dao.delete(entity)
    suspend fun confirmedProfileIds(): Set<String> = dao.confirmedProfileIds().toSet()

    fun profileOf(entity: SavedRemoteEntity): RemoteProfile? = CatalogParser.decodeSnapshot(entity.profileSnapshot)
}
