package com.akillikumanda.app.data.catalog

import com.akillikumanda.app.core.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CatalogDto(val schemaVersion: Int, val catalogVersion: String, val profiles: List<ProfileDto>)

@Serializable
data class ProfileDto(
    val id: String,
    val category: String,
    val brand: String,
    val model: String? = null,
    val modelPrefixes: List<String> = emptyList(),
    val protocol: String,
    val connectionType: String,
    val carrierFrequencyHz: Int,
    val source: String,
    val license: String,
    val verificationStatus: String,
    val version: Int,
    val commands: List<CommandDto>,
)

@Serializable
data class CommandDto(
    val id: String,
    val name: String,
    val description: String = "",
    val data: DataDto,
    val repeat: String = "ONCE",
    val state: String = "ANY",
)

@Serializable
data class DataDto(
    val type: String,
    val value: String? = null,
    val bits: Int? = null,
    val pattern: List<Int>? = null,
    val codec: String? = null,
)

sealed interface CatalogParseResult {
    data class Ok(val catalogVersion: String, val profiles: List<RemoteProfile>, val warnings: List<String>) : CatalogParseResult
    data class Failure(val reason: String) : CatalogParseResult
}

/** Katalog JSON'unu doğrular. Hatalı profiller atlanır ve uyarı olarak raporlanır; uydurma veri üretilmez. */
object CatalogParser {
    const val SUPPORTED_SCHEMA = 1
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parse(text: String): CatalogParseResult {
        val dto = try {
            json.decodeFromString(CatalogDto.serializer(), text)
        } catch (e: Exception) {
            return CatalogParseResult.Failure("Katalog dosyası okunamadı (bozuk JSON): ${e.message?.take(120)}")
        }
        if (dto.schemaVersion != SUPPORTED_SCHEMA)
            return CatalogParseResult.Failure("Desteklenmeyen katalog şeması: ${dto.schemaVersion}")
        val warnings = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val profiles = dto.profiles.mapNotNull { p ->
            if (!seen.add(p.id)) { warnings += "Yinelenen profil kimliği atlandı: ${p.id}"; return@mapNotNull null }
            when (val r = toDomain(p)) {
                is ProfileMapping.Ok -> r.profile
                is ProfileMapping.Error -> { warnings += "${p.id}: ${r.reason}"; null }
            }
        }
        return CatalogParseResult.Ok(dto.catalogVersion, profiles, warnings)
    }

    sealed interface ProfileMapping {
        data class Ok(val profile: RemoteProfile) : ProfileMapping
        data class Error(val reason: String) : ProfileMapping
    }

    fun toDomain(p: ProfileDto): ProfileMapping {
        if (p.id.isBlank() || p.brand.isBlank()) return ProfileMapping.Error("Kimlik veya marka boş")
        if (p.source.isBlank() || p.license.isBlank()) return ProfileMapping.Error("Kaynak/lisans bilgisi eksik")
        val category = DeviceCategory.fromName(p.category) ?: return ProfileMapping.Error("Bilinmeyen kategori ${p.category}")
        val protocol = IrProtocol.fromName(p.protocol) ?: return ProfileMapping.Error("Bilinmeyen protokol ${p.protocol}")
        val conn = ConnectionType.fromName(p.connectionType) ?: return ProfileMapping.Error("Bilinmeyen bağlantı ${p.connectionType}")
        val ver = VerificationStatus.fromName(p.verificationStatus) ?: return ProfileMapping.Error("Bilinmeyen doğrulama durumu")
        if (conn == ConnectionType.IR && p.carrierFrequencyHz !in 20_000..60_000)
            return ProfileMapping.Error("Geçersiz taşıyıcı frekans ${p.carrierFrequencyHz}")
        if (p.commands.isEmpty()) return ProfileMapping.Error("Komut yok")
        val cmds = mutableListOf<RemoteCommand>()
        val cmdIds = mutableSetOf<String>()
        for (c in p.commands) {
            if (!cmdIds.add(c.id)) return ProfileMapping.Error("Yinelenen komut ${c.id}")
            val data = toProtocolData(c.data) ?: return ProfileMapping.Error("Komut ${c.id} için geçersiz veri")
            val repeat = runCatching { RepeatPolicy.valueOf(c.repeat) }.getOrNull()
                ?: return ProfileMapping.Error("Komut ${c.id}: geçersiz tekrar politikası")
            val state = runCatching { DeviceStateRequirement.valueOf(c.state) }.getOrNull()
                ?: return ProfileMapping.Error("Komut ${c.id}: geçersiz durum")
            cmds += RemoteCommand(c.id, c.name, c.description, data, repeat, state)
        }
        return ProfileMapping.Ok(
            RemoteProfile(
                id = p.id, category = category, brand = p.brand.trim(), model = p.model?.trim()?.ifEmpty { null },
                modelPrefixes = p.modelPrefixes, protocol = protocol, connectionType = conn,
                carrierFrequencyHz = p.carrierFrequencyHz, commands = cmds, source = p.source, license = p.license,
                verificationStatus = ver, version = p.version,
            )
        )
    }

    private fun toProtocolData(d: DataDto): ProtocolData? = when (d.type) {
        "scalar" -> {
            val bits = d.bits
            val v = d.value?.removePrefix("0x")?.removePrefix("0X")?.let { runCatching { it.toULong(16).toLong() }.getOrNull() }
            if (bits == null || v == null || bits !in 1..64) null else ProtocolData.Scalar(v, bits)
        }
        "raw" -> d.pattern?.takeIf { it.isNotEmpty() && it.all { x -> x > 0 } }?.let { ProtocolData.Raw(it) }
        "ac_state" -> d.codec?.takeIf { it.isNotBlank() }?.let { ProtocolData.AcFullState(it) }
        else -> null
    }

    /** Kaydedilen kumandada profilin anlık kopyası tutulur; katalog güncellemesi kaydı bozmaz. */
    fun toDto(p: RemoteProfile): ProfileDto = ProfileDto(
        id = p.id, category = p.category.name, brand = p.brand, model = p.model, modelPrefixes = p.modelPrefixes,
        protocol = p.protocol.name, connectionType = p.connectionType.name, carrierFrequencyHz = p.carrierFrequencyHz,
        source = p.source, license = p.license, verificationStatus = p.verificationStatus.name, version = p.version,
        commands = p.commands.map { c ->
            CommandDto(
                id = c.id, name = c.name, description = c.description, repeat = c.repeatPolicy.name,
                state = c.supportedDeviceState.name,
                data = when (val d = c.protocolData) {
                    is ProtocolData.Scalar -> DataDto(type = "scalar", value = d.value.toULong().toString(16).uppercase(), bits = d.bits)
                    is ProtocolData.Raw -> DataDto(type = "raw", pattern = d.patternMicros)
                    is ProtocolData.AcFullState -> DataDto(type = "ac_state", codec = d.codecId)
                },
            )
        },
    )

    fun encodeSnapshot(p: RemoteProfile): String = json.encodeToString(ProfileDto.serializer(), toDto(p))

    fun decodeSnapshot(text: String): RemoteProfile? = runCatching {
        (toDomain(json.decodeFromString(ProfileDto.serializer(), text)) as? ProfileMapping.Ok)?.profile
    }.getOrNull()
}
