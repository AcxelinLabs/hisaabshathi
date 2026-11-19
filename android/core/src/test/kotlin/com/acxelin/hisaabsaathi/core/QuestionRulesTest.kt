package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the rules read ledger questions, and what they must leave alone. */
class QuestionRulesTest {
    private fun ask(text: String): Query? = (Understand.of(text) as? Understood.Ask)?.query

    private fun assertAsk(text: String, period: QueryPeriod, topic: QueryTopic) =
        assertEquals(text, Query(period, topic), ask(text))

    @Test fun pleaseTellMeIsNotTheNumberTwo() {
        // "बता दो ना" used to become a ₹2 entry.
        assertAsk("इस हफ्ते मैंने कितना कमाया बता दो ना", QueryPeriod.WEEK, QueryTopic.EARNED)
        assertAsk("आज का पूरा ब्यौरा दो", QueryPeriod.DAY, QueryTopic.ALL)
        assertTrue(Understand.of("मेरा मोबाइल रिचार्ज कर दो") !is Understood.Entries)
        // Still two when an amount, hours or a count follows.
        assertEquals(listOf(200.0), RuleBasedExtractor.numbersIn("बता दो सौ रुपये"))
        assertEquals(listOf(2.0), RuleBasedExtractor.numbersIn("काम कर दो घंटे"))
    }

    @Test fun localAndMixedWords() {
        assertAsk("इस बरस पेशगी कितनी ली", QueryPeriod.YEAR, QueryTopic.ADVANCE)
        assertAsk("इस मंथ की इनकम कितनी है", QueryPeriod.MONTH, QueryTopic.EARNED)
        assertAsk("मेरा कितना पैसा फंसा हुआ है", QueryPeriod.WEEK, QueryTopic.DUE)
        assertAsk("ଏ ହପ୍ତାରେ ଦାଦନ କେତେ ନେଲି", QueryPeriod.WEEK, QueryTopic.ADVANCE)
        assertAsk("ଚଳିତ ବର୍ଷର ରୋଜଗାର କେତେ", QueryPeriod.YEAR, QueryTopic.EARNED)
        assertAsk("ఈ వీక్ హిసాబ్ చెప్పు", QueryPeriod.WEEK, QueryTopic.ALL)
        assertAsk("ಈ ತಿಂಗಳು ಮುಂಗಡ ಎಷ್ಟು ತಗೊಂಡೆ", QueryPeriod.MONTH, QueryTopic.ADVANCE)
        assertAsk("இந்த மாத செலவு சொல்லு", QueryPeriod.MONTH, QueryTopic.EXPENSE)
        assertAsk("show this week's hisaab", QueryPeriod.WEEK, QueryTopic.ALL)
        assertAsk("this month's advance amount", QueryPeriod.MONTH, QueryTopic.ADVANCE)
    }

    @Test fun theSentenceDecidesTheTopic() {
        assertAsk("इस हफ्ते पैसे कहाँ गए", QueryPeriod.WEEK, QueryTopic.EXPENSE)
        assertAsk("सेठ से कितना उठाया", QueryPeriod.WEEK, QueryTopic.ADVANCE)
        assertAsk("मालिक ने अभी कितना नहीं दिया", QueryPeriod.WEEK, QueryTopic.DUE)
        assertAsk("did I get my full wages this week", QueryPeriod.WEEK, QueryTopic.DUE)
        assertAsk("ఈ వారం ఇంటికి ఎంత పంపించాను", QueryPeriod.WEEK, QueryTopic.EXPENSE)
        assertAsk("ಈ ವಾರ ಎಷ್ಟು ದುಡ್ಡು ಮಾಡಿದೆ ಹೇಳು", QueryPeriod.WEEK, QueryTopic.EARNED)
        assertAsk("இந்த வாரம் பஸ் சார்ஜுக்கு எவ்வளவு ஆச்சு", QueryPeriod.WEEK, QueryTopic.EXPENSE)
    }

