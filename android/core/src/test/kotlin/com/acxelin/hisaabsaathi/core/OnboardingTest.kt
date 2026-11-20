package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.time.LocalDate
import java.util.zip.GZIPOutputStream

class OnboardingTest {
    private val assets = File("../app/src/main/assets")
    private val today = LocalDate.of(2026, 11, 20)

    @Test fun stateAndLanguageFromLocation() {
        val map = StateMap.parse(File(assets, "india_states.txt").readText())
        val expected = listOf(
            Triple(20.2961, 85.8245, "Odisha" to Lang.OR),          // Bhubaneswar
            Triple(17.3850, 78.4867, "Telangana" to Lang.TE),       // Hyderabad
            Triple(16.5062, 80.6480, "Andhra Pradesh" to Lang.TE),  // Vijayawada
            Triple(12.9716, 77.5946, "Karnataka" to Lang.KN),       // Bengaluru
            Triple(13.0827, 80.2707, "Tamil Nadu" to Lang.TA),      // Chennai
            Triple(28.6139, 77.2090, "Delhi" to Lang.HI),
            Triple(25.5941, 85.1376, "Bihar" to Lang.HI),           // Patna
            Triple(19.0760, 72.8777, "Maharashtra" to Lang.EN),     // Mumbai
        )
        for ((lat, lon, want) in expected) {
            val state = map.stateAt(lat, lon)
            assertEquals(want.first, state)
            assertEquals(want.second, StateInfo.languageFor(state!!))
        }
        assertNull(map.stateAt(37.422, -122.084)) // outside India
        assertEquals("Other", StateInfo.regionFor("Bihar"))
        assertEquals("Odisha", StateInfo.regionFor("ORISSA"))
    }

