package com.acxelin.hisaabsaathi.core

/**
 * Every word the app shows or speaks, in Hindi, Odia and English. Kept in code (not Android
 * resources) so the spoken sentences can be unit-tested and the language can be switched
 * inside the app without touching the phone's system language.
 *
 * The Odia text is best-effort and must be reviewed by a native speaker before the pilot.
 */
class Strings(val lang: Lang) {
    /** Telugu, Kannada and Tamil text lives in the i18n tables, keyed by the English text. */
    private val extra: Map<String, String> = when (lang) {
        Lang.TE -> com.acxelin.hisaabsaathi.core.i18n.Te.strings
        Lang.KN -> com.acxelin.hisaabsaathi.core.i18n.Kn.strings
        Lang.TA -> com.acxelin.hisaabsaathi.core.i18n.Ta.strings
        else -> emptyMap()
    }

    private fun t(hi: String, or: String, en: String): String = when (lang) {
        Lang.HI -> hi
        Lang.OR -> or
        Lang.EN -> en
        else -> extra[en] ?: en.also { missing += it }
    }

    /** Like [t] for sentences with values in them: [key] is the English text with {0}, {1}… placeholders. */
    private fun tp(hi: String, or: String, en: String, key: String, vararg args: Any): String = when (lang) {
        Lang.HI -> hi
        Lang.OR -> or
        Lang.EN -> en
        else -> {
            var out = extra[key] ?: return en.also { missing += key }
            args.forEachIndexed { i, a -> out = out.replace("{$i}", a.toString()) }
            out
        }
    }

    /** English keys that had no translation; checked by a test. */
    val missing = LinkedHashSet<String>()

    // ---- general
    val appName get() = t("हिसाब साथी", "ହିସାବ ସାଥୀ", "HisaabSaathi")
    val welcome get() = t("आपके हिसाब साथी में आपका स्वागत है", "ଆପଣଙ୍କ ହିସାବ ସାଥୀକୁ ସ୍ୱାଗତ", "Welcome to your HisaabSaathi")
    val next get() = t("आगे बढ़ें", "ଆଗକୁ ଯିବା", "Next step")
    val done get() = t("हो गया", "ହୋଇଗଲା", "Done")
    val cancel get() = t("रद्द करें", "ବାତିଲ୍", "Cancel")
    val delete get() = t("हटाएं", "ହଟାନ୍ତୁ", "Delete")
    val yes get() = t("हाँ", "ହଁ", "Yes")
    val no get() = t("नहीं", "ନା", "No")
    val share get() = t("भेजें", "ପଠାନ୍ତୁ", "Share")

    // ---- onboarding
    val chooseLanguage get() = t("अपनी भाषा चुनें", "ନିଜ ଭାଷା ବାଛନ୍ତୁ", "Choose your language")
    val voicePackTitle get() = t("आवाज़ पैक डाउनलोड करें", "ଭଏସ୍ ପ୍ୟାକ୍ ଡାଉନଲୋଡ୍ କରନ୍ତୁ", "Download the voice pack")
    val voicePackBody get() = t(
        "एक बार डाउनलोड करने के बाद ऐप बिना इंटरनेट के आपकी आवाज़ समझेगा। आपका हिसाब फोन से बाहर नहीं जाता।",
        "ଥରେ ଡାଉନଲୋଡ୍ କଲା ପରେ ଆପ୍ ବିନା ଇଣ୍ଟରନେଟ୍‌ରେ ଆପଣଙ୍କ କଥା ବୁଝିବ। ଆପଣଙ୍କ ହିସାବ ଫୋନ୍ ବାହାରକୁ ଯାଏ ନାହିଁ।",
        "After one download the app understands your voice with no internet. Your records never leave this phone.",
    )
    val voicePackReadAloud get() = t("साफ़ आवाज़ में सुनने के लिए", "ସଫା ସ୍ୱରରେ ଶୁଣିବା ପାଇଁ", "For a clear voice when reading aloud")
    val download get() = t("डाउनलोड करें", "ଡାଉନଲୋଡ୍ କରନ୍ତୁ", "Download")
    val downloading get() = t("डाउनलोड हो रहा है", "ଡାଉନଲୋଡ୍ ହେଉଛି", "Downloading")
    val installed get() = t("तैयार", "ପ୍ରସ୍ତୁତ", "Ready")
    val skipForNow get() = t("अभी नहीं", "ଏବେ ନୁହେଁ", "Not now")
    val downloadFailed get() = t("डाउनलोड नहीं हुआ। इंटरनेट देखें और फिर कोशिश करें।", "ଡାଉନଲୋଡ୍ ହେଲା ନାହିଁ। ଇଣ୍ଟରନେଟ୍ ଦେଖି ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ।", "Download failed. Check the internet and try again.")
    val start get() = t("चलिए, शुरू करें", "ଚାଲନ୍ତୁ, ଆରମ୍ଭ କରିବା", "Let's begin")

