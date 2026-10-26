package com.akillikumanda.app

import com.akillikumanda.app.core.model.ConnectionType
import com.akillikumanda.app.core.model.DeviceCategory
import com.akillikumanda.app.data.catalog.CandidateQuery
import com.akillikumanda.app.data.catalog.CandidateRanker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateRankerTest {
    private val ranker = CandidateRanker { it in 30_000..40_000 }
    private val profiles = listOf(
        tvProfile(id = "lg.generic", brand = "LG"),
        tvProfile(id = "lg.exact", brand = "LG", model = "43UN7000"),
        tvProfile(id = "lg.prefix", brand = "LG", prefixes = listOf("55")),
        tvProfile(id = "samsung", brand = "Samsung"),
    )

    @Test fun exactModelFirst() {
        val r = ranker.rank(profiles, CandidateQuery(DeviceCategory.TV, "LG", "43un7000", ConnectionType.IR), emptySet())
        assertEquals("lg.exact", r.first().profile.id)
        assertTrue(r.none { it.profile.brand == "Samsung" })
    }

    @Test fun previouslyConfirmedBoosts() {
        val r = ranker.rank(profiles, CandidateQuery(DeviceCategory.TV, "LG", null, ConnectionType.IR), setOf("lg.prefix"))
        assertEquals("lg.prefix", r.first().profile.id)
    }

    @Test fun partialModelMatch() {
        val r = ranker.rank(profiles, CandidateQuery(DeviceCategory.TV, "LG", "55NANO", ConnectionType.IR), emptySet())
        assertEquals("lg.prefix", r.first().profile.id)
    }

    @Test fun unknownBrand_returnsAllOfCategory() {
        val r = ranker.rank(profiles, CandidateQuery(DeviceCategory.TV, null, null, ConnectionType.IR), emptySet())
        assertEquals(4, r.size)
    }

    @Test fun otherCategoryOrConnection_noCandidates() {
        assertTrue(ranker.rank(profiles, CandidateQuery(DeviceCategory.AC, null, null, ConnectionType.IR), emptySet()).isEmpty())
        assertTrue(ranker.rank(profiles, CandidateQuery(DeviceCategory.TV, null, null, ConnectionType.WIFI), emptySet()).isEmpty())
    }

    @Test fun unsupportedFrequencyRanksLower() {
        val list = listOf(tvProfile(id = "a", hz = 56_000), tvProfile(id = "b", hz = 38_000))
        val r = ranker.rank(list, CandidateQuery(DeviceCategory.TV, "LG", null, ConnectionType.IR), emptySet())
        assertEquals("b", r.first().profile.id)
    }
}
