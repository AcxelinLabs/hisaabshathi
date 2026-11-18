package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class NumberSpellerTest {
    /** The Kotlin speller must say exactly what the tested Python reference (tools/tts/numspell.py) says. */
    @Test fun matchesTheReference() {
        val cases = Json.parseToJsonElement(javaClass.getResource("/numspell_cases.json")!!.readText()).jsonObject
        for ((code, table) in cases) {
            val lang = Lang.fromCode(code)
            for ((k, v) in table.jsonObject) {
                val got = if (k == "600 more") NumberSpeller.spellNumbers(if (lang == Lang.EN) "600 x" else "600 a", lang).substringBeforeLast(' ')
                else NumberSpeller.spell(k.toLong(), lang)
                assertEquals("$code $k", v.jsonPrimitive.content, got)
            }
        }
    }

    @Test fun spellsNumbersInsideSentences() {
        assertEquals("इस हफ्ते आपने छह सौ रुपये कमाए।", NumberSpeller.spellNumbers("इस हफ्ते आपने 600 रुपये कमाए।", Lang.HI))
        assertEquals("ఈ వారం మీరు ఆరు వందల రూపాయలు సంపాదించారు.", NumberSpeller.spellNumbers("ఈ వారం మీరు 600 రూపాయలు సంపాదించారు.", Lang.TE))
        assertEquals("ऊपर एक हज़ार दो सौ", NumberSpeller.spellNumbers("ऊपर 1,200", Lang.HI))
        assertEquals("twelve point five", NumberSpeller.spellNumbers("12.50", Lang.EN))
    }
}
