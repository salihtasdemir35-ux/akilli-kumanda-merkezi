package com.akillikumanda.app.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akillikumanda.app.core.designsystem.BannerKind
import com.akillikumanda.app.core.model.ConnectionType
import com.akillikumanda.app.core.model.DeviceCategory
import com.akillikumanda.app.core.model.RemoteProfile
import com.akillikumanda.app.data.catalog.CandidateQuery
import com.akillikumanda.app.data.catalog.CandidateRanker
import com.akillikumanda.app.data.catalog.CatalogRepository
import com.akillikumanda.app.data.catalog.CatalogState
import com.akillikumanda.app.data.catalog.RankedCandidate
import com.akillikumanda.app.data.remote.SaveResult
import com.akillikumanda.app.data.remote.SavedRemoteRepository
import com.akillikumanda.app.data.settings.SettingsRepository
import com.akillikumanda.app.platform.capability.DeviceCapabilityRepository
import com.akillikumanda.app.platform.infrared.InfraredTransmitter
import com.akillikumanda.app.platform.infrared.TransmitResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DiscoveryStep(val number: Int, val title: String) {
    CATEGORY(1, "Cihaz türü"),
    DETAILS(2, "Marka ve model"),
    CONNECTION(3, "İletişim yöntemi"),
    TEST(4, "Aday profilleri test et"),
    NO_MATCH(4, "Sonuç"),
    SAVE(5, "Kaydet"),
    DONE(5, "Tamamlandı"),
}

data class StatusMessage(val text: String, val kind: BannerKind, val title: String? = null)

data class DiscoveryUiState(
    val step: DiscoveryStep = DiscoveryStep.CATEGORY,
    val category: DeviceCategory? = null,
    val brands: List<String> = emptyList(),
    val brand: String = "",
    val model: String = "",
    val customName: String = "",
    val catalogError: String? = null,
    val options: List<ConnectionOption> = emptyList(),
    val candidates: List<RankedCandidate> = emptyList(),
    val index: Int = 0,
    val testCommandId: String? = null,
    val sendsOnCurrent: Int = 0,
    val maxSends: Int = 3,
    val signalSentOnCurrent: Boolean = false,
    val sending: Boolean = false,
    val status: StatusMessage? = null,
    val confirmed: RemoteProfile? = null,
    val saveName: String = "",
    val room: String = "",
    val note: String = "",
    val saveError: String? = null,
    val duplicateId: Long? = null,
    val savedId: Long? = null,
    val noMatchReason: String? = null,
) {
    val current: RankedCandidate? get() = candidates.getOrNull(index)
    val canSendMore: Boolean get() = sendsOnCurrent < maxSends
}

