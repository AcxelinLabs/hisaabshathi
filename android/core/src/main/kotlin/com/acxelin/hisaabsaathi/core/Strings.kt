package com.acxelin.hisaabsaathi.core

/**
 * Every word the app shows or speaks, in Hindi, Odia and English. Kept in code (not Android
 * resources) so the spoken sentences can be unit-tested and the language can be switched
 * inside the app without touching the phone's system language.
 *
 * The Odia text is best-effort and must be reviewed by a native speaker before the pilot.
 */
class Strings(val lang: Lang) {
    private fun t(hi: String, or: String, en: String): String = when (lang) {
        Lang.HI -> hi
        Lang.OR -> or
        Lang.EN -> en
    }

    // ---- general
    val appName get() = t("हिसाब साथी", "ହିସାବ ସାଥୀ", "HisaabSaathi")
    val next get() = t("आगे", "ଆଗକୁ", "Next")
    val done get() = t("हो गया", "ହୋଇଗଲା", "Done")
    val cancel get() = t("रद्द करें", "ବାତିଲ୍", "Cancel")
    val delete get() = t("हटाएं", "ହଟାନ୍ତୁ", "Delete")
    val yes get() = t("हाँ", "ହଁ", "Yes")
    val no get() = t("नहीं", "ନା", "No")
    val share get() = t("भेजें", "ପଠାନ୍ତୁ", "Share")

    // ---- onboarding
    val chooseLanguage get() = t("अपनी भाषा चुनें", "ନିଜ ଭାଷା ବାଛନ୍ତୁ", "Choose your language")
    val chooseState get() = t("आप किस राज्य में काम करते हैं?", "ଆପଣ କେଉଁ ରାଜ୍ୟରେ କାମ କରନ୍ତି?", "Which state do you work in?")
    val voicePackTitle get() = t("आवाज़ पैक डाउनलोड करें", "ଭଏସ୍ ପ୍ୟାକ୍ ଡାଉନଲୋଡ୍ କରନ୍ତୁ", "Download the voice pack")
    val voicePackBody get() = t(
        "एक बार डाउनलोड करने के बाद ऐप बिना इंटरनेट के आपकी आवाज़ समझेगा। आपकी आवाज़ और हिसाब फोन से बाहर नहीं जाते।",
        "ଥରେ ଡାଉନଲୋଡ୍ କଲା ପରେ ଆପ୍ ବିନା ଇଣ୍ଟରନେଟ୍‌ରେ ଆପଣଙ୍କ କଥା ବୁଝିବ। ଆପଣଙ୍କ ସ୍ୱର ଓ ହିସାବ ଫୋନ୍ ବାହାରକୁ ଯାଏ ନାହିଁ।",
        "After one download the app understands your voice with no internet. Your voice and your records never leave this phone.",
    )
    val download get() = t("डाउनलोड करें", "ଡାଉନଲୋଡ୍ କରନ୍ତୁ", "Download")
    val downloading get() = t("डाउनलोड हो रहा है", "ଡାଉନଲୋଡ୍ ହେଉଛି", "Downloading")
    val installed get() = t("तैयार", "ପ୍ରସ୍ତୁତ", "Ready")
    val skipForNow get() = t("अभी नहीं", "ଏବେ ନୁହେଁ", "Not now")
    val downloadFailed get() = t("डाउनलोड नहीं हुआ। इंटरनेट देखें और फिर कोशिश करें।", "ଡାଉନଲୋଡ୍ ହେଲା ନାହିଁ। ଇଣ୍ଟରନେଟ୍ ଦେଖି ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ।", "Download failed. Check the internet and try again.")
    val start get() = t("शुरू करें", "ଆରମ୍ଭ କରନ୍ତୁ", "Start")

