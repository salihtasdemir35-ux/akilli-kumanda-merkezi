package com.akillikumanda.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_remotes",
    indices = [Index(value = ["profileId", "normalizedName"], unique = true)],
)
data class SavedRemoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val profileVersion: Int,
    /** Kaydedildiği andaki profilin JSON kopyası; katalog değişse de kumanda çalışmaya devam eder. */
    val profileSnapshot: String,
    val name: String,
    val normalizedName: String,
    val category: String,
    val brand: String,
    val model: String?,
    val connectionType: String,
    val room: String?,
    val favorite: Boolean = false,
    val lastUsedAt: Long? = null,
    val note: String? = null,
    /** Wi-Fi/BLE için yerel bağlantı yapılandırması (MVP'de kullanılmıyor). */
    val connectionConfig: String? = null,
    val verification: String,
    val createdAt: Long,
)

fun normalizeName(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")
