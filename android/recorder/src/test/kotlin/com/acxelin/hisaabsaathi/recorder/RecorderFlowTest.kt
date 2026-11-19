package com.acxelin.hisaabsaathi.recorder

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.core.VoiceSamples
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.sin

/** A microphone that "hears" one second of speech-loud tone. */
class FakeMic : Mic {
    override fun start(onLevel: (Float) -> Unit) { onLevel(0.5f) }
    override fun stop(): ShortArray = ShortArray(16_000) { (8000 * sin(it * 0.06)).toInt().toShort() }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class RecorderFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun setUp() {
        RecorderViewModel.micFactory = { FakeMic() }
        org.robolectric.Shadows.shadowOf(rule.activity.application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
    }

    private fun shot(name: String) = rule.onRoot().captureRoboImage("build/screens/$name.png")
    private fun click(text: String) {
        val node = rule.onNodeWithText(text)
        runCatching { node.performScrollTo() } // only inside scrolling screens
        node.performClick(); rule.waitForIdle()
    }
    private fun record() {
        rule.onNodeWithTag("record").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("record").performClick(); rule.waitForIdle()
    }
    private fun exportedZip(): File {
        val exports = File(rule.activity.cacheDir, "exports")
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[RecorderViewModel::class.java]
        try {
            rule.waitUntil(10_000) { exports.listFiles().orEmpty().any { it.name.endsWith(".zip") } }
        } catch (e: Throwable) {
            throw AssertionError("no zip; message: ${vm.message.value}; busy ${vm.busy.value}; screen ${vm.screen.value}; sessions ${vm.sessions.value.map { it.id + ":" + it.recordings.size + ":" + it.set }}; app ${vm.getApplication<android.app.Application>() === rule.activity.application}; files: ${exports.listFiles()?.map { it.name }}", e)
        }
        return exports.listFiles()!!.filter { it.name.endsWith(".zip") }.maxBy { it.lastModified() }
    }
    private fun rowsOf(zip: File): Pair<List<String>, List<Map<String, String>>> = ZipFile(zip).use { z ->
        val names = z.entries().asSequence().map { it.name }.toList()
        val csv = names.first { it.endsWith("metadata.csv") }
        val lines = z.getInputStream(z.getEntry(csv)).readBytes().toString(Charsets.UTF_8).trim().lines()
        assertEquals(VoiceSamples.columns, VoiceSamples.parseCsvLine(lines[0]))
        names to lines.drop(1).map { VoiceSamples.columns.zip(VoiceSamples.parseCsvLine(it)).toMap() }
    }

    /** First speaker: every step, consent, two questions, an unsaved take on leaving, and the zip. */
    @Test fun firstSpeakerRecordAndExport() {
        rule.onNode(hasSetTextAction()).performTextInput("Ravi")
        shot("r1-home")
        click("New speaker")
        rule.onNodeWithText("Next").assertIsNotEnabled()
        for (t in listOf("Woman", "26–35")) click(t)
        shot("r2-step1-speaker")
        click("Next")
        for (t in listOf("Odisha", "Quiet room")) click(t)
        shot("r3-step2-place")
        click("Next")
        click("In order"); click("10")
        shot("r4-step3-questions")
        click("Start")
        rule.onNodeWithText("Read this to them:").assertExists()
        shot("r5-consent")
        record(); click("Save their yes")

        rule.onNodeWithText("They read:").assertExists()
        shot("r6-question")
        record()
        rule.onNodeWithText("✓ Sounds good").assertExists()
        shot("r7-recorded")
        click("Save & next")
        // A recording not saved is never lost silently.
        record()
        click("Skip →")
        rule.onNodeWithText("Keep this recording?").assertExists()
        shot("r8-keep-dialog")
        click("Save it")
        rule.onNodeWithText("Question 3 of 10", substring = true).assertExists()
        click("Finish")
        rule.onNodeWithText("✓ 2 of 10 questions recorded").assertExists()
        shot("r9-done")
        click("Share zip")

        val zip = exportedZip()
        zip.copyTo(File("build/recorder-export-sample.zip"), overwrite = true) // for tools/voices.py checks
        val (names, rows) = rowsOf(zip)
        val base = names.first { it.endsWith("metadata.csv") }.removeSuffix("metadata.csv")
        assertTrue(base, base.matches(Regex("data/hi/hi-\\d{8}-\\d{4}-S[A-Z0-9]{4}/")))
        assertTrue(names.containsAll(listOf(base + "recordings.xlsx", base + "session.json", base + "consent.wav", base + "questions.jsonl")))
        assertEquals(2, names.count { it.startsWith(base + "audio/") })
        assertEquals(listOf("hi_001", "hi_002"), rows.map { it["prompt_id"] })
        val row = rows[0]
        assertEquals("hisaabsaathi", row["question_set"]); assertEquals("female", row["gender"]); assertEquals("26-35", row["age"])
        assertEquals("Odisha", row["state"]); assertEquals("quiet_room", row["place"]); assertEquals("Ravi", row["agent"])
        assertEquals("1.00", row["duration_s"]); assertEquals("good", row["quality"])
        assertTrue(base + row["file_name"] in names)

        click("Home")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("shared ✓", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("r10-home-after")
    }

    /** The next speaker starts with empty gender and age; the remembered place skips a step; anything can be recorded. */
    @Test fun nextSpeakerRecordsAnythingWithTypedText() {
        rule.onNode(hasSetTextAction()).performTextInput("Ravi")
        // First speaker sets the place.
        click("New speaker"); click("Man"); click("36–50"); click("Next"); click("Telangana"); click("Worksite"); click("Next")
        click("10"); click("Start"); record(); click("Save their yes"); record(); click("Save & next"); click("Finish")
        click("Next speaker")
        // A new person: nothing about the last speaker is pre-filled.
        rule.onNodeWithText("Next").assertIsNotEnabled()
        click("Woman"); click("18–25")
        rule.onNodeWithText("Next").assertIsEnabled()
        click("Next")
        // Place remembered: straight to the questions.
        rule.onNodeWithText("Change place").assertExists()
        click("Record anything (no list)")
        shot("r11-record-anything")
        click("Start"); record(); click("Save their yes")
        rule.onNode(hasSetTextAction()).performTextInput("आज पाँच सौ मिले")
        record(); click("Save & next"); click("Finish"); click("Share zip")
        val (_, rows) = rowsOf(exportedZip())
        assertEquals(1, rows.size)
        assertEquals("free", rows[0]["question_set"]); assertEquals("free_001", rows[0]["prompt_id"])
        assertEquals("आज पाँच सौ मिले", rows[0]["said_text"]); assertEquals("female", rows[0]["gender"]); assertEquals("Telangana", rows[0]["state"])
    }

    /** A list loaded from the phone becomes a choice and is used. */
    @Test fun loadedListIsUsed() {
        val f = File(rule.activity.cacheDir, "my questions.csv").apply { writeText("text,lang\nइस हफ्ते कितना कमाया,hi\nhow much today,\n") }
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[RecorderViewModel::class.java]
        rule.onNode(hasSetTextAction()).performTextInput("Ravi")
        click("New speaker")
        rule.runOnIdle { vm.importSet(android.net.Uri.fromFile(f)) }
        rule.waitForIdle()
        click("OK")
        assertEquals(2, vm.questionsOf(vm.form.value.set, "hi", PART_ALL).size)
        assertTrue(vm.form.value.set, vm.form.value.set != SET_BUILTIN)
    }
}
