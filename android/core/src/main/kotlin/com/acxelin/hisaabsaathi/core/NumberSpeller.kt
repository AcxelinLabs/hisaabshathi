package com.acxelin.hisaabsaathi.core

/**
 * Spells numbers out as words for the app's voices (Hindi, Odia, Telugu, Kannada, Tamil,
 * English). The voices read characters, not digits, so "600 रुपये" must become "छह सौ रुपये".
 * Mirrors tools/tts/numspell.py; keep the two in step.
 */
object NumberSpeller {
    private val units: Map<Lang, List<String>> = mapOf(
        Lang.HI to "शून्य एक दो तीन चार पाँच छह सात आठ नौ दस ग्यारह बारह तेरह चौदह पंद्रह सोलह सत्रह अठारह उन्नीस बीस इक्कीस बाईस तेईस चौबीस पच्चीस छब्बीस सत्ताईस अट्ठाईस उनतीस तीस इकतीस बत्तीस तैंतीस चौंतीस पैंतीस छत्तीस सैंतीस अड़तीस उनतालीस चालीस इकतालीस बयालीस तैंतालीस चौवालीस पैंतालीस छियालीस सैंतालीस अड़तालीस उनचास पचास इक्यावन बावन तिरपन चौवन पचपन छप्पन सत्तावन अट्ठावन उनसठ साठ इकसठ बासठ तिरसठ चौंसठ पैंसठ छियासठ सड़सठ अड़सठ उनहत्तर सत्तर इकहत्तर बहत्तर तिहत्तर चौहत्तर पचहत्तर छिहत्तर सतहत्तर अठहत्तर उनासी अस्सी इक्यासी बयासी तिरासी चौरासी पचासी छियासी सत्तासी अट्ठासी नवासी नब्बे इक्यानवे बानवे तिरानवे चौरानवे पचानवे छियानवे सत्तानवे अट्ठानवे निन्यानवे".split(" ").map { it.replace("_", " ") },
        Lang.OR to "ଶୂନ ଏକ ଦୁଇ ତିନି ଚାରି ପାଞ୍ଚ ଛଅ ସାତ ଆଠ ନଅ ଦଶ ଏଗାର ବାର ତେର ଚଉଦ ପନ୍ଦର ଷୋହଳ ସତର ଅଠର ଉଣେଇଶ କୋଡ଼ିଏ ଏକୋଇଶ ବାଇଶ ତେଇଶ ଚବିଶ ପଚିଶ ଛବିଶ ସତାଇଶ ଅଠାଇଶ ଅଣତିରିଶ ତିରିଶ ଏକତିରିଶ ବତିଶ ତେତିଶ ଚଉତିରିଶ ପଞ୍ଚତିରିଶ ଛତିଶ ସଇଁତିରିଶ ଅଠତିରିଶ ଅଣଚାଳିଶ ଚାଳିଶ ଏକଚାଳିଶ ବୟାଳିଶ ତେୟାଳିଶ ଚଉରାଳିଶ ପଞ୍ଚଚାଳିଶ ଛୟାଳିଶ ସତଚାଳିଶ ଅଠଚାଳିଶ ଅଣଚାଶ ପଚାଶ ଏକାବନ ବାଉନ ତେପନ ଚଉବନ ପଞ୍ଚାବନ ଛପନ ସତାବନ ଅଠାବନ ଅଣଷଠି ଷାଠିଏ ଏକଷଠି ବାଷଠି ତେଷଠି ଚଉଷଠି ପଞ୍ଚଷଠି ଛଅଷଠି ସତଷଠି ଅଠଷଠି ଅଣସ୍ତରି ସତୁରି ଏକସ୍ତରି ବାସ୍ତରି ତେସ୍ତରି ଚଉସ୍ତରି ପଞ୍ଚସ୍ତରି ଛଅସ୍ତରି ସତସ୍ତରି ଅଠସ୍ତରି ଅଣାଅଶୀ ଅଶୀ ଏକାଅଶୀ ବୟାଅଶୀ ତେୟାଅଶୀ ଚଉରାଅଶୀ ପଞ୍ଚାଅଶୀ ଛୟାଅଶୀ ସତାଅଶୀ ଅଠାଅଶୀ ଅଣାନବେ ନବେ ଏକାନବେ ବୟାନବେ ତେୟାନବେ ଚଉରାନବେ ପଞ୍ଚାନବେ ଛୟାନବେ ସତାନବେ ଅଠାନବେ ଅନେଶତ".split(" ").map { it.replace("_", " ") },
        Lang.TE to "సున్నా ఒకటి రెండు మూడు నాలుగు ఐదు ఆరు ఏడు ఎనిమిది తొమ్మిది పది పదకొండు పన్నెండు పదమూడు పద్నాలుగు పదిహేను పదహారు పదిహేడు పద్దెనిమిది పందొమ్మిది ఇరవై ఇరవై_ఒకటి ఇరవై_రెండు ఇరవై_మూడు ఇరవై_నాలుగు ఇరవై_ఐదు ఇరవై_ఆరు ఇరవై_ఏడు ఇరవై_ఎనిమిది ఇరవై_తొమ్మిది ముప్పై".split(" ").map { it.replace("_", " ") },
        Lang.KN to "ಸೊನ್ನೆ ಒಂದು ಎರಡು ಮೂರು ನಾಲ್ಕು ಐದು ಆರು ಏಳು ಎಂಟು ಒಂಬತ್ತು ಹತ್ತು ಹನ್ನೊಂದು ಹನ್ನೆರಡು ಹದಿಮೂರು ಹದಿನಾಲ್ಕು ಹದಿನೈದು ಹದಿನಾರು ಹದಿನೇಳು ಹದಿನೆಂಟು ಹತ್ತೊಂಬತ್ತು ಇಪ್ಪತ್ತು".split(" ").map { it.replace("_", " ") },
        Lang.TA to "பூஜ்ஜியம் ஒன்று இரண்டு மூன்று நான்கு ஐந்து ஆறு ஏழு எட்டு ஒன்பது பத்து பதினொன்று பன்னிரண்டு பதிமூன்று பதினான்கு பதினைந்து பதினாறு பதினேழு பதினெட்டு பத்தொன்பது இருபது".split(" ").map { it.replace("_", " ") },
    )
    private val tens: Map<Lang, List<String>> = mapOf(
        Lang.TE to "ఇరవై ముప్పై నలభై యాభై అరవై డెబ్బై ఎనభై తొంభై".split(" "),
        Lang.KN to "ಇಪ್ಪತ್ತು ಮೂವತ್ತು ನಲವತ್ತು ಐವತ್ತು ಅರವತ್ತು ಎಪ್ಪತ್ತು ಎಂಬತ್ತು ತೊಂಬತ್ತು".split(" "),
        Lang.TA to "இருபது முப்பது நாற்பது ஐம்பது அறுபது எழுபது எண்பது தொண்ணூறு".split(" "),
    )
    private val tensJoin: Map<Lang, List<String>> = mapOf(
        Lang.KN to "ಇಪ್ಪತ್ತ ಮೂವತ್ತ ನಲವತ್ತ ಐವತ್ತ ಅರವತ್ತ ಎಪ್ಪತ್ತ ಎಂಬತ್ತ ತೊಂಬತ್ತ".split(" "),
        Lang.TA to "இருபத்தி முப்பத்தி நாற்பத்தி ஐம்பத்தி அறுபத்தி எழுபத்தி எண்பத்தி தொண்ணூற்றி".split(" "),
    )
    /** Exact hundreds, and the form used when more follows ("నూట ఇరవై"). */
    private val hundreds: Map<Lang, Pair<List<String>, List<String>>> = mapOf(
        Lang.TE to ("వంద రెండు_వందలు మూడు_వందలు నాలుగు_వందలు ఐదు_వందలు ఆరు_వందలు ఏడు_వందలు ఎనిమిది_వందలు తొమ్మిది_వందలు".split(" ").map { it.replace("_", " ") } to "నూట రెండు_వందల మూడు_వందల నాలుగు_వందల ఐదు_వందల ఆరు_వందల ఏడు_వందల ఎనిమిది_వందల తొమ్మిది_వందల".split(" ").map { it.replace("_", " ") }),
        Lang.KN to ("ನೂರು ಇನ್ನೂರು ಮುನ್ನೂರು ನಾನ್ನೂರು ಐನೂರು ಆರುನೂರು ಏಳುನೂರು ಎಂಟುನೂರು ಒಂಬೈನೂರು".split(" ").map { it.replace("_", " ") } to "ನೂರ ಇನ್ನೂರ ಮುನ್ನೂರ ನಾನ್ನೂರ ಐನೂರ ಆರುನೂರ ಏಳುನೂರ ಎಂಟುನೂರ ಒಂಬೈನೂರ".split(" ").map { it.replace("_", " ") }),
        Lang.TA to ("நூறு இருநூறு முந்நூறு நானூறு ஐநூறு அறுநூறு எழுநூறு எண்ணூறு தொள்ளாயிரம்".split(" ").map { it.replace("_", " ") } to "நூற்று இருநூற்று முந்நூற்று நானூற்று ஐநூற்று அறுநூற்று எழுநூற்று எண்ணூற்று தொள்ளாயிரத்து".split(" ").map { it.replace("_", " ") }),
    )
    private val en = "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen".split(" ")
    private val enTens = "twenty thirty forty fifty sixty seventy eighty ninety".split(" ")
    /** hundred, thousand, lakh for languages that simply put the word after the count. */
    private val plain: Map<Lang, Triple<String, String, String>> = mapOf(
        Lang.HI to Triple("सौ", "हज़ार", "लाख"), Lang.OR to Triple("ଶହ", "ହଜାର", "ଲକ୍ଷ"), Lang.EN to Triple("hundred", "thousand", "lakh"),
    )
    /** thousand: exactly one thousand / several thousand / one thousand with more following / several with more following. */
    private val thousand: Map<Lang, List<String>> = mapOf(
        Lang.TE to listOf("వెయ్యి", "వేలు", "వెయ్యి", "వేల"),
        Lang.KN to listOf("ಸಾವಿರ", "ಸಾವಿರ", "ಸಾವಿರದ", "ಸಾವಿರದ"),
        Lang.TA to listOf("ஆயிரம்", "ஆயிரம்", "ஆயிரத்து", "ஆயிரத்து"),
    )
    private val lakh = mapOf(Lang.TE to "లక్షలు", Lang.KN to "ಲಕ್ಷ", Lang.TA to "லட்சம்")
    private val point = mapOf(Lang.HI to "दशमलव", Lang.OR to "ଦଶମିକ", Lang.TE to "పాయింట్", Lang.KN to "ಪಾಯಿಂಟ್", Lang.TA to "புள்ளி", Lang.EN to "point")