    // ---- onboarding: location, details, smart model (Odia, Telugu, Kannada, Tamil need native review)
    fun gettingVoiceReady(pct: Int) = tp("आवाज़ तैयार हो रही है… $pct%", "ସ୍ୱର ପ୍ରସ୍ତୁତ ହେଉଛି… $pct%", "Getting voice ready… $pct%", "Getting voice ready… {0}%", pct)
    val voiceReady get() = t("आवाज़ तैयार", "ସ୍ୱର ପ୍ରସ୍ତୁତ", "Voice ready")
    val detailsTitle get() = t("आपकी जानकारी", "ଆପଣଙ୍କ ବିବରଣୀ", "Your details")
    val detailsPrivacy get() = t(
        "आपकी जानकारी इसी फोन में ताले में रहती है। कोई और इसे नहीं देख सकता। कुछ भी अपलोड नहीं होता।",
        "ଆପଣଙ୍କ ବିବରଣୀ ଏହି ଫୋନ୍‌ରେ ତାଲା ପଡ଼ି ରହେ। ଆଉ କେହି ଦେଖିପାରିବେ ନାହିଁ। କିଛି ଅପଲୋଡ୍ ହୁଏ ନାହିଁ।",
        "Your details stay on this phone, locked. Nobody else can see them. Nothing is uploaded.",
    )
    val scanAadhaar get() = t("आधार का QR कोड स्कैन करें", "ଆଧାର QR କୋଡ୍ ସ୍କାନ୍ କରନ୍ତୁ", "Scan Aadhaar QR code")
    val scanAadhaarHint get() = t(
        "यह ज़रूरी नहीं है। कैमरा अपने आधार कार्ड के QR कोड पर रखें। सिर्फ़ नाम, उम्र, राज्य, ज़िला और पिन कोड रखे जाते हैं।",
        "ଏହା ଜରୁରୀ ନୁହେଁ। କ୍ୟାମେରାକୁ ଆପଣଙ୍କ ଆଧାର କାର୍ଡର QR କୋଡ୍ ଉପରେ ଧରନ୍ତୁ। କେବଳ ନାମ, ବୟସ, ରାଜ୍ୟ, ଜିଲ୍ଲା ଓ ପିନ୍ କୋଡ୍ ରଖାଯାଏ।",
        "This is optional. Point the camera at the QR code on your Aadhaar card. Only your name, age, state, district and PIN are kept.",
    )
    val fillMyself get() = t("खुद भरूँगा", "ମୁଁ ନିଜେ ଭରିବି", "Fill it myself")
    val choosePhoto get() = t("फोटो चुनें", "ଫଟୋ ବାଛନ୍ତୁ", "Choose a photo")
    val aadhaarFailed get() = t(
        "QR कोड पढ़ा नहीं गया। कार्ड को सीधा रखकर अच्छी रोशनी में फिर कोशिश करें, या खुद भरें।",
        "QR କୋଡ୍ ପଢ଼ି ହେଲା ନାହିଁ। କାର୍ଡକୁ ସିଧା ରଖି ଭଲ ଆଲୁଅରେ ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ, କିମ୍ବା ନିଜେ ଭରନ୍ତୁ।",
        "Could not read the QR code. Hold the card flat in good light and try again, or fill it yourself.",
    )
    val checkDetails get() = t("अपनी जानकारी देखें। कुछ गलत हो तो बदलें।", "ଆପଣଙ୍କ ବିବରଣୀ ଦେଖନ୍ତୁ। କିଛି ଭୁଲ୍ ଥିଲେ ବଦଳାନ୍ତୁ।", "Check your details. Change anything that is wrong.")
    val tryAgain get() = t("फिर कोशिश करें", "ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ", "Try again")
    val qName get() = t("आपका नाम क्या है?", "ଆପଣଙ୍କ ନାମ କଣ?", "What is your name?")
    val qAge get() = t("आपकी उम्र कितनी है?", "ଆପଣଙ୍କ ବୟସ କେତେ?", "How old are you?")
    // ---- language and place (setup and My details)
    val languageLater get() = t("भाषा आप कभी भी सेटिंग में बदल सकते हैं।", "ଭାଷା ଆପଣ ଯେକୌଣସି ସମୟରେ ସେଟିଂସ୍‌ରେ ବଦଳାଇ ପାରିବେ।", "You can change the language any time in Settings.")
    val pinHelp get() = t(
        "अपना पिन कोड लिखें या लोकेशन से भरें: राज्य और ज़िला अपने आप भर जाएंगे। या नीचे से चुनें।",
        "ଆପଣଙ୍କ ପିନ୍ କୋଡ୍ ଲେଖନ୍ତୁ କିମ୍ବା ଲୋକେସନ୍‌ରୁ ଭରନ୍ତୁ: ରାଜ୍ୟ ଓ ଜିଲ୍ଲା ନିଜେ ଭରିଯିବ। କିମ୍ବା ତଳୁ ବାଛନ୍ତୁ।",
        "Type your PIN code, or use your location: the state and district fill in by themselves. Or choose them below.",
    )
    val sayYesNoPrompt get() = t("सही है तो हाँ बोलिए, नहीं तो नहीं।", "ଠିକ୍ ଥିଲେ ହଁ କୁହନ୍ତୁ, ନଥିଲେ ନା।", "If it is right, say yes. If not, say no.")
    val orType get() = t("या यहाँ लिखें", "କିମ୍ବା ଏଠାରେ ଲେଖନ୍ତୁ", "Or type here")
    val speakNow get() = t("बोलिए", "କୁହନ୍ତୁ", "Speak now")
    val askRate get() = t("एक पीस का रेट कितना था?", "ଗୋଟିଏ ପିସ୍‌ର ରେଟ୍ କେତେ ଥିଲା?", "What was the rate for one piece?")
    fun askAmount(kind: String) = tp("$kind: कितने रुपये?", "$kind: କେତେ ଟଙ୍କା?", "$kind: how many rupees?", "{0}: how many rupees?", kind)
    val amountStillMissing get() = t("रकम समझ नहीं आई। नीचे लिख दीजिए, या फिर से बोलिए।", "ଟଙ୍କା ବୁଝିହେଲା ନାହିଁ। ତଳେ ଲେଖିଦିଅନ୍ତୁ, କିମ୍ବା ପୁଣି କୁହନ୍ତୁ।", "I didn't get the amount. Type it below, or say it again.")
    val sayYesNo get() = t("बोलकर हाँ या नहीं कहें", "କହି ହଁ କିମ୍ବା ନା କୁହନ୍ତୁ", "Say yes or no")
    val fillFromAadhaar get() = t("आधार कार्ड से भरें", "ଆଧାର କାର୍ଡରୁ ଭରନ୍ତୁ", "Fill from Aadhaar card")
    val noPin get() = t("पिन नहीं पता?", "ପିନ୍ ଜଣା ନାହିଁ?", "No PIN?")
    val shareCardTitle get() = t("ऐप को अपनी बोली सिखाएंगे?", "ଆପ୍‌କୁ ନିଜ କହିବା ଶୈଳୀ ଶିଖାଇବେ?", "Teach the app the way you speak?")
    val shareCardLine get() = t(
        "हाँ कहें तो आप ऐप से जो बोलते हैं, वह बिना नाम के टीम को भेजा जाएगा। सेटिंग में कभी भी बंद कर सकते हैं।",
        "ହଁ କହିଲେ, ଆପଣ ଆପ୍‌କୁ ଯାହା କୁହନ୍ତି ତାହା ନାମ ବିନା ଟିମ୍‌କୁ ପଠାଯିବ। ସେଟିଂସ୍‌ରେ ଯେକୌଣସି ସମୟରେ ବନ୍ଦ କରିପାରିବେ।",
        "If you say yes, what you say to the app is sent to the team without your name. You can stop it any time in Settings.",
    )
    // ---- spoken guides: on each screen the app says what is there and which button does what,
    // by colour and place (many workers cannot read the labels).
    val guideHome get() = t(
        "बड़ा पीला बटन दबाकर आज का काम या खर्च बोलिए। बिल या पर्ची की फोटो के लिए उसके नीचे सफ़ेद बटन दबाइए। सबसे नीचे दूसरा बटन आपका खाता दिखाता है, और तीसरा कुल हिसाब।",
        "ବଡ଼ ହଳଦିଆ ବଟନ୍ ଦବାଇ ଆଜିର କାମ ବା ଖର୍ଚ୍ଚ କୁହନ୍ତୁ। ବିଲ୍ ବା ସ୍ଲିପ୍‌ର ଫଟୋ ପାଇଁ ତା ତଳେ ଧଳା ବଟନ୍ ଦବାନ୍ତୁ। ସବୁଠୁ ତଳେ ଦ୍ୱିତୀୟ ବଟନ୍ ଆପଣଙ୍କ ଖାତା ଦେଖାଏ, ଆଉ ତୃତୀୟଟି ମୋଟ ହିସାବ।",
        "Press the big yellow button and tell me today's work or spending. For a photo of a bill or slip, press the white button below it. At the very bottom, the second button shows your ledger, and the third your totals.",
    )
    val guideLedger get() = t(
        "यह आपका खाता है, दिन के हिसाब से। किसी दिन को दबाकर उसकी लाइनें देखिए, और किसी लाइन को बदलने के लिए उसे दबाइए। वापस जाने के लिए सबसे नीचे पहला बटन दबाइए।",
        "ଏହା ଆପଣଙ୍କ ଖାତା, ଦିନ ଅନୁସାରେ। କୌଣସି ଦିନକୁ ଦବାଇ ତାର ଧାଡ଼ି ଦେଖନ୍ତୁ, ଏବଂ କୌଣସି ଧାଡ଼ି ବଦଳାଇବାକୁ ତାକୁ ଦବାନ୍ତୁ। ଫେରିବାକୁ ସବୁଠୁ ତଳେ ପ୍ରଥମ ବଟନ୍ ଦବାନ୍ତୁ।",
        "This is your ledger, day by day. Press a day to see its lines, and press a line to change it. To go back, press the first button at the very bottom.",
    )
    val guideSummary get() = t(
        "यह आपका कुल हिसाब है। ऊपर आज, हफ्ता, महीना या साल चुनिए। सुनने के लिए पीला बटन दबाइए। वापस जाने के लिए सबसे नीचे पहला बटन दबाइए।",
        "ଏହା ଆପଣଙ୍କ ମୋଟ ହିସାବ। ଉପରେ ଆଜି, ସପ୍ତାହ, ମାସ ବା ବର୍ଷ ବାଛନ୍ତୁ। ଶୁଣିବାକୁ ହଳଦିଆ ବଟନ୍ ଦବାନ୍ତୁ। ଫେରିବାକୁ ସବୁଠୁ ତଳେ ପ୍ରଥମ ବଟନ୍ ଦବାନ୍ତୁ।",
        "These are your totals. At the top, choose today, week, month or year. To hear them, press the yellow button. To go back, press the first button at the very bottom.",
    )
    val guideType get() = t(
        "बॉक्स में लिखिए, जैसे 120 पीस, 5 रुपये रेट। फिर नीचे पीला बटन दबाइए।",
        "ବାକ୍ସରେ ଲେଖନ୍ତୁ, ଯେପରି 120 ପିସ୍, 5 ଟଙ୍କା ଦର। ତା ପରେ ତଳେ ହଳଦିଆ ବଟନ୍ ଦବାନ୍ତୁ।",
        "Write in the box, like 120 pieces, rate 5 rupees. Then press the yellow button below.",
    )
    val guideReview get() = t(
        "ये लाइनें देखिए। सही है तो हाँ बोलिए, या नीचे हरा बटन दबाइए। गलत है तो लाल बटन दबाइए।",
        "ଏହି ଧାଡ଼ିଗୁଡ଼ିକ ଦେଖନ୍ତୁ। ଠିକ୍ ଥିଲେ ହଁ କୁହନ୍ତୁ, କିମ୍ବା ତଳେ ସବୁଜ ବଟନ୍ ଦବାନ୍ତୁ। ଭୁଲ୍ ଥିଲେ ନାଲି ବଟନ୍ ଦବାନ୍ତୁ।",
        "Check these lines. If they are right, say yes, or press the green button at the bottom. If they are wrong, press the red button.",
    )
    val guideSettings get() = t(
        "यह सेटिंग है। सबसे ऊपर भाषा बदल सकते हैं। आवाज़ भेजना बंद करने का बटन नीचे है। वापस जाने के लिए ऊपर बाईं ओर तीर दबाइए।",
        "ଏହା ସେଟିଂସ୍। ସବୁଠୁ ଉପରେ ଭାଷା ବଦଳାଇ ପାରିବେ। ସ୍ୱର ପଠାଇବା ବନ୍ଦ କରିବା ବଟନ୍ ତଳେ ଅଛି। ଫେରିବାକୁ ଉପରେ ବାମ ପଟେ ତୀର ଦବାନ୍ତୁ।",
        "These are the settings. At the top you can change the language. The switch to stop sending your voice is further down. To go back, press the arrow at the top left.",
    )
    val guideProfile get() = t(
        "यह आपकी जानकारी है। बदलने के लिए नीचे पीला बटन दबाइए। वापस जाने के लिए ऊपर बाईं ओर तीर दबाइए।",
        "ଏହା ଆପଣଙ୍କ ବିବରଣୀ। ବଦଳାଇବାକୁ ତଳେ ହଳଦିଆ ବଟନ୍ ଦବାନ୍ତୁ। ଫେରିବାକୁ ଉପରେ ବାମ ପଟେ ତୀର ଦବାନ୍ତୁ।",
        "These are your details. To change them, press the yellow button at the bottom. To go back, press the arrow at the top left.",
    )
    /** Said (and shown) before the app puts itself away: a touch keeps it open. */
    val closingSoon get() = t("मैं अब बंद हो रहा हूँ। फिर खोलेंगे तो यहीं से शुरू होगा।", "ମୁଁ ଏବେ ବନ୍ଦ ହେଉଛି। ପୁଣି ଖୋଲିଲେ ଏଠାରୁ ଆରମ୍ଭ ହେବ।", "I'll close now. Open me again to carry on from here.")
    val touchToStay get() = t("रुकना हो तो स्क्रीन छुएं।", "ରହିବାକୁ ହେଲେ ସ୍କ୍ରିନ୍ ଛୁଅଁନ୍ତୁ।", "Touch the screen to stay.")
    val guideNext get() = t("आगे बढ़ने के लिए नीचे पीला बटन दबाइए।", "ଆଗକୁ ଯିବାକୁ ତଳେ ହଳଦିଆ ବଟନ୍ ଦବାନ୍ତୁ।", "To go on, press the yellow button at the bottom.")

