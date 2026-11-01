package dev.couchtv.launcher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Application-level setup: one image pipeline for the whole app.
 *
 * Posters come from streaming services and are cached on disk (200 MB) so the home screen paints
 * without a network round trip after the first run; icons come from the package manager and are
 * cached in memory. Kept deliberately small for a 1–2 GB TV box.
 */
class CouchTvApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("artwork"))
                .maxSizeBytes(200L * 1024 * 1024)
                .build()
        }
        .crossfade(180)
        .respectCacheHeaders(true)
        .build()
}
