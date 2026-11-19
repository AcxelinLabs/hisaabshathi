package com.acxelin.hisaabsaathi.core

/**
 * Words workers use when they ask the app about their records, in Hindi (Devanagari and
 * Latin letters), Odia, Telugu, Kannada, Tamil and Indian English: everyday and local words
 * (dihadi, peshgi, hajira, dadan, sardar, mestri, mungada, munpanam), English words mixed in
 * (income, pending, report), and the verb forms that carry the meaning ("money went" =
 * spent, "took from the contractor" = advance, "has to give me" = still owed).
 *
 * Used only to understand questions; ledger entries keep their own word lists, so nothing
 * here can change how an amount is read.
 */
internal object QuestionWords {
    /** Words, and word roots marked with "*" that take any ending ("कमा*": कमाई, कमाया, कमाए...). */
    private fun words(vararg w: String): Keywords {
        val all = w.flatMap { it.split(' ') }.filter { it.isNotBlank() }
        return Keywords(
            all.filterNot { it.endsWith("*") }.map { Text.normalize(it) }.toSet(),
            all.filter { it.endsWith("*") }.map { Text.normalize(it.dropLast(1)) },
        )
    }

    /** "how much", "how many", "what": a question by itself. */
    val howMuch = words(
        "कितना कितनी कितने कितनो कित्ता कितेक kitna kitni kitne kitta how what kya-kya",
        "କେତେ କେତେଟା କେତେକ କେତେଦୂର କଣ",
        "ఎంత ఎంతా ఎన్ని ఎంతయింది ఎంతైంది ఎంతుంది ఎంతో ఏంటి ఏమిటి",
        "ಎಷ್ಟು ಎಷ್ಟ ಎಷ್ಟಾಯ್ತು ಎಷ್ಟಾಯಿತು ಎಷ್ಟಾಗಿದೆ ಎಷ್ಟಿದೆ ಎಷ್ಟಾಯ್ತ ಏನು",
        "எவ்வளவு எவ்ளோ எவ்வளோ எவ்ளவு எத்தனை எத்தன என்ன எம்புட்டு",
    )

    /** "what": asks, but not for a quantity ("ଆଜି ଖାଇବାକୁ କଣ ଅଛି", what is there to eat today). */
    val what = words("क्या kya what କଣ କି ఏంటి ఏమిటి ఏం ಏನು ಏನ್ என்ன")

    /** A price: "पेट्रोल का दाम कितना है" asks what something costs, not what the worker spent. */
    val price = words("दाम कीमत भाव रेट price prices cost rate ଦାମ ଦର ରେଟ ధర రేటు రేట్ ಬೆಲೆ ದರ ರೇಟ್ விலை ரேட் ரேட்டு")

    /** Clock time: "कितने बजे", "what time", "ఎన్ని గంటలకి" ask when, not how much. */
    val clock = words("बजे बजा बज baje time timing ଟାଇମ ବେଳେ గంటలకి గంటకి టైం టైమ్ ಗಂಟೆಗೆ ಟೈಮ್ மணிக்கு டைம்")

    /** "tell me", "show", "read out": a request for the records. */
    val request = words(
        "बताओ बताना बताइए बताइये बताएं बतायें बताओना बता बतादो सुनाओ सुनाना सुनाइए दिखाओ दिखाना दिखाइए दिखा सुना batao batana bata dikhao sunao",
        "tell show give read say",
        "କୁହ କହ କହିଲ କହନ୍ତୁ କହିବ ଦେଖାଅ ଦେଖାନ୍ତୁ ଶୁଣାଅ ଶୁଣାନ୍ତୁ ଦିଅ",
        "చెప్పు చెప్పండి చెప్పవా చూపించు చూపించండి చూపు వినిపించు ఇవ్వు",
        "ಹೇಳು ಹೇಳಿ ಹೇಳ್ರಿ ತೋರಿಸು ತೋರಿಸಿ ಕೊಡು ಕೊಡಿ",
        "சொல்லு சொல்லுங்க சொல்லுங்கள் காட்டு காட்டுங்க காமி குடு கொடு",
    )

