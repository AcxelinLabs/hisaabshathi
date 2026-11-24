package com.acxelin.hisaabsaathi.core

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

/**
 * What the worker tells the app about themselves during setup. Kept only on the phone,
 * encrypted (app/data/ProfileStore.kt). Every field may be empty: nothing here is required
 * to keep a ledger.
 */
data class Profile(
    val name: String = "",
    /** Whole years; null when not given. */
    val age: Int? = null,
    /** The state or union territory, as in [PinDirectory.states], or "" when not given. */
    val state: String = "",
    val district: String = "",
    /** Six digits, or "". */
    val pin: String = "",
    /** The village or town, as the worker said or typed it, or "". */
    val village: String = "",
) {
    val isEmpty: Boolean get() = name.isBlank() && age == null && district.isBlank() && pin.isBlank() && village.isBlank()

    /** One line for the top of a PDF report: "Ramesh Behera · Balianta, Khordha, Odisha · 751002". */
    fun line(): String = listOf(name.trim(), listOf(village.trim(), district.trim(), state.trim()).filter { it.isNotEmpty() }.joinToString(", "), pin)
        .filter { it.isNotBlank() }.joinToString(" · ")
}

object StateInfo {
    /** The app's own work states (as in assets/wage_pack_in.json), plus "Other". */
    val appRegions = listOf(
        "Odisha", "Telangana", "Karnataka", "Andhra Pradesh", "Tamil Nadu", "Kerala",
        "Maharashtra", "Gujarat", "Delhi", "West Bengal",
    )

    /** Old and official spellings to the names used here ("Orissa" → "Odisha", "NCT of Delhi" → "Delhi"). */
    fun normalize(state: String): String {
        val s = state.trim().replace(Regex("\\s+"), " ").let { if (it.lowercase().startsWith("the ")) it.drop(4) else it }
        val k = s.lowercase()
        return when {
            k.isEmpty() -> ""
            k == "orissa" || k == "odisha" -> "Odisha"
            "delhi" in k -> "Delhi"
            k == "tamilnadu" || k == "tamil nadu" -> "Tamil Nadu"
            k == "pondicherry" || k == "puducherry" -> "Puducherry"
            k == "uttaranchal" -> "Uttarakhand"
            k == "chattisgarh" -> "Chhattisgarh"
            k == "west bengal" || k == "westbengal" -> "West Bengal"
            else -> s.split(' ').joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
                .replace(" And ", " and ").replace(" Of ", " of ")
        }
    }

    /** The work state the app keeps for [state]: one of [appRegions], or "Other". */
    fun regionFor(state: String): String {
        val n = normalize(state)
        return appRegions.firstOrNull { it.equals(n, ignoreCase = true) } ?: if (n.isEmpty()) "" else "Other"
    }
}

/** Indian postal codes (PIN). */
object Pin {
    /** Six digits, starting 1–8 (0 is not used; 9 is the Army Postal Service, not a place). */
    fun valid(pin: String): Boolean = pin.length == 6 && pin.all { it in '0'..'9' } && pin[0] in '1'..'8'
}

/** Matching a district name as written elsewhere (an Aadhaar card) to the app's list. */
object Districts {
    /** The list entry that matches [name] (as printed on an Aadhaar card), if any. */
    fun match(name: String, list: List<String>): String? {
        val k = key(name)
        if (k.isEmpty()) return null
        return list.firstOrNull { key(it) == k }
            ?: list.firstOrNull { d -> key(d).let { it.isNotEmpty() && (k.startsWith(it) || it.startsWith(k)) } }
            ?: list.firstOrNull { d -> d.substringAfter('(', "").substringBefore(')').let { key(it).isNotEmpty() && key(it) == k } }
    }

    private fun key(s: String) = s.lowercase().substringBefore('(').filter { it.isLetter() }
}

