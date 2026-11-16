package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LedgerTest {
    private val day = LocalDate.of(2026, 11, 18) // a Wednesday
    private fun e(type: EntryType, rupees: Long, d: LocalDate = day, hours: Double? = null, pieces: Int? = null) =
        Entry(epochDay = d.toEpochDay(), createdAtMs = 1, type = type, amountPaise = rupees * 100, hours = hours, pieces = pieces)

    @Test fun totalsAndDue() {
        val t = Ledger.totals(
            listOf(
                e(EntryType.WORK, 600, hours = 8.0, pieces = 120),
                e(EntryType.WORK, 500, day.minusDays(1)),
                e(EntryType.ADVANCE, 300),
                e(EntryType.PAYMENT, 200),
                e(EntryType.EXPENSE, 80),
            ),
        )
        assertEquals(110000, t.earnedPaise)
        assertEquals(60000, t.duePaise)
        assertEquals(42000, t.inHandPaise)
        assertEquals(2, t.daysWorked)
        assertEquals(55000, t.avgDailyEarnedPaise)
        assertEquals(120, t.pieces)
    }

    @Test fun ranges() {
        assertEquals(LocalDate.of(2026, 11, 16).toEpochDay()..LocalDate.of(2026, 11, 22).toEpochDay(), Ledger.range(QueryPeriod.WEEK, day))
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay()..LocalDate.of(2026, 11, 30).toEpochDay(), Ledger.range(QueryPeriod.MONTH, day))
        assertEquals(LocalDate.of(2026, 1, 1).toEpochDay()..LocalDate.of(2026, 12, 31).toEpochDay(), Ledger.range(QueryPeriod.YEAR, day))
    }

    @Test fun hashChainDetectsTampering() {
        var prev = HashChain.GENESIS
        val chain = listOf(e(EntryType.WORK, 600), e(EntryType.ADVANCE, 300), e(EntryType.EXPENSE, 80)).map {
            HashChain.seal(prev, it).also { s -> prev = s.hash }
        }
        assertEquals(-1, HashChain.firstBroken(chain))
        val edited = chain.toMutableList().also { it[1] = it[1].copy(amountPaise = 100) }
        assertEquals(1, HashChain.firstBroken(edited))
        assertEquals(1, HashChain.firstBroken(chain.filterIndexed { i, _ -> i != 1 }))
    }

    @Test fun moneyFormat() {
        assertEquals("600", Money.format(60000))
        assertEquals("12.50", Money.format(1250))
        assertEquals("-300", Money.format(-30000))
        assertEquals("1,20,000", Money.formatGrouped(12000000))
        assertEquals("12,345.50", Money.formatGrouped(1234550))
        assertEquals("999", Money.formatGrouped(99900))
        assertEquals("-1,000", Money.formatGrouped(-100000))
    }
}

class SlmTest {
    private val said = "आज आठ घंटे काम किया एक सौ बीस पीस पांच रुपये रेट तीन सौ एडवांस लिया"

    @Test fun promptListsTheSpokenNumbers() {
        val p = Slm.prompt(said)!!
        assertTrue(p, "in order: 8, 120, 5, 300" in p && p.trimEnd().endsWith("300="))
        assertNull(Slm.prompt("नमस्ते"))
    }

    @Test fun buildsEntriesFromLabelsAndDoesTheMaths() {
        val out = Slm.parse("8=hours\n120 = pieces\n5=rate\n300=advance", said)!!
        assertEquals(DraftEntry(EntryType.WORK, 60000, hours = 8.0, pieces = 120, ratePaise = 500), out[0])
        assertEquals(DraftEntry(EntryType.ADVANCE, 30000), out[1])
    }

    @Test fun rejectsAnswersThatDoNotMatchWhatWasSaid() {
        assertNull(Slm.parse("8=hours\n120=pieces\n5=rate\n3000=advance", said)) // invented number
        assertNull(Slm.parse("8=hours\n120=pieces\n5=rate", said))                 // one missing
        assertNull(Slm.parse("8=hours\n120=pieces\n5=rate\n300=loan", said))       // unknown label
        assertNull(Slm.parse("I cannot help with that", said))
    }