    // "Help improve" is on by default: the worker is told once, on Home, aloud.
    val noticeTitle get() = t("आपकी आवाज़ से ऐप बेहतर बनता है", "ଆପଣଙ୍କ ସ୍ୱରରୁ ଆପ୍ ଉନ୍ନତ ହୁଏ", "Your voice helps improve the app")
    val noticeLine get() = t(
        "आप ऐप से जो बोलते हैं, उसकी रिकॉर्डिंग Wi-Fi पर हिसाब साथी टीम को जाती है, ताकि ऐप बेहतर समझे। आपका नाम, फ़ोन नंबर या आधार कभी नहीं भेजा जाता। सेटिंग में कभी भी बंद कर सकते हैं।",
        "ଆପଣ ଆପ୍‌କୁ ଯାହା କୁହନ୍ତି, ତାହାର ରେକର୍ଡିଂ Wi-Fi ରେ ହିସାବ ସାଥୀ ଟିମ୍‌କୁ ଯାଏ, ଯାହାଦ୍ୱାରା ଆପ୍ ଭଲ ବୁଝିବ। ଆପଣଙ୍କ ନାମ, ଫୋନ୍ ନମ୍ବର କିମ୍ବା ଆଧାର କେବେ ପଠାଯାଏ ନାହିଁ। ସେଟିଂସ୍‌ରେ ଯେକୌଣସି ସମୟରେ ବନ୍ଦ କରିପାରିବେ।",
        "Recordings of what you say to the app go to the HisaabSaathi team over Wi-Fi, so the app understands you better. Your name, phone number or Aadhaar are never sent. You can turn this off any time in Settings.",
    )
    val noticeOk get() = t("ठीक है", "ଠିକ୍ ଅଛି", "OK")
    val noticeOff get() = t("बंद करें", "ବନ୍ଦ କରନ୍ତୁ", "Turn off")
    val readAll get() = t("पूरी बात", "ସବୁ କଥା", "Read all")
    val useLocation get() = t("मेरी लोकेशन से भरें", "ମୋ ଲୋକେସନ୍‌ରୁ ଭରନ୍ତୁ", "Use my location")
    val findingPlace get() = t("आपकी जगह ढूँढ रहे हैं…", "ଆପଣଙ୍କ ଜାଗା ଖୋଜୁଛି…", "Finding your place…")
    val locationNote get() = t(
        "आपकी लोकेशन सिर्फ़ एक बार, इसी फोन में, पिन कोड ढूँढने के लिए इस्तेमाल होती है। यह कभी सेव या भेजी नहीं जाती।",
        "ଆପଣଙ୍କ ଲୋକେସନ୍ କେବଳ ଥରେ, ଏହି ଫୋନ୍‌ରେ, ପିନ୍ କୋଡ୍ ଖୋଜିବା ପାଇଁ ବ୍ୟବହାର ହୁଏ। ଏହା କେବେ ସେଭ୍ ହୁଏ ନାହିଁ କି ପଠାଯାଏ ନାହିଁ।",
        "Your location is used once, only on this phone, to find your PIN code. It is never saved or sent.",
    )
    val locationNotFound get() = t(
        "आपकी जगह नहीं मिली। अपना पिन कोड लिखें, या नीचे राज्य और ज़िला चुनें।",
        "ଆପଣଙ୍କ ଜାଗା ମିଳିଲା ନାହିଁ। ଆପଣଙ୍କ ପିନ୍ କୋଡ୍ ଲେଖନ୍ତୁ, କିମ୍ବା ତଳେ ରାଜ୍ୟ ଓ ଜିଲ୍ଲା ବାଛନ୍ତୁ।",
        "Could not find your place. Type your PIN code, or choose your state and district below.",
    )
    val locationCheckPin get() = t(
        "आपकी लोकेशन से भरा गया: पिन कोड एक बार जाँच लें।",
        "ଆପଣଙ୍କ ଲୋକେସନ୍‌ରୁ ଭରାଗଲା: ପିନ୍ କୋଡ୍ ଥରେ ଯାଞ୍ଚ କରନ୍ତୁ।",
        "Filled in from your location: please check the PIN code.",
    )
    val pinNotFound get() = t(
        "यह पिन कोड किसी जगह का नहीं मिला। अंक जाँचें, या नीचे राज्य और ज़िला चुनें।",
        "ଏହି ପିନ୍ କୋଡ୍ କୌଣସି ଜାଗାର ମିଳିଲା ନାହିଁ। ଅଙ୍କ ଯାଞ୍ଚ କରନ୍ତୁ, କିମ୍ବା ତଳେ ରାଜ୍ୟ ଓ ଜିଲ୍ଲା ବାଛନ୍ତୁ।",
        "No place has this PIN code. Check the digits, or choose your state and district below.",
    )
    val pinBadFormat get() = t(
        "पिन कोड में 6 अंक होते हैं और यह 1 से 8 के बीच किसी अंक से शुरू होता है।",
        "ପିନ୍ କୋଡ୍‌ରେ 6ଟି ଅଙ୍କ ଥାଏ ଓ ଏହା 1ରୁ 8 ମଧ୍ୟରୁ ଏକ ଅଙ୍କରେ ଆରମ୍ଭ ହୁଏ।",
        "A PIN code has 6 digits and starts with 1 to 8.",
    )
    val pinManyDistricts get() = t(
        "यह पिन कोड एक से ज़्यादा ज़िलों में है। नीचे अपना ज़िला चुनें।",
        "ଏହି ପିନ୍ କୋଡ୍ ଏକାଧିକ ଜିଲ୍ଲାରେ ଅଛି। ତଳେ ଆପଣଙ୍କ ଜିଲ୍ଲା ବାଛନ୍ତୁ।",
        "This PIN code is in more than one district. Choose yours below.",
    )
    fun pinFound(district: String, state: String) = tp("मिल गया: $district, $state", "ମିଳିଲା: $district, $state", "Found: $district, $state", "Found: {0}, {1}", district, state)
    val stateLabel get() = t("राज्य", "ରାଜ୍ୟ", "State")
    val credits get() = t("आभार", "କୃତଜ୍ଞତା", "Credits")
    /** The wage notification's sector, in the worker's language when it is the usual "all employments". */
    /** The kinds of work the wage data uses, in the worker's language. */
    fun sectorName(sector: String): String {
        val k = sector.lowercase()
        return when {
            k == "all scheduled employments" -> t("सभी अनुसूचित रोज़गार", "ସମସ୍ତ ଅନୁସୂଚିତ ନିଯୁକ୍ତି", "All scheduled employments")
            k.endsWith("scheduled employments") -> t("ज़्यादातर अनुसूचित रोज़गार", "ଅଧିକାଂଶ ଅନୁସୂଚିତ ନିଯୁକ୍ତି", "Most scheduled employments")
            k.startsWith("construction") -> t("निर्माण काम", "ନିର୍ମାଣ କାମ", "Construction work")
            k.startsWith("factories") -> t("कारखाने, वर्कशॉप, दुकानें और क्लिनिक", "କାରଖାନା, ୱର୍କସପ୍, ଦୋକାନ ଓ କ୍ଲିନିକ୍", "Factories, workshops, shops and clinics")
            k.startsWith("shops") -> t("दुकानें और प्रतिष्ठान", "ଦୋକାନ ଓ ପ୍ରତିଷ୍ଠାନ", "Shops and establishments")
            else -> sector
        }
    }
    val noMatch get() = t("कुछ नहीं मिला", "କିଛି ମିଳିଲା ନାହିଁ", "Nothing matches")
    // Address: asked, never required. Yes → the phone's location, or a few short questions.
    val qAddress get() = t("क्या आप अपना पता बताना चाहेंगे?", "ଆପଣ ନିଜ ଠିକଣା କହିବାକୁ ଚାହିଁବେ କି?", "Would you like to tell us your address?")
    val addressWhy get() = t(
        "इससे हम आपके राज्य की सही मज़दूरी बता पाएंगे।",
        "ଏହା ଦ୍ୱାରା ଆମେ ଆପଣଙ୍କ ରାଜ୍ୟର ଠିକ୍ ମଜୁରି କହିପାରିବୁ।",
        "Then we can tell you the right wage for your state.",
    )
    val qLocation get() = t("क्या फ़ोन की लोकेशन से पता भर दें?", "ଫୋନ୍‌ର ଲୋକେସନ୍‌ରୁ ଠିକଣା ଭରିଦେବୁ କି?", "Shall we fill it in from your phone's location?")
    val yesUseLocation get() = t("हाँ, लोकेशन से भरें", "ହଁ, ଲୋକେସନ୍‌ରୁ ଭରନ୍ତୁ", "Yes, use my location")
    val noTellMyself get() = t("नहीं, मैं खुद बताऊँ", "ନା, ମୁଁ ନିଜେ କହିବି", "No, I'll tell you")
    val qState get() = t("आप किस राज्य में रहते हैं?", "ଆପଣ କେଉଁ ରାଜ୍ୟରେ ରହନ୍ତି?", "Which state do you live in?")
    val qDistrict get() = t("आपका ज़िला कौन सा है?", "ଆପଣଙ୍କ ଜିଲ୍ଲା କେଉଁଟି?", "Which district are you in?")
    val qVillage get() = t("आपके गाँव या शहर का नाम क्या है?", "ଆପଣଙ୍କ ଗାଁ ବା ସହରର ନାମ କଣ?", "What is the name of your village or town?")
    val villageLabel get() = t("गाँव या शहर", "ଗାଁ ବା ସହର", "Village or town")
    val qPinIfKnown get() = t(
        "पिन कोड पता हो तो बताइए। नहीं पता, तो कोई बात नहीं।",
        "ପିନ୍ କୋଡ୍ ଜାଣିଥିଲେ କୁହନ୍ତୁ। ନ ଜାଣିଲେ କିଛି ଅସୁବିଧା ନାହିଁ।",
        "If you know your PIN code, say it. If not, that's fine.",
    )
    val qPinShort get() = t("पिन कोड पता है?", "ପିନ୍ କୋଡ୍ ଜାଣିଛନ୍ତି କି?", "Do you know your PIN code?")
    val pinNotNeeded get() = t("नहीं पता, तो कोई बात नहीं।", "ନ ଜାଣିଲେ କିଛି ଅସୁବିଧା ନାହିଁ।", "If not, that's fine.")
    val dontKnow get() = t("पता नहीं", "ଜାଣିନି", "I don't know")
    fun foundPlace(place: String): String = tp("मिल गया: $place", "ମିଳିଗଲା: $place", "Found it: $place", "Found it: {0}", place)
    val placeNotFoundAsk get() = t(
        "जगह नहीं मिली। कोई बात नहीं, चलिए आपसे पूछ लेते हैं।",
        "ଜାଗା ମିଳିଲା ନାହିଁ। କିଛି ଅସୁବିଧା ନାହିଁ, ଆପଣଙ୍କୁ ପଚାରି ନେଉଛୁ।",
        "Couldn't find your place. That's fine, let's ask you instead.",
    )
    val qPin get() = t("आपका पिन कोड क्या है? इसमें 6 अंक होते हैं।", "ଆପଣଙ୍କ ପିନ୍ କୋଡ୍ କଣ? ଏଥିରେ 6ଟି ଅଙ୍କ ଥାଏ।", "What is your PIN code? It has 6 digits.")
    val skip get() = t("अभी छोड़ें", "ଏବେ ଛାଡ଼ନ୍ତୁ", "Skip for now")
    val speakAnswer get() = t("दबाएं और जवाब बोलें", "ଦବାଇ ଉତ୍ତର କୁହନ୍ତୁ", "Tap and say your answer")
    val hearAgain get() = t("फिर से सुनें", "ପୁଣି ଶୁଣନ୍ତୁ", "Hear again")
    val adultsOnly get() = t("यह ऐप 18 साल और उससे ज़्यादा उम्र के लोगों के लिए है।", "ଏହି ଆପ୍ 18 ବର୍ଷ ଓ ତାଠାରୁ ଅଧିକ ବୟସର ଲୋକଙ୍କ ପାଇଁ।", "This app is for adults, 18 years and above.")
    val pinOtherState get() = t("यह पिन कोड किसी दूसरे राज्य का लगता है। कृपया जाँच लें।", "ଏହି ପିନ୍ କୋଡ୍ ଅନ୍ୟ ରାଜ୍ୟର ପରି ଲାଗୁଛି। ଦୟାକରି ଯାଞ୍ଚ କରନ୍ତୁ।", "This PIN code looks like another state's. Please check it.")
    val pinSixDigits get() = t("6 अंक डालें", "6ଟି ଅଙ୍କ ଦିଅନ୍ତୁ", "Enter 6 digits")
    val nameLabel get() = t("नाम", "ନାମ", "Name")
    val ageLabel get() = t("उम्र", "ବୟସ", "Age")
    val districtLabel get() = t("ज़िला", "ଜିଲ୍ଲା", "District")
    val pinLabel get() = t("पिन कोड", "ପିନ୍ କୋଡ୍", "PIN code")
    val notGiven get() = t("नहीं दिया", "ଦିଆଯାଇନି", "Not given")
    val modelTitle get() = t("समझदार मॉडल (चाहें तो)", "ସ୍ମାର୍ଟ ମଡେଲ୍ (ଇଚ୍ଛାଧୀନ)", "Smart model (optional)")
    fun modelBody(mb: Long) = tp(
        "यह ऐप को अधूरी या साफ़ न सुनी बात समझने में मदद करता है। करीब $mb MB, Wi-Fi पर बेहतर। इसके बिना भी ऐप चलता है।",
        "ଏହା ଆପ୍‌କୁ ଅସ୍ପଷ୍ଟ କଥା ବୁଝିବାରେ ସାହାଯ୍ୟ କରେ। ପ୍ରାୟ $mb MB, Wi-Fi ରେ ଭଲ। ଏହା ବିନା ମଧ୍ୟ ଆପ୍ ଚାଲେ।",
        "It helps the app understand unclear sentences. About $mb MB, best on Wi-Fi. The app works without it.",
        "It helps the app understand unclear sentences. About {0} MB, best on Wi-Fi. The app works without it.", mb,
    )
    val later get() = t("बाद में", "ପରେ", "Later")
    val waitForModel get() = t(
        "कृपया रुकें: समझदार मॉडल डाउनलोड हो रहा है। पूरा होने पर आगे बढ़ें।",
        "ଦୟାକରି ଅପେକ୍ଷା କରନ୍ତୁ: ସ୍ମାର୍ଟ ମଡେଲ୍ ଡାଉନଲୋଡ୍ ହେଉଛି। ଶେଷ ହେଲେ ଆଗକୁ ଯାଆନ୍ତୁ।",
        "Please wait: the smart model is downloading. You can go on when it is done.",
    )
    val myDetails get() = t("मेरी जानकारी", "ମୋ ବିବରଣୀ", "My details")
    val changeDetails get() = t("जानकारी बदलें", "ବିବରଣୀ ବଦଳାନ୍ତୁ", "Change my details")
    val showSetupAgain get() = t("शुरुआती सेटअप फिर दिखाएं", "ଆରମ୍ଭ ସେଟଅପ୍ ପୁଣି ଦେଖାନ୍ତୁ", "Show setup again")
    val qPhoto get() = t("अपनी फोटो लें", "ଆପଣଙ୍କ ଫଟୋ ନିଅନ୍ତୁ", "Take your photo")
    val photoHint get() = t(
        "फोन को अपने चेहरे के सामने रखें और सेल्फ़ी लें। फोटो सिर्फ़ इसी फोन में रहती है।",
        "ଫୋନ୍‌କୁ ମୁହଁ ସାମ୍ନାରେ ଧରି ସେଲ୍ଫି ନିଅନ୍ତୁ। ଫଟୋ କେବଳ ଏହି ଫୋନ୍‌ରେ ରହେ।",
        "Hold the phone in front of your face and take a selfie. The photo stays only on this phone.",
    )
    val openCamera get() = t("कैमरा खोलें", "କ୍ୟାମେରା ଖୋଲନ୍ତୁ", "Open camera")
    val takeAgain get() = t("फिर से लें", "ପୁଣି ନିଅନ୍ତୁ", "Take again")
    val photoLabel get() = t("फोटो", "ଫଟୋ", "Photo")
    val profileTitle get() = t("मेरी प्रोफ़ाइल", "ମୋ ପ୍ରୋଫାଇଲ୍", "My profile")
    val cameraNeeded get() = t("स्कैन करने के लिए कैमरा की अनुमति चाहिए।", "ସ୍କାନ୍ କରିବା ପାଇଁ କ୍ୟାମେରା ଅନୁମତି ଦରକାର।", "Camera permission is needed to scan.")
    val torch get() = t("लाइट", "ଲାଇଟ୍", "Light")
    val notAadhaarQr get() = t(
        "यह आधार का QR कोड नहीं है। आधार कार्ड या पत्र पर बड़ा QR कोड स्कैन करें।",
        "ଏହା ଆଧାରର QR କୋଡ୍ ନୁହେଁ। ଆଧାର କାର୍ଡ କିମ୍ବା ଚିଠିର ବଡ଼ QR କୋଡ୍ ସ୍କାନ୍ କରନ୍ତୁ।",
        "This is not the Aadhaar QR code. Scan the big QR code on the Aadhaar card or letter.",
    )