/** Reading a name, an age or a PIN from what the worker typed or said. */
object SpokenDetails {
    /**
     * Words people say around their name, in every app language and in Latin-letter Hindi:
     * "mera naam … hai", "main … hoon", "mujhe … kehte hain", "my name is …", "call me …",
     * "ମୋ ନାମ …", "ମୋତେ … ବୋଲି ଡାକନ୍ତି", "నా పేరు …", "నన్ను … అని పిలుస్తారు", "ನನ್ನ ಹೆಸರು …",
     * "ನನ್ನನ್ನು … ಎಂದು ಕರೆಯುತ್ತಾರೆ", "என் பெயர் …", "என்னை … என்று கூப்பிடுவாங்க", and polite words
     * (ji, sir, ଆଜ୍ଞା, అండి, ರೀ, ங்க). They are taken out wherever they are, so the order does not matter.
     * Tested with many phrasings per language in OnboardingTest.
     */
    private val nameFillers: Set<String> = setOf(
        // Hindi
        "मेरा", "मेरी", "मेरे", "हमारा", "हमरा", "नाम", "है", "हैं", "हूँ", "हूं", "हुँ", "मैं", "मै", "जी", "का", "की", "के",
        "मुझे", "मुझको", "हमें", "कहते", "बोलते", "बुलाते", "पुकारते", "लोग", "सब", "तो", "हाँ", "हां", "सर", "साहब", "मैडम", "भैया", "यहाँ", "बोल", "रहा", "रही", "हूँ।",
        // Hindi in Latin letters
        "mera", "meraa", "meri", "mere", "hamara", "humara", "hamra", "naam", "nam", "naa", "hai", "hain", "he", "hoon", "hun", "hu",
        "main", "mai", "mein", "ji", "jee", "mujhe", "mujhko", "hume", "kehte", "kahte", "bolte", "bulate", "log", "sab", "to",
        "ha", "haan", "han", "sir", "saab", "sahab", "madam", "bhaiya", "yahan", "bol", "raha", "rahi", "ka", "ki",
        // English
        "my", "name", "name's", "names", "is", "i", "am", "i'm", "im", "this", "it's", "its", "it", "call", "called", "me",
        "myself", "the", "here", "speaking", "people", "they", "everyone", "hello", "hi", "hey", "yes", "ok", "okay", "full",
        // Odia
        "ମୋ", "ମୋର", "ମୋ'ର", "ନାମ", "ନାଁ", "ନାଆଁ", "ଅଟେ", "ମୁଁ", "ମୋତେ", "ହେଉଛି", "ହେଲା", "ବୋଲି", "ଡାକନ୍ତି", "କୁହନ୍ତି", "ସମସ୍ତେ",
        "ଲୋକେ", "ଆଜ୍ଞା", "ଜୀ", "ସାର୍", "ହଁ", "ଏଠି", "କହୁଛି",
        // Telugu
        "నా", "నాది", "పేరు", "నేను", "నన్ను", "అండి", "అండీ", "అని", "అంటారు", "పిలుస్తారు", "పిలుస్తారండి", "గారు", "సార్", "అవును",
        "అందరూ", "ఇక్కడ", "మాట్లాడుతున్నాను", "ది",
        // Kannada
        "ನನ್ನ", "ನನ್ನದು", "ಹೆಸರು", "ನಾನು", "ನನ್ನನ್ನು", "ಅಂತ", "ಎಂದು", "ಆಗಿದೆ", "ಇದೆ", "ರೀ", "ಸಾರ್", "ಹೌದು", "ಕರೆಯುತ್ತಾರೆ",
        "ಕರೀತಾರೆ", "ಅನ್ನುತ್ತಾರೆ", "ಎಲ್ಲರೂ", "ಇಲ್ಲಿ", "ಮಾತಾಡ್ತಿದ್ದೀನಿ",
        // Tamil
        "என்", "என்னுடைய", "எனது", "பெயர்", "பேர்", "பேரு", "நான்", "என்னை", "ஆகும்", "தான்", "ங்க", "என்று", "ன்னு", "சார்",
        "ஆமாம்", "கூப்பிடுவாங்க", "கூப்பிடுவார்கள்", "சொல்லுவாங்க", "எல்லாரும்", "இங்கே", "பேசுறேன்",
    )