    // ---- home
    val today get() = t("आज", "ଆଜି", "Today")
    val earned get() = t("कमाई", "ରୋଜଗାର", "Earned")
    val advance get() = t("एडवांस", "ଆଡଭାନ୍ସ", "Advance")
    val received get() = t("मिला", "ମିଳିଲା", "Received")
    val spent get() = t("खर्च", "ଖର୍ଚ୍ଚ", "Spent")
    val due get() = t("बाकी", "ବାକି", "Due")
    val tapAndSpeak get() = t("दबाएं और काम या खर्च बोलें", "ଦବାଇ କାମ କିମ୍ବା ଖର୍ଚ୍ଚ କୁହନ୍ତୁ", "Tap and speak your work or expense")
    val listening get() = t("सुन रहा हूँ… बोलिए", "ଶୁଣୁଛି… କୁହନ୍ତୁ", "Listening… speak now")
    val thinking get() = t("समझ रहा हूँ…", "ବୁଝୁଛି…", "Understanding…")
    val tapToStop get() = t("रोकने के लिए दबाएं", "ବନ୍ଦ କରିବାକୁ ଦବାନ୍ତୁ", "Tap to stop")
    val scanBill get() = t("बिल / पर्ची की फोटो", "ବିଲ୍ / ସ୍ଲିପ୍ ଫଟୋ", "Scan bill / advance slip")
    val typeInstead get() = t("लिखकर डालें", "ଲେଖି ଦିଅନ୍ତୁ", "Type instead")
    val typeHint get() = t("जैसे: 120 पीस, 5 रुपये रेट, 300 एडवांस", "ଯେପରି: 120 ପିସ୍, 5 ଟଙ୍କା ଦର, 300 ଆଡଭାନ୍ସ", "e.g. 120 pieces, rate 5 rupees, 300 advance")
    val tabHome get() = t("होम", "ହୋମ୍", "Home")
    val tabLedger get() = t("खाता", "ଖାତା", "Ledger")
    val tabSummary get() = t("हिसाब", "ହିସାବ", "Summary")
    val tabWage get() = t("मजदूरी व मदद", "ମଜୁରୀ ଓ ସାହାଯ୍ୟ", "Wage & Help")
    val micPermission get() = t("बोलकर हिसाब लिखने के लिए माइक की अनुमति दें।", "କହି ହିସାବ ଲେଖିବାକୁ ମାଇକ୍ ଅନୁମତି ଦିଅନ୍ତୁ।", "Allow the microphone to log entries by voice.")
    val notUnderstood get() = t("समझ नहीं आया। फिर से बोलिए।", "ବୁଝି ହେଲା ନାହିଁ। ପୁଣି କୁହନ୍ତୁ।", "I did not understand. Please say it again.")
    val noSpeechEngine get() = t("आवाज़ पैक नहीं है। सेटिंग में डाउनलोड करें या लिखकर डालें।", "ଭଏସ୍ ପ୍ୟାକ୍ ନାହିଁ। ସେଟିଂରେ ଡାଉନଲୋଡ୍ କରନ୍ତୁ କିମ୍ବା ଲେଖି ଦିଅନ୍ତୁ।", "No voice pack yet. Download it in Settings, or type the entry.")

    // ---- confirm
    val youSaid get() = t("आपने कहा", "ଆପଣ କହିଲେ", "You said")
    val isThisRight get() = t("क्या यह सही है?", "ଏହା ଠିକ୍ କି?", "Is this right?")
    val pleaseCheck get() = t("कृपया ध्यान से देखें", "ଦୟାକରି ଭଲଭାବେ ଦେଖନ୍ତୁ", "Please check carefully")
    val save get() = t("सही है, सेव करें", "ଠିକ୍ ଅଛି, ସେଭ୍ କରନ୍ତୁ", "Correct, save")
    val fixByVoice get() = t("गलत है, फिर बोलें", "ଭୁଲ୍ ଅଛି, ପୁଣି କୁହନ୍ତୁ", "Wrong, say it again")
    val saved get() = t("सेव हो गया", "ସେଭ୍ ହୋଇଗଲା", "Saved")
    val playAgain get() = t("फिर सुनें", "ପୁଣି ଶୁଣନ୍ତୁ", "Play again")
    val amountLabel get() = t("रकम (₹)", "ରାଶି (₹)", "Amount (₹)")

