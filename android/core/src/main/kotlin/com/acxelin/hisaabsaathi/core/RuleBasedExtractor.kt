package com.acxelin.hisaabsaathi.core

import com.acxelin.hisaabsaathi.core.NumberWords.Num
import com.acxelin.hisaabsaathi.core.NumberWords.Tok
import com.acxelin.hisaabsaathi.core.NumberWords.Word
import com.acxelin.hisaabsaathi.core.i18n.Kn
import com.acxelin.hisaabsaathi.core.i18n.Ta
import com.acxelin.hisaabsaathi.core.i18n.Te
import kotlin.math.abs

/**
 * Turns a transcript into draft ledger entries with plain rules: find the numbers, look at
 * the words around each one, decide what it is. Works in Hindi, Odia, English and Hindi
 * typed in Latin letters, with no model, so it runs on any phone and is the fallback (and
 * the checker) for the language model.
 */
object RuleBasedExtractor {

    private fun words(vararg w: String): Keywords =
        Keywords(w.flatMap { it.split(' ') }.filter { it.isNotBlank() }.map { Text.normalize(it) }.toSet())

    /** Telugu, Kannada and Tamil words for a role, from the i18n tables. */
    private fun kw(role: String): Keywords =
        words(*listOf(Te.keywords, Kn.keywords, Ta.keywords).mapNotNull { it[role] }.toTypedArray())

    private val hours = words(
        "घंटे घंटा घंटों घण्टे घण्टा ghante ghanta ghanto hour hours hrs hr ଘଣ୍ଟା ଘଂଟା ଘଣ୍ଟେ",
    ) + kw("hours")
    private val pieces = words(
        "पीस पीसेस पिस नग दाना दाने अदद piece pieces pcs pis peece ପିସ୍ ପିସ ଖଣ୍ଡ ଗୋଟା ପିସି",
    ) + kw("pieces")
    private val money = words(
        "रुपये रुपया रुपए रुपय रुपयों रूपये रूपया रूपए rupaye rupay rupaya rupees rupee rs inr ଟଙ୍କା ଟଂକା ଟଙ୍କାର taka tanka",
    ) + kw("money")
    private val rate = words("रेट दर भाव हिसाब प्रति rate per bhav dar ଦର ରେଟ୍ ରେଟ ହିସାବ ପ୍ରତି") + kw("rate")
    private val atWords = setOf("at", "@")
    private val advance = words(
        "एडवांस अडवांस एडवान्स एडवांस्ड खर्ची उधार बयाना अग्रिम advance kharchi udhar udhaar " +
            "ଆଡଭାନ୍ସ ଆଡ୍ଭାନ୍ସ ଆଭାଂସ ଆଡଭାଂସ ଆଡ୍ଭାଂସ ଆଭାନ୍ସ ଏଡଭାନ୍ସ ଅଗ୍ରୀମ ଅଗ୍ରିମ ଆଗୁଆ ଉଧାର ବଇନା",
    ) + kw("advance")
    private val expense = words(
        "खर्च खर्चा खर्चे खरीदा खरीदी खरीदे kharch kharcha spent spend bought expense ଖର୍ଚ୍ଚ ଖର୍ଚ କିଣିଲି କିଣିଲା କିଣିଲେ",
    ) + kw("expense")
    private val wage = words(
        "मजदूरी दिहाड़ी वेतन तनख्वाह पगार कमाया कमाई कमाए कमाये majdoori mazdoori majduri dihadi wage wages " +
            "salary earned earn earnings kamaya kamai kamaye ମଜୁରୀ ମଜୁରି ଦରମା ରୋଜଗାର",
    ) + kw("wage")
    private val received = words(
        "मिला मिली मिले पेमेंट भुगतान mila mili mile received got get paid payment ମିଳିଲା ମିଳିଲେ ପାଇଲି ପେମେଣ୍ଟ",
    ) + kw("received")
    private val workContext = words("काम काम किया kaam work worked କାମ") + kw("workContext")
    private val separators = words("और फिर तथा and then aur phir ଆଉ ଏବଂ ଓ") + kw("separators")

    private val categories: List<Pair<ExpenseCategory, Set<String>>> = listOf(
        ExpenseCategory.FOOD to words(
            "खाना खाने सब्जी राशन चाय नाश्ता दूध चावल आटा दाल khana sabzi sabji ration chai nashta food milk rice " +
                "ଖାଇବା ପରିବା ଚାଉଳ ଚା ଜଳଖିଆ ଡାଲି କ୍ଷୀର",
        ) + kw("food"),
        ExpenseCategory.TRAVEL to words(
            "बस ऑटो टिकट पेट्रोल भाड़ा रिक्शा ट्रेन bus auto ticket petrol bhada train ବସ୍ ବସ ଅଟୋ ଟିକେଟ୍ ଭଡ଼ା ପେଟ୍ରୋଲ",
        ) + kw("travel"),
        ExpenseCategory.RENT to words("किराया किराये kiraya rent ଘରଭଡ଼ା") + kw("rent"),
        ExpenseCategory.PHONE to words("रिचार्ज मोबाइल फोन recharge mobile phone ରିଚାର୍ଜ ମୋବାଇଲ୍ ମୋବାଇଲ ଫୋନ୍") + kw("phone"),
        ExpenseCategory.HEALTH to words(
            "दवा दवाई डॉक्टर अस्पताल dawa dawai medicine doctor hospital ଔଷଧ ଡାକ୍ତର ଡାକ୍ତରଖାନା",
        ) + kw("health"),
        ExpenseCategory.HOME to words("भेजा भेजे भेजी bheja bheje sent ପଠାଇଲି ପଠେଇଲି") + kw("home"),
    )
    private val categoryWords: Map<String, ExpenseCategory> =
        categories.flatMap { (c, ws) -> ws.map { it to c } }.toMap()