    /** "mera naam Aditya hai", "ମୋତେ ଆଦିତ୍ୟ ବୋଲି ଡାକନ୍ତି", "my name is Aditya" → "Aditya". If nothing is left, what was said. */
    fun name(text: String): String {
        // Same letters however the keyboard or the speech engine encoded them; no invisible joiners.
        val clean = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC).replace(Regex("[\u200B-\u200D\uFEFF]"), "")
        val words = clean.trim().replace('’', '\'').split(Regex("[\\s,.!?।|:;\"“”]+")).map { it.trim('\'') }.filter { it.isNotBlank() }
        var kept = words.filter { it.lowercase() !in nameFillers }
        // Telugu "నేను ఆదిత్యని" ("I am Aditya"): the "-ని" ending is not part of the name.
        if ("నేను" in words) kept = kept.map { if (it.length > 3 && it.endsWith("ని")) it.dropLast(2) else it }
        val name = (kept.ifEmpty { words }).take(4).joinToString(" ")
        // Names said in Latin letters get capitals: "aditya panda" → "Aditya Panda".
        return name.split(' ').joinToString(" ") { w ->
            if (w.firstOrNull()?.let { it in 'a'..'z' } == true) w.replaceFirstChar { it.uppercase() } else w
        }
    }

    /**
     * "मेरा गाँव बलियंता है", "I live in Balianta town" → "Balianta". The village or town, without
     * the words around it. If nothing is left, what was said.
     */
    fun place(text: String): String {
        val clean = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC).replace(Regex("[\u200B-\u200D\uFEFF]"), "")
        val words = clean.trim().split(Regex("[\\s,.!?।|:;\"“”]+")).filter { it.isNotBlank() }
        val kept = words.filter { it.lowercase() !in placeFillers }
        return (kept.ifEmpty { words }).take(5).joinToString(" ") { w ->
            if (w.firstOrNull()?.let { it in 'a'..'z' } == true) w.replaceFirstChar { it.uppercase() } else w
        }.take(60)
    }

    private val placeFillers = setOf(
        // Hindi
        "मेरा", "मेरे", "मेरी", "गाँव", "गांव", "गाव", "शहर", "कस्बा", "का", "के", "की", "नाम", "है", "हैं", "में", "मैं", "रहता", "रहती", "रहते", "हूँ", "हूं", "जी",
        // Odia
        "ମୋର", "ମୋ", "ଗାଁ", "ଗାଁର", "ସହର", "ସହରର", "ନାମ", "ଅଟେ", "ମୁଁ", "ରେ", "ରହେ", "ରହୁଛି",
        // Telugu
        "మా", "నా", "ఊరు", "ఊరి", "గ్రామం", "పట్టణం", "పేరు", "నేను", "లో", "ఉంటాను",
        // Kannada
        "ನನ್ನ", "ನಮ್ಮ", "ಊರು", "ಊರಿನ", "ಹಳ್ಳಿ", "ಗ್ರಾಮ", "ಪಟ್ಟಣ", "ಹೆಸರು", "ನಾನು", "ಇರುತ್ತೇನೆ",
        // Tamil
        "என்", "எங்கள்", "ஊர்", "ஊரின்", "கிராமம்", "நகரம்", "பெயர்", "நான்", "இருக்கிறேன்",
        // English
        "my", "village", "town", "city", "is", "name", "i", "live", "in", "the", "of", "it's", "its", "called",
    )

    private val zero = setOf("zero", "shunya", "sunya", "shoonya", "शून्य", "जीरो", "ଶୂନ", "ଶୂନ୍ୟ", "సున్నా", "ಸೊನ್ನೆ", "பூஜ்ஜியம்")

    /**
     * The age from "35", "meri umar 35 saal hai", "पैंतीस साल", "ముప్పై ఐదు సంవత్సరాలు", "I am thirty five",
     * or a year of birth ("1990 mein paida hua" → [year] − 1990). Null if there is none between 1 and 120.
     */
    fun age(text: String, year: Int = LocalDate.now().year): Int? {
        Regex("(?<!\\d)(19\\d\\d|20\\d\\d)(?!\\d)").find(text)?.let { m ->
            return (year - m.value.toInt()).takeIf { it in 1..120 }
        }
        Regex("(?<!\\d)\\d{1,3}(?!\\d)").find(text)?.let { return it.value.toInt().takeIf { a -> a in 1..120 } }
        val nums = NumberWords.numbersIn(text).filter { it % 1.0 == 0.0 }.map { it.toInt() }
        // Said as two words ("ముప్పై ఐదు" = 30 + 5): join a round ten and the unit after it.
        for (i in 0 until nums.size - 1) {
            if (nums[i] in 20..90 && nums[i] % 10 == 0 && nums[i + 1] in 1..9) return nums[i] + nums[i + 1]
        }
        return nums.firstOrNull { it in 1..120 }
    }

    /** Six digits from typed digits, a spoken number, or digits said one by one ("7 5 1 0 0 2"). */
    fun pin(text: String): String? {
        val typed = text.filter { it in '0'..'9' }
        if (typed.length == 6) return typed
        val words = text.split(Regex("\\s+")).joinToString(" ") { if (it.lowercase() in zero) "0" else it }
        val nums = NumberWords.numbersIn(words)
        nums.firstOrNull { it >= 100000 && it < 1000000 && it % 1.0 == 0.0 }?.let { return it.toLong().toString() }
        val joined = nums.filter { it >= 0 && it % 1.0 == 0.0 }.joinToString("") { it.toLong().toString() }
        return joined.takeIf { it.length == 6 }
    }
}

/**
 * Reads the QR code printed on an Aadhaar card, e-Aadhaar or Aadhaar PVC card, on the phone.
 * Returns only what the app keeps: name, age, state, district and PIN. The Aadhaar digits,
 * photo, gender, address lines, mobile and e-mail in the code are never returned or stored.
 *
 * Two formats exist: the Secure QR (since 2018; a long decimal number holding compressed,
 * signed data whose text fields are separated by byte 255) and the older XML QR
 * (`<PrintLetterBarcodeData name="…" yob="…" dist="…" state="…" pc="…"/>`).
 * The signature is not checked: this only saves the worker typing, it is not identity proof.
 */
object AadhaarQr {
    data class Details(val name: String, val age: Int?, val state: String, val district: String, val pin: String)

