package com.akillikumanda.app.data.remote

import com.akillikumanda.app.core.database.SavedRemoteDao
import com.akillikumanda.app.core.database.SavedRemoteEntity
import com.akillikumanda.app.core.database.normalizeName
import com.akillikumanda.app.data.catalog.CatalogParser
import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(val format: String = FORMAT, val version: Int = 1, val exportedAt: Long, val remotes: List<BackupRemote>) {
    companion object { const val FORMAT = "akilli-kumanda-yedek" }
}

@Serializable
data class BackupRemote(
    val profileId: String, val profileVersion: Int, val profileSnapshot: String, val name: String,
    val category: String, val brand: String, val model: String? = null, val connectionType: String,
    val room: String? = null, val favorite: Boolean = false, val note: String? = null,
    val verification: String, val createdAt: Long,
)

data class ImportReport(val imported: Int, val skippedDuplicate: Int, val skippedInvalid: Int)

class BackupManager(private val dao: SavedRemoteDao) {
    private val json = CatalogParser.json

    suspend fun export(now: Long = System.currentTimeMillis()): String =
        json.encodeToString(BackupFile.serializer(), BackupFile(exportedAt = now, remotes = dao.getAll().map {
            BackupRemote(it.profileId, it.profileVersion, it.profileSnapshot, it.name, it.category, it.brand, it.model,
                it.connectionType, it.room, it.favorite, it.note, it.verification, it.createdAt)
        }))

    /** @throws IllegalArgumentException dosya bu uygulamanın yedeği değilse. */
    suspend fun import(text: String): ImportReport {
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("Dosya okunamadı ya da bozuk") }
        require(file.format == BackupFile.FORMAT) { "Bu dosya Akıllı Kumanda Merkezi yedeği değil" }
        var ok = 0; var dup = 0; var bad = 0
        for (r in file.remotes) {
            if (CatalogParser.decodeSnapshot(r.profileSnapshot) == null || r.name.isBlank()) { bad++; continue }
            val norm = normalizeName(r.name)
            if (dao.findDuplicate(r.profileId, norm) != null) { dup++; continue }
            val id = dao.insertIgnore(SavedRemoteEntity(
                profileId = r.profileId, profileVersion = r.profileVersion, profileSnapshot = r.profileSnapshot,
                name = r.name.trim(), normalizedName = norm, category = r.category, brand = r.brand, model = r.model,
                connectionType = r.connectionType, room = r.room, favorite = r.favorite, note = r.note,
                verification = r.verification, createdAt = r.createdAt,
            ))
            if (id > 0) ok++ else dup++
        }
        return ImportReport(ok, dup, bad)
    }
}