    @Test fun expenseKeepsTheCategoryFromTheWords() {
        val out = Slm.parse("80=expense", "सब्जी पर अस्सी रुपये खर्च किए")!!
        assertEquals(DraftEntry(EntryType.EXPENSE, 8000, category = ExpenseCategory.FOOD), out.single())
    }

    @Test fun chooseKeepsRulesWhenModelFails() {
        val rules = RuleBasedExtractor.extract("पांच सौ")
        assertTrue(Slm.needed(rules))
        assertEquals(rules, Slm.choose(rules, "nonsense"))
        val better = Slm.choose(rules, "500=advance")
        assertEquals(EntryType.ADVANCE, better.entries.single().type)
        assertTrue(!Slm.needed(RuleBasedExtractor.extract(said)))
    }

    @Test fun chatRepliesAreShortened() {
        assertEquals("Delhi is the capital. It is big.", Slm.cleanChat("Delhi is the capital. It is big. It has many people.\nMore."))
        assertNull(Slm.cleanChat("  "))
        assertNull(Slm.cleanChat(null))
    }
}

class WagesTest {
    @Test fun parseAndCompare() {
        val pack = Wages.parse(
            """{"version":1,"updated":"2024-10-01","rates":[
            {"region":"CENTRAL","sector":"Construction","skill":"Unskilled","dailyRupees":783,"effectiveFrom":"2024-10-01","source":"x"},
            {"region":"CENTRAL","sector":"Construction","skill":"Skilled","dailyRupees":954,"effectiveFrom":"2024-10-01","source":"x"}]}""",
        )
        val rates = Wages.forRegion(pack, "Odisha")
        assertEquals(2, rates.size)
        val floor = Wages.floor(rates)!!
        assertEquals(783.0, floor.dailyRupees, 0.0)
        assertEquals(18300, Wages.shortfallPaise(60000, floor))
    }
}

class ReceiptReaderTest {
    @Test fun totalLineWins() {
        val d = ReceiptReader.read("Sri Ganesh Stores\nPh 9876543210\nRice 2kg 120.00\nDal 95.00\nTOTAL 215.00\nThank you")!!
        assertEquals(EntryType.EXPENSE, d.type)
        assertEquals(21500, d.amountPaise)
    }

    @Test fun advanceSlip() {
        val d = ReceiptReader.read("एडवांस पर्ची\nनाम: रमेश\nराशि ₹ 1,500\nदिनांक 12/11/2026")!!
        assertEquals(EntryType.ADVANCE, d.type)
        assertEquals(150000, d.amountPaise)
    }

    @Test fun largestAmountWhenNoTotal() {
        assertEquals(34000, ReceiptReader.read("Bus ticket\n9876543210\n340")!!.amountPaise)
        assertNull(ReceiptReader.read("no numbers here"))
    }
}

class StringsTest {
    @Test fun readbackAndSummaryInEachLanguage() {
        val drafts = RuleBasedExtractor.extract("120 piece, 5 rupaye rate, 300 advance").entries
        for (lang in Lang.entries) {
            val s = Strings(lang)
            val text = s.readback(drafts)
            assertTrue(text, "120" in text && "600" in text && "300" in text)
            val sum = s.summary(QueryPeriod.WEEK, Totals(earnedPaise = 360000, advancePaise = 90000))
            assertTrue(sum, "3600" in sum && "2700" in sum)
        }
        assertEquals("120 pieces × ₹5, 8 hours", Strings(Lang.EN).workDetail(8.0, 120, 500))
    }

    @Test fun confirmWords() {
        assertEquals(true, Confirm.parse("हाँ सही है"))
        assertEquals(false, Confirm.parse("नहीं गलत है"))
        assertEquals(true, Confirm.parse("ହଁ"))
        assertNull(Confirm.parse("पांच सौ"))
    }
}