    // ---- ledger / reports
    val noEntries get() = t("अभी कोई एंट्री नहीं है", "ଏବେ କୌଣସି ଏଣ୍ଟ୍ରି ନାହିଁ", "No entries yet")
    val deleteEntryQ get() = t("यह एंट्री हटाएं?", "ଏହି ଏଣ୍ଟ୍ରି ହଟାଇବେ?", "Delete this entry?")
    val readAloud get() = t("हिसाब सुनाएं", "ହିସାବ ଶୁଣାନ୍ତୁ", "Read aloud")
    val exportPdf get() = t("PDF बनाएं / भेजें", "PDF ତିଆରି / ପଠାନ୍ତୁ", "Export PDF / share")
    val savingsGoal get() = t("बचत का लक्ष्य", "ସଞ୍ଚୟ ଲକ୍ଷ୍ୟ", "Savings goal")
    val setGoal get() = t("लक्ष्य तय करें", "ଲକ୍ଷ୍ୟ ଠିକ୍ କରନ୍ତୁ", "Set a goal")
    val goalMonthly get() = t("हर महीने कितना बचाना है? (₹)", "ପ୍ରତି ମାସ କେତେ ସଞ୍ଚୟ କରିବେ? (₹)", "How much to save each month? (₹)")
    val daysWorked get() = t("काम के दिन", "କାମ ଦିନ", "Days worked")
    val inHand get() = t("हाथ में बचा", "ହାତରେ ବଳିଲା", "Left in hand")
    fun periodName(p: QueryPeriod): String = when (p) {
        QueryPeriod.DAY -> t("आज", "ଆଜି", "Today")
        QueryPeriod.WEEK -> t("हफ्ता", "ସପ୍ତାହ", "Week")
        QueryPeriod.MONTH -> t("महीना", "ମାସ", "Month")
        QueryPeriod.YEAR -> t("साल", "ବର୍ଷ", "Year")
    }

    // ---- wage & help
    val minWageTitle get() = t("सरकारी न्यूनतम मजदूरी", "ସରକାରୀ ସର୍ବନିମ୍ନ ମଜୁରୀ", "Official minimum wage")
    val centralRates get() = t("केंद्र सरकार की दरें (इस राज्य की दरें अभी ऐप में नहीं हैं)", "କେନ୍ଦ୍ର ସରକାରଙ୍କ ଦର (ଏହି ରାଜ୍ୟର ଦର ଏବେ ଆପ୍‌ରେ ନାହିଁ)", "Central government rates (this state's rates are not in the app yet)")
    val perDay get() = t("प्रति दिन", "ପ୍ରତି ଦିନ", "per day")
    val effectiveFrom get() = t("लागू", "ଲାଗୁ", "Effective")
    val source get() = t("स्रोत", "ଉତ୍ସ", "Source")
    val yourAverage get() = t("आपकी औसत रोज़ की कमाई (पिछले 30 दिन)", "ଆପଣଙ୍କ ହାରାହାରି ଦୈନିକ ରୋଜଗାର (ଗତ 30 ଦିନ)", "Your average daily earning (last 30 days)")
    val wageDataNote get() = t(
        "ये दरें पुरानी हो सकती हैं। अपने श्रम कार्यालय या यूनियन से पक्का करें।",
        "ଏହି ଦର ପୁରୁଣା ହୋଇଥାଇପାରେ। ନିଜ ଶ୍ରମ କାର୍ଯ୍ୟାଳୟ କିମ୍ବା ୟୁନିଅନ୍‌ରୁ ନିଶ୍ଚିତ କରନ୍ତୁ।",
        "These rates may be out of date. Confirm with your labour office or union.",
    )
    val notEnoughData get() = t("तुलना के लिए पहले कुछ दिन का काम दर्ज करें।", "ତୁଳନା ପାଇଁ ଆଗେ କିଛି ଦିନର କାମ ଲେଖନ୍ତୁ।", "Log a few days of work to compare.")
    val evidenceTitle get() = t("सबूत फ़ाइल", "ପ୍ରମାଣ ଫାଇଲ୍", "Evidence pack")
    val evidenceBody get() = t(
        "यूनियन, श्रम कार्यालय या बैंक के लिए आपका पूरा रिकॉर्ड। आप भेजेंगे तभी जाएगा।",
        "ୟୁନିଅନ୍, ଶ୍ରମ କାର୍ଯ୍ୟାଳୟ କିମ୍ବା ବ୍ୟାଙ୍କ ପାଇଁ ଆପଣଙ୍କ ସମ୍ପୂର୍ଣ୍ଣ ରେକର୍ଡ। ଆପଣ ପଠାଇଲେ ହିଁ ଯିବ।",
        "Your full record for a union, labour office or bank. It only leaves the phone when you send it.",
    )
    val exportEvidence get() = t("सबूत फ़ाइल बनाएं", "ପ୍ରମାଣ ଫାଇଲ୍ ତିଆରି କରନ୍ତୁ", "Create evidence pack")
    val recordIntact get() = t("रिकॉर्ड से छेड़छाड़ नहीं हुई", "ରେକର୍ଡରେ କୌଣସି ଛେଡ଼ଛାଡ଼ ହୋଇନାହିଁ", "Record has not been altered")
    val recordBroken get() = t("रिकॉर्ड में बदलाव मिला", "ରେକର୍ଡରେ ପରିବର୍ତ୍ତନ ମିଳିଲା", "Record shows a change")
    fun shortfall(paise: Long): String = t(
        "आपको न्यूनतम मजदूरी से रोज़ ${rupees(paise)} कम मिल रहा है।",
        "ଆପଣଙ୍କୁ ସର୍ବନିମ୍ନ ମଜୁରୀଠାରୁ ଦିନକୁ ${rupees(paise)} କମ୍ ମିଳୁଛି।",
        "You are getting ${rupees(paise)} a day less than the minimum wage.",
    )
    val atOrAbove get() = t("आपकी कमाई न्यूनतम मजदूरी के बराबर या ज़्यादा है।", "ଆପଣଙ୍କ ରୋଜଗାର ସର୍ବନିମ୍ନ ମଜୁରୀ ସହ ସମାନ କିମ୍ବା ଅଧିକ।", "Your earning is at or above the minimum wage.")

