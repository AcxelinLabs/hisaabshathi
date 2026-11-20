package com.acxelin.hisaabsaathi.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acxelin.hisaabsaathi.R
import com.acxelin.hisaabsaathi.core.PhotoKind
import com.acxelin.hisaabsaathi.core.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** The picture for each kind of photo, the same ones the ledger uses. */
internal fun photoIcon(k: PhotoKind): Int = when (k) {
    PhotoKind.BILL -> R.drawable.ic_expense
    PhotoKind.ADVANCE, PhotoKind.LOAN -> R.drawable.ic_advance
    PhotoKind.SALARY -> R.drawable.ic_payment
    PhotoKind.BANK, PhotoKind.OTHER -> R.drawable.ic_camera
}

/** "What is this a photo of?": six big choices, then the camera opens. */
@Composable
fun PhotoKindDialog(s: Strings, onDismiss: () -> Unit, onPick: (PhotoKind) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paper).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.photoWhat, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, s.cancel, tint = InkSoft) }
            }
            for (k in PhotoKind.entries) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp)).background(Chalk)
                        .clickable { onPick(k) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(if (k.entryType == null) InkWash else Marigold), contentAlignment = Alignment.Center) {
                        Icon(painterResource(photoIcon(k)), null, tint = Ink, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(s.photoKindName(k), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** All photos: of saved lines, and papers kept on their own (bank statements, loans…). */
@Composable
fun PhotosScreen(vm: AppViewModel, s: Strings, onBack: () -> Unit, onTakePhoto: () -> Unit) {
    BackHandler(onBack = onBack)
    val photos by vm.photos.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<AppViewModel.Photo?>(null) }
    Column(Modifier.fillMaxSize().background(Chalk)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.cancel, tint = Ink) }
            Text(s.photosTitle, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        }
        Button(
            onClick = onTakePhoto, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Marigold, contentColor = Ink),
        ) {
            Icon(painterResource(R.drawable.ic_camera), null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.addPhoto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(s.photosEmpty, fontSize = 17.sp, color = InkSoft, textAlign = TextAlign.Center)
            }
        } else {
            LazyVerticalGrid(
                GridCells.Adaptive(150.dp), Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(photos, key = { it.path }) { p -> PhotoTile(p, s) { open = p } }
            }
        }
    }
    open?.let { p -> PhotoViewer(p, s, onClose = { open = null }, onDelete = if (p.entry == null) ({ vm.deletePaper(p.path); open = null }) else null) }
}

@Composable
private fun PhotoTile(p: AppViewModel.Photo, s: Strings, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Paper).clickable(onClickLabel = s.viewPhoto, onClick = onClick)) {
        val bmp = rememberPhoto(p.path, 400)
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(InkWash), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(s.photoKindName(p.kind), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 18.sp)
            val line = p.entry?.let { " · " + s.rupees(it.amountPaise) }.orEmpty()
            Text(dayOf(p.takenAtMs) + line, fontSize = 13.sp, color = InkSoft)
        }
    }
}

/** One photo, whole and large. Papers can be deleted here; a line's photo goes with its line. */
@Composable
fun PhotoViewer(p: AppViewModel.Photo, s: Strings, onClose: () -> Unit, onDelete: (() -> Unit)?) {
    var confirm by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().background(Ink).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, s.cancel, tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(s.photoKindName(p.kind), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(dayOf(p.takenAtMs) + (p.entry?.let { " · " + s.rupees(it.amountPaise) } ?: ""), color = Color(0xFFB5CDBB), fontSize = 14.sp)
                }
                if (onDelete != null) IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, s.delete, tint = Color.White) }
            }
            val bmp = rememberPhoto(p.path, 2000)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (bmp != null) Image(bmp, s.photoKindName(p.kind), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }
    if (confirm && onDelete != null) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(s.deletePhotoQ) },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text(s.delete, color = Red) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(s.cancel) } },
    )
}

private fun dayOf(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** A photo, made small enough for [maxPx] and turned the right way up, read off the main thread. */
@Composable
internal fun rememberPhoto(path: String, maxPx: Int): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, path, maxPx) {
        value = withContext(Dispatchers.IO) { runCatching { decodePhoto(File(path), maxPx)?.asImageBitmap() }.getOrNull() }
    }
    return bmp
}

internal fun decodePhoto(f: File, maxPx: Int): Bitmap? {
    if (!f.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val degrees = when (runCatching { ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(0)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    return if (degrees == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
}
