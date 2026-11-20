package com.acxelin.hisaabsaathi.core

/**
 * Every Indian PIN code with its state, district and position, on the phone: typing a PIN, or
 * "use my location", fills in the place.
 * From India Post's All India Pincode Directory (assets/pin_in.txt, built by
 * tools/make_pin_directory.py; Government Open Data Licence - India).
 *
 * Where a postal district has since been split (Andhra Pradesh in 2022, for example), a PIN
 * lists the current districts it may be in, best guess first; the worker confirms.
 */
class PinDirectory private constructor(
    /** All states and union territories, alphabetical. */
    val states: List<String>,
    private val districtLists: List<List<String>>,
    private val rows: Map<Int, String>,
    /** PINs with a known position (the middle of their post offices), for [nearest]. */
    private val placed: IntArray,
    private val lats: FloatArray,
    private val lons: FloatArray,
) {
    /** The PIN closest to a position, and how far away its post offices are. */
    data class Nearby(val pin: String, val km: Double, val place: Result.Found)

    sealed interface Result {
        /** Not six digits, or starts with 0. */
        data object Invalid : Result

        /** Six digits, but no post office has it: probably mistyped. */
        data object Unknown : Result

        /**
         * [districts]: the district, or the ones it may be in (best first). [sure]: there is only
         * one, or the first is a confident guess (the PIN's post offices are named after its town).
         */
        data class Found(val state: String, val districts: List<String>, val sure: Boolean) : Result
    }

    /** The districts of [state], alphabetical; empty for an unknown state. */
    fun districts(state: String): List<String> = states.indexOf(state).let { if (it < 0) emptyList() else districtLists[it] }

    fun lookup(pin: String): Result {
        if (!Pin.valid(pin)) return Result.Invalid
        val row = rows[pin.toInt()] ?: return Result.Unknown
        val si = row.substringBefore('|').toInt()
        val ds = row.substringAfter('|').substringBefore('|')
        val list = ds.removePrefix("*").split(',').map { districtLists[si][it.toInt()] }
        return Result.Found(states[si], list, list.size == 1 || ds.startsWith("*"))
    }

    /**
     * The nearest PIN to a position, from the bundled positions (no internet). Null if none is
     * within [maxKm]: far from any post office the directory knows, the answer would be a guess.
     */
    fun nearest(lat: Double, lon: Double, maxKm: Double = 60.0): Nearby? {
        var best = -1
        var bestD = Double.MAX_VALUE
        val k = Math.cos(Math.toRadians(lat))
        for (i in placed.indices) {
            val dx = (lons[i] - lon) * k
            val dy = lats[i] - lat
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = i }
        }
        if (best < 0) return null
        val km = Math.sqrt(bestD) * 111.2
        if (km > maxKm) return null
        val pin = placed[best].toString()
        return Nearby(pin, km, lookup(pin) as Result.Found)
    }

    companion object {
        val EMPTY = PinDirectory(emptyList(), emptyList(), emptyMap(), IntArray(0), FloatArray(0), FloatArray(0))

        fun parse(text: String): PinDirectory {
            val states = ArrayList<String>()
            val lists = ArrayList<List<String>>()
            val rows = HashMap<Int, String>(24_000)
            val placed = ArrayList<Int>(20_000)
            val lats = ArrayList<Float>(20_000)
            val lons = ArrayList<Float>(20_000)
            for (line in text.lineSequence()) {
                when {
                    line.isBlank() || line.startsWith("#") -> Unit
                    line.startsWith("S|") -> {
                        val parts = line.split('|', limit = 3)
                        states += parts[1]
                        lists += parts[2].split(';').filter { it.isNotEmpty() }
                    }
                    else -> line.substringBefore('|').toIntOrNull()?.let { pin ->
                        rows[pin] = line.substringAfter('|')
                        val parts = line.split('|')
                        if (parts.size >= 4) {
                            val la = parts[3].substringBefore(',').toFloatOrNull()
                            val lo = parts[3].substringAfter(',').toFloatOrNull()
                            if (la != null && lo != null) { placed += pin; lats += la; lons += lo }
                        }
                    }
                }
            }
            return PinDirectory(states, lists, rows, placed.toIntArray(), lats.toFloatArray(), lons.toFloatArray())
        }
    }
}
