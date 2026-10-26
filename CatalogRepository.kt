package com.akillikumanda.app.data.catalog

import android.content.Context
import com.akillikumanda.app.core.model.ConnectionType
import com.akillikumanda.app.core.model.DeviceCategory
import com.akillikumanda.app.core.model.RemoteProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface CatalogState {
    data class Loaded(val version: String, val profiles: List<RemoteProfile>, val warnings: List<String>) : CatalogState
    data class Broken(val reason: String) : CatalogState
}

/** Uygulamayla gelen yerel katalog. Çevrim dışı çalışır; ağdan indirme MVP'de yoktur. */
class CatalogRepository(private val context: Context) {
    private val mutex = Mutex()
    private var cached: CatalogState? = null

    suspend fun load(): CatalogState = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            val text = runCatching {
                context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrElse { return@withContext CatalogState.Broken("Katalog dosyası bulunamadı") }
            when (val r = CatalogParser.parse(text)) {
                is CatalogParseResult.Ok -> CatalogState.Loaded(r.catalogVersion, r.profiles, r.warnings)
                is CatalogParseResult.Failure -> CatalogState.Broken(r.reason)
            }
        }.also { cached = it }
    }

    suspend fun profiles(): List<RemoteProfile> = (load() as? CatalogState.Loaded)?.profiles.orEmpty()

    suspend fun brands(category: DeviceCategory, connection: ConnectionType? = null): List<String> =
        profiles().filter { it.category == category && (connection == null || it.connectionType == connection) }
            .map { it.brand }.distinct().sorted()

    suspend fun hasProfiles(category: DeviceCategory, connection: ConnectionType): Boolean =
        profiles().any { it.category == category && it.connectionType == connection }

    companion object { const val ASSET_PATH = "catalog/catalog_v1.json" }
}
