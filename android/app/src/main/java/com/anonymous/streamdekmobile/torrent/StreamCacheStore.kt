package com.anonymous.streamdekmobile.torrent

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class StreamCacheStore(context: Context) {
  private val rootDir = File(context.cacheDir, "streamdek-server-cache")

  init {
    rootDir.mkdirs()
  }

  fun cacheDirectoryPath(): String = rootDir.absolutePath

  @Synchronized
  fun isEnabled(cacheSizeGb: Int): Boolean = cacheSizeGb > 0

  @Synchronized
  fun completedFile(cacheKey: String): File = File(rootDir, "$cacheKey.bin")

  @Synchronized
  fun partFile(cacheKey: String): File = File(rootDir, "$cacheKey.part")

  @Synchronized
  fun hasCompletedFile(cacheKey: String): Boolean = completedFile(cacheKey).isFile

  @Synchronized
  fun openPartOutput(cacheKey: String): FileOutputStream {
    val file = partFile(cacheKey)
    file.parentFile?.mkdirs()
    return FileOutputStream(file, false)
  }

  @Synchronized
  fun commitPart(cacheKey: String, contentType: String?) {
    val part = partFile(cacheKey)
    if (!part.exists()) return

    val complete = completedFile(cacheKey)
    if (complete.exists()) complete.delete()
    if (!part.renameTo(complete)) {
      part.copyTo(complete, overwrite = true)
      part.delete()
    }

    complete.setLastModified(System.currentTimeMillis())
    writeMeta(cacheKey, contentType)
  }

  @Synchronized
  fun abortPart(cacheKey: String) {
    partFile(cacheKey).delete()
  }

  @Synchronized
  fun touch(cacheKey: String) {
    val file = completedFile(cacheKey)
    if (file.exists()) file.setLastModified(System.currentTimeMillis())
  }

  @Synchronized
  fun readContentType(cacheKey: String): String? {
    val meta = metaFile(cacheKey)
    if (!meta.exists()) return null

    val props = Properties()
    FileInputStream(meta).use { props.load(it) }
    return props.getProperty("contentType")
  }

  @Synchronized
  fun enforceLimit(cacheSizeGb: Int) {
    if (cacheSizeGb <= 0) {
      clearAll()
      return
    }

    val limitBytes = cacheSizeGb.toLong() * GIGABYTE_BYTES
    val files = rootDir.listFiles { file -> file.extension == "bin" }
      ?.sortedBy { it.lastModified() }
      ?: return

    var totalBytes = files.sumOf { it.length() }
    if (totalBytes <= limitBytes) return

    for (file in files) {
      if (totalBytes <= limitBytes) break
      val size = file.length()
      file.delete()
      metaFile(file.nameWithoutExtension).delete()
      totalBytes -= size
    }
  }

  @Synchronized
  fun clearAll() {
    rootDir.listFiles()?.forEach { it.deleteRecursively() }
    rootDir.mkdirs()
  }

  @Synchronized
  fun totalSizeBytes(): Long {
    return rootDir.walkTopDown()
      .filter { it.isFile }
      .fold(0L) { total, file -> total + file.length() }
  }

  private fun writeMeta(cacheKey: String, contentType: String?) {
    val props = Properties().apply {
      setProperty("contentType", contentType ?: "application/octet-stream")
    }
    FileOutputStream(metaFile(cacheKey), false).use { props.store(it, null) }
  }

  private fun metaFile(cacheKey: String): File = File(rootDir, "$cacheKey.meta")

  companion object {
    private const val GIGABYTE_BYTES = 1024L * 1024L * 1024L
  }
}