    private fun secureQr(fields: List<String>): String {
        val raw = java.io.ByteArrayOutputStream()
        for (f in fields) { raw.write(f.toByteArray(Charsets.ISO_8859_1)); raw.write(255) }
        raw.write(ByteArray(300) { (it * 7).toByte() }) // stand-in for the photo and signature
        val gz = java.io.ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(raw.toByteArray()) }
        return BigInteger(1, gz.toByteArray()).toString()
    }

    @Test fun aadhaarSecureQr() {
        val qr = secureQr(listOf(
            "V2", "3", "123420190101120000000", "Ramesh Behera", "15-08-1990", "M", "S/O Hari Behera", "Khordha",
            "Near Temple", "12", "Old Town", "751002", "Bhubaneswar GPO", "Odisha", "Main Road", "Bhubaneswar", "Bhubaneswar",
        ))
        assertEquals(AadhaarQr.Details("Ramesh Behera", 36, "Odisha", "Khordha", "751002"), AadhaarQr.read(qr, today))
        // Without the version field.
        val old = secureQr(listOf(
            "2", "123420190101120000000", "Lakshmi Devi", "01/01/1985", "F", "W/O Ravi", "Hyderabad", "", "4-5",
            "Ameerpet", "500016", "Ameerpet", "Telangana", "", "Hyderabad", "Hyderabad",
        ))
        assertEquals(AadhaarQr.Details("Lakshmi Devi", 41, "Telangana", "Hyderabad", "500016"), AadhaarQr.read(old, today))
    }

    @Test fun aadhaarXmlQr() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><PrintLetterBarcodeData uid="999999990019" name="Shivshankar Choudhury" gender="M" yob="1968" co="S/O: Ramchandra" house="12" vtc="Sector 2" dist="Bengaluru Urban" state="Karnataka" pc="560001"/>"""
        assertEquals(AadhaarQr.Details("Shivshankar Choudhury", 58, "Karnataka", "Bengaluru Urban", "560001"), AadhaarQr.read(xml, today))
        assertNull(AadhaarQr.read("https://example.com", today))
        assertNull(AadhaarQr.read("1234", today))
    }

    @Test fun districtsAndPin() {
        val d = Districts.parse(File(assets, "districts_in.txt").readText())
        assertEquals(30, d.getValue("Odisha").size)
        assertEquals("Khordha (Khurda)", Districts.match("Khurda", d.getValue("Odisha")))
        assertEquals("Khordha (Khurda)", Districts.match("KHORDHA", d.getValue("Odisha")))
        assertEquals("Mysuru (Mysore)", Districts.match("Mysore", d.getValue("Karnataka")))
        assertTrue(Pin.fitsState("751002", "Odisha"))
        assertFalse(Pin.fitsState("500016", "Odisha"))
        assertTrue(Pin.fitsState("845401", "Other"))
        assertFalse(Pin.valid("012345"))
    }

    /** Many ways people say their name, in every app language: only the name is kept. */
    @Test fun spokenName() {
        val cases = listOf(
            // Hindi / Hinglish
            "mera naam hai aditya" to "Aditya", "Mera naam Aditya Panda hai" to "Aditya Panda", "main aditya hoon" to "Aditya",
            "mujhe aditya kehte hain" to "Aditya", "ji mera naam aditya hai ji" to "Aditya", "hamara naam ramesh hai" to "Ramesh",
            "sir mera naam suresh kumar hai" to "Suresh Kumar", "naam aditya" to "Aditya", "aditya" to "Aditya",
            "मेरा नाम आदित्य है" to "आदित्य", "मैं आदित्य हूँ" to "आदित्य", "मुझे सब आदित्य बुलाते हैं" to "आदित्य", "जी, मेरा नाम रमेश कुमार है।" to "रमेश कुमार",
            // English
            "My name is Ramesh." to "Ramesh", "I'm Aditya" to "Aditya", "I am Lakshmi Devi" to "Lakshmi Devi", "This is Aditya speaking" to "Aditya",
            "call me Raju" to "Raju", "my full name is aditya panda" to "Aditya Panda", "It's Aditya here" to "Aditya", "hello, my name's Priya" to "Priya",
            // Odia
            "ମୋ ନାମ ଆଦିତ୍ୟ" to "ଆଦିତ୍ୟ", "ମୋର ନାମ ହେଉଛି ଆଦିତ୍ୟ" to "ଆଦିତ୍ୟ", "ମୋର ନାଁ ଆଦିତ୍ୟ ପଣ୍ଡା ଅଟେ" to "ଆଦିତ୍ୟ ପଣ୍ଡା", "ମୁଁ ଆଦିତ୍ୟ" to "ଆଦିତ୍ୟ", "ମୋତେ ଆଦିତ୍ୟ ବୋଲି ଡାକନ୍ତି" to "ଆଦିତ୍ୟ", "ଆଜ୍ଞା, ମୋ ନାମ ଆଦିତ୍ୟ" to "ଆଦିତ୍ୟ",
            // Telugu
            "నా పేరు ఆదిత్య" to "ఆదిత్య", "నా పేరు ఆదిత్య అండి" to "ఆదిత్య", "నేను ఆదిత్య" to "ఆదిత్య", "నేను ఆదిత్యని" to "ఆదిత్య", "నన్ను ఆదిత్య అని పిలుస్తారు" to "ఆదిత్య", "ఆదిత్య నా పేరు" to "ఆదిత్య",
            // Kannada
            "ನನ್ನ ಹೆಸರು ಆದಿತ್ಯ" to "ಆದಿತ್ಯ", "ನನ್ನ ಹೆಸರು ಆದಿತ್ಯ ಅಂತ" to "ಆದಿತ್ಯ", "ನಾನು ಆದಿತ್ಯ" to "ಆದಿತ್ಯ", "ನನ್ನನ್ನು ಆದಿತ್ಯ ಎಂದು ಕರೆಯುತ್ತಾರೆ" to "ಆದಿತ್ಯ", "ನನ್ನ ಹೆಸರು ಆದಿತ್ಯ ರೀ" to "ಆದಿತ್ಯ",
            // Tamil
            "என் பெயர் ஆதித்யா" to "ஆதித்யா", "என் பேரு ஆதித்யா" to "ஆதித்யா", "நான் ஆதித்யா" to "ஆதித்யா", "என்னை ஆதித்யா என்று கூப்பிடுவாங்க" to "ஆதித்யா", "ஆதித்யா தான் என் பெயர்" to "ஆதித்யா", "என்னுடைய பெயர் ஆதித்யா ங்க" to "ஆதித்யா",
        )
        for ((said, name) in cases) assertEquals(said, name, SpokenDetails.name(said))
    }

    /** Ages said in words or digits, as two words (30 + 5), or as a year of birth. */
    @Test fun spokenAge() {
        val cases = listOf(
            "35" to 35, "meri umar 35 saal hai" to 35, "35 saal ka hoon" to 35, "meri umar chalis saal hai" to 40, "पैंतीस साल" to 35, "मेरी उम्र 28 साल है" to 28,
            "I am thirty five years old" to 35, "twenty eight" to 28, "I'm 42" to 42, "1990 mein paida hua" to 36, "born in 1985" to 41,
            "ମୋ ବୟସ ପଞ୍ଚତିରିଶି ବର୍ଷ" to 35, "ପଚିଶ ବର୍ଷ" to 25, "ମୋ ବୟସ 30" to 30,
            "నా వయసు ముప్పై ఐదు సంవత్సరాలు" to 35, "ఇరవై ఐదు" to 25, "నా వయసు 30" to 30,
            "ನನ್ನ ವಯಸ್ಸು ಮೂವತ್ತೈದು ವರ್ಷ" to 35, "ಇಪ್ಪತ್ತೈದು" to 25,
            "என் வயது முப்பத்தைந்து" to 35, "இருபத்தைந்து" to 25, "எனக்கு 40 வயசு" to 40,
        )
        for ((said, age) in cases) assertEquals(said, age, SpokenDetails.age(said, 2026))
    }

    @Test fun spokenAgeAndPin() {
        assertEquals(35, SpokenDetails.age("35"))
        assertEquals(40, SpokenDetails.age("meri umar chalis saal hai"))
        assertNull(SpokenDetails.age("200"))
        assertEquals("751002", SpokenDetails.pin("751002"))
        assertEquals("751002", SpokenDetails.pin("7 5 1 0 0 2"))
        assertEquals("751002", SpokenDetails.pin("seven five one zero zero two"))
        assertNull(SpokenDetails.pin("75100"))
    }
}
