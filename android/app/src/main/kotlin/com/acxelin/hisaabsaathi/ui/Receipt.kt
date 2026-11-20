package com.acxelin.hisaabsaathi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.acxelin.hisaabsaathi.core.Entry

/**
 * A number that goes up each time the worker saves a new entry, so the day's bill can print
 * again with the new totals. Entries that were already there when the screen opened (or that
 * load a moment later from the database) do not count.
 */
@Composable
fun rememberPrintKey(entries: List<Entry>): Int {
    val newest = entries.maxOfOrNull { it.createdAtMs } ?: 0L
    var prints by remember { mutableIntStateOf(0) }
    var seen by remember { mutableLongStateOf(newest) }
    LaunchedEffect(newest) {
        if (newest > seen && System.currentTimeMillis() - newest < 15_000) prints++
        if (newest > seen) seen = newest
    }
    return prints
}

/**
 * The day's bill coming out of a billing machine: a glossy, slightly see-through machine top
 * with a deep slit, and the receipt pushed out of it, leaning back in 3D and settling like paper
 * (about 0.9 s). It prints when the home
 * screen opens and again whenever [printKey] changes; otherwise it stays still. With the phone's
 * animations turned off (accessibility), it simply appears.
 */
@Composable
fun PrintedReceipt(printKey: Any, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val motion = remember {
        runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) > 0f
    }
    // 0 = still inside the machine, 1 = fully out. Overshoots a little, then settles, like paper.
    val out = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(printKey) {
        if (!motion) { out.snapTo(1f); return@LaunchedEffect }
        out.snapTo(0f)
        out.animateTo(1f, tween(durationMillis = 900, easing = PaperOut))
    }
    Box(modifier.fillMaxWidth()) {
        // The paper comes out of the slit (about 20 dp down). Only what is out is drawn; it may
        // dip a little below its place while settling.
        Box(
            Modifier.fillMaxWidth().padding(top = 20.dp, start = 12.dp, end = 12.dp)
                .drawWithContent {
                    clipRect(left = -size.width, top = 0f, right = size.width * 2, bottom = size.height * 2) { this@drawWithContent.drawContent() }
                },
        ) {
            Box(
                Modifier.graphicsLayer {
                    translationY = -(1f - out.value) * size.height
                    // 3D: the paper leans back as it is pushed out, and straightens as it comes to rest.
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    cameraDistance = 14f * density
                    rotationX = (1f - out.value).coerceIn(0f, 1f) * 22f
                    rotationZ = (1f - out.value).coerceIn(0f, 1f) * 0.6f
                },
            ) {
                content()
                // The machine's shadow on the paper just below the slit.
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().height(26.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Black.copy(alpha = 0.08f), Color.Transparent))),
                )
            }
        }
        // The billing machine's top: glossy, slightly see-through plastic, so the paper shows faintly inside it.
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(32.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp), clip = false, ambientColor = Ink, spotColor = Ink)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xD94A7F65), Color(0xCC1E4F37), Color(0xB30B241A)),
                    ),
                ),
        ) {
            // A reflection along the top edge, like light on glass.
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 3.dp, start = 18.dp, end = 18.dp).fillMaxWidth().height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.04f)))),
            )
            // The slit the paper comes out of: dark and deep, lit a little at its lower lip.
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp, start = 9.dp, end = 9.dp).fillMaxWidth().height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF071810), Color(0xFF2F5C47)))),
            )
        }
    }
}

/** Fast out of the slot, gently slowing, a slight overshoot, then rest. */
private val PaperOut = CubicBezierEasing(0.22f, 0.9f, 0.3f, 1.06f)