    /** "account", "report", "summary": the records themselves. */
    val ledger = words(
        "हिसाब हिसाब-किताब किताब लेखा जोखा लेखाजोखा ब्यौरा ब्योरा विवरण रिपोर्ट समरी खाता hisab hisaab hisaab-kitaab lekha jokha khata",
        "account accounts report summary details statement ledger picture overview breakdown",
        "ହିସାବ ହିସାବକିତାବ ଲେଖାଜୋଖା ଲେଖା ବିବରଣୀ ରିପୋର୍ଟ ଖାତା",
        "లెక్క లెక్కలు లెక్కా వివరాలు వివరం రిపోర్ట్ హిసాబ్ ఖాతా",
        "ಲೆಕ್ಕ ಲೆಕ್ಕಾ ವಿವರ ವಿವರಗಳು ರಿಪೋರ್ಟ್ ಹಿಸಾಬ್ ಖಾತೆ",
        "கணக்கு கணக்க கணக்கை விவரம் விவரங்கள் ரிப்போர்ட் ரிப்போர்ட்டு",
    )

    /** Yes/no and "where": a question only together with a ledger topic. */
    val weakAsk = words(
        "क्या कहाँ कहां किधर kya kahan kidhar where did does do is",
        "କି କେଉଁଠି କୁଆଡେ କୁଆଡ଼େ",
        "ఎక్కడ ఎక్కడికి",
        "ಎಲ್ಲಿ ಎಲ್ಲಿಗೆ",
        "எங்க எங்கே",
    )

    /** "money" as a noun: "where did the money go". */
    val moneyNoun = words(
        "पैसे पैसा पैसों रुपये रुपए रकम paise paisa money cash",
        "ଟଙ୍କା ପଇସା",
        "డబ్బు డబ్బులు రూపాయలు పైసలు",
        "ದುಡ್ಡು ಹಣ ರೂಪಾಯಿ",
        "காசு பணம் ரூபா ரூபாய்",
    )

    /** "went", "flew away", "got used up": money that was spent. */
    val went = words(
        "गए गया गई गये उड़ उड़े उड़ गया लगा लगे लग खत्म gaye gaya ude laga went go gone",
        "ଗଲା ଗଲେ ଗଲାଣି ସରିଗଲା",
        "పోయాయి పోయింది పోయినయి అయిపోయాయి ఖర్చైపోయాయి",
        "ಹೋಯ್ತು ಹೋಯಿತು ಹೋದವು ಖಾಲಿ",
        "போச்சு போயிடுச்சு போனது போச்சி காலி",
    )

    /** The person who pays or advances money. */
    val employer = words(
        "सेठ सेठजी ठेकेदार मालिक मुंशी मुनीम साहब जमादार मेट seth thekedar malik munshi sahab mistri",
        "contractor owner boss maistry mestri supervisor employer",
        "ଠିକାଦାର ମାଲିକ ସର୍ଦ୍ଦାର ସାହୁକାର ମୁନସି ମେଟ",
        "మేస్త్రి మేస్త్రీ ఓనర్ కాంట్రాక్టర్ యజమాని గుత్తేదారు సేట్",
        "ಮೇಸ್ತ್ರಿ ಮೇಸ್ತ್ರೀ ಓನರ್ ಕಂಟ್ರಾಕ್ಟರ್ ಮಾಲೀಕ ಮಾಲಿಕ ಸಾಹುಕಾರ ಧಣಿ",
        "மேஸ்திரி முதலாளி கான்ட்ராக்டர் ஓனர் சேட்டு கங்காணி",
    )

