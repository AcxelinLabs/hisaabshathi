package com.acxelin.hisaabsaathi.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.acxelin.hisaabsaathi.R

/**
 * Two short sounds, so the worker knows what the app is doing without looking: a rising note
 * when the mic opens ("speak now") and a falling one when it has heard them ("got it").
 * Played as notification-type sounds; never fail the app.
 */
class Earcons(context: Context) {
    private val pool: SoundPool? = runCatching {
        SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        ).build()
    }.getOrNull()
    private val start = pool?.let { p -> runCatching { p.load(context, R.raw.listen_start, 1) }.getOrNull() }
    private val done = pool?.let { p -> runCatching { p.load(context, R.raw.listen_done, 1) }.getOrNull() }

    /** "Speak now". The caller waits [START_MS] before opening the mic, so the sound is not recorded. */
    fun listening() = play(start)

    /** "Got it": the worker has finished speaking. */
    fun heard() = play(done)

    private fun play(id: Int?) {
        val p = pool ?: return
        if (id == null || id == 0) return
        runCatching { p.play(id, 1f, 1f, 1, 0, 1f) }
    }

    companion object {
        /** Length of the "listening" sound plus a little air. */
        const val START_MS = 220L
    }
}
