package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Food said with an amount is noted with its meal; loans with what they were for. */
class ItemsTest {
    private fun lines(text: String) = RuleBasedExtractor.extract(text).entries
    private fun one(text: String) = lines(text).single()

    @Test fun foodAndMealInEveryLanguage() {
        val said = mapOf(
            "नाश्ते में समोसा और चाय 30 रुपये" to "food=SAMOSA,TEA;meal=BREAKFAST",
            "ଜଳଖିଆରେ ଚା ସିଙ୍ଗଡ଼ା 25 ଟଙ୍କା" to "food=TEA,SAMOSA;meal=BREAKFAST",
            "టిఫిన్ ఇడ్లీ వడ 40 రూపాయలు" to "food=IDLI,VADA;meal=BREAKFAST",
            "ಮಧ್ಯಾಹ್ನ ಊಟ ಅನ್ನ ಸಾರು 60 ರೂಪಾಯಿ" to "food=RICE;meal=LUNCH",
            "இரவு பரோட்டா 50 ரூபாய்" to "food=PARATHA;meal=DINNER",
            "dinner biryani 120 rupees" to "food=BIRYANI;meal=DINNER",
        )
        val wrong = said.mapNotNull { (text, want) ->
            val e = lines(text).singleOrNull()
            val got = e?.let { "${it.type}/${it.category}/${it.detail}" }
            if (e != null && e.type == EntryType.EXPENSE && e.category == ExpenseCategory.FOOD && e.detail == want) null else "$text → $got"
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test fun eachFoodGoesWithItsAmount() {
        val l = lines("चाय 10 रुपये समोसा 20 रुपये")
        assertEquals(listOf(1_000L, 2_000L), l.map { it.amountPaise })
        assertEquals(listOf("food=TEA", "food=SAMOSA"), l.map { it.detail })
    }

    @Test fun loansTakenAndRepaid() {
        one("बाइक लोन की किस्त 2000 रुपये").let {
            assertEquals(EntryType.EXPENSE, it.type); assertEquals(ExpenseCategory.LOAN, it.category); assertEquals("loan=BIKE", it.detail)
        }
        one("car loan emi 5000").let { assertEquals(ExpenseCategory.LOAN, it.category); assertEquals("loan=CAR", it.detail) }
        one("गोल्ड लोन लिया 10000").let { assertEquals(EntryType.ADVANCE, it.type); assertEquals("loan=GOLD", it.detail) }
        one("ମୋବାଇଲ୍ କିସ୍ତି 1500 ଟଙ୍କା").let { assertEquals(ExpenseCategory.LOAN, it.category); assertEquals("loan=PHONE", it.detail) }
    }

    @Test fun shownInTheWorkersLanguage() {
        assertEquals("समोसा, चाय · नाश्ता", Strings(Lang.HI).describe("food=SAMOSA,TEA;meal=BREAKFAST"))
        assertEquals("Bike loan", Strings(Lang.EN).describe("loan=BIKE"))
        assertEquals("ಬೈಕ್ ಸಾಲ", Strings(Lang.KN).describe("loan=BIKE"))
        assertTrue(Strings(Lang.HI).readback(lines("बाइक की किस्त 2000 रुपये")).startsWith("बाइक लोन की किस्त पर खर्च"))
        for (l in Lang.entries) {
            val s = Strings(l)
            s.describe("food=SAMOSA;meal=LUNCH;loan=GROUP"); s.describe("loan=")
            assertEquals("$l: ${s.missing}", emptySet<String>(), s.missing)
        }
    }

    @Test fun plainSpendingIsUnchanged() {
        one("बस टिकट 40 रुपये").let { assertEquals(ExpenseCategory.TRAVEL, it.category); assertEquals("", it.detail) }
        one("300 एडवांस लिया").let { assertEquals(EntryType.ADVANCE, it.type); assertEquals("", it.detail) }
    }
}