    @Test fun yesterdayLastWeekLastMonth() {
        assertAsk("कल मैंने कितना कमाया", QueryPeriod.YESTERDAY, QueryTopic.EARNED)
        assertAsk("पिछले हफ्ते का बकाया कितना है", QueryPeriod.LAST_WEEK, QueryTopic.DUE)
        assertAsk("ଗଲା ସପ୍ତାହର ହିସାବ ଦେଖାଅ", QueryPeriod.LAST_WEEK, QueryTopic.ALL)
        assertAsk("போன மாச கணக்கு வழக்கு சொல்லு", QueryPeriod.LAST_MONTH, QueryTopic.ALL)
        assertAsk("how much did I spend last month", QueryPeriod.LAST_MONTH, QueryTopic.EXPENSE)
    }

    @Test fun governmentRateIsTheMinimumWage() {
        for (t in listOf("सरकारी मजदूरी कितनी है", "एक दिन का सरकारी रेट क्या है", "ప్రభుత్వ కూలి రేటు ఎంత", "what is the official wage rate")) {
            assertEquals(t, QueryTopic.MIN_WAGE, ask(t)?.topic)
        }
    }

    /** Everyday questions, some with a ledger-like word in them, are not ledger questions. */
    @Test fun everydayQuestionsAreLeftAlone() {
        val everyday = listOf(
            "आज खाने में क्या बना है", "पेट्रोल का दाम कितना है", "मेरे गाँव की बस कितने बजे जाती है", "बच्चे स्कूल गए क्या", "आज कितनी गर्मी है",
            "डॉक्टर कब आएगा", "मेरा फोन चार्ज नहीं हो रहा", "आज कौन सी फिल्म लगी है", "ठेकेदार का नंबर क्या है", "इस हफ्ते छुट्टी कब है",
            "kal mausam kaisa rahega", "seth ka ghar kahan hai", "ट्रेन कितने बजे है", "गाना बजाओ", "आज बारिश होगी क्या", "तुम्हें किसने बनाया",
            "ଆଜି ଖାଇବାକୁ କଣ ଅଛି", "ପେଟ୍ରୋଲ ଦାମ କେତେ", "ଡାକ୍ତର କେବେ ଆସିବେ", "ପିଲାମାନେ ସ୍କୁଲ ଗଲେ କି", "ଆଜି କେତେ ଗରମ", "ରାସନ୍ କେବେ ମିଳିବ", "ଆଜି କି ବାର",
            "పెట్రోల్ రేటు ఎంత", "ఇవాళ ఎంత ఎండగా ఉంది", "డాక్టర్ ఎప్పుడు వస్తారు", "పిల్లలు స్కూల్ కి వెళ్ళారా", "మేస్త్రి ఫోన్ నంబర్ ఏంటి", "ట్రైన్ ఎన్ని గంటలకి",
            "ಪೆಟ್ರೋಲ್ ಬೆಲೆ ಎಷ್ಟು", "ಇವತ್ತು ಎಷ್ಟು ಬಿಸಿಲು", "ಡಾಕ್ಟರ್ ಯಾವಾಗ ಬರ್ತಾರೆ", "ಮಕ್ಕಳು ಶಾಲೆಗೆ ಹೋದ್ರಾ", "ಮೇಸ್ತ್ರಿ ನಂಬರ್ ಏನು", "ನಿನ್ನ ಹೆಸರು ಏನು",
            "பெட்ரோல் விலை எவ்வளவு", "இன்னைக்கு எவ்வளவு வெயில்", "டாக்டர் எப்போ வருவாரு", "பசங்க ஸ்கூல் போனாங்களா", "மேஸ்திரி நம்பர் என்ன", "ட்ரெயின் எத்தனை மணிக்கு",
            "what is the petrol price", "how hot is it today", "when will the doctor come", "did the kids go to school", "what is the contractor's phone number",
            "how far is the station", "what is the time now", "where is my son", "how many days in this month", "is the shop open today", "what is the capital of India",
        )
        for (t in everyday) {
            val u = Understand.of(t)
            assertTrue("$t => $u", u !is Understood.Ask && u !is Understood.Entries)
        }
    }
}
