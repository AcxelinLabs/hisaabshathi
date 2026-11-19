package com.acxelin.hisaabsaathi.recorder

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.sin

/** A microphone that "hears" one second of a tone. */
class FakeMic : Mic {
    override fun start(onLevel: (Float) -> Unit) { onLevel(0.5f) }
    override fun stop(): ShortArray = ShortArray(16_000) { (8000 * sin(it * 0.06)).toInt().toShort() }
}

/** An agent records a speaker's consent and two questions, then shares the session's zip. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class RecorderFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) = rule.onRoot().captureRoboImage("build/screens/$name.png")
    private fun click(text: String) {
        val node = rule.onNodeWithText(text)
        runCatching { node.performScrollTo() } // only inside scrolling screens
        node.performClick(); rule.waitForIdle()
    }

    @Test fun recordAndExportOneSession() {
        RecorderViewModel.micFactory = { FakeMic() }
        org.robolectric.Shadows.shadowOf(rule.activity.application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        rule.onNode(hasSetTextAction()).performTextInput("Ravi")
        shot("r1-home")
        click("New speaker")
        for (t in listOf("Woman", "26–35", "Odisha", "Quiet room", "10")) click(t)
        shot("r2-new-speaker")
        click("Next: their consent")
        rule.onNodeWithText("Read this to them:").assertExists()
        shot("r3-consent")
        rule.onNodeWithTag("record").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("record").performClick(); rule.waitForIdle()
        click("Save their yes")

        // Question 1 and 2.
        rule.onNodeWithText("They read:").assertExists()
        shot("r4-question")
        repeat(2) { i ->
            rule.onNodeWithTag("record").performClick(); rule.waitForIdle()
            if (i == 0) shot("r5-recording")
            rule.onNodeWithTag("record").performClick(); rule.waitForIdle()
            if (i == 0) shot("r6-recorded")
            click("Save & next")
        }
        click("Finish")
        rule.onNodeWithText("✓ 2 of 10 questions recorded").assertExists()
        shot("r7-done")
        click("Share zip")
        val exports = File(rule.activity.cacheDir, "exports")
        rule.waitUntil(10_000) { exports.listFiles().orEmpty().any { it.name.endsWith(".zip") } }

        val zip = exports.listFiles()!!.single { it.name.endsWith(".zip") }
        zip.copyTo(File("build/recorder-export-sample.zip"), overwrite = true) // for tools/voices.py checks
        ZipFile(zip).use { z ->
            val names = z.entries().asSequence().map { it.name }.toList()
            val base = names.first { it.endsWith("metadata.csv") }.removeSuffix("metadata.csv")
            assertTrue(base, base.matches(Regex("data/hi/hi-\\d{8}-\\d{4}-S[A-Z0-9]{4}/")))
            assertTrue(names.containsAll(listOf(base + "recordings.xlsx", base + "session.json", base + "consent.wav")))
            val wavs = names.filter { it.startsWith(base + "audio/") }
            assertEquals(2, wavs.size)
            assertTrue(wavs.first(), wavs.first().matches(Regex(".*/audio/hi_\\d{3}__S[A-Z0-9]{4}__1\\.wav")))
            val csv = z.getInputStream(z.getEntry(base + "metadata.csv")).readBytes().toString(Charsets.UTF_8).trim().lines()
            assertEquals(VoiceSamples.columns, VoiceSamples.parseCsvLine(csv[0]))
            assertEquals(3, csv.size)
            val row = VoiceSamples.columns.zip(VoiceSamples.parseCsvLine(csv[1])).toMap()
            assertEquals("hi", row["lang"]); assertEquals("female", row["gender"]); assertEquals("26-35", row["age"])
            assertEquals("Odisha", row["state"]); assertEquals("quiet_room", row["place"]); assertEquals("Ravi", row["agent"])
            assertEquals("1.00", row["duration_s"])
            assertTrue(row["file_name"]!!, row["file_name"]!!.startsWith("audio/") && base + row["file_name"] in names)
            assertTrue(row["prompt_text"]!!.isNotBlank())
        }
        // The session shows as shared on the home screen.
        click("Home")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("shared ✓", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("r8-home-after")
    }
}
