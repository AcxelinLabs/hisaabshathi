package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberWordsTest {
    private fun nums(s: String) = NumberWords.numbersIn(s)

    @Test fun hindiWords() {
        assertEquals(listOf(120.0), nums("एक सौ बीस"))
        assertEquals(listOf(2500.0), nums("दो हजार पांच सौ"))
        assertEquals(listOf(8.0, 120.0, 5.0, 300.0), nums("आठ घंटे एक सौ बीस पीस पाँच रुपये रेट तीन सौ एडवांस"))
        assertEquals(listOf(99.0), nums("निन्यानवे"))
        assertEquals(listOf(100000.0), nums("एक लाख"))
        assertEquals(listOf(125000.0), nums("एक लाख पच्चीस हज़ार"))
    }

    @Test fun hindiFractions() {
        assertEquals(listOf(150.0), nums("डेढ़ सौ"))
        assertEquals(listOf(2500.0), nums("ढाई हजार"))
        assertEquals(listOf(350.0), nums("साढ़े तीन सौ"))
        assertEquals(listOf(8.5), nums("साढ़े आठ घंटे"))
        assertEquals(listOf(125.0), nums("सवा सौ"))
        assertEquals(listOf(175.0), nums("पौने दो सौ"))
    }

    @Test fun digitsInAnyScript() {
        assertEquals(listOf(120.0, 5.0, 300.0), nums("120 piece, ₹5 rate, Rs.300 advance"))
        assertEquals(listOf(1200.0), nums("1,200 रुपये"))
        assertEquals(listOf(350.0), nums("३५० रुपये"))
        assertEquals(listOf(450.0), nums("୪୫୦ ଟଙ୍କା"))
        assertEquals(listOf(2500.0), nums("2 हजार 5 सौ"))
        assertEquals(listOf(12.5), nums("12.50 rupees"))
    }

    @Test fun odiaWords() {
        assertEquals(listOf(200.0), nums("ଦୁଇ ଶହ ଟଙ୍କା"))
        assertEquals(listOf(500.0), nums("ପାଞ୍ଚଶହ ଟଙ୍କା"))
        assertEquals(listOf(120.0), nums("ଏକ ଶହ କୋଡ଼ିଏ"))
        assertEquals(listOf(1500.0), nums("ଏକ ହଜାର ପାଞ୍ଚ ଶହ"))
        assertEquals(listOf(8.0), nums("ଆଠ ଘଣ୍ଟା"))
    }

    @Test fun englishAndRoman() {
        assertEquals(listOf(25.0), nums("twenty five"))
        assertEquals(listOf(325.0), nums("three hundred twenty five"))
        assertEquals(listOf(2000.0), nums("do hazaar"))
        assertEquals(listOf(500.0), nums("paanch sau rupaye"))
        assertEquals(emptyList<Double>(), nums("how much do I get"))
    }

    @Test fun adjacentNumbersStaySeparate() {
        assertEquals(listOf(120.0, 5.0), nums("एक सौ बीस पांच"))
        assertEquals(listOf(8.0, 600.0), nums("आठ छह सौ"))
    }
}

class RuleBasedExtractorTest {
    private fun ex(s: String) = RuleBasedExtractor.extract(s)
    private fun rs(n: Int) = n * 100L

    @Test fun proposalExampleHindi() {
        val e = ex("आज आठ घंटे काम किया, एक सौ बीस पीस, पांच रुपये रेट, तीन सौ एडवांस लिया")
        assertEquals(2, e.entries.size)
        val work = e.entries[0]
        assertEquals(EntryType.WORK, work.type)
        assertEquals(8.0, work.hours!!, 0.0)
        assertEquals(120, work.pieces)
        assertEquals(rs(5), work.ratePaise)
        assertEquals(rs(600), work.amountPaise)
        assertEquals(DraftEntry(EntryType.ADVANCE, rs(300)), e.entries[1])
        assertTrue(!e.lowConfidence)
    }

    @Test fun proposalExampleMixedDigits() {
        val e = ex("8 hours, 120 pieces at Rs 5, Rs 300 advance")
        assertEquals(rs(600), e.entries[0].amountPaise)
        assertEquals(EntryType.ADVANCE, e.entries[1].type)
        assertEquals(rs(300), e.entries[1].amountPaise)
    }

    @Test fun hinglishCodeMixed() {
        val e = ex("120 piece, paanch rupaya rate, 300 advance")
        assertEquals(120, e.entries[0].pieces)
        assertEquals(rs(600), e.entries[0].amountPaise)
        assertEquals(rs(300), e.entries[1].amountPaise)
    }