    /** "took", "drew", "borrowed": with the employer, an advance. */
    val took = words(
        "తీసుకున్న* ತಗೊಂಡ* ಇಸ್ಕೊಂಡ* வாங்கி* வாங்கின*",
        "लिया लिए ली ले लिये उठाया उठाए उठाई उठाये liya liye uthaya",
        "took take taken borrowed borrow drew",
        "ନେଲି ନେଇଛି ନେଇଥିଲି ନେଇଛନ୍ତି ଉଠାଇଲି",
        "తీసుకున్నాను తీసుకున్నా తీసుకున్నాం తీసుకున్న",
        "ತಗೊಂಡೆ ತಗೊಂಡಿದೀನಿ ತಗೊಂಡಿದ್ದೇನೆ ಇಸ್ಕೊಂಡೆ ತೆಗೆದುಕೊಂಡೆ ತಗೊಂಡ",
        "வாங்கினேன் வாங்குனேன் வாங்கிட்டேன் வாங்கியிருக்கேன்",
    )

    /** Advance, beyond the entry words: "peshgi", "dadan", taken "early". */
    val advance = words(
        "उधार* एडवांस* पेशगी* advanc* borrow* ଆଡଭାନ୍ସ* ଦାଦନ* అడ్వాన్స* అప్పు* ಅಡ್ವಾನ್ಸ* ಮುಂಗಡ* அட்வான்ஸ* முன்பண* கடன*",
        "पेशगी पेसगी एडवांस अडवांस उधार खर्ची बयाना अग्रिम peshgi advance udhar loan",
        "ଦାଦନ ଦାଦନି ଧାର ଆଗୁଆ ଆଡଭାନ୍ସ ଅଗ୍ରିମ",
        "అడ్వాన్స్ అప్పు ముందుగా ముందస్తు బయానా",
        "ಅಡ್ವಾನ್ಸ್ ಮುಂಗಡ ಸಾಲ ಕೈಸಾಲ",
        "அட்வான்ஸ் முன்பணம் கடன் கைமாத்து",
    )

    /** Still owed to the worker: "pending", "stuck", "has to give me". */
    val due = words(
        "बकाय* बाकी* pend* ବକେୟା* ବାକି* బాకీ* బకాయి* ಬಾಕಿ* பாக்கி* நிலுவை*",
        "बाकी बकाया फंसा फंसे फंसी रुका रुके रुकी अटका अटके पेंडिंग देना देंगे देने लेना baki baaki bakaya bakaaya lena dena pending fasa phasa ruka",
        "due balance owe owes owed stuck pending outstanding",
        "ବାକି ବକେୟା ଅଟକିଛି ଅଟକିଲା ଦେବେ ଦେବା",
        "బాకీ బాకి బకాయి బ్యాలెన్స్ రావాలి రావాల్సి రావాల్సింది ఇవ్వాలి ఆగిపోయాయి ఆగిపోయింది పెండింగ్",
        "ಬಾಕಿ ಬ್ಯಾಲೆನ್ಸ್ ಬರಬೇಕು ಬರಬೇಕಿದೆ ಬರೋದಿದೆ ಕೊಡಬೇಕು ನಿಂತಿದೆ ಉಳಿಕೆ ಪೆಂಡಿಂಗ್",
        "பாக்கி நிலுவை மீதி பேலன்ஸ் தரணும் தரவேண்டும் கொடுக்கணும் வரணும் வேண்டியிருக்கு நிக்குது பெண்டிங்",
    )

    /** Still owed, matched exactly: one letter turns "ପାଇବାକୁ" (to get) into "ଖାଇବାକୁ" (to eat). */
    val dueExact: Set<String> = setOf("ପାଇବା", "ପାଇବାକୁ", "ପାଇବାର").map { Text.normalize(it) }.toSet()

