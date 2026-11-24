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

    private fun kindOf(word: String): Kind? = when {
        // "bike loan liya" is money borrowed, like an advance; "loan ki kisht" (paying it back) is spending.
        Items.isRepay(word) -> Kind.EXPENSE
        Items.isLoan(word) -> Kind.ADVANCE
        Items.food(word) != null -> Kind.EXPENSE
        else -> kindOfWord(word)
    }

    private fun kindOfWord(word: String): Kind? = when (word) {
        in advance -> Kind.ADVANCE
        in expense -> Kind.EXPENSE
        else -> if (categoryOf(word) != null) Kind.EXPENSE else kindOfRest(word)
    }

    private fun categoryOf(word: String): ExpenseCategory? = when {
        Items.isRepay(word) || Items.isLoan(word) -> ExpenseCategory.LOAN
        Items.food(word) != null -> ExpenseCategory.FOOD
        else -> categoryWords[word] ?: categories.firstOrNull { (_, ws) -> word in ws }?.first
    }

    private fun kindOfRest(word: String): Kind? = when (word) {
        in wage -> Kind.WAGE
        in received -> Kind.RECEIVED
        else -> null
    }

    /** The transcript's words, with numbers split off the money, hours and pieces words they are written onto. */
    fun tokensOf(transcript: String): List<String> =
        NumberWords.expand(Text.tokens(transcript)) { it in money || it in hours || it in pieces }

    /** Every number spoken in [transcript], read exactly as [extract] reads it. */
    fun numbersIn(transcript: String): List<Double> =
        NumberWords.parse(tokensOf(transcript)).filterIsInstance<NumberWords.Num>().map { it.value }

    fun extract(transcript: String): Extraction {
        val tokens = tokensOf(transcript)
        val toks = NumberWords.parse(tokens)
        val wordSet = tokens.toSet()

        query(tokens, toks)?.let { return Extraction(transcript, query = it) }

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

        val lineAt = LinkedHashMap<Int, Int>() // amount's token index → its place in entries
        for (i in amounts) {
            val value = (toks[i] as Num).value
            val kinds = kindsNear(toks, i)
            when {
                Kind.ADVANCE in kinds -> { lineAt[i] = entries.size; entries += DraftEntry(
                    EntryType.ADVANCE, Money.rupeesToPaise(value), dayOffset = dayOffset,
                ) }
                Kind.EXPENSE in kinds -> { lineAt[i] = entries.size; entries += DraftEntry(
                    EntryType.EXPENSE, Money.rupeesToPaise(value),
                    category = categoryNear(toks, i) ?: ExpenseCategory.OTHER, dayOffset = dayOffset,
                ) }
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

        for ((i, code) in details(toks, lineAt.mapValues { entries[it.value] })) {
            val at = lineAt.getValue(i)
            entries[at] = entries[at].copy(detail = code)
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

        return Extraction(transcript, entries = entries, lowConfidence = lowConfidence).sane()
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
        // "bike loan ki kisht": paying a loan back is spending, though "loan" alone is borrowing.
        if (window(toks, i).any { (w, _) -> Items.isRepay(w) }) return setOf(Kind.EXPENSE)
        if (Kind.ADVANCE in all) return setOf(Kind.ADVANCE)
        if (primary.any { it == Kind.WAGE || it == Kind.RECEIVED }) {
            if (Kind.WAGE in all) primary += Kind.WAGE
            if (Kind.RECEIVED in all) primary += Kind.RECEIVED
        }
        if (Kind.EXPENSE in all && Kind.WAGE !in all) return setOf(Kind.EXPENSE)
        return primary
    }

    private fun categoryNear(toks: List<Tok>, i: Int): ExpenseCategory? {
        val near = window(toks, i)
        // A loan word anywhere beside the amount decides: "bike ki kisht" is a loan, not travel.
        if (near.any { (w, _) -> Items.isRepay(w) || Items.isLoan(w) }) return ExpenseCategory.LOAN
        return near.mapNotNull { (w, d) -> categoryOf(w)?.let { it to d } }.minByOrNull { it.second }?.first
    }

    /**
     * The foods, meal and loan said with each amount ([lines]: token index of the amount → its
     * line). Each food or meal word goes to the nearest of those amounts (a word just before an
     * amount wins a tie: "chai 10, samosa 20"); a loan's kind to the loan line beside it.
     */
    private fun details(toks: List<Tok>, lines: Map<Int, DraftEntry>): Map<Int, String> {
        if (lines.isEmpty()) return emptyMap()
        val foods = HashMap<Int, LinkedHashSet<String>>()
        val meals = HashMap<Int, Meal>()
        fun nearest(p: Int, among: Collection<Int>): Int? = among.minWithOrNull(compareBy<Int>({ kotlin.math.abs(it - p) }, { if (it > p) 0 else 1 }))
        val foodLines = lines.filterValues { it.type == EntryType.EXPENSE && it.category == ExpenseCategory.FOOD }.keys
        for ((p, t) in toks.withIndex()) {
            val w = (t as? Word)?.text ?: continue
            val next = (toks.getOrNull(p + 1) as? Word)?.text
            val food = Items.food(w) ?: next?.let { Items.foodPair(w, it) }
            if (food != null) nearest(p, foodLines)?.let { foods.getOrPut(it) { LinkedHashSet() } += food }
            Items.meal(w)?.let { m -> nearest(p, foodLines)?.let { meals.putIfAbsent(it, m) } }
        }
        return lines.mapValues { (i, d) ->
            val isLoan = d.category == ExpenseCategory.LOAN || (d.type == EntryType.ADVANCE && window(toks, i).any { (w, _) -> Items.isLoan(w) })
            Detail(
                foods = foods[i]?.toList().orEmpty(),
                meal = meals[i],
                loan = isLoan,
                loanKind = if (isLoan) window(toks, i).sortedBy { it.second }.firstNotNullOfOrNull { (w, _) -> Items.loanKind(w) } else null,
            ).encode()
        }.filterValues { it.isNotEmpty() }
    }

    private val lastWeek = QueryPeriod.LAST_WEEK
    private val periodSets: List<Pair<QueryPeriod, Keywords>> = listOf(
        QueryPeriod.DAY to (QuestionWords.day + periodWords[0].second),
        QueryPeriod.WEEK to (QuestionWords.week + periodWords[1].second),
        QueryPeriod.MONTH to (QuestionWords.month + periodWords[2].second),
        QueryPeriod.YEAR to (QuestionWords.year + periodWords[3].second),
    )
    private val yesterdayAsked = QuestionWords.yesterday + yesterday

    /** The period a question names: "this week", "इस मंथ का", "ଗଲା ସପ୍ତାହ", "நேத்து"; null when none. */
    private fun periodOf(tokens: List<String>): QueryPeriod? {
        for ((i, t) in tokens.withIndex()) {
            // Exact, or one letter off for a long word: short ones ("இந்த", this) are too close to other words.
            val long = t.codePointCount(0, t.length) >= 6
            val p = periodSets.firstOrNull { (_, ws) -> t in ws.exact || (long && t in ws) }?.first ?: continue
            // "last week", "पिछले महीने": the word right before decides.
            val last = i > 0 && tokens[i - 1] in QuestionWords.last
            return when {
                last && p == QueryPeriod.WEEK -> lastWeek
                last && p == QueryPeriod.MONTH -> QueryPeriod.LAST_MONTH
                else -> p
            }
        }
        return if (tokens.any { it in yesterdayAsked.exact }) QueryPeriod.YESTERDAY else null
    }

    /**
     * A question about the worker's records: its period and topic, or null when the
     * utterance is not one. Clear topic words ("pending", "took from the contractor", "where
     * did the money go") decide the topic; looser clues (a category such as "phone", "got",
     * "work") count only together with a real question word, so "मेरा फोन चार्ज नहीं हो रहा"
     * or "where is the hospital" never become ledger questions.
     */
    private fun query(rawTokens: List<String>, toks: List<Tok>): Query? {
        val q = QuestionWords
        val tokens = rawTokens.map { it.removeSuffix("'s") }
        // Short words must match exactly: one letter turns "कितने" (how many) into "किसने" (who),
        // "बकाया" (dues) into "बनाया" (made), "ఎన్ని" (how many) into "అన్ని" (all).
        fun hasExactish(k: Keywords) = tokens.any { t -> t in k.exact || k.hasStem(t) || (t.codePointCount(0, t.length) >= 6 && t in k) }

        if (hasExactish(minWageWords) || hasExactish(q.minimum)) return Query(QueryPeriod.DAY, QueryTopic.MIN_WAGE)
        // "how much" with amounts in it is an entry, not a question.
        if (toks.any { it is Num && abs(it.value) > 1.0 }) return null

        val strongAsk = hasExactish(q.howMuch) || hasExactish(questionWords) || hasExactish(q.request)
        val weakAsk = hasExactish(q.weakAsk)
        val ledgerNoun = hasExactish(q.ledger)
        val period = periodOf(tokens)
        val employer = hasExactish(q.employer)
        val money = hasExactish(q.moneyNoun)
        // "last" before a week or month ("ଗଲା ସପ୍ତାହ", "போன மாசம்") is not "went" (ଗଲା) or "phone" (போன்).
        val lastBeforePeriod = tokens.indices.filter { i ->
            tokens[i] in q.last.exact && tokens.getOrNull(i + 1)?.let { n -> periodSets.any { (_, ws) -> n in ws.exact } } == true
        }.toSet()
        val went = tokens.withIndex().any { (i, t) ->
            i !in lastBeforePeriod && (t in q.went.exact || (t.codePointCount(0, t.length) >= 6 && t in q.went))
        }
        val english = tokens.joinToString(" ")
        // "did I get my full wages", "पूरा पैसा मिला क्या", "முழு கூலி கிடைச்சுதா": a yes/no
        // question about whether anything is still owed.
        val fullyPaid = hasExactish(q.full) && hasExactish(q.got) && !hasExactish(q.howMuch) && !ledgerNoun

        val explicit: QueryTopic? = when {
            hasExactish(dueWords) || hasExactish(q.due) || tokens.any { it in q.dueExact } || fullyPaid || (employer && hasExactish(q.not)) ||
                (employer && "pay me" in english) || "have to get" in english -> QueryTopic.DUE
            hasExactish(advance) || hasExactish(q.advance) || (employer && hasExactish(q.took)) -> QueryTopic.ADVANCE
            hasExactish(expense) || hasExactish(q.spent) || (went && money) -> QueryTopic.EXPENSE
            hasExactish(wage) || hasExactish(q.earned) || (money && hasExactish(q.made)) -> QueryTopic.EARNED
            else -> null
        }
        val quantity = tokens.any { t -> t in q.howMuch.exact && t !in q.what.exact }
        val category = tokens.withIndex().any { (i, t) -> i !in lastBeforePeriod && categories.any { (_, ws) -> t in ((ws as? Keywords)?.exact ?: ws) || (t.codePointCount(0, t.length) >= 6 && t in ws) } }
        val implied: QueryTopic? = when {
            explicit != null || !strongAsk -> null
            // A thing bought counts only with money or spending in the sentence: "ट्रेन कितने बजे है" asks the time.
            // ... and only when it asks "how much", not "what": "ଆଜି ଖାଇବାକୁ କଣ ଅଛି" asks what there is to eat.
            // "ट्रेन कितने बजे है" asks the time.
            (hasExactish(q.spendingThings) || category || went) && (money || went || quantity) &&
                tokens.none { it in q.clock.exact || it in q.price.exact } -> QueryTopic.EXPENSE
            hasExactish(q.got) || hasExactish(q.work) || hasExactish(workContext) -> QueryTopic.EARNED
            else -> null
        }
        // A guessed topic (from "work", "came", a thing bought) needs "how much" to be about
        // something the app knows: in "आज कितने लोग काम पर आए", "ఎంత మంది", "எத்தனை பேர்" it
        // counts people, not money. The word right after "how much" says what is counted.
        val known: (String) -> Boolean = { t ->
            t in Keywords.known || t in q.filler.exact || periodSets.any { (_, ws) -> t in ws.exact } || t in yesterdayAsked.exact ||
                listOf(q.earned, q.spent, q.advance, q.due, q.got, q.went, q.moneyNoun, q.spendingThings).any { it.hasStem(t) }
        }
        val counted = tokens.withIndex().firstOrNull { (_, t) -> t in q.howMuch.exact }?.let { (i, _) -> tokens.getOrNull(i + 1) }
        if (implied != null && counted != null && !known(counted)) return null
        val topic = explicit ?: implied ?: QueryTopic.ALL
        // Is it a question at all? "how much", "tell me"; "where"/"did" with a ledger topic;
        // "this week's earnings please" (a period and a topic); "this week's account".
        val asks = strongAsk || fullyPaid || (weakAsk && explicit != null) || (period != null && (explicit != null || ledgerNoun))
        // "सरकारी रेट क्या है", "what is the government rate": the official minimum wage.
        if (hasExactish(q.government) && (strongAsk || weakAsk)) return Query(QueryPeriod.DAY, QueryTopic.MIN_WAGE)
        if (!asks) return null
        if (topic == QueryTopic.ALL) {
            // "What is your name?", "आज बारिश होगी क्या": asking, but nothing about the records.
            if (!ledgerNoun && !(strongAsk && period != null)) return null
            // "how hot is it today", "आज कितनी गर्मी है": every word but the period, "how much"
            // and fillers must be about the records, or it asks about something else.
            if (!ledgerNoun && !tokens.all { t ->
                    t in q.filler.exact || t in q.howMuch.exact || t in questionWords.exact || t in q.request.exact ||
                        t in q.last.exact || t in q.moneyNoun.exact || t in q.got.exact || t in q.full.exact ||
                        t in yesterdayAsked.exact || periodSets.any { (_, ws) -> t in ws.exact }
                }
            ) return null
            // "मेरा हिसाब कोई और देख सकता है क्या" is about privacy, not a request for the account.
            if (!strongAsk && period == null) return null
        }
        return Query(period ?: QueryPeriod.WEEK, topic)
    }
}
