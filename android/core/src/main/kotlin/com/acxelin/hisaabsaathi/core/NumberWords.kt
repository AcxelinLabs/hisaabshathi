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
        "0=ଶୂନ|ଶୂନ୍ୟ; 1=ଏକ|ଗୋଟିଏ; 2=ଦୁଇ; 3=ତିନି; 4=ଚାରି; 5=ପାଞ୍ଚ; 6=ଛଅ; 7=ସାତ; 8=ଆଠ; 9=ନଅ; 10=ଦଶ",
        "11=ଏଗାର; 12=ବାର; 13=ତେର; 14=ଚଉଦ; 15=ପନ୍ଦର; 16=ଷୋହଳ; 17=ସତର; 18=ଅଠର; 19=ଉଣେଇଶ; 20=କୋଡ଼ିଏ|କୋଡ଼ି",
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

    private val units: Map<String, Double> = HashMap<String, Double>().apply {
        putAll(english); putAll(roman)
        for ((numbers, _) in extraLangs) putAll(table(*numbers.toTypedArray()))
        putAll(odia); putAll(hindi)
    }

    private val englishTens = english.filterValues { it >= 20 }.keys

    private val multipliers: Map<String, Double> = table(
        "100=सौ|सो|ଶହ|ଶହେ|ଶ|sau|hundred",
        "1000=हजार|ହଜାର|hazaar|hazar|hajar|thousand",
        "100000=लाख|ଲକ୍ଷ|lakh|lac",
        *extraLangs.map { it.second }.toTypedArray(),
    )

    /** Words that add or subtract a fraction of the following count: "साढ़े तीन सौ" = 350. */
    private val fractionPrefix: Map<String, Double> = table(
        "0.5=साढ़े|ସାଢ଼େ|sadhe|saadhe",
        "0.25=सवा|ସବା|sava",
        "-0.25=पौने|ପଉଣେ|paune",
        Te.fractionPrefix, Kn.fractionPrefix, Ta.fractionPrefix,
    )

    private val odiaHundredSuffixes = listOf("ଶହେ", "ଶହ")
    private val digits = Regex("\\d+(\\.\\d+)?")

    /** Splits joined Odia forms such as "ପାଞ୍ଚଶହ" into "ପାଞ୍ଚ", "ଶହ". */
    fun expand(tokens: List<String>): List<String> {
        val out = ArrayList<String>(tokens.size)
        for (t in tokens) {
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
        if (t == "do") {
            val next = tokens.getOrNull(i + 1) ?: return null
            if (next in multipliers || next in romanDoFollowers) return 2.0
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