    // ---- home
    val today get() = t("आज", "ଆଜି", "Today")
    val yesterday get() = t("कल", "ଗତକାଲି", "Yesterday")
    val dayBefore get() = t("परसों", "ପଅରଦିନ", "Day before")
    val amountInvalid get() = t("सिर्फ़ रकम लिखें, जैसे 450", "କେବଳ ଟଙ୍କା ଲେଖନ୍ତୁ, ଯେପରି 450", "Type only the amount, like 450")
    val earned get() = t("कमाई", "ରୋଜଗାର", "Earned")
    val advance get() = t("एडवांस", "ଆଡଭାନ୍ସ", "Advance")
    val received get() = t("मिला", "ମିଳିଲା", "Received")
    val spent get() = t("खर्च", "ଖର୍ଚ୍ଚ", "Spent")
    val due get() = t("बाकी", "ବାକି", "Due")
    val tapAndSpeak get() = t("माइक दबाइए और आज का काम या खर्च बताइए", "ମାଇକ୍ ଦବାନ୍ତୁ ଓ ଆଜିର କାମ ବା ଖର୍ଚ୍ଚ କୁହନ୍ତୁ", "Tap the mic and tell me today's work or spending")
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
    val notUnderstood get() = t("माफ़ कीजिए, समझ नहीं आया। एक बार फिर बोलिए।", "କ୍ଷମା କରିବେ, ବୁଝିପାରିଲି ନାହିଁ। ଆଉ ଥରେ କୁହନ୍ତୁ।", "Sorry, I didn't catch that. Please say it once more.")
    val noSpeechEngine get() = t("आवाज़ पैक नहीं है। सेटिंग में डाउनलोड करें या लिखकर डालें।", "ଭଏସ୍ ପ୍ୟାକ୍ ନାହିଁ। ସେଟିଂରେ ଡାଉନଲୋଡ୍ କରନ୍ତୁ କିମ୍ବା ଲେଖି ଦିଅନ୍ତୁ।", "No voice pack yet. Download it in Settings, or type the entry.")

    val heardNothing get() = t("कुछ सुनाई नहीं दिया। माइक दबाकर फोन के पास बोलिए।", "କିଛି ଶୁଣାଗଲା ନାହିଁ। ମାଇକ୍ ଦବାଇ ଫୋନ୍ ପାଖରେ କୁହନ୍ତୁ।", "I didn't hear anything. Tap the mic and speak close to the phone.")
    val micFailed get() = t("माइक चालू नहीं हुआ। माइक इस्तेमाल करने वाले दूसरे ऐप बंद करके फिर कोशिश करें।", "ମାଇକ୍ ଚାଲୁ ହେଲା ନାହିଁ। ମାଇକ୍ ବ୍ୟବହାର କରୁଥିବା ଅନ୍ୟ ଆପ୍ ବନ୍ଦ କରି ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ।", "The microphone did not start. Close other apps using it and try again.")
    val voiceFailed get() = t("आवाज़ समझने में दिक्कत आई। फिर से कोशिश करें या लिखकर डालें।", "ସ୍ୱର ବୁଝିବାରେ ଅସୁବିଧା ହେଲା। ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ କିମ୍ବା ଲେଖି ଦିଅନ୍ତୁ।", "Something went wrong understanding your voice. Try again, or type instead.")
    val lastProblemLabel get() = t("पिछली दिक्कत (जाँच के लिए)", "ପୂର୍ବ ଅସୁବିଧା (ଯାଞ୍ଚ ପାଇଁ)", "Last problem (for testing)")

