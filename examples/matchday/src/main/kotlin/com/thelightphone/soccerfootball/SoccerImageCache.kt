package com.thelightphone.soccerfootball

import java.io.File

/** Re-download a cached image after this long — same 30 days the proxy itself uses before
 * refreshing its own copy (soccer-pro-proxy's IMAGE_REFRESH_SECONDS), so a rebrand or a new
 * season's headshot eventually reaches the phone too. */
private const val IMAGE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

/** Cap on the cache folder's total size. A full season of browsing every lineup across every
 * followed competition could otherwise grow it toward the proxy's ~170MB full set; past this, the
 * least-recently-written files are dropped first. Typical use (a few followed leagues) stays far
 * below it. */
private const val IMAGE_CACHE_MAX_BYTES = 40L * 1024 * 1024

/**
 * On-device copies of crests, league badges, and player headshots, so each image is downloaded
 * once rather than on every Scores refresh or Match Detail open. One file per image in [dir]
 * (app-private storage), named from the proxy's own `/img/{kind}/{id}.png` path — see
 * [ApiFootballApi.fetchImageBytes] for the read/write flow and [imageCacheKey] for the naming.
 *
 * Blocking file I/O throughout — callers run it on `Dispatchers.IO`.
 */
internal class ImageDiskCache(private val dir: File) {
    private var writesSincePrune = 0

    /** Cached bytes if present and younger than [IMAGE_MAX_AGE_MS], else null. */
    fun readFresh(key: String): ByteArray? {
        val file = File(dir, key)
        val age = System.currentTimeMillis() - file.lastModified()
        return if (file.isFile && age < IMAGE_MAX_AGE_MS) file.readBytesOrNull() else null
    }

    /** Cached bytes regardless of age — the offline fallback when a re-download fails. */
    fun readAny(key: String): ByteArray? = File(dir, key).takeIf { it.isFile }?.readBytesOrNull()

    fun write(key: String, bytes: ByteArray) {
        runCatching {
            dir.mkdirs()
            // Per-thread temp name: two concurrent downloads of the same image never share one.
            val tmp = File(dir, "$key.${Thread.currentThread().id}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(File(dir, key))) tmp.delete()
        }
        // Checking the folder's size means listing every file in it, so only every so often.
        if (++writesSincePrune >= 50) {
            writesSincePrune = 0
            prune()
        }
    }

    /** Drops the oldest files until the folder is back under ~80% of [IMAGE_CACHE_MAX_BYTES]. */
    fun prune() {
        runCatching {
            val files = dir.listFiles()?.filter { it.isFile } ?: return
            var total = files.sumOf { it.length() }
            if (total <= IMAGE_CACHE_MAX_BYTES) return
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= IMAGE_CACHE_MAX_BYTES * 8 / 10) break
                total -= file.length()
                file.delete()
            }
        }
    }

    private fun File.readBytesOrNull(): ByteArray? = runCatching { readBytes() }.getOrNull()
}

/** `https://…/img/players/276.png` → `players_276.png`. Null for any URL that isn't one of the
 * proxy's image routes — those are fetched without being cached, rather than risk building a file
 * name out of an arbitrary URL. */
internal fun imageCacheKey(proxyImageUrl: String): String? {
    val path = proxyImageUrl.substringAfter("$PROXY_IMAGE_BASE/", missingDelimiterValue = "")
    val kind = path.substringBefore('/')
    val id = path.substringAfter('/').removeSuffix(".png")
    val valid = kind in PROXIED_IMAGE_KINDS && path.endsWith(".png") && id.isNotEmpty() && id.all { it.isDigit() }
    return if (valid) "${kind}_$id.png" else null
}