    private fun below100(n: Int, lang: Lang): String {
        if (lang == Lang.EN) return if (n < 20) en[n] else enTens[n / 10 - 2] + if (n % 10 == 0) "" else " " + en[n % 10]
        val u = units.getValue(lang)
        if (lang == Lang.HI || lang == Lang.OR || n < u.size) return u[n]
        val t = n / 10; val r = n % 10
        if (r == 0) return tens.getValue(lang)[t - 2]
        return "${(tensJoin[lang] ?: tens.getValue(lang))[t - 2]} ${u[r]}"
    }

    /** [n] as words; [more] means another word follows, which some languages mark ("ఆరు వందల రూపాయలు"). */
    fun spell(n: Long, lang: Lang, more: Boolean = false): String {
        if (n < 0) return spell(-n, lang, more)
        // Only Telugu marks a number that comes before a noun ("ఆరు వందల రూపాయలు").
        @Suppress("NAME_SHADOWING") val more = more && lang == Lang.TE
        if (n < 100) return below100(n.toInt(), lang)
        val parts = ArrayList<String>()
        val lakhs = n / 100_000; var rest = n % 100_000
        if (lakhs > 0) parts += "${spell(lakhs, lang)} ${plain[lang]?.third ?: lakh.getValue(lang)}"
        val th = rest / 1000; rest %= 1000
        val tail = rest > 0 || more
        if (th > 0) {
            val p = plain[lang]
            parts += if (p != null) "${spell(th, lang)} ${p.second}" else {
                val f = thousand.getValue(lang)
                if (th == 1L) (if (tail) f[2] else f[0]) else "${spell(th, lang)} ${if (tail) f[3] else f[1]}"
            }
        }
        val h = (rest / 100).toInt(); rest %= 100
        if (h > 0) {
            val p = plain[lang]
            parts += if (p != null) "${below100(h, lang)} ${p.first}" else {
                val (exact, joined) = hundreds.getValue(lang)
                (if (rest > 0 || more) joined else exact)[h - 1]
            }
        }
        if (rest > 0) parts += below100(rest.toInt(), lang)
        return parts.joinToString(" ")
    }

    private val number = Regex("\\d[\\d,]*(?:\\.\\d+)?")

    /** Every number in [text] spelled out. */
    fun spellNumbers(text: String, lang: Lang): String = number.replace(text) { m ->
        val s = m.value.replace(",", "")
        val more = text.substring(m.range.last + 1).trimStart().firstOrNull()?.isLetter() == true
        val whole = s.substringBefore('.').toLong()
        val frac = s.substringAfter('.', "").trimEnd('0')
        if (frac.isEmpty()) spell(whole, lang, more)
        else spell(whole, lang) + " " + point.getValue(lang) + " " + frac.map { spell((it - '0').toLong(), lang) }.joinToString(" ")
    }
}