    // ---- edit, undo, backup, downloads
    val editEntry get() = t("बदलें", "ବଦଳାନ୍ତୁ", "Change entry")
    val change get() = t("बदलें", "ବଦଳାନ୍ତୁ", "Change")
    val entryRemoved get() = t("हटा दिया", "ହଟାଗଲା", "Entry removed")
    val undo get() = t("वापस लाएं", "ଫେରାନ୍ତୁ", "Undo")
    val earlierDay get() = t("पिछला दिन", "ପୂର୍ବ ଦିନ", "Earlier day")
    val laterDay get() = t("अगला दिन", "ପର ଦିନ", "Later day")
    val backupTitle get() = t("बैकअप (हिसाब की कॉपी)", "ବ୍ୟାକଅପ୍ (ହିସାବର କପି)", "Backup")
    val backupBody get() = t(
        "फोन खो जाए या बदलें तो हिसाब भी चला जाता है। एक कॉपी सेव करें (जैसे WhatsApp पर खुद को भेजें) और नए फोन पर वापस लाएं।",
        "ଫୋନ୍ ହଜିଗଲେ କିମ୍ବା ବଦଳାଇଲେ ହିସାବ ମଧ୍ୟ ଚାଲିଯାଏ। ଗୋଟିଏ କପି ସେଭ୍ କରନ୍ତୁ (ଯେପରି WhatsApp ରେ ନିଜକୁ ପଠାନ୍ତୁ) ଏବଂ ନୂଆ ଫୋନ୍‌ରେ ଫେରାନ୍ତୁ।",
        "If this phone is lost or changed, your records go with it. Save a copy (for example, send it to yourself on WhatsApp) and restore it on the new phone.",
    )
    val saveBackup get() = t("कॉपी सेव करें", "କପି ସେଭ୍ କରନ୍ତୁ", "Save a copy")
    val restoreBackup get() = t("कॉपी से वापस लाएं", "କପିରୁ ଫେରାନ୍ତୁ", "Restore from a copy")
    fun restored(n: Int) = tp("$n हिसाब जोड़े गए", "$n ଟି ହିସାବ ଯୋଡ଼ାଗଲା", "$n records added", "{0} records added", n)
    val restoreFailed get() = t("यह हिसाब साथी की कॉपी नहीं है।", "ଏହା ହିସାବ ସାଥୀର କପି ନୁହେଁ।", "This file is not a HisaabSaathi copy.")
    fun notEnoughSpace(mb: Long) = tp(
        "फोन में जगह कम है। $mb MB खाली करके फिर कोशिश करें।", "ଫୋନ୍‌ରେ ଜାଗା କମ୍। $mb MB ଖାଲି କରି ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ।",
        "Not enough space on the phone. Free $mb MB and try again.", "Not enough space on the phone. Free {0} MB and try again.", mb,
    )
    val waitingForInternet get() = t("इंटरनेट का इंतज़ार…", "ଇଣ୍ଟରନେଟ୍ ଅପେକ୍ଷା…", "Waiting for internet…")

    // ---- help improve the app (asked at setup; changed in Settings)
    val improveTitle get() = t("ऐप को बेहतर बनाने में मदद", "ଆପ୍‌କୁ ଉନ୍ନତ କରିବାରେ ସାହାଯ୍ୟ", "Help improve the app")
    val shareTitle get() = t("हिसाब साथी को बेहतर बनाने में मदद करेंगे?", "ହିସାବ ସାଥୀକୁ ଉନ୍ନତ କରିବାରେ ସାହାଯ୍ୟ କରିବେ?", "Help improve HisaabSaathi?")
    /** The consent question, shown and read aloud at setup. Says plainly what is kept, where it goes, and how to stop. */
    val shareBody get() = t(
        "अगर आप हाँ कहें, तो जब भी आप ऐप से बोलेंगे या लिखेंगे, ऐप आपकी आवाज़, सुनी हुई बात, और जो उसने लिखा या जवाब दिया (रकम समेत) रख लेगा। यह सब Wi-Fi पर हिसाब साथी टीम को भेजा जाएगा, आपके नाम या फोन नंबर के बिना, ताकि ऐप आप जैसे लोगों की बात बेहतर समझना सीखे। आपका हिसाब इसी फोन में रहेगा। आप कभी भी सेटिंग में इसे बंद कर सकते हैं या भेजा हुआ मिटा सकते हैं। साथ ही हर हफ्ते कितनी एंट्री सेव हुईं और ऐप ने कितनी बार गलत समझा, ये सिर्फ गिनती भेजी जाएगी।",
        "ଆପଣ ହଁ କହିଲେ, ଯେତେବେଳେ ଆପଣ ଆପ୍ ସହ କଥା ହେବେ କିମ୍ବା ଲେଖିବେ, ଆପ୍ ଆପଣଙ୍କ ସ୍ୱର, ଶୁଣିଥିବା କଥା, ଏବଂ ଯାହା ଲେଖିଲା କିମ୍ବା ଉତ୍ତର ଦେଲା (ଟଙ୍କା ସହିତ) ରଖିବ। ଏସବୁ Wi-Fi ରେ ହିସାବ ସାଥୀ ଟିମ୍‌କୁ ପଠାଯିବ, ଆପଣଙ୍କ ନାମ କିମ୍ବା ଫୋନ୍ ନମ୍ବର ବିନା, ଯାହାଦ୍ୱାରା ଆପ୍ ଆପଣଙ୍କ ପରି ଲୋକଙ୍କ କଥା ଭଲ ଭାବେ ବୁଝିବା ଶିଖିବ। ଆପଣଙ୍କ ହିସାବ ଏହି ଫୋନ୍‌ରେ ରହିବ। ଆପଣ ଯେକୌଣସି ସମୟରେ ସେଟିଂସ୍‌ରେ ଏହା ବନ୍ଦ କରିପାରିବେ କିମ୍ବା ପଠାଯାଇଥିବା ଲିଭାଇ ପାରିବେ। ସହିତ ପ୍ରତି ସପ୍ତାହରେ କେତେ ଏଣ୍ଟ୍ରି ସେଭ୍ ହେଲା ଓ ଆପ୍ କେତେ ଥର ଭୁଲ ବୁଝିଲା, କେବଳ ଏହି ଗଣନା ପଠାଯିବ।",
        "If you say yes, each time you speak or type to the app, it keeps your voice, the words it heard, and what it wrote down or answered, including amounts. It sends them over Wi-Fi to the HisaabSaathi team, without your name or phone number, so the app learns to understand people like you better. Your ledger stays on this phone. You can stop this, or delete what was sent, at any time in Settings. It also sends, each week, how many entries you saved and how often the app got them wrong: numbers only.",
    )
    val shareYes get() = t("हाँ, भेजें", "ହଁ, ପଠାନ୍ତୁ", "Yes, share")
    val shareNo get() = t("नहीं, धन्यवाद", "ନା, ଧନ୍ୟବାଦ", "No, thanks")
    val listen get() = t("सुनें", "ଶୁଣନ୍ତୁ", "Listen")
    val improveBody get() = t(
        "यह चालू हो तो आप ऐप से जो कहते हैं (आपकी आवाज़, सुनी हुई बात और ऐप का जवाब) Wi-Fi पर हिसाब साथी टीम को भेजा जाता है, आपके नाम के बिना, ताकि ऐप बेहतर समझे। आपका हिसाब इसी फोन में रहता है। साथ ही हर हफ्ते की गिनती: कितनी एंट्री सेव हुईं और ऐप ने कितनी बार गलत समझा।",
        "ଏହା ଚାଲୁ ଥିଲେ, ଆପଣ ଆପ୍‌କୁ ଯାହା କୁହନ୍ତି (ଆପଣଙ୍କ ସ୍ୱର, ଶୁଣିଥିବା କଥା ଓ ଆପ୍‌ର ଉତ୍ତର) Wi-Fi ରେ ହିସାବ ସାଥୀ ଟିମ୍‌କୁ ପଠାଯାଏ, ଆପଣଙ୍କ ନାମ ବିନା, ଯାହାଦ୍ୱାରା ଆପ୍ ଭଲ ବୁଝିବ। ଆପଣଙ୍କ ହିସାବ ଏହି ଫୋନ୍‌ରେ ରହେ। ସହିତ ପ୍ରତି ସପ୍ତାହର ଗଣନା: କେତେ ଏଣ୍ଟ୍ରି ସେଭ୍ ହେଲା ଓ ଆପ୍ କେତେ ଥର ଭୁଲ ବୁଝିଲା।",
        "When this is on, what you say to the app (your voice, the words it heard and its answer) is sent over Wi-Fi to the HisaabSaathi team, without your name, so it learns to understand better. Your ledger stays on this phone. Also, each week, how many entries you saved and how often the app got them wrong (numbers only).",
    )
    val shareSwitch get() = t("मेरी आवाज़ और सवाल भेजें", "ମୋ ସ୍ୱର ଓ ପ୍ରଶ୍ନ ପଠାନ୍ତୁ", "Share my voice and questions")
    fun shareWaiting(n: Int) = tp("$n भेजने बाकी", "$n ଟି ପଠାଇବା ବାକି", "$n waiting to be sent", "{0} waiting to be sent", n)
    fun shareSent(n: Int) = tp("अब तक $n भेजे", "ଏ ପର୍ଯ୍ୟନ୍ତ $n ଟି ପଠାଯାଇଛି", "$n sent so far", "{0} sent so far", n)
    val sendNow get() = t("अभी भेजें", "ଏବେ ପଠାନ୍ତୁ", "Send now")
    val deleteShared get() = t("भेजा हुआ मिटाएं", "ପଠାଯାଇଥିବା ଲିଭାନ୍ତୁ", "Delete what I shared")
    val deleteSharedQ get() = t(
        "इस फोन से भेजा गया सब कुछ टीम के कंप्यूटर से मिटा दिया जाएगा, और यहाँ बाकी भी। पक्का?",
        "ଏହି ଫୋନ୍‌ରୁ ପଠାଯାଇଥିବା ସବୁକିଛି ଟିମ୍‌ର କମ୍ପ୍ୟୁଟରରୁ ଲିଭାଯିବ, ଏବଂ ଏଠାରେ ବାକି ଥିବା ବି। ନିଶ୍ଚିତ?",
        "Everything this phone sent will be deleted from the team's computers, along with what is waiting here. Are you sure?",
    )
    val deleteSharedDone get() = t("मिटाया जा रहा है। फोन के इंटरनेट पर आने पर पूरा होगा।", "ଲିଭାଯାଉଛି। ଫୋନ୍ ଇଣ୍ଟରନେଟ୍‌ରେ ଆସିଲେ ଶେଷ ହେବ।", "Deleting. It finishes the next time the phone is online.")
    fun voicePackDownloading(pct: Int): String = tp("आवाज़ पैक अभी डाउनलोड हो रहा है ($pct%)। थोड़ा रुकिए, या लिखकर डालें।", "ଭଏସ୍ ପ୍ୟାକ୍ ଏବେ ଡାଉନଲୋଡ୍ ହେଉଛି ($pct%)। ଟିକେ ଅପେକ୍ଷା କରନ୍ତୁ, କିମ୍ବା ଲେଖି ଦିଅନ୍ତୁ।", "The voice pack is still downloading ($pct%). Please wait, or type instead.", "The voice pack is still downloading ({0}%). Please wait, or type instead.", pct)