    private val yesterday = words("कल kal yesterday ଗତକାଲି କାଲି") + kw("yesterday")
    private val dayBefore = words("परसों parso ପଅରଦିନ") + kw("dayBefore")

    private val questionWords = words(
        "कितना कितनी कितने कितनी kitna kitni kitne how what बताओ batao କେତେ କେତେଟା",
    ) + kw("question")
    private val periodWords: List<Pair<QueryPeriod, Set<String>>> = listOf(
        QueryPeriod.DAY to words("आज aaj today ଆଜି") + kw("day"),
        QueryPeriod.WEEK to words("हफ्ते हफ्ता सप्ताह hafte hafta week ସପ୍ତାହ ସପ୍ତାହରେ") + kw("week"),
        QueryPeriod.MONTH to words("महीने महीना माह mahine mahina month ମାସ ମାସରେ") + kw("month"),
        QueryPeriod.YEAR to words("साल वर्ष saal year ବର୍ଷ ବର୍ଷରେ") + kw("year"),
    )
    private val dueWords = words("बाकी बकाया baaki baki due balance ବାକି ବକେୟା") + kw("due")
    private val minWageWords = words("न्यूनतम minimum ସର୍ବନିମ୍ନ") + kw("minWage")

    private enum class Kind { ADVANCE, EXPENSE, WAGE, RECEIVED }

    private fun kindOf(word: String): Kind? = when (word) {
        in advance -> Kind.ADVANCE
        in expense -> Kind.EXPENSE
        else -> if (categoryOf(word) != null) Kind.EXPENSE else kindOfRest(word)
    }

    private fun categoryOf(word: String): ExpenseCategory? =
        categoryWords[word] ?: categories.firstOrNull { (_, ws) -> word in ws }?.first

    private fun kindOfRest(word: String): Kind? = when (word) {
        in wage -> Kind.WAGE
        in received -> Kind.RECEIVED
        else -> null
    }

    fun extract(transcript: String): Extraction {
        val tokens = NumberWords.expand(Text.tokens(transcript)) { it in money || it in hours || it in pieces }
        val toks = NumberWords.parse(tokens)
        val wordSet = tokens.toSet()

        query(wordSet, toks)?.let { return Extraction(transcript, query = it) }

        val dayOffset = when {
            wordSet.any { it in dayBefore } -> -2
            wordSet.any { it in yesterday } -> -1
            else -> 0
        }

        var hoursValue: Double? = null
        var piecesValue: Int? = null
        var rateValue: Double? = null
        val amounts = ArrayList<Int>() // indices into toks of money amounts

        var lowConfidence = false
        val claimedRateWords = HashSet<Int>()
        for ((i, tok) in toks.withIndex()) {
            if (tok !is Num) continue
            val next = (toks.getOrNull(i + 1) as? Word)?.text
            val next2 = (toks.getOrNull(i + 2) as? Word)?.text
            val prev = (toks.getOrNull(i - 1) as? Word)?.text
            val prev2 = (toks.getOrNull(i - 2) as? Word)?.text
            // "5 रुपये रेट": the rate word belongs to the number before it, so a number after
            // it ("रेट 100 एडवांस") is not also a rate.
            val rateWordAfter = when {
                next in rate -> i + 1
                next in money && next2 in rate -> i + 2
                else -> -1
            }
            if (rateWordAfter >= 0 && rateValue == null) claimedRateWords += rateWordAfter
            val isRate = rateWordAfter >= 0 || (prev in rate && (i - 1) !in claimedRateWords) ||
                prev in atWords || (prev in money && prev2 in atWords) ||
                (next in money && (next2 in pieces || next2 in hours || isPerWord(next2)))
            when {
                next in hours && hoursValue == null -> hoursValue = tok.value
                next in pieces && piecesValue == null -> piecesValue = tok.value.toInt()
                isRate && rateValue == null -> rateValue = tok.value
                else -> amounts += i
            }
        }

        val entries = ArrayList<DraftEntry>()
        var earned: Double? = null
        var paid = ArrayList<Double>()
        val hasWorkContext = hoursValue != null || piecesValue != null || wordSet.any { it in workContext }

        if (rateValue != null) {
            val p = piecesValue
            val h = hoursValue
            if (p != null) earned = p * rateValue else if (h != null) earned = h * rateValue
            // Implausible for one day's work: almost certainly a misheard number.
            if (rateValue > 2000 || (earned ?: 0.0) > 20000) lowConfidence = true
        }

        for (i in amounts) {
            val value = (toks[i] as Num).value
            val kinds = kindsNear(toks, i)
            when {
                Kind.ADVANCE in kinds -> entries += DraftEntry(
                    EntryType.ADVANCE, Money.rupeesToPaise(value), dayOffset = dayOffset,
                )
                Kind.EXPENSE in kinds -> entries += DraftEntry(
                    EntryType.EXPENSE, Money.rupeesToPaise(value),
                    category = categoryNear(toks, i) ?: ExpenseCategory.OTHER, dayOffset = dayOffset,
                )
                Kind.WAGE in kinds -> {
                    if (earned == null) earned = value
                    if (Kind.RECEIVED in kinds) paid += value
                }
                Kind.RECEIVED in kinds -> paid += value
                earned == null && hasWorkContext -> earned = value
                else -> {
                    // "एक चुटकुला सुनाओ": a lone small number with no rupee word is just a word.
                    val saysMoney = (toks.getOrNull(i + 1) as? Word)?.text in money || (toks.getOrNull(i - 1) as? Word)?.text in money
                    if (value < 10 && !saysMoney) continue
                    // A bare amount: most often the day's wage. Ask the worker to check.
                    if (earned == null) earned = value
                    lowConfidence = true
                }
            }
        }

        if (earned != null || hoursValue != null || piecesValue != null) {
            entries.add(
                0,
                DraftEntry(
                    type = EntryType.WORK,
                    amountPaise = Money.rupeesToPaise(earned ?: 0.0),
                    hours = hoursValue,
                    pieces = piecesValue,
                    ratePaise = rateValue?.let(Money::rupeesToPaise),
                    dayOffset = dayOffset,
                ),
            )
            if (earned == null) lowConfidence = true
        }
        for (p in paid) entries += DraftEntry(EntryType.PAYMENT, Money.rupeesToPaise(p), dayOffset = dayOffset)

        return Extraction(transcript, entries = entries, lowConfidence = lowConfidence)
    }