    /**
     * Words that carry no subject of their own ("my", "this", "tell me", "brother"): a
     * question made only of these, a period and "how much" ("इस हफ्ते कितना", "how much this
     * week") asks for the whole summary. Any other word ("how hot is it today") means it
     * asks about something else.
     */
    val filler = words(
        "का की के है हैं था थी हुआ हुई हुए हो गया गई लगा आया मेरा मेरी मेरे मैंने मैं मुझे में ने तो जी भाई अरे सुनो ना न अभी सब कुछ पूरा पूरी सारा तक भर दिन ज़रा जरा हम हमने हमारा इस यह ये",
        "ka ki ke hai hua hui meri mera maine mujhe mein ne to ji bhai are suno na abhi sab kuch pura bhar is",
        "how much many what did do i is it was my me this that the for of in on so far whole all total till now up to bro hey listen please everything",
        "ମୋ ମୋର ମୁଁ ମୋତେ ରେ ର ଟିକେ ଭାଇ ଆରେ ଶୁଣ ସବୁ ପୁରା ଏ ଏହି ଅଛି ହେଲା ହେଲାଣି ହୋଇଛି ଆଉ ଚଳିତ",
        "ఈ నా నేను నాకు అన్నా ఏయ్ విను మొత్తం అన్నీ అన్ని కొంచెం ఉంది ఉన్నాయి అయింది అయ్యింది ఐంది అవుతుంది",
        "ಈ ನನ್ನ ನಾನು ನನಗೆ ಅಣ್ಣಾ ಏ ಕೇಳು ಎಲ್ಲಾ ಪೂರ್ತಿ ಸ್ವಲ್ಪ ಇದೆ ಆಯ್ತು ಆಯಿತು ಆಗಿದೆ ಆಗುತ್ತೆ",
        "இந்த என் நான் எனக்கு அண்ணா ஏய் கேளு எல்லாம் முழு கொஞ்சம் இருக்கு ஆச்சு ஆனது ஆகியிருக்கு ஆகுது",
    )

    /** "Not paid": "मालिक ने नहीं दिया". */
    val not = words("नहीं नही नई not didn't ନାହିଁ ନାହି ଦେଇନି లేదు ಇಲ್ಲ கொடுக்கல இல்ல")

    /** "full", "all of it": with "got the wages", asks whether anything is still owed. */
    val full = words("पूरा पूरी पूरे सारा पुरा full all complete ପୁରା ସମ୍ପୂର୍ଣ୍ଣ మొత్తం పూర్తి ಪೂರ್ತಿ ಪೂರ್ತ ಎಲ್ಲಾ முழு முழுசா எல்லா")

    /** "got", "came", "received": wages that came in. */
    val got = words(
        "मिला मिली मिले मिल आया आए आई बने बना बनी mila mili aaya bana",
        "got get came received paid",
        "ମିଳିଲା ମିଳିଲେ ପାଇଲି ଆସିଲା ହେଲା",
        "వచ్చింది వచ్చాయి వచ్చిందా అందింది దొరికింది",
        "ಸಿಕ್ತು ಸಿಕ್ತಾ ಸಿಕ್ಕಿತು ಬಂತು ಬಂದಿದೆ",
        "கிடைச்சுது கிடைச்சுதா கிடைத்தது வந்துச்சு வந்தது",
    )