    // ---- confirm
    val youSaid get() = t("आपने कहा", "ଆପଣ କହିଲେ", "You said")
    val isThisRight get() = t("क्या यह सही है?", "ଏହା ଠିକ୍ କି?", "Is this right?")
    val pleaseCheck get() = t("कृपया ध्यान से देखें", "ଦୟାକରି ଭଲଭାବେ ଦେଖନ୍ତୁ", "Please check carefully")
    val save get() = t("सही है, सेव करें", "ଠିକ୍ ଅଛି, ସେଭ୍ କରନ୍ତୁ", "Correct, save")
    val fixByVoice get() = t("गलत है, फिर बोलें", "ଭୁଲ୍ ଅଛି, ପୁଣି କୁହନ୍ତୁ", "Wrong, say it again")
    val saved get() = t("हिसाब में लिख लिया", "ହିସାବରେ ଲେଖିଦେଲୁ", "Written in your ledger")
    val playAgain get() = t("फिर सुनें", "ପୁଣି ଶୁଣନ୍ତୁ", "Play again")
    val amountLabel get() = t("रकम (₹)", "ରାଶି (₹)", "Amount (₹)")

    // ---- ledger / reports
    val noEntries get() = t("अभी कुछ नहीं लिखा है", "ଏବେ କିଛି ଲେଖାଯାଇନି", "Nothing written down yet")
    val deleteEntryQ get() = t("यह लाइन हटा दें?", "ଏହି ଧାଡ଼ି ହଟାଇଦେବେ?", "Remove this line?")
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
        QueryPeriod.YESTERDAY -> yesterday
        QueryPeriod.LAST_WEEK -> t("पिछला हफ्ता", "ଗତ ସପ୍ତାହ", "Last week")
        QueryPeriod.LAST_MONTH -> t("पिछला महीना", "ଗତ ମାସ", "Last month")
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
    val photoWhat get() = t("यह किस चीज़ की फोटो है?", "ଏହା କାହାର ଫଟୋ?", "What is this a photo of?")
    fun photoKindName(k: PhotoKind): String = when (k) {
        PhotoKind.BILL -> t("बिल या रसीद", "ବିଲ୍ ବା ରସିଦ", "Bill or receipt")
        PhotoKind.ADVANCE -> t("एडवांस की पर्ची", "ଆଡଭାନ୍ସ ସ୍ଲିପ୍", "Advance slip")
        PhotoKind.LOAN -> t("उधार / लोन का कागज़", "ଋଣ / ଲୋନ୍ କାଗଜ", "Loan paper")
        PhotoKind.SALARY -> t("वेतन / मजदूरी की पर्ची", "ଦରମା / ମଜୁରୀ ସ୍ଲିପ୍", "Salary or wage slip")
        PhotoKind.BANK -> t("बैंक स्टेटमेंट या पासबुक", "ବ୍ୟାଙ୍କ ଷ୍ଟେଟମେଣ୍ଟ ବା ପାସବୁକ୍", "Bank statement or passbook")
        PhotoKind.OTHER -> t("कोई और कागज़", "ଅନ୍ୟ କାଗଜ", "Other paper")
    }
    val photosTitle get() = t("फोटो और कागज़", "ଫଟୋ ଓ କାଗଜ", "Photos and papers")
    val photoKept get() = t("फोटो आपके कागज़ों में रख ली गई।", "ଫଟୋ ଆପଣଙ୍କ କାଗଜ ସହିତ ରଖାଗଲା।", "Photo kept with your papers.")
    val photoNoAmount get() = t("रकम पढ़ी नहीं जा सकी। कृपया लिख दें।", "ଟଙ୍କା ପଢ଼ି ହେଲା ନାହିଁ। ଦୟାକରି ଲେଖନ୍ତୁ।", "I could not read the amount. Please type it.")
    val viewPhoto get() = t("फोटो देखें", "ଫଟୋ ଦେଖନ୍ତୁ", "See photo")
    val deletePhotoQ get() = t("यह फोटो हटाएं?", "ଏହି ଫଟୋ ହଟାଇବେ?", "Delete this photo?")
    val addPhoto get() = t("फोटो लें", "ଫଟୋ ନିଅନ୍ତୁ", "Take a photo")
    val photosEmpty get() = t("बिल, पर्ची, पासबुक की फोटो यहाँ दिखेंगी।", "ବିଲ୍, ସ୍ଲିପ୍, ପାସବୁକ୍‌ର ଫଟୋ ଏଠାରେ ଦେଖାଯିବ।", "Photos of bills, slips and passbooks show here.")
    val outsideCities get() = t("छोटे शहरों और गाँवों की दरें", "ଛୋଟ ସହର ଓ ଗାଁର ଦର", "Rates for small towns and villages")
    fun cityRate(skill: String, paise: Long): String = rupees(paise).let {
        tp("बड़े शहरों में $skill काम की न्यूनतम मजदूरी ज़्यादा है: $it प्रति दिन।", "ବଡ଼ ସହରରେ $skill କାମର ସର୍ବନିମ୍ନ ମଜୁରୀ ଅଧିକ: ଦିନକୁ $it।",
            "In big cities the minimum for $skill work is higher: $it a day.", "In big cities the minimum for {0} work is higher: {1} a day.", skill, it)
    }
    val wageNotChecked get() = t("ये आंकड़े अभी सरकारी सूचना से मिलाए नहीं गए हैं।", "ଏହି ଅଙ୍କ ଏବେ ସରକାରୀ ବିଜ୍ଞପ୍ତି ସହ ମିଳାଯାଇ ନାହିଁ।", "These figures are not yet checked against the government notice.")
    val validUntil get() = t("कब तक", "କେବେ ପର୍ଯ୍ୟନ୍ତ", "Valid until")
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
    val atOrAbove get() = t("आपकी कमाई न्यूनतम मजदूरी के बराबर या ज़्यादा है।", "ଆପଣଙ୍କ ରୋଜଗାର ସର୍ବନିମ୍ନ ମଜୁରୀ ସହ ସମାନ କିମ୍ବା ଅଧିକ।", "Your earning is at or above the minimum wage.")

    // ---- settings / privacy
    val settings get() = t("सेटिंग", "ସେଟିଂ", "Settings")
    val language get() = t("भाषा", "ଭାଷା", "Language")
    val workState get() = t("काम का राज्य", "କାମର ରାଜ୍ୟ", "Work state")
    val reminders get() = t("रोज़ 3 बजे और 9 बजे याद दिलाएं", "ପ୍ରତିଦିନ 3ଟା ଓ 9ଟାରେ ମନେ ପକାନ୍ତୁ", "Remind me daily at 3 PM and 9 PM")
    val moreSettings get() = t("और विकल्प", "ଆହୁରି ବିକଳ୍ପ", "More options")
    val speechPackTitle get() = t("आपकी आवाज़ समझने के लिए", "ଆପଣଙ୍କ ସ୍ୱର ବୁଝିବା ପାଇଁ", "To understand your voice")
    val modelPacks get() = t("ऑफ़लाइन पैक", "ଅଫ୍‌ଲାଇନ୍ ପ୍ୟାକ୍", "Offline packs")
    val smartPack get() = t("समझदार मॉडल: अधूरी बात भी समझने के लिए", "ସ୍ମାର୍ଟ ମଡେଲ୍: ଅଧା କଥା ବି ବୁଝିବା ପାଇଁ", "Smart model: understands sentences the app is unsure about")
    val addFromPhone get() = t("फोन से मॉडल फ़ाइल जोड़ें", "ଫୋନ୍‌ରୁ ମଡେଲ୍ ଫାଇଲ୍ ଯୋଡ଼ନ୍ତୁ", "Add model file from phone")
    val privacyTitle get() = t("आपका डेटा, आपके पास", "ଆପଣଙ୍କ ଡାଟା, ଆପଣଙ୍କ ପାଖରେ", "Your data stays with you")
    val privacyBody get() = t(
        "• आपका हिसाब इसी फोन में ताले (एन्क्रिप्शन) में रहता है।\n• आवाज़ फोन में ही समझी जाती है। ऐप को बेहतर बनाने के लिए आप ऐप से जो बोलते हैं उसकी रिकॉर्डिंग Wi-Fi पर टीम को जाती है; आपका नाम, फ़ोन नंबर या आधार कभी नहीं। इसे सेटिंग में बंद कर सकते हैं।\n• ठेकेदार या कंपनी आपका हिसाब नहीं देख सकते।\n• आप जब चाहें सब कुछ मिटा सकते हैं।",
        "• ଆପଣଙ୍କ ହିସାବ ଏହି ଫୋନ୍‌ରେ ତାଲା (ଏନକ୍ରିପ୍ସନ୍) ଭିତରେ ରହେ।\n• ସ୍ୱର ଫୋନ୍‌ରେ ହିଁ ବୁଝାଯାଏ। ଆପ୍ ଉନ୍ନତି ପାଇଁ ଆପଣ ଆପ୍‌କୁ ଯାହା କୁହନ୍ତି ତାହାର ରେକର୍ଡିଂ Wi-Fi ରେ ଟିମ୍‌କୁ ଯାଏ; ଆପଣଙ୍କ ନାମ, ଫୋନ୍ ନମ୍ବର କିମ୍ବା ଆଧାର କେବେ ନୁହେଁ। ସେଟିଂସ୍‌ରେ ଏହା ବନ୍ଦ କରିପାରିବେ।\n• ଠିକାଦାର କିମ୍ବା କମ୍ପାନୀ ଆପଣଙ୍କ ହିସାବ ଦେଖିପାରିବେ ନାହିଁ।\n• ଆପଣ ଚାହିଁଲେ ସବୁ ଲିଭାଇ ପାରିବେ।",
        "• Your records are kept encrypted on this phone only.\n• Your voice is understood on the phone. To improve the app, recordings of what you say to it go to the team over Wi-Fi; never your name, phone number or Aadhaar. You can turn this off in Settings.\n• No contractor or company can see your records.\n• You can erase everything whenever you want.",
    )
    val eraseAll get() = t("सारा हिसाब मिटाएं", "ସବୁ ହିସାବ ଲିଭାନ୍ତୁ", "Erase all records")
    val eraseAllQ get() = t("सारा हिसाब हमेशा के लिए मिट जाएगा। पक्का?", "ସବୁ ହିସାବ ସବୁଦିନ ପାଇଁ ଲିଭିଯିବ। ନିଶ୍ଚିତ?", "All records will be erased for ever. Are you sure?")

