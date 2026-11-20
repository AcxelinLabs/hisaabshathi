package com.acxelin.hisaabsaathi.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acxelin.hisaabsaathi.R
import com.acxelin.hisaabsaathi.core.Strings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shown each time the app opens: the mic in its marigold circle, "Welcome to your
 * HisaabSaathi" in the worker's language, and a short ting. It moves on by itself after a
 * moment, or at a tap.
 */
@Composable
fun WelcomeScreen(s: Strings, playSound: Boolean = true, onDone: () -> Unit) {
    val context = LocalContext.current
    val grow = remember { Animatable(0.82f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (playSound) ting(context)
        launch { fade.animateTo(1f, tween(350)) }
        grow.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        delay(1_150)
        onDone()
    }
    Box(
        Modifier.fillMaxSize().background(Ink)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp).alpha(fade.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.scale(grow.value).size(132.dp).clip(CircleShape).background(Marigold.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(104.dp).clip(CircleShape).background(Marigold), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_mic), null, tint = Ink, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(Modifier.height(36.dp))
            Text(
                s.welcome, color = androidx.compose.ui.graphics.Color.White, fontSize = 30.sp, lineHeight = 40.sp,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(18.dp))
            Box(Modifier.width(56.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Marigold))
        }
    }
}

/** A short, soft bell (res/raw/ting.wav), as a notification-type sound. Never fails the app. */
private fun ting(context: Context) {
    runCatching {
        val player = MediaPlayer()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        context.resources.openRawResourceFd(R.raw.ting).use { player.setDataSource(it) }
        player.setOnCompletionListener { it.release() }
        player.setOnErrorListener { mp, _, _ -> mp.release(); true }
        player.prepare()
        player.start()
    }
}
