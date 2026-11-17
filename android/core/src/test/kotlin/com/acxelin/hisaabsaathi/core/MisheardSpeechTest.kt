package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real speech-recognition mistakes found by the speech test (tools/speech_test.py). */
class MisheardSpeechTest {
    private fun ex(s: String) = RuleBasedExtractor.extract(s)
    private fun rs(n: Int) = n * 100L

    @Test fun keywordMissingALetter() {
        // "एडवांस" heard as "एवांस"
        val e = ex("आज आठ घंटे काम किया दो सौ पचास पीस तीन रुपये रेट एक सौ एवांस लिया")
        assertEquals(rs(750), e.entries[0].amountPaise)
        assertEquals(DraftEntry(EntryType.ADVANCE, rs(100)), e.entries[1])
    }

    @Test fun garbledRupeeWordDoesNotMakeTheNextNumberTheRate() {
        // was ₹25,000: "रुपएे" not recognised, so "रेट एक सौ" made 100 the rate
        val e = ex("आज आठ घंटे काम किया दो सौ पचास पीस तीन रुपएे रेट एक सौ एडवांस लिया")
        assertEquals(rs(750), e.entries[0].amountPaise)
        assertEquals(rs(3), e.entries[0].ratePaise)
        assertEquals(DraftEntry(EntryType.ADVANCE, rs(100)), e.entries[1])
    }

    @Test fun joinedNumberAndRupeeWord() {
        // Odia "ତିନିଟଙ୍କା" = three + rupees, "ଦୁଇଶହ" = two hundred; was ₹24,000
        val e = ex("ଆଜି ବାର ଘଣ୍ଟା କାମ କଲି ଗୋଟିଏ ଶହ କୋଡ଼ି ପିସ୍ ତିନିଟଙ୍କା ଦର ଦୁଇଶହ ଟଙ୍କା ଆଭାଂସ ନେଲି")
        assertEquals(rs(360), e.entries[0].amountPaise)
        assertEquals(EntryType.ADVANCE, e.entries[1].type)
        assertEquals(rs(200), e.entries[1].amountPaise)
    }

    @Test fun finalViramaAndVowelLength() {
        // "ହଜାର୍" and "ମଜୁରି": was ₹500
        val e = ex("ଆଜି ଏକ ହଜାର୍ ପାଞ୍ଚଶହ ଟଙ୍କା ମଜୁରି ମିଳିଲା")
        assertEquals(rs(1500), e.entries[0].amountPaise)
    }

    @Test fun misheardNumberWord() {
        // Kannada "ಅರುವತ್ತು" for "ಅರವತ್ತು" (60)
        val e = ex("ಹತ್ತು ಗಂಟೆ ಕೆಲಸ ಗಂಟೆಗೆ ಅರುವತ್ತು ರೂಪಾಯಿ")
        assertEquals(rs(600), e.entries.single().amountPaise)
    }

    @Test fun everydayWordsAreNotNumbers() {
        assertTrue(NumberWords.numbersIn("ಇವತ್ತು ಕೆಲಸ ಮಾಡಿದೆ ಇನ್ನೂ ಕೊಟ್ಟಿಲ್ಲ").isEmpty()) // today / yet
        assertTrue(NumberWords.numbersIn("నాకు సహాయం చెయ్యి").isEmpty()) // help me
    }

    @Test fun spentIsNotAnAdvance() {
        assertEquals(EntryType.EXPENSE, ex("सब्जी पर अस्सी रुपये खर्च किए").entries.single().type)
    }

    @Test fun smallTalkIsNotALedgerQuestion() {
        assertEquals(Understood.Talk(Chat.MAKER), Understand.of("तुम्हें किसने बनाया"))
        assertEquals(Understood.Talk(Chat.MAKER), Understand.of("who make you"))
    }

    @Test fun implausibleAmountsAskTheWorkerToCheck() {
        assertTrue(ex("250 पीस 100 रुपये रेट").lowConfidence)
        assertNull(ex("250 पीस 3 रुपये रेट").takeIf { it.lowConfidence })
    }
}