class DiscoveryViewModel(
    private val catalog: CatalogRepository,
    private val ranker: CandidateRanker,
    private val infrared: InfraredTransmitter,
    private val capabilities: DeviceCapabilityRepository,
    private val remotes: SavedRemoteRepository,
    private val settings: SettingsRepository,
    initialCategory: DeviceCategory?,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoveryUiState())
    val state: StateFlow<DiscoveryUiState> = _state.asStateFlow()
    private var testJob: Job? = null

    init {
        viewModelScope.launch {
            val max = settings.settings.first().maxTestsPerCandidate
            _state.update { it.copy(maxSends = max) }
        }
        if (initialCategory != null) selectCategory(initialCategory)
    }

    fun selectCategory(category: DeviceCategory) {
        _state.update { DiscoveryUiState(step = DiscoveryStep.DETAILS, category = category, maxSends = it.maxSends) }
        viewModelScope.launch {
            when (val c = catalog.load()) {
                is CatalogState.Broken -> _state.update { it.copy(catalogError = c.reason) }
                is CatalogState.Loaded -> _state.update { it.copy(brands = catalog.brands(category, ConnectionType.IR)) }
            }
        }
    }

    fun setBrand(v: String) = _state.update { it.copy(brand = v.take(40)) }
    fun setModel(v: String) = _state.update { it.copy(model = v.take(40)) }
    fun setCustomName(v: String) = _state.update { it.copy(customName = v.take(60)) }

    fun continueFromDetails() {
        val cat = _state.value.category ?: return
        viewModelScope.launch {
            val caps = capabilities.read()
            val hasIr = catalog.hasProfiles(cat, ConnectionType.IR)
            _state.update {
                it.copy(step = DiscoveryStep.CONNECTION, options = ConnectionPolicy.options(cat, caps.irUsable, hasIr))
            }
        }
    }

    fun chooseConnection(option: ConnectionOption) {
        if (!option.usable) return
        val s = _state.value
        val cat = s.category ?: return
        viewModelScope.launch {
            val query = CandidateQuery(cat, s.brand.ifBlank { null }, s.model.ifBlank { null }, option.type)
            val ranked = ranker.rank(catalog.profiles(), query, remotes.confirmedProfileIds())
            if (ranked.isEmpty()) {
                _state.update {
                    it.copy(step = DiscoveryStep.NO_MATCH, candidates = emptyList(),
                        noMatchReason = if (s.brand.isNotBlank())
                            "Yerel katalogda \"${s.brand}\" markası için ${cat.title.lowercase()} profili bulunamadı."
                        else "Yerel katalogda bu kategori için profil bulunamadı.")
                }
            } else {
                _state.update { it.copy(step = DiscoveryStep.TEST, candidates = ranked).resetCandidate(0) }
            }
        }
    }

    private fun DiscoveryUiState.resetCandidate(i: Int): DiscoveryUiState {
        val profile = candidates.getOrNull(i)?.profile
        val first = profile?.let { ConnectionPolicy.safeTestCommands(it).firstOrNull() }
        return copy(index = i, testCommandId = first, sendsOnCurrent = 0, signalSentOnCurrent = false, sending = false,
            status = StatusMessage("Telefonun üst kenarını cihaza doğrultun (1–3 m, arada engel olmasın). Ardından \"Test et\"e basın.", BannerKind.INFO))
    }

    fun chooseTestCommand(id: String) = _state.update { it.copy(testCommandId = id) }

    fun test() {
        val s = _state.value
        val profile = s.current?.profile ?: return
        val cmd = s.testCommandId ?: return
        if (s.sending || !s.canSendMore) return
        _state.update { it.copy(sending = true, status = StatusMessage("Sinyal gönderiliyor…", BannerKind.INFO)) }
        testJob = viewModelScope.launch {
            val result = try {
                infrared.send(profile, cmd)
            } catch (e: CancellationException) {
                _state.update { it.copy(sending = false) }
                throw e
            }
            _state.update {
                it.copy(
                    sending = false,
                    sendsOnCurrent = it.sendsOnCurrent + 1,
                    signalSentOnCurrent = it.signalSentOnCurrent || result == TransmitResult.Success,
                    status = statusFor(result),
                )
            }
        }
    }

    fun stop() {
        testJob?.cancel()
        infrared.cancelActiveTest()
        _state.update { it.copy(sending = false, status = StatusMessage("Test durduruldu. Başka sinyal gönderilmeyecek.", BannerKind.INFO)) }
    }

    /** Kullanıcı cihazın tepki verdiğini onayladı. Yalnızca gerçekten sinyal gönderildiyse geçerli. */
    fun worked() {
        val s = _state.value
        if (!s.signalSentOnCurrent || s.sending) return
        val profile = s.current?.profile ?: return
        val name = s.customName.ifBlank { "${profile.brand} ${profile.category.title}" }
        _state.update { it.copy(step = DiscoveryStep.SAVE, confirmed = profile, saveName = name, saveError = null, duplicateId = null) }
    }

    fun nextCandidate() {
        if (_state.value.sending) stop()
        val next = _state.value.index + 1
        if (next >= _state.value.candidates.size) {
            _state.update {
                it.copy(step = DiscoveryStep.NO_MATCH, noMatchReason = "Denenen ${it.candidates.size} adayın hiçbiri için cihaz tepkisi onaylanmadı.")
            }
        } else _state.update { it.resetCandidate(next) }
    }

    fun setSaveName(v: String) = _state.update { it.copy(saveName = v.take(60), saveError = null, duplicateId = null) }
    fun setRoom(v: String) = _state.update { it.copy(room = v.take(40)) }
    fun setNote(v: String) = _state.update { it.copy(note = v.take(200)) }

    fun save() {
        val s = _state.value
        val profile = s.confirmed ?: return
        viewModelScope.launch {
            when (val r = remotes.saveConfirmed(profile, s.saveName, s.room, s.note)) {
                is SaveResult.Saved -> _state.update { it.copy(step = DiscoveryStep.DONE, savedId = r.id) }
                is SaveResult.Duplicate -> _state.update {
                    it.copy(saveError = "Bu profil aynı adla zaten kayıtlı. Farklı bir ad verin ya da mevcut kumandayı açın.", duplicateId = r.existingId)
                }
                is SaveResult.Invalid -> _state.update { it.copy(saveError = r.reason) }
            }
        }
    }

    fun back() {
        if (_state.value.sending) stop()
        _state.update {
            when (it.step) {
                DiscoveryStep.CATEGORY, DiscoveryStep.DONE -> it
                DiscoveryStep.DETAILS -> it.copy(step = DiscoveryStep.CATEGORY)
                DiscoveryStep.CONNECTION -> it.copy(step = DiscoveryStep.DETAILS)
                DiscoveryStep.TEST, DiscoveryStep.NO_MATCH -> it.copy(step = DiscoveryStep.CONNECTION)
                DiscoveryStep.SAVE -> it.copy(step = DiscoveryStep.TEST)
            }
        }
    }

    fun editDetails() = _state.update { it.copy(step = DiscoveryStep.DETAILS) }
    fun restart() = _state.update { DiscoveryUiState(maxSends = it.maxSends) }

    override fun onCleared() {
        testJob?.cancel()
        infrared.cancelActiveTest()
    }

    private fun statusFor(r: TransmitResult): StatusMessage = when (r) {
        TransmitResult.Success -> StatusMessage(
            "Sinyal gönderildi; cihaz tepkisini doğrulayın. Cihaz tepki verdiyse \"Çalıştı\", vermediyse \"Çalışmadı\" seçin.",
            BannerKind.SUCCESS, "Sinyal gönderildi")
        TransmitResult.UnsupportedHardware -> StatusMessage("Bu telefonda kullanılabilir IR verici yok.", BannerKind.ERROR, "Donanım desteklenmiyor")
        is TransmitResult.UnsupportedFrequency -> StatusMessage(
            "Profil ${r.requestedHz} Hz taşıyıcı istiyor; telefon bu frekansı desteklemiyor. \"Sonraki kod\"u deneyin.",
            BannerKind.ERROR, "Frekans desteklenmiyor")
        is TransmitResult.InvalidProfile -> StatusMessage(r.reason, BannerKind.ERROR, "Geçersiz profil")
        is TransmitResult.PermissionDenied -> StatusMessage(r.detail, BannerKind.ERROR, "İzin sorunu")
        is TransmitResult.TransmissionError -> StatusMessage("Gönderim başarısız: ${r.detail}", BannerKind.ERROR, "Gönderim hatası")
        TransmitResult.Cancelled -> StatusMessage("Test durduruldu.", BannerKind.INFO)
    }
}