    // ---- reminders
    val reminderTitle get() = t("आज का हिसाब लिखा?", "ଆଜିର ହିସାବ ଲେଖିଲେ?", "Logged today's work?")
    val reminderBody get() = t("दबाएं और बोलकर काम, एडवांस या खर्च दर्ज करें।", "ଦବାଇ କହି କାମ, ଆଡଭାନ୍ସ କିମ୍ବା ଖର୍ଚ୍ଚ ଲେଖନ୍ତୁ।", "Tap and say your work, advance or expense.")


    // ---- simple questions and greetings
    val chatName get() = t("मैं हिसाब साथी हूँ। मैं आपकी कमाई, एडवांस और खर्च का हिसाब रखता हूँ।", "ମୁଁ ହିସାବ ସାଥୀ। ମୁଁ ଆପଣଙ୍କ ରୋଜଗାର, ଆଡଭାନ୍ସ ଓ ଖର୍ଚ୍ଚର ହିସାବ ରଖେ।", "I am HisaabSaathi. I keep track of your earnings, advances and expenses.")
    val chatGreeting get() = t("नमस्ते! आज का काम, एडवांस या खर्च बोलिए।", "ନମସ୍କାର! ଆଜିର କାମ, ଆଡଭାନ୍ସ କିମ୍ବା ଖର୍ଚ୍ଚ କୁହନ୍ତୁ।", "Hello! Tell me today's work, advance or expense.")
    val chatHelp get() = t("मैक दबाकर बोलिए: कितना काम किया, कितना एडवांस लिया, या कितना खर्च हुआ। जैसे: 120 पीस, 5 रुपये रेट, 300 एडवांस। आप पूछ भी सकते हैं: इस हफ्ते कितना कमाया?", "ମାଇକ୍ ଦବାଇ କୁହନ୍ତୁ: କେତେ କାମ କଲେ, କେତେ ଆଡଭାନ୍ସ ନେଲେ, କିମ୍ବା କେତେ ଖର୍ଚ୍ଚ ହେଲା। ଯେପରି: 120 ପିସ୍, 5 ଟଙ୍କା ଦର, 300 ଆଡଭାନ୍ସ। ଆପଣ ପଚାରି ପାରିବେ: ଏହି ସପ୍ତାହରେ କେତେ ରୋଜଗାର ହେଲା?", "Tap the mic and say how much you worked, what advance you took, or what you spent. For example: 120 pieces, rate 5 rupees, 300 advance. You can also ask: how much did I earn this week?")
    val chatThanks get() = t("आपका स्वागत है।", "ଆପଣଙ୍କୁ ସ୍ୱାଗତ।", "You are welcome.")
    val chatHowAreYou get() = t("मैं ठीक हूँ। आज का हिसाब लिख लें?", "ମୁଁ ଭଲ ଅଛି। ଆଜିର ହିସାବ ଲେଖିବା?", "I am fine. Shall we note today's work?")
    val chatMaker get() = t("मुझे Acxelin Labs ने मजदूर भाई-बहनों के लिए बनाया है।", "ମୋତେ Acxelin Labs ଶ୍ରମିକ ଭାଇ-ଭଉଣୀଙ୍କ ପାଇଁ ତିଆରି କରିଛି।", "Acxelin Labs made me for working people.")
    val chatPrivacy get() = t(
        "आपका हिसाब सिर्फ़ इसी फोन में ताले में रहता है। ठेकेदार या कंपनी इसे नहीं देख सकते। ऐप सुधारने के लिए आपकी कही बात की रिकॉर्डिंग हिसाब साथी टीम को जाती है, आपके नाम या फ़ोन नंबर के बिना। सेटिंग में इसे बंद कर सकते हैं।",
        "ଆପଣଙ୍କ ହିସାବ କେବଳ ଏହି ଫୋନ୍‌ରେ ତାଲା ଭିତରେ ରହେ। ଠିକାଦାର କିମ୍ବା କମ୍ପାନୀ ଦେଖିପାରିବେ ନାହିଁ। ଆପ୍ ଉନ୍ନତି ପାଇଁ ଆପଣଙ୍କ କଥାର ରେକର୍ଡିଂ ହିସାବ ସାଥୀ ଟିମ୍‌କୁ ଯାଏ, ଆପଣଙ୍କ ନାମ କିମ୍ବା ଫୋନ୍ ନମ୍ବର ବିନା। ସେଟିଂସ୍‌ରେ ଏହା ବନ୍ଦ କରିପାରିବେ।",
        "Your records stay locked on this phone only. No contractor or company can see them. To improve the app, recordings of what you say to it go to the HisaabSaathi team, without your name or phone number. You can turn this off in Settings.",
    )
    val chatBye get() = t("ठीक है, फिर मिलेंगे।", "ଠିକ୍ ଅଛି, ପୁଣି ଦେଖା ହେବ।", "All right, see you again.")
    val chatFallback get() = t("यह मैं नहीं समझा। मैं काम, एडवांस और खर्च का हिसाब रखता हूँ। ऐसे बोलिए: 500 रुपये मजदूरी मिली।", "ଏହା ମୁଁ ବୁଝିଲି ନାହିଁ। ମୁଁ କାମ, ଆଡଭାନ୍ସ ଓ ଖର୍ଚ୍ଚର ହିସାବ ରଖେ। ଏପରି କୁହନ୍ତୁ: 500 ଟଙ୍କା ମଜୁରୀ ମିଳିଲା।", "I did not understand that. I keep track of work, advances and expenses. Say it like this: got 500 rupees wages.")
    fun chatDate(date: String): String = tp("आज $date है।", "ଆଜି $date।", "Today is $date.", "Today is {0}.", date)

    fun chat(c: Chat, date: String): String = when (c) {
        Chat.MAKER -> chatMaker
        Chat.HOW_ARE_YOU -> chatHowAreYou
        Chat.NAME -> chatName
        Chat.PRIVACY -> chatPrivacy
        Chat.DATE -> chatDate(date)
        Chat.THANKS -> chatThanks
        Chat.BYE -> chatBye
        Chat.HELP -> chatHelp
        Chat.GREETING -> chatGreeting
    }

    // ---- formatting
    fun rupees(paise: Long): String = "₹" + Money.formatGrouped(paise)

