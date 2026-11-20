package com.acxelin.hisaabsaathi.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One approximate location fix, used only to work out the worker's state on the phone
 * (core/StateMap). It is not stored and never leaves the phone. Needs ACCESS_COARSE_LOCATION;
 * returns null if it is not granted, location is off, or nothing comes within [timeoutMs].
 *
 * Asks every location source the phone has at the same time (network, fused, GPS) and takes
 * the first answer: some phones (and the emulator) only answer from one of them.
 */
object OneLocation {
    @SuppressLint("MissingPermission")
    suspend fun get(context: Context, timeoutMs: Long = 20_000): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val enabled = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val known = (enabled + LocationManager.PASSIVE_PROVIDER).distinct()
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        // A fix from the last half hour is good enough to know the state.
        if (known != null && System.currentTimeMillis() - known.time < 30 * 60 * 1000L) return known
        val live = enabled.filter { it != LocationManager.PASSIVE_PROVIDER }
        if (live.isEmpty()) return known
        val shot = FirstFix()
        var started = 0
        for (p in live) {
            if (runCatching { lm.requestLocationUpdates(p, 1000L, 0f, shot, Looper.getMainLooper()) }.isSuccess) started++
        }
        if (started == 0) return known
        return try {
            withTimeoutOrNull(timeoutMs) { shot.first.await() } ?: known
        } finally {
            runCatching { lm.removeUpdates(shot) }
        }
    }

    private class FirstFix : LocationListener {
        val first = CompletableDeferred<Location>()
        override fun onLocationChanged(location: Location) { first.complete(location) }
        // Android 10 still calls these; they must exist.
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
}