    // ---- settings / privacy
    val settings get() = t("सेटिंग", "ସେଟିଂ", "Settings")
    val language get() = t("भाषा", "ଭାଷା", "Language")
    val workState get() = t("काम का राज्य", "କାମର ରାଜ୍ୟ", "Work state")
    val reminders get() = t("रोज़ 3 बजे और 9 बजे याद दिलाएं", "ପ୍ରତିଦିନ 3ଟା ଓ 9ଟାରେ ମନେ ପକାନ୍ତୁ", "Remind me daily at 3 PM and 9 PM")
    val modelPacks get() = t("ऑफ़लाइन पैक", "ଅଫ୍‌ଲାଇନ୍ ପ୍ୟାକ୍", "Offline packs")
    val smartPack get() = t("समझदार मॉडल (बेहतर समझ)", "ସ୍ମାର୍ଟ ମଡେଲ୍ (ଭଲ ବୁଝାମଣା)", "Smart model (better understanding)")
    val privacyTitle get() = t("आपका डेटा, आपके पास", "ଆପଣଙ୍କ ଡାଟା, ଆପଣଙ୍କ ପାଖରେ", "Your data stays with you")
    val privacyBody get() = t(
        "• आपका हिसाब इसी फोन में ताले (एन्क्रिप्शन) में रहता है।\n• आवाज़ फोन में ही समझी जाती है और सेव नहीं होती।\n• ठेकेदार, कंपनी या हम — कोई आपका हिसाब नहीं देख सकता।\n• इंटरनेट सिर्फ़ पैक डाउनलोड करने के लिए लगता है।\n• आप जब चाहें सब कुछ मिटा सकते हैं।",
        "• ଆପଣଙ୍କ ହିସାବ ଏହି ଫୋନ୍‌ରେ ତାଲା (ଏନକ୍ରିପ୍ସନ୍) ଭିତରେ ରହେ।\n• ସ୍ୱର ଫୋନ୍‌ରେ ହିଁ ବୁଝାଯାଏ, ସେଭ୍ ହୁଏ ନାହିଁ।\n• ଠିକାଦାର, କମ୍ପାନୀ କିମ୍ବା ଆମେ — କେହି ଆପଣଙ୍କ ହିସାବ ଦେଖିପାରିବେ ନାହିଁ।\n• ଇଣ୍ଟରନେଟ୍ କେବଳ ପ୍ୟାକ୍ ଡାଉନଲୋଡ୍ ପାଇଁ ଲାଗେ।\n• ଆପଣ ଚାହିଁଲେ ସବୁ ଲିଭାଇ ପାରିବେ।",
        "• Your records are kept encrypted on this phone only.\n• Your voice is understood on the phone and is not saved.\n• No contractor, company, or we ourselves can see your records.\n• The internet is used only to download packs.\n• You can erase everything whenever you want.",
    )
    val eraseAll get() = t("सारा हिसाब मिटाएं", "ସବୁ ହିସାବ ଲିଭାନ୍ତୁ", "Erase all records")
    val eraseAllQ get() = t("सारा हिसाब हमेशा के लिए मिट जाएगा। पक्का?", "ସବୁ ହିସାବ ସବୁଦିନ ପାଇଁ ଲିଭିଯିବ। ନିଶ୍ଚିତ?", "All records will be erased for ever. Are you sure?")

