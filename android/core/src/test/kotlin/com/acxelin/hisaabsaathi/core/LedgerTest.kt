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
    }
}

class SlmTest {
    private val said = "आज आठ घंटे काम किया एक सौ बीस पीस पांच रुपये रेट तीन सौ एडवांस लिया"

    @Test fun acceptsFaithfulAnswerAndDoesTheMaths() {
        val out = Slm.parse("""Sure: [{"type":"work","hours":8,"pieces":120,"rate":5},{"type":"advance","amount":300}] done""", said)!!
        assertEquals(60000, out[0].amountPaise)
        assertEquals(DraftEntry(EntryType.ADVANCE, 30000), out[1])
    }

    @Test fun rejectsInventedNumber() {
        assertNull(Slm.parse("""[{"type":"advance","amount":3000}]""", said))
        assertNull(Slm.parse("""[{"type":"work","pieces":120,"rate":5,"amount":700}]""", said))
    }

    @Test fun acceptsCorrectProduct() {
        val out = Slm.parse("""[{"type":"work","pieces":120,"rate":5,"amount":600}]""", said)!!
        assertEquals(60000, out.single().amountPaise)
    }

    @Test fun rejectsGarbage() {
        assertNull(Slm.parse("I cannot help with that", said))
        assertNull(Slm.parse("""[{"type":"loan","amount":300}]""", said))
    }

    @Test fun chooseKeepsRulesWhenModelFails() {
        val rules = RuleBasedExtractor.extract("पांच सौ")
        assertTrue(Slm.needed(rules))
        assertEquals(rules, Slm.choose(rules, "nonsense"))
        val better = Slm.choose(rules, """[{"type":"advance","amount":500}]""")
        assertEquals(EntryType.ADVANCE, better.entries.single().type)
        assertTrue(!Slm.needed(RuleBasedExtractor.extract(said)))
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