    private fun spokenRupees(paise: Long): String =
        tp("${Money.format(paise)} रुपये", "${Money.format(paise)} ଟଙ୍କା", "${Money.format(paise)} rupees", "{0} rupees", Money.format(paise))

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
        ExpenseCategory.LOAN -> t("लोन की किस्त", "ଋଣ କିସ୍ତି", "Loan repayment")
        ExpenseCategory.OTHER -> t("अन्य", "ଅନ୍ୟ", "Other")
    }

    fun mealName(m: Meal): String = when (m) {
        Meal.BREAKFAST -> t("नाश्ता", "ଜଳଖିଆ", "Breakfast")
        Meal.LUNCH -> t("दोपहर का खाना", "ମଧ୍ୟାହ୍ନ ଭୋଜନ", "Lunch")
        Meal.DINNER -> t("रात का खाना", "ରାତି ଖାଇବା", "Dinner")
    }

    private fun loanKindName(k: LoanKind): String = when (k) {
        LoanKind.CAR -> t("कार", "କାର", "Car")
        LoanKind.BIKE -> t("बाइक", "ବାଇକ୍", "Bike")
        LoanKind.PHONE -> t("फोन", "ଫୋନ୍", "Phone")
        LoanKind.HOUSE -> t("घर", "ଘର", "House")
        LoanKind.GOLD -> t("गोल्ड", "ସୁନା", "Gold")
        LoanKind.TRACTOR -> t("ट्रैक्टर", "ଟ୍ରାକ୍ଟର", "Tractor")
        LoanKind.GROUP -> t("समूह", "ଗୋଷ୍ଠୀ", "Group")
    }

    /** "बाइक लोन", "Gold loan"; "लोन" when it was not said what for. */
    fun loanName(k: LoanKind?): String =
        if (k == null) t("लोन", "ଋଣ", "Loan")
        else loanKindName(k).let { n -> tp("$n लोन", "$n ଋଣ", "$n loan", "{0} loan", n) }

    /** The short line under an entry: "समोसा, चाय · नाश्ता", "बाइक लोन"; "" when nothing was said. */
    fun describe(detailCode: String): String {
        val d = Detail.decode(detailCode)
        val parts = ArrayList<String>()
        if (d.foods.isNotEmpty()) parts += d.foods.joinToString(", ") { Items.foodName(it, lang) }
        d.meal?.let { parts += mealName(it) }
        if (d.loan) parts += loanName(d.loanKind)
        return parts.joinToString(" · ")
    }

    fun skillName(skill: String): String = when (skill.lowercase()) {
        "unskilled" -> t("अकुशल", "ଅଣକୁଶଳୀ", "Unskilled")
        "semi-skilled" -> t("अर्ध-कुशल", "ଅର୍ଦ୍ଧ-କୁଶଳୀ", "Semi-skilled")
        "skilled" -> t("कुशल", "କୁଶଳୀ", "Skilled")
        "highly skilled" -> t("अति कुशल", "ଅତି କୁଶଳୀ", "Highly skilled")
        else -> skill
    }

    private fun piecesText(n: Int) = tp("$n पीस", "$n ପିସ୍", "$n pieces", "{0} pieces", n)
    private fun hoursText(h: Double) = tp("${num(h)} घंटे", "${num(h)} ଘଣ୍ଟା", "${num(h)} hours", "{0} hours", num(h))

    /** "120 पीस × ₹5, 8 घंटे" — the detail line under a work entry. */
    fun workDetail(hours: Double?, pieces: Int?, ratePaise: Long?): String {
        val parts = ArrayList<String>()
        if (pieces != null) parts += if (ratePaise != null) "${piecesText(pieces)} × ${rupees(ratePaise)}" else piecesText(pieces)
        if (hours != null) parts += if (pieces == null && ratePaise != null) "${hoursText(hours)} × ${rupees(ratePaise)}" else hoursText(hours)
        return parts.joinToString(", ")
    }

    /** What the phone says before the worker confirms. */
    fun readback(entries: List<DraftEntry>): String {
        val sb = StringBuilder()
        for (e in entries) {
            when (e.type) {
                EntryType.WORK -> {
                    e.hours?.let { sb.append(tp("${num(it)} घंटे काम। ", "${num(it)} ଘଣ୍ଟା କାମ। ", "${num(it)} hours of work. ", "{0} hours of work. ", num(it))) }
                    e.pieces?.let { sb.append(tp("$it पीस। ", "$it ପିସ୍। ", "$it pieces. ", "{0} pieces. ", it)) }
                    e.ratePaise?.let { sb.append(tp("रेट ${spokenRupees(it)}। ", "ଦର ${spokenRupees(it)}। ", "Rate ${spokenRupees(it)}. ", "Rate {0}. ", spokenRupees(it))) }
                    if (e.amountPaise > 0) {
                        val a = spokenRupees(e.amountPaise)
                        sb.append(tp("कमाई $a। ", "ରୋଜଗାର $a। ", "Earned $a. ", "Earned {0}. ", a))
                    }
                }
                EntryType.ADVANCE -> spokenRupees(e.amountPaise).let { a ->
                    val loan = Detail.decode(e.detail).takeIf { it.loan }
                    if (loan != null) loanName(loan.loanKind).let { n -> sb.append(tp("$n लिया $a। ", "$n ନେଲେ $a। ", "$n taken $a. ", "{0} taken {1}. ", n, a)) }
                    else sb.append(tp("एडवांस लिया $a। ", "ଆଡଭାନ୍ସ ନେଲେ $a। ", "Advance taken $a. ", "Advance taken {0}. ", a))
                }
                EntryType.PAYMENT -> spokenRupees(e.amountPaise).let { a ->
                    sb.append(tp("मजदूरी मिली $a। ", "ମଜୁରୀ ମିଳିଲା $a। ", "Wages received $a. ", "Wages received {0}. ", a))
                }
                EntryType.EXPENSE -> {
                    // What was said is clearer than the group: "समोसा, चाय पर खर्च", "बाइक लोन की किस्त".
                    val d = Detail.decode(e.detail)
                    val c = when {
                        d.loan -> loanName(d.loanKind).let { n -> tp("$n की किस्त", "$n କିସ୍ତି", "$n repayment", "{0} repayment", n) }
                        d.foods.isNotEmpty() -> describe(e.detail)
                        d.meal != null -> mealName(d.meal)
                        else -> categoryName(e.category ?: ExpenseCategory.OTHER)
                    }
                    val a = spokenRupees(e.amountPaise)
                    sb.append(tp("$c पर खर्च $a। ", "$c ପାଇଁ ଖର୍ଚ୍ଚ $a। ", "Spent $a on $c. ", "Spent {0} on {1}. ", a, c))
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
        QueryPeriod.YESTERDAY -> yesterday
        QueryPeriod.LAST_WEEK -> t("पिछले हफ्ते", "ଗତ ସପ୍ତାହରେ", "Last week")
        QueryPeriod.LAST_MONTH -> t("पिछले महीने", "ଗତ ମାସରେ", "Last month")
    }

    /** The spoken report for a period. */
    fun summary(period: QueryPeriod, s: Totals): String {
        val p = spokenPeriod(period)
        val e = spokenRupees(s.earnedPaise); val a = spokenRupees(s.advancePaise); val r = spokenRupees(s.paidPaise)
        val x = spokenRupees(s.expensePaise); val d = spokenRupees(s.duePaise)
        return tp(
            "$p आपने $e कमाए। एडवांस लिया $a। मजदूरी मिली $r। खर्च किया $x। मालिक पर बाकी है $d।",
            "$p ଆପଣ $e ରୋଜଗାର କଲେ। ଆଡଭାନ୍ସ ନେଲେ $a। ମଜୁରୀ ମିଳିଲା $r। ଖର୍ଚ୍ଚ କଲେ $x। ମାଲିକ ପାଖରେ ବାକି ଅଛି $d।",
            "$p you earned $e. Advance taken $a. Wages received $r. Spent $x. Your employer still owes you $d.",
            "{0} you earned {1}. Advance taken {2}. Wages received {3}. Spent {4}. Your employer still owes you {5}.",
            p, e, a, r, x, d,
        )
    }

    /** The spoken answer to a voice question. */
    fun answer(q: Query, s: Totals): String {
        val p = spokenPeriod(q.period)
        return when (q.topic) {
            QueryTopic.ALL, QueryTopic.MIN_WAGE -> summary(q.period, s)
            QueryTopic.EARNED -> spokenRupees(s.earnedPaise).let { tp("$p आपने $it कमाए।", "$p ଆପଣ $it ରୋଜଗାର କଲେ।", "$p you earned $it.", "{0} you earned {1}.", p, it) }
            QueryTopic.ADVANCE -> spokenRupees(s.advancePaise).let { tp("$p आपने $it एडवांस लिया।", "$p ଆପଣ $it ଆଡଭାନ୍ସ ନେଲେ।", "$p you took $it in advances.", "{0} you took {1} in advances.", p, it) }
            QueryTopic.EXPENSE -> spokenRupees(s.expensePaise).let { tp("$p आपने $it खर्च किए।", "$p ଆପଣ $it ଖର୍ଚ୍ଚ କଲେ।", "$p you spent $it.", "{0} you spent {1}.", p, it) }
            QueryTopic.DUE -> spokenRupees(s.duePaise).let { tp("$p का बाकी $it है।", "$p ବାକି $it ଅଛି।", "$p $it is still due to you.", "{0} {1} is still due to you.", p, it) }
        }
    }

    fun minWageSpoken(rate: WageRate): String {
        val amount = spokenRupees(Money.rupeesToPaise(rate.dailyRupees))
        val skill = skillName(rate.skill)
        return tp(
            "$skill काम की सरकारी न्यूनतम मजदूरी $amount प्रति दिन है।",
            "$skill କାମର ସରକାରୀ ସର୍ବନିମ୍ନ ମଜୁରୀ ଦିନକୁ $amount।",
            "The official minimum wage for $skill work is $amount per day.",
            "The official minimum wage for {0} work is {1} per day.", skill, amount,
        )
    }

    fun shortfall(paise: Long): String = rupees(paise).let {
        tp(
            "आपको न्यूनतम मजदूरी से रोज़ $it कम मिल रहा है।",
            "ଆପଣଙ୍କୁ ସର୍ବନିମ୍ନ ମଜୁରୀଠାରୁ ଦିନକୁ $it କମ୍ ମିଳୁଛି।",
            "You are getting $it a day less than the minimum wage.",
            "You are getting {0} a day less than the minimum wage.", it,
        )
    }

    fun goalProgress(savedPaise: Long, goalPaise: Long): String = tp(
        "इस महीने ${rupees(savedPaise)} बचे, लक्ष्य ${rupees(goalPaise)}",
        "ଏହି ମାସ ${rupees(savedPaise)} ବଳିଲା, ଲକ୍ଷ୍ୟ ${rupees(goalPaise)}",
        "${rupees(savedPaise)} left this month, goal ${rupees(goalPaise)}",
        "{0} left this month, goal {1}", rupees(savedPaise), rupees(goalPaise),
    )

    /** Advice for irregular income: how much to put aside per working day left this month. */
    fun goalAdvice(perDayPaise: Long): String = rupees(perDayPaise).let {
        tp(
            "लक्ष्य के लिए हर काम के दिन $it अलग रखें।",
            "ଲକ୍ଷ୍ୟ ପାଇଁ ପ୍ରତି କାମ ଦିନ $it ଅଲଗା ରଖନ୍ତୁ।",
            "To reach your goal, put aside $it on each working day.",
            "To reach your goal, put aside {0} on each working day.", it,
        )
    }
    val goalReached get() = t("शाबाश! इस महीने का लक्ष्य पूरा।", "ସାବାସ୍! ଏହି ମାସର ଲକ୍ଷ୍ୟ ପୂରା ହେଲା।", "Well done! This month's goal is reached.")
}

/** Yes/no words for confirming by voice. */
object Confirm {
    private fun extraWords(role: String): Set<String> =
        listOf(com.acxelin.hisaabsaathi.core.i18n.Te.keywords, com.acxelin.hisaabsaathi.core.i18n.Kn.keywords, com.acxelin.hisaabsaathi.core.i18n.Ta.keywords)
            .mapNotNull { it[role] }.flatMap { it.split(' ') }.filter { it.isNotBlank() }.map(Text::normalize).toSet()

    private val yes = setOf("हां", "हाँ", "जी", "सही", "ठीक", "haan", "han", "yes", "sahi", "theek", "ok", "ହଁ", "ଠିକ୍", "ଠିକ").map(Text::normalize).toSet() + extraWords("yes")
    private val no = setOf("नहीं", "नही", "गलत", "ना", "nahi", "nahin", "no", "galat", "wrong", "ନା", "ନାହିଁ", "ଭୁଲ୍", "ଭୁଲ").map(Text::normalize).toSet() + extraWords("no")

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