    private fun isPerWord(w: String?): Boolean = w != null && (w == "के" || w == "ke" || w == "per" || w == "ପ୍ରତି")

    /** Words between the neighbouring numbers, stopping at "and"/"then", with their distance. */
    private fun window(toks: List<Tok>, i: Int): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        var j = i + 1
        while (j < toks.size) {
            val t = toks[j] as? Word ?: break
            if (t.text in separators) break
            out += t.text to (j - i)
            j++
        }
        j = i - 1
        while (j >= 0) {
            val t = toks[j] as? Word ?: break
            if (t.text in separators) break
            // Words before the number count as slightly farther: the verb comes last.
            out += t.text to (i - j) + 1
            j--
        }
        return out
    }

    private fun kindsNear(toks: List<Tok>, i: Int): Set<Kind> {
        val hits = window(toks, i).mapNotNull { (w, d) -> kindOf(w)?.let { it to d } }
        if (hits.isEmpty()) return emptySet()
        val nearest = hits.minOf { it.second }
        val primary = hits.filter { it.second == nearest }.map { it.first }.toMutableSet()
        // "मजदूरी मिली": wage and received belong together even when not equally near.
        val all = hits.map { it.first }.toSet()
        if (Kind.ADVANCE in all) return setOf(Kind.ADVANCE)
        if (primary.any { it == Kind.WAGE || it == Kind.RECEIVED }) {
            if (Kind.WAGE in all) primary += Kind.WAGE
            if (Kind.RECEIVED in all) primary += Kind.RECEIVED
        }
        if (Kind.EXPENSE in all && Kind.WAGE !in all) return setOf(Kind.EXPENSE)
        return primary
    }

    private fun categoryNear(toks: List<Tok>, i: Int): ExpenseCategory? =
        window(toks, i).mapNotNull { (w, d) -> categoryOf(w)?.let { it to d } }.minByOrNull { it.second }?.first

    private fun query(wordSet: Set<String>, toks: List<Tok>): Query? {
        if (wordSet.any { it in minWageWords.exact }) return Query(QueryPeriod.DAY, QueryTopic.MIN_WAGE)
        val asksHowMuch = wordSet.any { it in questionWords.exact }
        if (!asksHowMuch) return null
        // "how much" with amounts in it is an entry, not a question.
        if (toks.any { it is Num && abs(it.value) > 1.0 }) return null
        val saidPeriod = periodWords.firstOrNull { (_, ws) -> wordSet.any { it in (ws as Keywords).exact } }?.first
        val period = saidPeriod ?: QueryPeriod.WEEK
        val topic = when {
            wordSet.any { it in dueWords } -> QueryTopic.DUE
            wordSet.any { it in advance } -> QueryTopic.ADVANCE
            wordSet.any { it in expense } -> QueryTopic.EXPENSE
            wordSet.any { it in wage } -> QueryTopic.EARNED
            else -> QueryTopic.ALL
        }
        // "What is your name?" has a question word but nothing about the ledger: not ours.
        if (topic == QueryTopic.ALL && saidPeriod == null) return null
        return Query(period, topic)
    }
}
