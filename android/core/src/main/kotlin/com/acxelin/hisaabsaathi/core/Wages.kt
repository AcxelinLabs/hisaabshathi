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

    /** Lowest applicable daily rate: the floor nobody in that list should be paid below. */
    fun floor(rates: List<WageRate>): WageRate? = rates.minByOrNull { it.dailyRupees }

    /** Rupees per day the worker's recorded average is below the benchmark; 0 or less means at or above. */
    fun shortfallPaise(avgDailyEarnedPaise: Long, rate: WageRate): Long =
        Money.rupeesToPaise(rate.dailyRupees) - avgDailyEarnedPaise
}
