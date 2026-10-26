package com.akillikumanda.app

import com.akillikumanda.app.core.model.CommandIds
import com.akillikumanda.app.core.model.IrProtocol
import com.akillikumanda.app.core.model.ProtocolData
import com.akillikumanda.app.core.model.VerificationStatus
import com.akillikumanda.app.data.catalog.CatalogParseResult
import com.akillikumanda.app.data.catalog.CatalogParser
import com.akillikumanda.app.platform.infrared.EncodeResult
import com.akillikumanda.app.platform.infrared.IrEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogParserTest {
    private fun bundled() = File("src/main/assets/catalog/catalog_v1.json").readText()

    @Test fun bundledCatalog_parsesWithoutWarnings() {
        val r = CatalogParser.parse(bundled()) as CatalogParseResult.Ok
        assertEquals(4, r.profiles.size)
        assertTrue(r.warnings.isEmpty())
        assertTrue(r.profiles.all { it.verificationStatus == VerificationStatus.UNVERIFIED })
        assertTrue(r.profiles.all { it.source.isNotBlank() && it.license.isNotBlank() })
    }

    @Test fun bundledCatalog_everyCommandEncodes() {
        val r = CatalogParser.parse(bundled()) as CatalogParseResult.Ok
        val enc = IrEncoder()
        r.profiles.forEach { p -> p.commands.forEach { c ->
            val e = enc.encode(p.protocol, p.carrierFrequencyHz, c.protocolData)
            assertTrue("${p.id}/${c.id}: $e", e is EncodeResult.Ok)
        } }
    }

    @Test fun samsungValueParsedAsUnsigned32() {
        val r = CatalogParser.parse(bundled()) as CatalogParseResult.Ok
        val p = r.profiles.first { it.protocol == IrProtocol.SAMSUNG32 }
        assertEquals(ProtocolData.Scalar(0xE0E040BFL, 32), p.command(CommandIds.POWER)!!.protocolData)
    }

    @Test fun brokenJson_isFailure() {
        assertTrue(CatalogParser.parse("{ bozuk") is CatalogParseResult.Failure)
        assertTrue(CatalogParser.parse("") is CatalogParseResult.Failure)
    }

    @Test fun wrongSchema_isFailure() {
        assertTrue(CatalogParser.parse("""{"schemaVersion":99,"catalogVersion":"x","profiles":[]}""") is CatalogParseResult.Failure)
    }

    @Test fun emptyCatalog_isOkWithNoProfiles() {
        val r = CatalogParser.parse("""{"schemaVersion":1,"catalogVersion":"x","profiles":[]}""") as CatalogParseResult.Ok
        assertTrue(r.profiles.isEmpty())
    }

    @Test fun invalidProfilesSkippedWithWarnings() {
        val json = """{"schemaVersion":1,"catalogVersion":"x","profiles":[
          {"id":"a","category":"TV","brand":"X","protocol":"NEC","connectionType":"IR","carrierFrequencyHz":38000,
           "source":"","license":"l","verificationStatus":"UNVERIFIED","version":1,
           "commands":[{"id":"POWER","name":"G","data":{"type":"scalar","value":"20DF10EF","bits":32}}]},
          {"id":"b","category":"UZAY","brand":"X","protocol":"NEC","connectionType":"IR","carrierFrequencyHz":38000,
           "source":"s","license":"l","verificationStatus":"UNVERIFIED","version":1,
           "commands":[{"id":"POWER","name":"G","data":{"type":"scalar","value":"20DF10EF","bits":32}}]},
          {"id":"c","category":"TV","brand":"X","protocol":"NEC","connectionType":"IR","carrierFrequencyHz":38000,
           "source":"s","license":"l","verificationStatus":"UNVERIFIED","version":1,
           "commands":[{"id":"POWER","name":"G","data":{"type":"scalar","value":"ZZ","bits":32}}]},
          {"id":"d","category":"TV","brand":"X","protocol":"NEC","connectionType":"IR","carrierFrequencyHz":38000,
           "source":"s","license":"l","verificationStatus":"UNVERIFIED","version":1,
           "commands":[{"id":"POWER","name":"G","data":{"type":"scalar","value":"20DF10EF","bits":32}}]},
          {"id":"d","category":"TV","brand":"Y","protocol":"NEC","connectionType":"IR","carrierFrequencyHz":38000,
           "source":"s","license":"l","verificationStatus":"UNVERIFIED","version":1,
           "commands":[{"id":"POWER","name":"G","data":{"type":"scalar","value":"20DF10EF","bits":32}}]}
        ]}"""
        val r = CatalogParser.parse(json) as CatalogParseResult.Ok
        assertEquals(listOf("d"), r.profiles.map { it.id })
        assertEquals("X", r.profiles.single().brand)
        assertEquals(4, r.warnings.size)
    }

    @Test fun snapshotRoundTrip() {
        val r = CatalogParser.parse(bundled()) as CatalogParseResult.Ok
        r.profiles.forEach { p ->
            assertEquals(p, CatalogParser.decodeSnapshot(CatalogParser.encodeSnapshot(p)))
        }
    }

    @Test fun corruptSnapshot_returnsNull() {
        assertEquals(null, CatalogParser.decodeSnapshot("{bozuk"))
    }
}