    // ---- reminders
    val reminderTitle get() = t("आज का हिसाब लिखा?", "ଆଜିର ହିସାବ ଲେଖିଲେ?", "Logged today's work?")
    val reminderBody get() = t("दबाएं और बोलकर काम, एडवांस या खर्च दर्ज करें।", "ଦବାଇ କହି କାମ, ଆଡଭାନ୍ସ କିମ୍ବା ଖର୍ଚ୍ଚ ଲେଖନ୍ତୁ।", "Tap and say your work, advance or expense.")

    // ---- formatting
    fun rupees(paise: Long): String = "₹" + Money.format(paise)

    private fun spokenRupees(paise: Long): String =
        t("${Money.format(paise)} रुपये", "${Money.format(paise)} ଟଙ୍କା", "${Money.format(paise)} rupees")

    private fun num(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    fun typeName(type: EntryType): String = when (type) {
        EntryType.WORK -> t("काम", "କାମ", "Work")
        EntryType.ADVANCE -> advance
        EntryType.PAYMENT -> t("मजदूरी मिली", "ମଜୁରୀ ମିଳିଲା", "Wages received")
        EntryType.EXPENSE -> spent
    }

    fun categoryName(c: ExpenseCategory): String = when (c) {
        ExpenseCategory.FOOD -> t("खाना", "ଖାଇବା", "Food")
        ExpenseCategory.TRAVEL -> t("आना-जाना", "ଯିବା-ଆସିବା", "Travel")
        ExpenseCategory.RENT -> t("किराया", "ଘରଭଡ଼ା", "Rent")
        ExpenseCategory.PHONE -> t("मोबाइल", "ମୋବାଇଲ୍", "Phone")
        ExpenseCategory.HEALTH -> t("दवा", "ଔଷଧ", "Health")
        ExpenseCategory.HOME -> t("घर भेजा", "ଘରକୁ ପଠାଇଲି", "Sent home")
        ExpenseCategory.OTHER -> t("अन्य", "ଅନ୍ୟ", "Other")
    }

    /** "120 पीस × ₹5 · 8 घंटे" — the detail line under a work entry. */
    fun workDetail(hours: Double?, pieces: Int?, ratePaise: Long?): String {
        val parts = ArrayList<String>()
        if (pieces != null) {
            val p = t("$pieces पीस", "$pieces ପିସ୍", "$pieces pieces")
            parts += if (ratePaise != null) "$p × ${rupees(ratePaise)}" else p
        }
        if (hours != null) {
            val h = t("${num(hours)} घंटे", "${num(hours)} ଘଣ୍ଟା", "${num(hours)} hours")
            parts += if (pieces == null && ratePaise != null) "$h × ${rupees(ratePaise)}" else h
        }
        return parts.joinToString(" · ")
    }

    /** What the phone says before the worker confirms. */
    fun readback(entries: List<DraftEntry>): String {
        val sb = StringBuilder()
        for (e in entries) {
            when (e.type) {
                EntryType.WORK -> {
                    e.hours?.let { sb.append(t("${num(it)} घंटे काम। ", "${num(it)} ଘଣ୍ଟା କାମ। ", "${num(it)} hours of work. ")) }
                    e.pieces?.let { sb.append(t("$it पीस। ", "$it ପିସ୍। ", "$it pieces. ")) }
                    e.ratePaise?.let { sb.append(t("रेट ${spokenRupees(it)}। ", "ଦର ${spokenRupees(it)}। ", "Rate ${spokenRupees(it)}. ")) }
                    if (e.amountPaise > 0) {
                        sb.append(t("कमाई ${spokenRupees(e.amountPaise)}। ", "ରୋଜଗାର ${spokenRupees(e.amountPaise)}। ", "Earned ${spokenRupees(e.amountPaise)}. "))
                    }
                }
                EntryType.ADVANCE -> sb.append(t("एडवांस लिया ${spokenRupees(e.amountPaise)}। ", "ଆଡଭାନ୍ସ ନେଲେ ${spokenRupees(e.amountPaise)}। ", "Advance taken ${spokenRupees(e.amountPaise)}. "))
                EntryType.PAYMENT -> sb.append(t("मजदूरी मिली ${spokenRupees(e.amountPaise)}। ", "ମଜୁରୀ ମିଳିଲା ${spokenRupees(e.amountPaise)}। ", "Wages received ${spokenRupees(e.amountPaise)}. "))
                EntryType.EXPENSE -> {
                    val c = categoryName(e.category ?: ExpenseCategory.OTHER)
                    sb.append(t("$c पर खर्च ${spokenRupees(e.amountPaise)}। ", "$c ପାଇଁ ଖର୍ଚ୍ଚ ${spokenRupees(e.amountPaise)}। ", "Spent ${spokenRupees(e.amountPaise)} on $c. "))
                }
            }
        }
        sb.append(isThisRight)
        return sb.toString()
    }

    private fun spokenPeriod(p: QueryPeriod): String = when (p) {
        QueryPeriod.DAY -> t("आज", "ଆଜି", "Today")
        QueryPeriod.WEEK -> t("इस हफ्ते", "ଏହି ସପ୍ତାହରେ", "This week")
        QueryPeriod.MONTH -> t("इस महीने", "ଏହି ମାସରେ", "This month")
        QueryPeriod.YEAR -> t("इस साल", "ଏହି ବର୍ଷ", "This year")
    }

    /** The spoken report for a period. */
    fun summary(period: QueryPeriod, s: Totals): String {
        val p = spokenPeriod(period)
        return t(
            "$p आपने ${spokenRupees(s.earnedPaise)} कमाए। एडवांस लिया ${spokenRupees(s.advancePaise)}। मजदूरी मिली ${spokenRupees(s.paidPaise)}। खर्च किया ${spokenRupees(s.expensePaise)}। मालिक पर बाकी है ${spokenRupees(s.duePaise)}।",
            "$p ଆପଣ ${spokenRupees(s.earnedPaise)} ରୋଜଗାର କଲେ। ଆଡଭାନ୍ସ ନେଲେ ${spokenRupees(s.advancePaise)}। ମଜୁରୀ ମିଳିଲା ${spokenRupees(s.paidPaise)}। ଖର୍ଚ୍ଚ କଲେ ${spokenRupees(s.expensePaise)}। ମାଲିକ ପାଖରେ ବାକି ଅଛି ${spokenRupees(s.duePaise)}।",
            "$p you earned ${spokenRupees(s.earnedPaise)}. Advance taken ${spokenRupees(s.advancePaise)}. Wages received ${spokenRupees(s.paidPaise)}. Spent ${spokenRupees(s.expensePaise)}. Your employer still owes you ${spokenRupees(s.duePaise)}.",
        )
    }

    /** The spoken answer to a voice question. */
    fun answer(q: Query, s: Totals): String {
        val p = spokenPeriod(q.period)
        return when (q.topic) {
            QueryTopic.ALL, QueryTopic.MIN_WAGE -> summary(q.period, s)
            QueryTopic.EARNED -> t("$p आपने ${spokenRupees(s.earnedPaise)} कमाए।", "$p ଆପଣ ${spokenRupees(s.earnedPaise)} ରୋଜଗାର କଲେ।", "$p you earned ${spokenRupees(s.earnedPaise)}.")
            QueryTopic.ADVANCE -> t("$p आपने ${spokenRupees(s.advancePaise)} एडवांस लिया।", "$p ଆପଣ ${spokenRupees(s.advancePaise)} ଆଡଭାନ୍ସ ନେଲେ।", "$p you took ${spokenRupees(s.advancePaise)} in advances.")
            QueryTopic.EXPENSE -> t("$p आपने ${spokenRupees(s.expensePaise)} खर्च किए।", "$p ଆପଣ ${spokenRupees(s.expensePaise)} ଖର୍ଚ୍ଚ କଲେ।", "$p you spent ${spokenRupees(s.expensePaise)}.")
            QueryTopic.DUE -> t("$p का बाकी ${spokenRupees(s.duePaise)} है।", "$p ବାକି ${spokenRupees(s.duePaise)} ଅଛି।", "$p ${spokenRupees(s.duePaise)} is still due to you.")
        }
    }

    fun minWageSpoken(rate: WageRate): String {
        val amount = spokenRupees(Money.rupeesToPaise(rate.dailyRupees))
        return t(
            "${rate.skill} काम की सरकारी न्यूनतम मजदूरी $amount प्रति दिन है।",
            "${rate.skill} କାମର ସରକାରୀ ସର୍ବନିମ୍ନ ମଜୁରୀ ଦିନକୁ $amount।",
            "The official minimum wage for ${rate.skill} work is $amount per day.",
        )
    }

    fun goalProgress(savedPaise: Long, goalPaise: Long): String = t(
        "इस महीने ${rupees(savedPaise)} बचे, लक्ष्य ${rupees(goalPaise)}",
        "ଏହି ମାସ ${rupees(savedPaise)} ବଳିଲା, ଲକ୍ଷ୍ୟ ${rupees(goalPaise)}",
        "${rupees(savedPaise)} left this month, goal ${rupees(goalPaise)}",
    )

    /** Advice for irregular income: how much to put aside per working day left this month. */
    fun goalAdvice(perDayPaise: Long): String = t(
        "लक्ष्य के लिए हर काम के दिन ${rupees(perDayPaise)} अलग रखें।",
        "ଲକ୍ଷ୍ୟ ପାଇଁ ପ୍ରତି କାମ ଦିନ ${rupees(perDayPaise)} ଅଲଗା ରଖନ୍ତୁ।",
        "To reach your goal, put aside ${rupees(perDayPaise)} on each working day.",
    )
    val goalReached get() = t("शाबाश! इस महीने का लक्ष्य पूरा।", "ସାବାସ୍! ଏହି ମାସର ଲକ୍ଷ୍ୟ ପୂରା ହେଲା।", "Well done! This month's goal is reached.")
}

/** Yes/no words for confirming by voice. */
object Confirm {
    private val yes = setOf("हां", "हाँ", "जी", "सही", "ठीक", "haan", "han", "yes", "sahi", "theek", "ok", "ହଁ", "ଠିକ୍", "ଠିକ").map(Text::normalize).toSet()
    private val no = setOf("नहीं", "नही", "गलत", "ना", "nahi", "nahin", "no", "galat", "wrong", "ନା", "ନାହିଁ", "ଭୁଲ୍", "ଭୁଲ").map(Text::normalize).toSet()

    /** true = yes, false = no, null = neither. "No" wins when both are said. */
    fun parse(transcript: String): Boolean? {
        val tokens = Text.tokens(transcript)
        return when {
            tokens.any { it in no } -> false
            tokens.any { it in yes } -> true
            else -> null
        }
    }
}