    @Test fun dailyWageReceived() {
        val e = ex("आज पांच सौ रुपये मजदूरी मिली")
        assertEquals(
            listOf(DraftEntry(EntryType.WORK, rs(500)), DraftEntry(EntryType.PAYMENT, rs(500))),
            e.entries,
        )
    }

    @Test fun dailyWageNotYetPaid() {
        val e = ex("आज की दिहाड़ी छह सौ रुपये")
        assertEquals(listOf(DraftEntry(EntryType.WORK, rs(600))), e.entries)
    }

    @Test fun expenseWithCategory() {
        val e = ex("सब्जी पर अस्सी रुपये खर्च किए")
        assertEquals(listOf(DraftEntry(EntryType.EXPENSE, rs(80), category = ExpenseCategory.FOOD)), e.entries)
    }

    @Test fun advanceFromContractor() {
        val e = ex("ठेकेदार से दो हजार रुपये एडवांस लिया")
        assertEquals(listOf(DraftEntry(EntryType.ADVANCE, rs(2000))), e.entries)
    }

    @Test fun kharchiIsAdvance() {
        assertEquals(EntryType.ADVANCE, ex("दो सौ रुपये खर्ची ली").entries.single().type)
    }

    @Test fun advanceThenExpense() {
        val e = ex("तीन सौ एडवांस लिया और अस्सी रुपये सब्जी पर खर्च किए")
        assertEquals(
            listOf(
                DraftEntry(EntryType.ADVANCE, rs(300)),
                DraftEntry(EntryType.EXPENSE, rs(80), category = ExpenseCategory.FOOD),
            ),
            e.entries,
        )
    }

    @Test fun yesterday() {
        val e = ex("कल सात घंटे काम किया चार सौ रुपये मजदूरी")
        assertEquals(-1, e.entries.single().dayOffset)
        assertEquals(7.0, e.entries.single().hours!!, 0.0)
        assertEquals(rs(400), e.entries.single().amountPaise)
    }

    @Test fun hourlyRate() {
        val e = ex("आठ घंटे काम, रेट साठ रुपये")
        assertEquals(rs(480), e.entries.single().amountPaise)
    }

    @Test fun hoursOnlyIsLowConfidence() {
        val e = ex("आज आठ घंटे काम किया")
        assertEquals(0L, e.entries.single().amountPaise)
        assertTrue(e.lowConfidence)
    }

    @Test fun bareAmountIsLowConfidenceWage() {
        val e = ex("पांच सौ")
        assertEquals(EntryType.WORK, e.entries.single().type)
        assertTrue(e.lowConfidence)
    }

    @Test fun odiaWorkAndAdvance() {
        val e = ex("ଆଜି ଆଠ ଘଣ୍ଟା କାମ କଲି, ପାଞ୍ଚ ଶହ ଟଙ୍କା ମଜୁରୀ, ଦୁଇ ଶହ ଟଙ୍କା ଆଡଭାନ୍ସ ନେଲି")
        assertEquals(8.0, e.entries[0].hours!!, 0.0)
        assertEquals(rs(500), e.entries[0].amountPaise)
        assertEquals(DraftEntry(EntryType.ADVANCE, rs(200)), e.entries[1])
    }

    @Test fun odiaExpense() {
        val e = ex("ପରିବା ପାଇଁ ଅଶୀ ଟଙ୍କା ଖର୍ଚ୍ଚ କଲି")
        assertEquals(listOf(DraftEntry(EntryType.EXPENSE, rs(80), category = ExpenseCategory.FOOD)), e.entries)
    }

    @Test fun queries() {
        assertEquals(Query(QueryPeriod.WEEK, QueryTopic.EARNED), ex("इस हफ्ते कितना कमाया").query)
        assertEquals(Query(QueryPeriod.MONTH, QueryTopic.ADVANCE), ex("इस महीने कितना एडवांस लिया").query)
        assertEquals(Query(QueryPeriod.DAY, QueryTopic.DUE), ex("आज कितना बाकी है").query)
        assertEquals(Query(QueryPeriod.WEEK, QueryTopic.ALL), ex("how much this week").query)
        assertEquals(Query(QueryPeriod.MONTH, QueryTopic.EXPENSE), ex("ଏହି ମାସରେ କେତେ ଖର୍ଚ୍ଚ ହେଲା").query)
        assertEquals(QueryTopic.MIN_WAGE, ex("यहां न्यूनतम मजदूरी कितनी है").query?.topic)
    }

    @Test fun nothingUnderstood() {
        val e = ex("नमस्ते कैसे हो")
        assertTrue(e.isEmpty)
        assertNull(e.query)
    }
}