    /** Money earned, beyond the entry words: "income", "dihadi", "hajira". */
    val earned = words(
        "कमा* मजदूरी* दिहाड* तनख्वा* पगार* earn* salar* wage* ରୋଜଗାର* ଦରମା* ମଜୁରି* କମେଇ* କମାଇ* సంపాద* జీత* కూలి* ಸಂಪಾದ* ದುಡಿ* ಸಂಬಳ* ಕೂಲಿ* சம்பாதி* சம்பாத்திய* சம்பள* கூலி*",
        "कमाई कमाया कमाए कमा कमाने इनकम आमदनी आय मजदूरी मजूरी मजुरी दिहाड़ी दिहाडी तनख्वाह पगार वेतन kamai kamaya income dihadi",
        "earn earned earning earnings income salary wage wages make made",
        "ରୋଜଗାର ଇନକମ୍ ଇନକମ ମଜୁରି ମଜୁରୀ ହାଜିରା ଦରମା କମେଇଲି କମାଇଲି ଆୟ",
        "సంపాదన సంపాదించాను సంపాదించా సంపాదించానో ఇన్కమ్ కూలి జీతం వేతనం ఆదాయం",
        "ಸಂಪಾದನೆ ದುಡಿದೆ ದುಡಿಮೆ ಇನ್ಕಮ್ ಕೂಲಿ ಸಂಬಳ ಆದಾಯ ಗಳಿಕೆ",
        "சம்பாதிச்சேன் சம்பாதிச்சேன்னு வருமானம் இன்கம் சம்பளம் கூலி ஊதியம்",
    )

    /** Spent, beyond the entry words. */
    val spent = words(
        "खर्च* spend* expens* ଖର୍ଚ୍ଚ* ଖର୍ଚ* ఖర్చ* ಖರ್ಚ* செலவ*",
        "खर्च खर्चा खर्चे खर्ची kharcha spend spent spending expense expenses",
        "ଖର୍ଚ୍ଚ ଖର୍ଚ ଖରଚ",
        "ఖర్చు ఖర్చులు",
        "ಖರ್ಚು ಖರ್ಚಾಯ್ತು ವೆಚ್ಚ",
        "செலவு செலவுகள்",
    )

    /** Sent home, bus fare: spending words the entry categories may lack. */
    val spendingThings = words(
        "भेज* भिजवा* ପଠା* ପଠେ* పంప* ಕಳಿಸ* ಕಳುಹಿಸ* அனுப்ப*",
        "भेजा भेजे भेजी भाड़े भाड़ा किराए किराया खाने पीने fare fares send sent food",
        "ପଠାଇଲି ଭଡାରେ ଭଡା ଖାଇବାରେ",
        "పంపించాను చార్జీలకి తిండికి",
        "ಕಳಿಸಿದೆ ಚಾರ್ಜ್‌ಗೆ ಊಟಕ್ಕೆ",
        "அனுப்பினேன் சார்ஜுக்கு சாப்பாட்டுக்கு",
    )

    /** "government", "official": with a wage, the minimum wage. */
    val government = words(
        "सरकारी सरकार सरकारि गवर्नमेंट sarkari sarkar government govt official",
        "ସରକାରୀ ସରକାର",
        "ప్రభుత్వ ప్రభుత్వం సర్కారు సర్కార్ గవర్నమెంట్",
        "ಸರ್ಕಾರ ಸರ್ಕಾರದ ಸರ್ಕಾರಿ ಗವರ್ನಮೆಂಟ್",
        "அரசாங்க அரசாங்கம் அரசு அரசின் கவர்ன்மென்ட் கவர்மெண்ட்",
    )

    val minimum = words(
        "न्यूनतम मिनिमम minimum ସର୍ବନିମ୍ନ ମିନିମମ୍ ମିନିମମ କନିଷ୍ଠ కనీస మినిమం మినిమమ్ ಕನಿಷ್ಠ ಕನಿಷ್ಟ ಮಿನಿಮಮ್",
        "குறைந்தபட்ச குறைந்தபட்சம் குறைஞ்சபட்ச குறைஞ்சபட்சம் மினிமம்",
    )

