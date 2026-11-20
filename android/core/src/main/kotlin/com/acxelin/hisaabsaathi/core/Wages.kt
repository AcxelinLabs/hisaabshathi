package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WageRate(
    /** "CENTRAL" or an Indian state name as shown to the worker. */
    val region: String,
    val sector: String,
    val skill: String,
    val dailyRupees: Double,
    val effectiveFrom: String,
    val source: String,
    val url: String = "",
    /** Area or zone inside the region, when rates differ by place. */
    val zone: String = "",
    /** "high", "medium" or "low": how well the figure was verified when the pack was made. */
    val confidence: String = "",
    /** Last day the rate applies (a half-yearly allowance), or "" when open-ended. */
    val validTo: String = "",
)

@Serializable
data class WagePack(
    val version: Int,
    val updated: String,
    val note: String = "",
    val regions: List<String> = emptyList(),
    val rates: List<WageRate> = emptyList(),
)

object Wages {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): WagePack = json.decodeFromString(WagePack.serializer(), text)

    /** Rates for the worker's state, falling back to the central-government rates. */
    fun forRegion(pack: WagePack, region: String): List<WageRate> =
        pack.rates.filter { it.region.equals(region, ignoreCase = true) }
            .ifEmpty { pack.rates.filter { it.region == "CENTRAL" } }

    /**
     * Where a state's rates differ by zone, the zone paying least: the floor that applies
     * anywhere in the state, so the app never tells a worker they are underpaid when they may
     * not be. Rates without zones are returned as they are.
     */
    fun lowestZone(rates: List<WageRate>): List<WageRate> {
        val zones = rates.groupBy { it.zone }
        if (zones.size <= 1) return rates
        return zones.values.minBy { z -> z.minOf { it.dailyRupees } }
    }

    /** The best rate any zone pays for [skill]: what city workers in the same job get. */
    fun topFor(rates: List<WageRate>, skill: String): WageRate? =
        rates.filter { it.skill.equals(skill, ignoreCase = true) }.maxByOrNull { it.dailyRupees }

    /** Lowest applicable daily rate: the floor nobody in that list should be paid below. */
    fun floor(rates: List<WageRate>): WageRate? = rates.minByOrNull { it.dailyRupees }

    /** Rupees per day the worker's recorded average is below the benchmark; 0 or less means at or above. */
    fun shortfallPaise(avgDailyEarnedPaise: Long, rate: WageRate): Long =
        Money.rupeesToPaise(rate.dailyRupees) - avgDailyEarnedPaise
}
