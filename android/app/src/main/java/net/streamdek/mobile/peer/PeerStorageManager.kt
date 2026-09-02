package net.streamdek.mobile.peer

import android.content.Context
import java.io.File

class PeerStorageManager(context: Context) {
  private val root = File(context.cacheDir, "streamdek-peer-store").apply { mkdirs() }

  init {
    // The store was called "streamdek-torrent-store" before this was renamed. Left behind it would
    // sit in the cache forever holding gigabytes nothing reads, so it is taken over on first use.
    runCatching {
      val previous = File(context.cacheDir, "streamdek-torrent-store")
      if (previous.exists() && previous.isDirectory) {
        previous.listFiles()?.forEach { entry -> entry.renameTo(File(root, entry.name)) }
        previous.deleteRecursively()
      }
    }
  }

  fun rootPath(): String = root.absolutePath

  fun sessionDirectory(infoHash: String): File {
    return File(root, infoHash.lowercase()).apply { mkdirs() }
  }

  fun touch(infoHash: String) {
    sessionDirectory(infoHash).setLastModified(System.currentTimeMillis())
  }

  fun totalSizeBytes(): Long {
    return root.walkTopDown()
      .filter { it.isFile }
      .fold(0L) { total, file -> total + file.length() }
  }

  /**
   * Deletes every stored torrent that is not currently being played, and says how much that freed.
   *
   * Sessions still in [activeInfoHashes] are left alone on purpose: their files are what a running
   * playback is reading from, and removing them mid-stream would end the playback rather than tidy
   * up after it. In the ordinary case — nobody watching anything — that set is empty and this
   * clears the lot.
   */
  fun clearAll(activeInfoHashes: Set<String>): Long {
    var freed = 0L
    root.listFiles()?.forEach { entry ->
      if (entry.name in activeInfoHashes) return@forEach
      freed += entry.walkTopDown().filter { it.isFile }.fold(0L) { total, file -> total + file.length() }
      entry.deleteRecursively()
    }
    return freed
  }

  fun enforceLimit(cacheSizeGb: Int, activeInfoHashes: Set<String>) {
    if (cacheSizeGb <= 0) {
      root.listFiles()?.forEach { dir ->
        if (dir.name !in activeInfoHashes) {
          dir.deleteRecursively()
        }
      }
      return
    }

    val maxBytes = cacheSizeGb * 1024L * 1024L * 1024L
    var currentBytes = totalSizeBytes()
    if (currentBytes <= maxBytes) return

    val candidates = root.listFiles()
      ?.filter { it.isDirectory && it.name !in activeInfoHashes }
      ?.sortedBy { it.lastModified() }
      ?: emptyList()

    for (dir in candidates) {
      if (currentBytes <= maxBytes) break
      val dirBytes = dir.walkTopDown()
        .filter { it.isFile }
        .fold(0L) { total, file -> total + file.length() }
      dir.deleteRecursively()
      currentBytes -= dirBytes
    }
  }
}