    /** Periods, each with every form the word takes ("this week", "this week's", "in this week"). */
    val day = words(
        "आज आजका आज़ aaj today todays",
        "ଆଜି ଆଜିର ଆଜିକା ଆଜିକାର",
        "ఈరోజు ఇవాళ ఇవ్వాళ ఇవాళ్టి ఈవేళ నేడు ఈరోజున ఈరోజుది ఈనాటి",
        "ಇವತ್ತು ಈವತ್ತು ಇವತ್ ಇಂದು ಇವತ್ತಿನ ಇಂದಿನ ಈದಿನ",
        "இன்று இன்னைக்கு இன்னிக்கு இன்றைக்கு இன்றைய இன்னைக்கி",
    )
    val week = words(
        "हफ्ते हफ्ता हफ़्ते सप्ताह हप्ते हप्ता वीक hafte hafta week weeks weekly",
        "ସପ୍ତାହ ସପ୍ତାହରେ ସପ୍ତାହର ହପ୍ତା ହପ୍ତାରେ ହପ୍ତାର ୱିକ ୱିକରେ ୱିକର ଉଇକ",
        "వారం వారంలో వారము వారానికి వారంలోనూ వీక్",
        "ವಾರ ವಾರದ ವಾರದಲ್ಲಿ ವಾರಕ್ಕೆ ವೀಕ್",
        "வாரம் வார வாரத்து வாரத்தில் வாரத்துல வாரத்தில வாரத்துக்கு வாரமா வீக்",
    )
    val month = words(
        "महीने महीना माह मंथ मास महिने mahine mahina month months",
        "ମାସ ମାସରେ ମାସର ମାସକ",
        "నెల నెలలో నెలకు నెలకి మాసం మంత్",
        "ತಿಂಗಳು ತಿಂಗಳ ತಿಂಗಳಲ್ಲಿ ತಿಂಗಳಿನಲ್ಲಿ ತಿಂಗಳಿಗೆ ತಿಂಗ್ಳು",
        "மாதம் மாத மாசம் மாச மாசத்து மாதத்தில் மாசத்துல மாசத்தில மாசத்துக்கு மாதத்துக்கு",
    )
    val year = words(
        "साल वर्ष बरस बरस भर सालभर saal baras year years",
        "ବର୍ଷ ବର୍ଷରେ ବର୍ଷର ସାଲ",
        "సంవత్సరం సంవత్సరంలో సంవత్సరానికి ఏడాది ఏడాదిలో ఇయర్",
        "ವರ್ಷ ವರುಷ ವರ್ಷದ ವರ್ಷದಲ್ಲಿ ವರ್ಷಕ್ಕೆ",
        "வருடம் வருட வருஷம் வருஷ வருசம் ஆண்டு வருடத்தில் வருஷத்துல வருசத்துல ஆண்டில் வருஷத்து",
    )

    /** "Last" right before a week or month word: last week, last month. */
    val last = words(
        "पिछले पिछला पिछली pichhle pichle last previous past",
        "ଗଲା ଗତ ପୂର୍ବ",
        "పోయిన గత క్రితం",
        "ಹೋದ ಕಳೆದ ಹಿಂದಿನ",
        "போன கடந்த போன",
    )

    /** Yesterday, in a question. */
    val yesterday = words(
        "कल कल्ह kal yesterday yesterdays",
        "କାଲି କାଲିର ଗତକାଲି ଗତକାଲିର",
        "నిన్న నిన్నటి",
        "ನಿನ್ನೆ ನೆನ್ನೆ ನಿನ್ನೆಯ",
        "நேற்று நேத்து நேத்திக்கு நேற்றைக்கு நேற்றைய",
    )

    /** "made": with money, earned ("ಎಷ್ಟು ದುಡ್ಡು ಮಾಡಿದೆ", how much money did I make). */
    val made = words("बनाए बनाया कमाए made make ମିଳିଲା ତିଆରି సంపాదించా చేశాను ಮಾಡಿದೆ ಮಾಡ್ದೆ ಮಾಡಿದ್ದೀನಿ சம்பாதிச்சேன் பண்ணேன்")

    /** Work words: "how much work did I do" asks about earnings. */
    val work = words("काम kaam work କାମ పని పనికి పనిచేసి ಕೆಲಸ ಕೆಲಸದಿಂದ ಕೆಲಸ ಮಾಡಿ வேலை வேலைக்கு")
}
