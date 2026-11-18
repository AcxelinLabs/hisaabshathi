package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Number-reading cases found in real rural speech (Vaani and IndicVoices transcripts, Nov 2026,
 * tools/asr/indic): colloquial words for "one", numbers with a counting suffix written as one
 * word, and everyday words that were being read as numbers.
 */
class RealSpeechNumbersTest {
    private fun nums(s: String) = NumberWords.numbersIn(s)

    @Test fun colloquialOne() {
        assertEquals(listOf(1.0), nums("ଏଟା ଗୋଟେ ସେଲୁନ୍ ଦୁକାନ୍"))
        assertEquals(listOf(1.0), nums("ଗୁଟେ ପିଲା"))
        assertEquals(listOf(1.0), nums("ଗୋଟାଏ ହାତରେ ବ୍ୟାଗ୍"))
        assertEquals(listOf(1.0), nums("ஒன்னு இருக்கு"))
    }

    @Test fun colloquialOdiaSixteen() {
        assertEquals(listOf(1650.0), nums("ଷୋଳଶହ ପଚାଶ"))
        assertEquals(listOf(16.0), nums("ଷୋଳ ଘଣ୍ଟା"))
    }

    @Test fun odiaCountingSuffix() {
        assertEquals(listOf(2.0), nums("ଦୁଇଜଣ ମହିଳା ଅଛନ୍ତି"))
        assertEquals(listOf(15.0, 20.0), nums("ପନ୍ଦର କୋଡ଼ିଏଟା ବଲ୍")) // "fifteen or twenty balls"
        assertEquals(listOf(4.0), nums("ଚାରିଟା ଦୋକାନ"))
        assertEquals(listOf(5.0), nums("ପାଞ୍ଚଟି ଗଛ"))
        assertEquals(listOf(3.0), nums("ତିନିଜଣ"))
    }

    @Test fun wordsThatAreNotNumbers() {
        assertEquals(listOf(1.0), nums("ଏଇଟି ଗୋଟିଏ ବଜାରର ଦୃଶ୍ୟ"))
        assertEquals(emptyList<Double>(), nums("ଏଟି ଏକ ଅଫିସ୍").filter { it != 1.0 })
        assertEquals(emptyList<Double>(), nums("ଏଇଟେ ଗାଡ଼ି ପାର୍କିଂ"))
        assertEquals(emptyList<Double>(), nums("ଘର ଭିତର ପ୍ରବେଶ କରିବାର"))
        assertEquals(emptyList<Double>(), nums("நாங்கள் வாங்கிட்டு வரோம்"))
        assertEquals(emptyList<Double>(), nums("ಊಟದ ಹೊತ್ತು ಆಯ್ತು"))
    }

    @Test fun englishEightyInOdiaScriptStillWorksForAmounts() {
        assertEquals(listOf(80.0), nums("ଏଇଟି ଟଙ୍କା"))
        assertEquals(listOf(80000.0), nums("ଏଇଟି ହଜାର"))
        assertEquals(listOf(85.0), nums("ଏଇଟି ଫାଇଭ ଟଙ୍କା"))
    }

    @Test fun ledgerSentencesStillRead() {
        assertEquals(12.0, RuleBasedExtractor.extract("ଆଜିବାର ଘଣ୍ଟା କାମ କଲି ଦର ଷାଠିଏ ଟଙ୍କା").entries.single().hours)
        // The language model is shown the same numbers the rules read.
        assertEquals(listOf(12.0, 60.0), RuleBasedExtractor.numbersIn("ଆଜିବାର ଘଣ୍ଟା କାମ କଲି ଦର ଷାଠିଏ ଟଙ୍କା"))
        assertTrue(Slm.prompt("ଆଜିବାର ଘଣ୍ଟା କାମ କଲି ଦର ଷାଠିଏ ଟଙ୍କା")!!.contains("in order: 12, 60"))
        assertEquals(listOf(10.0), nums("ಹತ್ತು ಗಂಟೆ"))
        assertEquals(listOf(4.0), nums("நான்கு மணி நேரம்"))
    }
}
