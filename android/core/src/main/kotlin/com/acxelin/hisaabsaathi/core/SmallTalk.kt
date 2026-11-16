package com.acxelin.hisaabsaathi.core

/** Simple things people say to the app that are not ledger entries. */
enum class Chat { MAKER, HOW_ARE_YOU, NAME, PRIVACY, DATE, THANKS, BYE, HELP, GREETING }

/**
 * Recognises greetings and simple questions ("what is your name", "what can you do",
 * "is my data safe") in all six languages, so the app always has an answer even on a
 * phone with no language model installed. Checked only after an utterance turned out not
 * to be a ledger entry or a ledger question.
 */
object SmallTalk {
    // Order matters: the first match wins ("कैसे हो" is HOW_ARE_YOU, not the "कैसे" of HELP).
    private val patterns: List<Pair<Chat, List<String>>> = listOf(
        Chat.MAKER to listOf(
            "किसने बनाया", "कौन बनाया", "who made", "who built", "who created", "kisne banaya", "କିଏ ତିଆରି", "କିଏ ବନେଇ",
            "ఎవరు తయారు", "ఎవరు చేశారు", "ಯಾರು ಮಾಡಿ", "ಯಾರು ತಯಾರ", "யார் உருவாக்க", "யார் செய்த",
        ),
        Chat.HOW_ARE_YOU to listOf(
            "कैसे हो", "कैसे हैं", "कैसी हो", "क्या हाल", "how are you", "kaise ho", "kya haal", "କେମିତି ଅଛ", "କିପରି ଅଛ",
            "ఎలా ఉన్నా", "బాగున్నా", "ಹೇಗಿದ್ದೀ", "ಚೆನ್ನಾಗಿದ್ದೀ", "எப்படி இருக்க", "நலமா",
        ),
        Chat.NAME to listOf(
            "नाम", "कौन हो", "तुम कौन", "आप कौन", "name", "naam", "who are you", "kaun ho", "ନାମ", "ନାଁ", "ତୁମେ କିଏ", "ଆପଣ କିଏ",
            "పేర", "నువ్వు ఎవరు", "మీరు ఎవరు", "ಹೆಸರ", "ನೀನು ಯಾರು", "ನೀವು ಯಾರು", "பெயர", "பேர", "நீ யார்", "நீங்க யார்", "நீங்கள் யார்",
        ),
        Chat.PRIVACY to listOf(
            "डेटा", "सुरक्षित", "कौन देख", "देख सकता", "data", "safe", "private", "privacy", "secure", "who can see",
            "ସୁରକ୍ଷିତ", "ଡାଟା", "କିଏ ଦେଖ", "సురక్షిత", "డేటా", "ఎవరు చూడ", "భద్ర", "ಸುರಕ್ಷಿತ", "ಡೇಟಾ", "ಯಾರು ನೋಡ",
            "பாதுகாப்ப", "டேட்டா", "யார் பார்க்க",
        ),
        Chat.DATE to listOf(
            "तारीख", "कौन सा दिन", "क्या दिन", "date", "tarikh", "what day", "ତାରିଖ", "କି ବାର", "తేదీ", "ఏ రోజు", "ದಿನಾಂಕ", "ಯಾವ ದಿನ", "தேதி", "என்ன நாள்", "என்ன கிழமை",
        ),
        Chat.THANKS to listOf(
            "धन्यवाद", "शुक्रिया", "थैंक", "thank", "thanks", "dhanyavad", "dhanyawad", "shukriya", "ଧନ୍ୟବାଦ",
            "ధన్యవాద", "థాంక్", "ಧನ್ಯವಾದ", "ಥ್ಯಾಂಕ್", "நன்றி", "தேங்க்",
        ),
        Chat.BYE to listOf("बाय", "अलविदा", "फिर मिलेंगे", "bye", "goodbye", "alvida", "ବାଏ", "ବିଦାୟ", "బై", "వెళ్తున్నా", "ಬೈ", "ಹೋಗ್ತೀನಿ", "பை", "போயிட்டு வர"),
        Chat.HELP to listOf(
            "क्या कर सकते", "क्या करते", "क्या काम", "कैसे", "मदद", "help", "how to", "how do", "what can you", "what do you do", "kaise", "madad",
            "ସାହାଯ୍ୟ", "କଣ କରିପାର", "କିପରି", "କେମିତି", "సహాయ", "ఏం చేయగల", "ఏమి చేయగల", "ఎలా", "ಸಹಾಯ", "ಏನು ಮಾಡ", "ಹೇಗೆ",
            "உதவி", "என்ன செய்ய", "எப்படி",
        ),
        Chat.GREETING to listOf(
            "नमस्ते", "नमस्कार", "हेलो", "हैलो", "हलो", "हाय", "राम राम", "सलाम", "प्रणाम", "hello", "hi", "hey", "namaste", "namaskar", "good morning", "good evening",
            "ନମସ୍କାର", "ହେଲୋ", "ଜୟ ଜଗନ୍ନାଥ", "నమస్కార", "నమస్తే", "హలో", "ನಮಸ್ಕಾರ", "ನಮಸ್ತೆ", "ಹಲೋ", "வணக்கம்", "ஹலோ",
        ),
    )

    private val compiled = patterns.map { (chat, list) -> chat to list.map(Text::normalize) }

    fun detect(utterance: String): Chat? {
        val tokens = Text.tokens(utterance)
        if (tokens.isEmpty()) return null
        val joined = " " + tokens.joinToString(" ") + " "
        for ((chat, list) in compiled) {
            for (p in list) {
                // Latin words must match whole ("hi" is not inside "this"); Indian-script stems may carry endings.
                val latin = p.all { it.code < 128 }
                if (if (latin) " $p " in joined || joined.startsWith(" $p ") else p in joined) return chat
            }
        }
        return null
    }
}
