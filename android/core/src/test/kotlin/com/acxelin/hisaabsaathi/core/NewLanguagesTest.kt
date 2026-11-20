package com.acxelin.hisaabsaathi.core

import com.acxelin.hisaabsaathi.core.i18n.Kn
import com.acxelin.hisaabsaathi.core.i18n.Ta
import com.acxelin.hisaabsaathi.core.i18n.Te
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewLanguagesTest {
    /** Every string the app can show or speak must have a Telugu, Kannada and Tamil translation. */
    @Test fun noMissingTranslations() {
        val drafts = RuleBasedExtractor.extract("8 hours, 120 pieces at Rs 5, Rs 300 advance, 80 rupees spent on food, 500 rupees wage received").entries
        for (lang in listOf(Lang.TE, Lang.KN, Lang.TA)) {
            val s = Strings(lang)
            for (m in Strings::class.java.methods) {
                if (m.name.startsWith("get") && m.parameterCount == 0 && m.returnType == String::class.java) m.invoke(s)
            }
            EntryType.entries.forEach(s::typeName)
            ExpenseCategory.entries.forEach(s::categoryName)
            QueryPeriod.entries.forEach { p -> s.periodName(p); s.summary(p, Totals()); QueryTopic.entries.forEach { t -> s.answer(Query(p, t), Totals()) } }
            listOf("Unskilled", "Semi-skilled", "Skilled", "Highly skilled").forEach(s::skillName)
            s.readback(drafts); s.workDetail(8.0, 120, 500); s.workDetail(8.0, null, 6000)
            s.voicePackDownloading(40); s.shortfall(100); s.goalProgress(1, 2); s.goalAdvice(1); Chat.entries.forEach { c -> s.chat(c, "16 November 2026") }
            s.shareWaiting(3); s.shareSent(12)
            s.restored(4); s.notEnoughSpace(120)
            s.yourState("Odisha"); s.gettingVoiceReady(40); s.modelBody(617)
            s.minWageSpoken(WageRate("CENTRAL", "x", "Unskilled", 783.0, "", ""))
            assertTrue("$lang is missing: ${s.missing}", s.missing.isEmpty())
            assertTrue(s.readback(drafts), "600" in s.readback(drafts) && "{" !in s.readback(drafts))
        }
    }

    @Test fun printExamples() {
        for ((name, examples) in listOf("TE" to Te.examples, "KN" to Kn.examples, "TA" to Ta.examples)) {
            for ((sentence, meaning) in examples) {
                val e = RuleBasedExtractor.extract(sentence)
                val got = e.query?.toString() ?: e.entries.joinToString("; ") {
                    "${it.type} ${Money.format(it.amountPaise)}" + (it.hours?.let { h -> " h=$h" } ?: "") + (it.pieces?.let { p -> " pcs=$p" } ?: "") +
                        (it.ratePaise?.let { r -> " rate=${Money.format(r)}" } ?: "") + (it.category?.let { c -> " $c" } ?: "") + (if (it.dayOffset != 0) " day${it.dayOffset}" else "")
                }
                println("EX|$name|$meaning|=> $got${if (e.lowConfidence) " (unsure)" else ""}|nums=${NumberWords.numbersIn(sentence)}")
            }
        }
    }
}
