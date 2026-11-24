package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** A place said aloud, in any of the app's languages, finds the right state and district. */
class PlacesTest {
    private val pins by lazy { PinDirectory.parse(File("../app/src/main/assets/pin_in.txt").readText()) }

    @Test fun scriptsInLatinLetters() {
        assertEquals("odishaa", Translit.latin("ओडिशा"))
        assertEquals("khordhaa", Translit.latin("खोर्धा"))
        assertEquals("kataka", Translit.latin("କଟକ"))
    }

    @Test fun statesSaidInEveryLanguage() {
        val said = mapOf(
            "मैं ओडिशा में रहता हूँ" to "Odisha",
            "उड़ीसा" to "Odisha",
            "ଓଡ଼ିଶା" to "Odisha",
            "బీహార్" to "Bihar",
            "ಕರ್ನಾಟಕ" to "Karnataka",
            "தமிழ்நாடு" to "Tamil Nadu",
            "tamil nadu" to "Tamil Nadu",
            "पश्चिम बंगाल" to "West Bengal",
            "West Bengal" to "West Bengal",
            "तेलंगाना" to "Telangana",
            "आंध्र प्रदेश" to "Andhra Pradesh",
            "महाराष्ट्र" to "Maharashtra",
            "झारखंड" to "Jharkhand",
            "उत्तर प्रदेश" to "Uttar Pradesh",
            "राजस्थान" to "Rajasthan",
        )
        val wrong = said.mapNotNull { (text, want) ->
            val got = PlaceMatch.best(text, pins.states)
            if (got == want) null else "$text → $got (want $want)"
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test fun districtsWithinTheState() {
        fun d(state: String, text: String) = PlaceMatch.best(text, pins.districts(state))
        assertEquals("Odisha districts: ${pins.districts("Odisha")}", true, pins.districts("Odisha").isNotEmpty())
        val od = pins.districts("Odisha")
        assertEquals(od.first { it.startsWith("Khordha") }, d("Odisha", "खोरधा"))
        assertEquals(od.first { it.startsWith("Cuttack") }, d("Odisha", "କଟକ"))
        assertEquals(od.first { it.startsWith("Ganjam") }, d("Odisha", "गंजाम ज़िला"))
        val ka = pins.districts("Karnataka")
        assertEquals(ka.first { it.contains("Mysuru") || it.contains("Mysore") }, d("Karnataka", "ಮೈಸೂರು"))
        val tn = pins.districts("Tamil Nadu")
        assertEquals(tn.first { it.startsWith("Coimbatore") }, d("Tamil Nadu", "கோயம்புத்தூர்"))
    }

    @Test fun nothingCloseIsNothing() {
        assertNull(PlaceMatch.best("पता नहीं", pins.states))
        assertNull(PlaceMatch.best("", pins.states))
    }
}
