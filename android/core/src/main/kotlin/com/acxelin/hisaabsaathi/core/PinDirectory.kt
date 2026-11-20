package com.acxelin.hisaabsaathi.core

/**
 * Every Indian PIN code with its state and district, on the phone, so typing a PIN fills both in.
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
) {
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
        val ds = row.substringAfter('|')
        val list = ds.removePrefix("*").split(',').map { districtLists[si][it.toInt()] }
        return Result.Found(states[si], list, list.size == 1 || ds.startsWith("*"))
    }

    companion object {
        val EMPTY = PinDirectory(emptyList(), emptyList(), emptyMap())

        fun parse(text: String): PinDirectory {
            val states = ArrayList<String>()
            val lists = ArrayList<List<String>>()
            val rows = HashMap<Int, String>(24_000)
            for (line in text.lineSequence()) {
                when {
                    line.isBlank() || line.startsWith("#") -> Unit
                    line.startsWith("S|") -> {
                        val parts = line.split('|', limit = 3)
                        states += parts[1]
                        lists += parts[2].split(';').filter { it.isNotEmpty() }
                    }
                    else -> line.substringBefore('|').toIntOrNull()?.let { rows[it] = line.substringAfter('|') }
                }
            }
            return PinDirectory(states, lists, rows)
        }
    }
}