    fun read(raw: String, today: LocalDate = LocalDate.now()): Details? {
        val text = raw.trim()
        return when {
            text.isEmpty() -> null
            text.all { it in '0'..'9' } && text.length > 100 -> secure(text, today)
            "PrintLetterBarcodeData" in text || "<" in text -> xml(text, today)
            else -> null
        }
    }

    private fun xml(text: String, today: LocalDate): Details? {
        fun attr(n: String) = Regex("""\b$n\s*=\s*["']([^"']*)["']""").find(text)?.groupValues?.get(1)?.trim().orEmpty()
        val name = attr("name")
        if (name.isEmpty()) return null
        val age = attr("dob").takeIf { it.isNotEmpty() }?.let { ageFromDate(it, today) }
            ?: attr("yob").toIntOrNull()?.let { today.year - it }?.takeIf { it in 1..120 }
        return Details(name, age, StateInfo.normalize(attr("state")), attr("dist"), attr("pc").filter { it.isDigit() }.takeIf { it.length == 6 }.orEmpty())
    }

    private fun secure(digits: String, today: LocalDate): Details? {
        val bytes = BigInteger(digits).toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        val data = decompress(bytes) ?: return null
        // Text fields come first, separated by 255; the photo and signature follow.
        val fields = ArrayList<String>()
        var start = 0
        for (i in data.indices) {
            if (data[i] == 0xFF.toByte()) {
                fields += String(data, start, i - start, Charsets.ISO_8859_1).trim()
                start = i + 1
                if (fields.size >= 20) break
            }
        }
        // UIDAI's layout: [version "V2"…], e-mail/mobile flag, reference id, name, date of birth, gender,
        // care of, district, landmark, house, location, PIN, post office, state, street, sub-district, village.
        val o = if (fields.firstOrNull()?.matches(Regex("V\\d+")) == true) 1 else 0
        val gender = setOf("M", "F", "T", "MALE", "FEMALE", "TRANSGENDER")
        if (fields.size > o + 12 && fields[o + 4].uppercase() in gender && fields[o + 2].isNotBlank() && fields[o + 2].none { it.isDigit() }) {
            val dob = fields[o + 3]
            val pin = fields[o + 10].filter { it.isDigit() }.takeIf { it.length == 6 }
                ?: fields.drop(o + 5).firstOrNull { it.length == 6 && it.all(Char::isDigit) }.orEmpty()
            return Details(
                fields[o + 2], ageFromDate(dob, today) ?: dob.toIntOrNull()?.let { today.year - it }?.takeIf { it in 1..120 },
                StateInfo.normalize(fields[o + 12]), fields[o + 6], pin,
            )
        }
        // Otherwise find the date of birth (or the gender after it); the name is just before it.
        val dateRx = Regex("""^\d{2}[-/]\d{2}[-/]\d{4}$""")
        val dobAt = fields.indexOfFirst { dateRx.matches(it) }.takeIf { it >= 1 }
            ?: fields.indexOfFirst { it.uppercase() in gender }.takeIf { it >= 2 }?.minus(1)
            ?: return null
        val name = fields[dobAt - 1]
        if (name.isBlank() || name.any { it.isDigit() }) return null
        val pinAt = (dobAt + 1 until fields.size).firstOrNull { fields[it].length == 6 && fields[it].all(Char::isDigit) }
        // After the date: gender, care of, district, landmark, house, location, PIN, post office, state…
        val district = fields.getOrNull(dobAt + 3).orEmpty()
        val state = pinAt?.let { fields.getOrNull(it + 2) }.orEmpty()
        return Details(name, ageFromDate(fields[dobAt], today), StateInfo.normalize(state), district, pinAt?.let { fields[it] }.orEmpty())
    }

    private fun decompress(bytes: ByteArray): ByteArray? =
        runCatching { GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } }.getOrNull()
            ?: runCatching {
                // Some readers report the stream without its gzip header: try raw zlib/deflate too.
                for (nowrap in listOf(false, true)) {
                    val inf = Inflater(nowrap)
                    inf.setInput(bytes)
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    try {
                        while (!inf.finished()) { val n = inf.inflate(buf); if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break; out.write(buf, 0, n) }
                    } finally { inf.end() }
                    if (out.size() > 0) return@runCatching out.toByteArray()
                }
                null
            }.getOrNull()

    fun ageFromDate(date: String, today: LocalDate = LocalDate.now()): Int? {
        for (f in listOf("dd-MM-yyyy", "dd/MM/yyyy", "yyyy-MM-dd")) {
            val d = runCatching { LocalDate.parse(date.trim(), DateTimeFormatter.ofPattern(f)) }.getOrNull() ?: continue
            return Period.between(d, today).years.takeIf { it in 0..120 }
        }
        return null
    }
}
