package com.acxelin.hisaabsaathi.core

import com.acxelin.hisaabsaathi.core.i18n.Kn
import com.acxelin.hisaabsaathi.core.i18n.Ta
import com.acxelin.hisaabsaathi.core.i18n.Te

/**
 * Reads spoken numbers ("एक सौ बीस", "ଦୁଇ ଶହ", "do hazaar", "twenty five", "120") out of a
 * token list. Speech models write numbers as words, so this is what turns a transcript
 * into amounts.
 *
 * The Odia table is best-effort and needs review by a native speaker.
 */
object NumberWords {

    sealed interface Tok {
        val start: Int
        val end: Int
    }

    /** A number covering tokens [start, end). */
    data class Num(val value: Double, override val start: Int, override val end: Int) : Tok

    data class Word(val text: String, override val start: Int) : Tok {
        override val end: Int get() = start + 1
    }

    private fun table(vararg rows: String): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (row in rows) {
            for (item in row.split(';')) {
                val parts = item.trim().split('=')
                if (parts.size != 2) continue
                val value = parts[0].trim().toDouble()
                for (word in parts[1].split('|')) out[Text.normalize(word.trim())] = value
            }
        }
        return out
    }

    private val hindi = table(
        "0=शून्य|जीरो; 1=एक; 2=दो; 3=तीन; 4=चार; 5=पांच|पाँच|पाच; 6=छह|छः|छे|छै|छ; 7=सात; 8=आठ; 9=नौ; 10=दस",
        "11=ग्यारह; 12=बारह; 13=तेरह; 14=चौदह; 15=पंद्रह|पन्द्रह|पंदरह; 16=सोलह; 17=सत्रह; 18=अठारह; 19=उन्नीस; 20=बीस",
        "21=इक्कीस; 22=बाईस|बाइस; 23=तेईस|तेइस; 24=चौबीस; 25=पच्चीस; 26=छब्बीस; 27=सत्ताईस|सत्ताइस; 28=अट्ठाईस|अट्ठाइस|अठाईस|अठाइस; 29=उनतीस|उन्तीस; 30=तीस",
        "31=इकतीस|इकत्तीस; 32=बत्तीस; 33=तैंतीस|तेंतीस|तैतीस; 34=चौंतीस|चौतीस; 35=पैंतीस|पैतीस; 36=छत्तीस; 37=सैंतीस|सैतीस; 38=अड़तीस; 39=उनतालीस|उनचालीस; 40=चालीस",
        "41=इकतालीस; 42=बयालीस|बयालिस; 43=तैंतालीस|तिरतालीस|तैतालीस; 44=चौवालीस|चवालीस; 45=पैंतालीस|पैतालीस; 46=छियालीस; 47=सैंतालीस|सैतालीस; 48=अड़तालीस; 49=उनचास; 50=पचास",
        "51=इक्यावन; 52=बावन; 53=तिरपन|तिरेपन; 54=चौवन; 55=पचपन; 56=छप्पन; 57=सत्तावन; 58=अट्ठावन|अठावन; 59=उनसठ; 60=साठ",
        "61=इकसठ; 62=बासठ; 63=तिरसठ|तिरेसठ; 64=चौंसठ|चौसठ; 65=पैंसठ|पैसठ; 66=छियासठ|छाछठ; 67=सड़सठ; 68=अड़सठ; 69=उनहत्तर; 70=सत्तर",
        "71=इकहत्तर; 72=बहत्तर; 73=तिहत्तर; 74=चौहत्तर; 75=पचहत्तर; 76=छिहत्तर; 77=सतहत्तर; 78=अठहत्तर; 79=उनासी|उन्यासी; 80=अस्सी",
        "81=इक्यासी; 82=बयासी; 83=तिरासी; 84=चौरासी; 85=पचासी; 86=छियासी; 87=सत्तासी; 88=अट्ठासी|अठासी; 89=नवासी; 90=नब्बे",
        "91=इक्यानवे|इक्यानबे; 92=बानवे|बानबे; 93=तिरानवे|तिरानबे; 94=चौरानवे|चौरानबे; 95=पचानवे|पंचानवे|पचानबे; 96=छियानवे|छियानबे; 97=सत्तानवे|सत्तानबे; 98=अट्ठानवे|अठानवे|अट्ठानबे; 99=निन्यानवे|निन्यानबे",
        "1.5=डेढ़; 2.5=ढाई|ढाइ",
    )

    private val odia = table(
        "0=ଶୂନ|ଶୂନ୍ୟ; 1=ଏକ|ଗୋଟିଏ|ଗୋଟେ|ଗୁଟେ|ଗୋଟାଏ|ଗୁଟିଏ; 2=ଦୁଇ; 3=ତିନି; 4=ଚାରି; 5=ପାଞ୍ଚ; 6=ଛଅ; 7=ସାତ; 8=ଆଠ; 9=ନଅ; 10=ଦଶ",
        "11=ଏଗାର; 12=ବାର; 13=ତେର; 14=ଚଉଦ; 15=ପନ୍ଦର; 16=ଷୋହଳ|ଷୋଳ; 17=ସତର; 18=ଅଠର; 19=ଉଣେଇଶ; 20=କୋଡ଼ିଏ|କୋଡ଼ି",
        "21=ଏକୋଇଶ; 22=ବାଇଶ; 23=ତେଇଶ; 24=ଚବିଶ; 25=ପଚିଶ; 26=ଛବିଶ; 27=ସତାଇଶ; 28=ଅଠାଇଶ; 29=ଅଣତିରିଶ; 30=ତିରିଶ",
        "31=ଏକତିରିଶ; 32=ବତିଶ; 33=ତେତିଶ; 34=ଚଉତିରିଶ; 35=ପଞ୍ଚତିରିଶ; 36=ଛତିଶ; 37=ସଇଁତିରିଶ; 38=ଅଠତିରିଶ; 39=ଅଣଚାଳିଶ; 40=ଚାଳିଶ",
        "41=ଏକଚାଳିଶ; 42=ବୟାଳିଶ; 43=ତେୟାଳିଶ; 44=ଚଉରାଳିଶ; 45=ପଞ୍ଚଚାଳିଶ; 46=ଛୟାଳିଶ; 47=ସତଚାଳିଶ; 48=ଅଠଚାଳିଶ; 49=ଅଣଚାଶ; 50=ପଚାଶ",
        "51=ଏକାବନ; 52=ବାଉନ; 53=ତେପନ; 54=ଚଉବନ; 55=ପଞ୍ଚାବନ; 56=ଛପନ; 57=ସତାବନ; 58=ଅଠାବନ; 59=ଅଣଷଠି; 60=ଷାଠିଏ|ଷାଠି",
        "61=ଏକଷଠି; 62=ବାଷଠି; 63=ତେଷଠି; 64=ଚଉଷଠି; 65=ପଞ୍ଚଷଠି; 66=ଛଅଷଠି; 67=ସତଷଠି; 68=ଅଠଷଠି; 69=ଅଣସ୍ତରି; 70=ସତୁରି",
        "71=ଏକସ୍ତରି; 72=ବାସ୍ତରି; 73=ତେସ୍ତରି; 74=ଚଉସ୍ତରି; 75=ପଞ୍ଚସ୍ତରି; 76=ଛଅସ୍ତରି; 77=ସତସ୍ତରି; 78=ଅଠସ୍ତରି; 79=ଅଣାଅଶୀ; 80=ଅଶୀ",
        "81=ଏକାଅଶୀ; 82=ବୟାଅଶୀ; 83=ତେୟାଅଶୀ; 84=ଚଉରାଅଶୀ; 85=ପଞ୍ଚାଅଶୀ; 86=ଛୟାଅଶୀ; 87=ସତାଅଶୀ; 88=ଅଠାଅଶୀ; 89=ଅଣାନବେ; 90=ନବେ",
        "91=ଏକାନବେ; 92=ବୟାନବେ; 93=ତେୟାନବେ; 94=ଚଉରାନବେ; 95=ପଞ୍ଚାନବେ; 96=ଛୟାନବେ; 97=ସତାନବେ; 98=ଅଠାନବେ; 99=ଅନେଶତ",
        "1.5=ଦେଢ଼; 2.5=ଅଢ଼େଇ",
    )

    private val english = table(
        "0=zero; 1=one; 2=two; 3=three; 4=four; 5=five; 6=six; 7=seven; 8=eight; 9=nine; 10=ten",
        "11=eleven; 12=twelve; 13=thirteen; 14=fourteen; 15=fifteen; 16=sixteen; 17=seventeen; 18=eighteen; 19=nineteen",
        "20=twenty; 30=thirty; 40=forty; 50=fifty; 60=sixty; 70=seventy; 80=eighty; 90=ninety",
    )

    /** Hindi typed in Latin letters. "do" is handled separately because it is an English word. */
    private val roman = table(
        "1=ek; 3=teen|tin; 4=char|chaar; 5=paanch|panch; 6=chhah|chhe|chheh; 7=saat; 8=aath; 9=nau; 10=das",
        "11=gyarah; 12=barah; 13=terah; 14=chaudah; 15=pandrah; 16=solah; 17=satrah; 18=atharah; 19=unnis; 20=bees",
        "25=pachees|pachis; 30=tees; 40=chalis|chalees; 50=pachas|pachaas; 60=saath; 70=sattar; 80=assi; 90=nabbe",
        "1.5=dedh; 2.5=dhai",
    )

    private val extraLangs = listOf(Te.numbers to Te.multipliers, Kn.numbers to Kn.multipliers, Ta.numbers to Ta.multipliers)

    /**
     * English numbers as each Indian-language speech model writes them ("ఫైవ్ హండ్రెడ్").
     * Workers often say amounts in English. Spellings are those the app's models produced
     * for Indian-accented English speech, plus close variants.
     */
    private val englishSaid = table(
        // Hindi
        "0=जीरो|ज़ीरो; 1=वन; 3=थ्री; 4=फोर|फ़ोर; 5=फाइव|फ़ाइव|फाईव; 6=सिक्स; 7=सेवन|सेवेन; 9=नाइन|नाईन; 10=टेन",
        "11=इलेवन|इलेवेन; 12=ट्वेल्व; 13=थर्टीन; 14=फोर्टीन; 15=फिफ्टीन|फ़िफ़्टीन; 16=सिक्सटीन; 17=सेवनटीन|सेवेनटीन; 18=एटीन|ऐटीन; 19=नाइनटीन",
        "20=ट्वेंटी|ट्वेन्टी; 30=थर्टी; 40=फोर्टी|फ़ोर्टी|फॉर्टी; 50=फिफ्टी|फ़िफ़्टी; 60=सिक्सटी; 70=सेवेंटी|सेवंटी|सेवनटी|सेवेन्टी; 80=एटी|ऐटी; 90=नाइंटी|नाइनटी",
        // Odia
        "0=ଜିରୋ; 1=ୱାନ|ୱନ; 3=ଥ୍ରୀ; 4=ଫୋର; 5=ଫାଇଭ; 6=ସିକ୍ସ; 7=ସେଭେନ|ସେଭନ; 9=ନାଇନ; 10=ଟେନ",
        "11=ଇଲେଭେନ; 12=ଟ୍ୱେଲଭ; 13=ଥର୍ଟିନ; 14=ଫୋର୍ଟିନ; 15=ଫିଫ୍ଟିନ; 16=ସିକ୍ସଟିନ; 17=ସେଭେନଟିନ; 18=ଏଟିନ|ଏଇଟିନ; 19=ନାଇନଟିନ",
        "20=ଟ୍ୱେଣ୍ଟି|ଟୁଏଣ୍ଟି; 30=ଥର୍ଟି; 40=ଫୋର୍ଟି|ଫର୍ଟି; 50=ଫିଫ୍ଟି; 60=ସିକ୍ସଟି; 70=ସେଭେଣ୍ଟି|ସେଭେନ୍ଟି; 90=ନାଇଣ୍ଟି|ନାଇନଟି",
        // Telugu
        "0=జీరో; 1=వన్; 3=త్రీ|థ్రీ; 4=ఫోర్; 5=ఫైవ్; 6=సిక్స్; 7=సెవెన్|సెవన్; 9=నైన్; 10=టెన్",
        "11=ఎలెవెన్|ఇలెవెన్; 12=ట్వెల్వ్; 13=థర్టీన్; 14=ఫోర్టీన్; 15=ఫిఫ్టీన్; 16=సిక్స్టీన్; 17=సెవెంటీన్|సెవెన్టీన్; 18=ఎయిటీన్|ఏటీన్; 19=నైంటీన్|నైన్టీన్",
        "20=ట్వెంటీ|ట్వంటీ; 30=థర్టీ; 40=ఫోర్టీ|ఫార్టీ; 50=ఫిఫ్టీ; 60=సిక్స్టీ; 70=సెవెంటీ|సెవెన్టీ; 80=ఎయిటీ|ఏటీ; 90=నైంటీ|నైన్టీ",
        // Kannada
        "0=ಜೀರೋ|ಝೀರೋ; 1=ಒನ್|ವನ್|ವಾನ್; 3=ಥ್ರೀ|ತ್ರೀ; 4=ಫೋರ್; 5=ಫೈವ್|ಫೈಬ್; 6=ಸಿಕ್ಸ್; 7=ಸೆವೆನ್|ಸೆವನ್; 9=ನೈನ್; 10=ಟೆನ್",
        "11=ಇಲೆವೆನ್|ಎಲೆವೆನ್; 12=ಟ್ವೆಲ್ವ್; 13=ಥರ್ಟೀನ್; 14=ಫೋರ್ಟೀನ್; 15=ಫಿಫ್ಟೀನ್; 16=ಸಿಕ್ಸ್ಟೀನ್; 17=ಸೆವೆಂಟೀನ್; 18=ಏಟೀನ್|ಎಯ್ಟೀನ್; 19=ನೈಂಟೀನ್",
        "20=ಟ್ವೆಂಟಿ; 30=ಥರ್ಟಿ; 40=ಫೋರ್ಟಿ|ಫಾರ್ಟಿ; 50=ಫಿಫ್ಟಿ; 60=ಸಿಕ್ಸ್ಟಿ; 70=ಸೆವೆಂಟಿ; 80=ಏಟಿ|ಎಯ್ಟಿ; 90=ನೈಂಟಿ",
        // Tamil
        "0=ஜீரோ; 1=ஒன்|வன்; 3=த்ரீ|திரீ; 4=ஃபோர்; 5=ஃபைவ்|பைவ்; 6=சிக்ஸ்; 7=செவன்|செவென்; 9=நைன்; 10=டென்",
        "11=இலெவன்|லெவன்; 12=ட்வெல்வ்; 13=தர்டீன்; 14=ஃபோர்டீன்; 15=ஃபிஃப்டீன்|ஃபிப்டீன்|பிஃப்டீன்; 16=சிக்ஸ்டீன்; 17=செவன்டீன்; 18=எயிட்டீன்|எய்டீன்; 19=நைன்டீன்",
        "20=ட்வென்டி|ட்வெண்டி|டுவெண்டி; 30=தர்டி|தேர்டி|தீர்டி; 40=ஃபோர்டி|ஃபார்டி; 50=ஃபிஃப்டி|ஃபிப்டி|பிஃப்டி|பிப்டி; 60=சிக்ஸ்டி; 70=செவன்டி|செவண்டி; 80=எயிட்டி|எய்டி; 90=நைன்டி|நைண்டி",
    )

    /**
     * "two" and "eight" in Indian scripts are also how the models write English "to" and
     * "at" ("डाउन टू", "ऐट नाइन"), so they count only right before a hundred, a thousand or
     * a rupee word. Odia "eighty" (ଏଇଟି, ଏଟି) is spelled like "this one", so it counts in the
     * same places or before an English unit ("ଏଇଟି ଫାଇଭ" = 85).
     */
    private val englishSaidAmbiguous = table(
        "2=टू|ଟୁ|టూ|ಟೂ|டூ",
        "8=एट|ऐट|एइट|ଏଇଟ|ଏଟ|ఎయిట్|ఏట్|ಏಟ್|ಎಯ್ಟ್|எயிட்|எய்ட்",
        "80=ଏଇଟି|ଏଟି",
    )

    /**
     * Everyday words one letter away from a number word, never read as numbers:
     * Tamil நாங்கள் (we) vs நாங்கு (four), Kannada ಹೊತ್ತು (time) vs ಹತ್ತು (ten),
     * Odia ଏଇଟେ / ଏଇଟା (this) vs ଏଇଟି.
     */
    private val notNumbers: Set<String> = setOf("நாங்கள்", "நாங்க", "ಹೊತ್ತು", "ಹೊತ್ತಿಗೆ", "ଏଇଟେ", "ଏଇଟା", "ଏଟେ", "ଏଟା")
        .map { Text.normalize(it) }.toSet()

    /** Odia counting words written onto a number: ଦୁଇଜଣ (two people), ଚାରିଟା (four of). */
    private val odiaCounters = listOf("ଟିଏ", "ଜଣ", "ଟା", "ଟି", "ଟେ").map { Text.normalize(it) }
    private val currency: Set<String> = setOf(
        "रुपये", "रुपए", "रुपया", "रूपये", "रुपय", "ଟଙ୍କା", "రూపాయలు", "రూపాయల", "రూపాయి", "రుపాయలు",
        "ರೂಪಾಯಿ", "ರೂಪಾಯ", "ரூபாய்", "ரூபா", "rupees", "rupaye", "rs",
    ).map { Text.normalize(it) }.toSet()

    private val units: Map<String, Double> = HashMap<String, Double>().apply {
        putAll(english); putAll(roman); putAll(englishSaid)
        for ((numbers, _) in extraLangs) putAll(table(*numbers.toTypedArray()))
        putAll(odia); putAll(hindi)
    }

    /** Tens a following unit adds to: "twenty five", "ట్వెంటీ ఫైవ్". */
    private val englishTens = english.filterValues { it >= 20 }.keys + englishSaid.filterValues { it >= 20 && it % 10 == 0.0 }.keys +
        englishSaidAmbiguous.filterValues { it >= 20 }.keys

    private val multipliers: Map<String, Double> = table(
        "100=सौ|सो|ଶହ|ଶହେ|ଶ|sau|hundred|हंड्रेड|हन्ड्रेड|ହଣ୍ଡ୍ରେଡ|హండ్రెడ్|హండ్రడ్|ಹಂಡ್ರೆಡ್|ಹಂಡ್ರಡ್|ஹண்ட்ரட்|ஹன்ட்ரட்|ஹண்ட்ரெட்|ஹன்ட்ரெட்",
        "1000=हजार|ହଜାର|hazaar|hazar|hajar|thousand|थाउजेंड|थाउसेंड|थाउजंड|ଥାଉଜେଣ୍ଡ|ଥାଉଜାଣ୍ଡ|థౌజండ్|థౌజెండ్|తౌజండ్|ಥೌಸಂಡ್|ಥೌಸೆಂಡ್|ಥೌಜಂಡ್|தவுசண்ட்|தௌசண்ட்|தவுசன்ட்",
        "100000=लाख|ଲକ୍ଷ|lakh|lac",
        *extraLangs.map { it.second }.toTypedArray(),
    )

    /** "and" inside an English number: "three hundred and seventy five". */
    private val connectors: Set<String> = setOf("and", "एंड", "ऐंड", "ଆଣ୍ଡ", "ଏଣ୍ଡ", "అండ్", "ಆ್ಯಂಡ್", "ಆಂಡ್", "ಅಂಡ್", "அண்ட்", "அண்டு")
        .map { Text.normalize(it) }.toSet()

    /** Words that add or subtract a fraction of the following count: "साढ़े तीन सौ" = 350. */
    private val fractionPrefix: Map<String, Double> = table(
        "0.5=साढ़े|ସାଢ଼େ|sadhe|saadhe",
        "0.25=सवा|ସବା|sava",
        "-0.25=पौने|ପଉଣେ|paune",
        Te.fractionPrefix, Kn.fractionPrefix, Ta.fractionPrefix,
    )

    private val odiaHundredSuffixes = listOf("ଶହେ", "ଶହ")
    private val digits = Regex("\\d+(\\.\\d+)?")

    /**
     * Splits joined forms such as "ପାଞ୍ଚଶହ" (five-hundred) and "ତିନିଟଙ୍କା" (three-rupees) into
     * their parts. [glue] says which words may follow a number inside one written word.
     */
    fun expand(tokens: List<String>, glue: (String) -> Boolean = { false }): List<String> {
        val out = ArrayList<String>(tokens.size)
        for ((i, t) in tokens.withIndex()) {
            if (t !in units && t !in multipliers) {
                val counter = odiaCounters.firstOrNull { t.length > it.length && t.endsWith(it) }
                if (counter != null && t.dropLast(counter.length) in units) {
                    out += t.dropLast(counter.length); out += counter
                    continue
                }
                val cps = t.codePoints().toArray()
                // A known word joined to the number after it, "ଆଜିବାର" (today + twelve), splits too,
                // but only before an amount, hours or pieces word: "କରିବାର" (of doing) is not "କରି 12".
                val next = tokens.getOrNull(i + 1)
                val counted = next != null && (next in multipliers || next in currency || glue(next))
                val afterWord = counted && t !in Keywords.known && t.any { it.code >= 128 }
                val split = (cps.size - 2 downTo 2).firstOrNull { k ->
                    val head = String(cps, 0, k); val tail = String(cps, k, cps.size - k)
                    (head in units && (tail in multipliers || tail in units || glue(tail))) ||
                        (afterWord && head in Keywords.known && tail in units)
                }
                if (split != null) {
                    out += String(cps, 0, split); out += String(cps, split, cps.size - split)
                    continue
                }
            }
            val suffix = odiaHundredSuffixes.firstOrNull { t.length > it.length && t.endsWith(it) }
            val head = suffix?.let { t.dropLast(it.length) }
            if (suffix != null && head in units && t !in units) {
                out += head!!
                out += suffix
            } else {
                out += t
            }
        }
        return out
    }

    private fun unitValue(tokens: List<String>, i: Int): Double? {
        val t = tokens[i]
        units[t]?.let { return it }
        // A misheard number word ("ಅರುವತ್ತು" for "ಅರವತ್ತು"): accept only an unambiguous match.
        if (t !in multipliers && t !in fractionPrefix && t !in notNumbers && t !in englishSaidAmbiguous) Fuzzy.unique(t, units)?.let { return it }
        if (t == "do") {
            val next = tokens.getOrNull(i + 1) ?: return null
            if (next in multipliers || next in romanDoFollowers) return 2.0
        }
        englishSaidAmbiguous[t]?.let { v ->
            val next = tokens.getOrNull(i + 1) ?: return null
            if (next in multipliers || next in currency) return v
            if (v >= 20 && englishSaid[next]?.let { it >= 1 && it < 10 } == true) return v
        }
        return null
    }

    private val romanDoFollowers = setOf(
        "ghante", "ghanta", "piece", "pieces", "pis", "rupaye", "rupay", "rupees", "din", "rs",
    )

    /** [tokens] must already be normalized by [Text.tokens] and [expand]. */
    fun parse(tokens: List<String>): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < tokens.size) {
            val num = readNumber(tokens, i)
            if (num != null) {
                out += num
                i = num.end
            } else {
                out += Word(tokens[i], i)
                i++
            }
        }
        return out
    }

    private fun addsTo(cur: Double, unit: Double): Boolean =
        (cur >= 1000 && cur % 1000 == 0.0 && unit < 1000 && unit >= 1) || (cur >= 100 && cur % 100 == 0.0 && unit < 100 && unit >= 1)

    private fun readNumber(tokens: List<String>, start: Int): Num? {
        var acc = 0.0
        var cur: Double? = null
        var curIsEnglishTens = false
        var frac: Double? = null
        var lastMult = Double.MAX_VALUE
        var consumed = false
        var j = start
        while (j < tokens.size) {
            val t = tokens[j]
            if (t in connectors && acc > 0 && cur == null && frac == null && j + 1 < tokens.size && unitValue(tokens, j + 1) != null) {
                j++
                continue
            }
            val f = fractionPrefix[t]
            val m = multipliers[t]
            val digit = if (digits.matches(t)) t.toDouble() else null
            val unit = digit ?: unitValue(tokens, j)
            if (f != null) {
                if (consumed) break
                frac = f
            } else if (m != null) {
                if (m >= lastMult) break
                acc += ((cur ?: 1.0) + (frac ?: 0.0)) * m
                cur = null
                frac = null
                lastMult = m
                consumed = true
            } else if (unit != null) {
                if (cur != null) {
                    if (curIsEnglishTens && digit == null && unit < 10 && unit >= 1) {
                        cur += unit
                        curIsEnglishTens = false
                    } else if (digit == null && addsTo(cur, unit)) {
                        // Telugu/Kannada/Tamil say hundreds and thousands as one word: "ஐநூறு இருபது" = 500 + 20.
                        cur += unit
                    } else {
                        break
                    }
                } else {
                    cur = unit
                    curIsEnglishTens = t in englishTens
                }
                consumed = true
            } else {
                break
            }
            j++
        }
        if (!consumed) return null
        return Num(acc + (cur ?: 0.0) + (frac ?: 0.0), start, j)
    }

    /** Every number spoken in [text], in order. */
    fun numbersIn(text: String): List<Double> =
        parse(expand(Text.tokens(text))).filterIsInstance<Num>().map { it.value }
}
