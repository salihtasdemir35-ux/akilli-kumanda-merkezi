package com.akillikumanda.app.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akillikumanda.app.data.catalog.CatalogRepository
import com.akillikumanda.app.data.catalog.CatalogState
import com.akillikumanda.app.data.remote.BackupManager
import com.akillikumanda.app.data.settings.AppSettings
import com.akillikumanda.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val backup: BackupManager,
    private val catalog: CatalogRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    private val _catalog = MutableStateFlow<CatalogState?>(null)
    val catalogState: StateFlow<CatalogState?> = _catalog.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init { viewModelScope.launch { _catalog.value = catalog.load() } }

    fun setHaptics(v: Boolean) = viewModelScope.launch { settingsRepo.setHaptics(v) }
    fun setMaxTests(v: Int) = viewModelScope.launch { settingsRepo.setMaxTests(v) }
    fun clearMessage() { _message.value = null }

    fun exportTo(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _message.value = runCatching {
            val text = backup.export()
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    ?: error("Dosya açılamadı")
            }
            "Yedek kaydedildi"
        }.getOrElse { "Yedekleme başarısız: ${it.message}" }
    }

    fun importFrom(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _message.value = runCatching {
            val text = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { s ->
                    val bytes = s.readBytes()
                    require(bytes.size <= 2_000_000) { "Dosya çok büyük" }
                    String(bytes, Charsets.UTF_8)
                } ?: error("Dosya açılamadı")
            }
            val r = backup.import(text)
            "Geri yükleme: ${r.imported} eklendi, ${r.skippedDuplicate} zaten vardı, ${r.skippedInvalid} geçersiz"
        }.getOrElse { "Geri yükleme başarısız: ${it.message}" }
    }
}
