package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Numbers as the Indian-language speech models actually write them.
 *
 * English numbers: workers often say amounts in English ("five hundred"), and every
 * Indian-language model writes them in its own script. The spellings here are the ones the
 * app's models produced for Indian-accented English speech (Svarah, Karya and FLEURS clips
 * with number words; scratch tool english_in_indic.py).
 *
 * Misheard forms: from the end-to-end speech test report (core/build/speech-report.txt).
 */
class SpokenNumbersTest {
    private fun nums(s: String) = NumberWords.numbersIn(s)
    private fun ex(s: String) = RuleBasedExtractor.extract(s)
    private fun rs(n: Int) = n * 100L

    @Test fun englishNumbersInEveryScript() {
        assertEquals(listOf(375.0), nums("थ्री हंड्रेड एंड सेवेंटी फाइव"))
        assertEquals(listOf(375.0), nums("ଥ୍ରୀ ହଣ୍ଡ୍ରେଡ଼୍ ଆଣ୍ଡ ସେଭେଣ୍ଟି ଫାଇଭ୍"))
        assertEquals(listOf(375.0), nums("త్రీ హండ్రెడ్ అండ్ సెవెంటీ ఫైవ్"))
        assertEquals(listOf(375.0), nums("ಥ್ರೀ ಹಂಡ್ರೆಡ್ ಆ್ಯಂಡ್ ಸೆವೆಂಟಿ ಫೈವ್"))
        assertEquals(listOf(375.0), nums("த்ரீ ஹண்ட்ரட் அண்ட் செவன்்டி ஃபைவ்"))
        assertEquals(listOf(375.0), nums("three hundred and seventy five"))
    }

    @Test fun englishNumberWordsSeenFromTheModels() {
        for (s in listOf("सेवन", "ସେଭେନ୍", "సెవెన్", "ಸೆವೆನ್", "செவன்")) assertEquals(s, listOf(7.0), nums(s))
        for (s in listOf("फाइव", "ଫାଇଭ୍", "ఫైవ్", "ಫೈವ್", "ಫೈಬ್", "ஃபைவ்")) assertEquals(s, listOf(5.0), nums(s))
        for (s in listOf("नाइन", "ନାଇନ୍", "నైన్", "ನೈನ್", "நைன்")) assertEquals(s, listOf(9.0), nums(s))
        for (s in listOf("थर्टी", "థర్టీ", "ಥರ್ಟಿ", "தீர்டி")) assertEquals(s, listOf(30.0), nums(s))
        assertEquals(listOf(2500.0), nums("టూ థౌజండ్ ఫైవ్ హండ్రెడ్"))
        assertEquals(listOf(1200.0), nums("ಒನ್ ಥೌಸಂಡ್ ಟೂ ಹಂಡ್ರೆಡ್"))
        assertEquals(listOf(50.0), nums("ఫిఫ్టీ"))
    }

    @Test fun englishAmountsInLedgerSentences() {
        assertEquals(rs(500), ex("ఫైవ్ హండ్రెడ్ రూపాయలు అడ్వాన్స్ తీసుకున్నాను").entries.single().amountPaise)
        assertEquals(rs(250), ex("டூ ஹண்ட்ரட் ஃபிஃப்டி ரூபாய் செலவு").entries.single().amountPaise)
        assertEquals(rs(800), ex("आज एट हंड्रेड रुपये मजदूरी मिली").entries.first().amountPaise)
    }

    @Test fun englishWordsThatAreAlsoOtherWordsNeedANumberContext() {
        // "टू" and "ऐट" are also how the models write "to" and "at".
        assertEquals(emptyList<Double>(), nums("डाउन टू"))
        assertEquals(emptyList<Double>(), nums("ऐट नाइट"))
        assertEquals(emptyList<Double>(), nums("ఇంటి టు వెళ్ళాను"))
        assertEquals(listOf(2.0), nums("टू रुपये"))
    }

    @Test fun vowelSignsTheModelsDoubled() {
        assertEquals(listOf(500.0), nums("అయిదుొందల")) // ఐదొందల
        assertEquals(listOf(8.0), nums("ଆାଠ")) // ଆଠ
    }

    @Test fun commonSpellingVariants() {
        assertEquals(listOf(30.0), nums("ముఫై"))
        assertEquals(listOf(500.0), nums("అయిదొందల"))
        assertEquals(listOf(120.0), nums("నూటరవై"))
    }

    @Test fun numberJoinedToTheWordBeforeIt() {
        // "ଆଜିବାର" = today + twelve
        assertEquals(12.0, ex("ଆଜିବାର ଘଣ୍ଟା କାମ କଲି ଦର ଷାଠିଏ ଟଙ୍କା").entries.single().hours)
    }

    @Test fun advanceWithTheNasalDropped() {
        assertEquals(EntryType.ADVANCE, ex("ನೂರು ರೂಪಾಯಿ ಅಡ್ವಸ್ ತಗೊಂಡೆ").entries.single().type)
        assertEquals(EntryType.ADVANCE, ex("ನೂರು ರೂಪಾಯಿ ಅಡ್ವಾಸ್ ತಗೊಂಡೆ").entries.single().type)
    }
}
