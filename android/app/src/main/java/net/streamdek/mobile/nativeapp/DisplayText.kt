package net.streamdek.mobile.nativeapp

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset

private val mojibakeMarkers = setOf('\u00C3', '\u00C2', '\u00E2', '\u00F0', '\uFFFD')

internal fun sanitizeDisplayText(value: String?): String? {
  var current = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
  val windows1252 = Charset.forName("windows-1252")
  repeat(8) {
    if (current.none(mojibakeMarkers::contains)) return@repeat
    val candidate = runCatching {
      val bytes = windows1252.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .encode(java.nio.CharBuffer.wrap(current))
      StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(ByteArray(bytes.remaining()).also(bytes::get)))
        .toString()
    }.getOrNull() ?: return@repeat
    if (candidate == current) return@repeat
    current = candidate
  }
  return current.replace('\uFFFD', ' ').replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ").trim()
}

/**
 * The stored form of an audio-language choice.
 *
 * Delegates to [Languages], which knows every ISO language rather than the nine that used to be
 * listed here. That mattered beyond tidiness: anything outside the nine fell through to English, so
 * a viewer who chose Vietnamese got English audio and no indication why.
 */
internal fun normalizePreferredAudioLanguage(value: String?): String =
  when (val normalized = Languages.normalize(value?.trim()?.lowercase().let {
    if (it == "default" || it == "auto") Languages.ORIGINAL else it
  })) {
    Languages.ORIGINAL -> Languages.ORIGINAL
    Languages.NONE, "" -> "en"
    else -> normalized
  }

/** Track tags for one audio-language choice; empty means "leave the release alone". */
internal fun preferredAudioLanguageTags(value: String?): List<String> =
  when (val normalized = normalizePreferredAudioLanguage(value)) {
    Languages.ORIGINAL -> emptyList()
    else -> Languages.tags(normalized)
  }

/**
 * Track tags for a first and second choice, in order of preference.
 *
 * A player asked for several languages takes the earliest one it can satisfy, which is exactly what
 * a secondary preference means: use it when the release carries nothing in the first.
 */
internal fun orderedLanguageTags(primary: String?, secondary: String?): List<String> =
  (preferredAudioLanguageTags(primary) + Languages.tags(secondary)).distinct()

internal fun addonStreamPlaybackIdentity(stream: AddonStream): String {
  stream.url?.trim()?.takeIf(String::isNotBlank)?.let { return "url:${it.lowercase()}" }
  stream.infoHash?.trim()?.takeIf(String::isNotBlank)?.let {
    return "torrent:${it.lowercase()}:${stream.fileIdx ?: -1}:${stream.filename.orEmpty().trim().lowercase()}"
  }
  return listOf(stream.addonId, stream.bingeGroup, stream.filename, stream.name, stream.title)
    .joinToString("|") { it.orEmpty().trim().lowercase() }
}
