package com.akillikumanda.app.data.catalog

import com.akillikumanda.app.core.model.ConnectionType
import com.akillikumanda.app.core.model.DeviceCategory
import com.akillikumanda.app.core.model.RemoteProfile

data class CandidateQuery(
    val category: DeviceCategory,
    val brand: String?,          // null = marka bilinmiyor
    val model: String?,          // null/boş = model bilinmiyor
    val connection: ConnectionType,
)

data class RankedCandidate(val profile: RemoteProfile, val score: Int, val reasons: List<String>)

/**
 * Yalnızca sıralama yapar; puan gerçek donanım uyumluluğunun yerine geçmez.
 * Kategori ve bağlantı türü eşleşmeyen profiller hiç aday olmaz.
 */
class CandidateRanker(
    private val frequencySupported: (Int) -> Boolean,
) {
    fun rank(
        profiles: List<RemoteProfile>,
        query: CandidateQuery,
        previouslyConfirmedProfileIds: Set<String>,
    ): List<RankedCandidate> {
        val brand = query.brand?.trim()?.takeIf { it.isNotEmpty() }
        val model = query.model?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        return profiles
            .asSequence()
            .filter { it.category == query.category && it.connectionType == query.connection }
            .filter { brand == null || it.brand.equals(brand, ignoreCase = true) }
            .map { p ->
                var s = 0
                val r = mutableListOf<String>()
                val pModel = p.model?.uppercase()
                if (model != null && pModel != null && pModel == model) { s += 1000; r += "Tam model eşleşmesi" }
                if (brand != null) { s += 300; r += "Marka ve kategori eşleşmesi" }
                if (frequencySupported(p.carrierFrequencyHz)) { s += 100; r += "Frekans bu telefonda destekleniyor" }
                else r += "Frekans bu telefonda desteklenmiyor olabilir"
                if (p.id in previouslyConfirmedProfileIds) { s += 50; r += "Daha önce bu telefonda çalıştı" }
                if (model != null && (p.modelPrefixes.any { model.startsWith(it.uppercase()) } ||
                        (pModel != null && pModel != model && (model.startsWith(pModel) || pModel.startsWith(model))))
                ) { s += 20; r += "Kısmi model eşleşmesi" }
                RankedCandidate(p, s, r)
            }
            .sortedWith(compareByDescending<RankedCandidate> { it.score }.thenBy { it.profile.brand }.thenBy { it.profile.id })
            .toList()
    }
}
