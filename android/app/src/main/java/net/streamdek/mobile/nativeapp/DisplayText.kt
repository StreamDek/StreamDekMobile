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

internal fun normalizePreferredAudioLanguage(value: String?): String = when (value?.trim()?.lowercase()) {
  "original", "default", "auto" -> "original"
  "es", "spa", "spanish" -> "es"
  "fr", "fra", "fre", "french" -> "fr"
  "de", "deu", "ger", "german" -> "de"
  "it", "ita", "italian" -> "it"
  "pt", "por", "portuguese", "pt-br", "pob" -> "pt"
  "ja", "jpn", "japanese" -> "ja"
  "ko", "kor", "korean" -> "ko"
  "hi", "hin", "hindi" -> "hi"
  else -> "en"
}

internal fun preferredAudioLanguageTags(value: String?): List<String> = when (normalizePreferredAudioLanguage(value)) {
  "original" -> emptyList()
  "en" -> listOf("en", "eng")
  "es" -> listOf("es", "spa")
  "fr" -> listOf("fr", "fra", "fre")
  "de" -> listOf("de", "deu", "ger")
  "it" -> listOf("it", "ita")
  "pt" -> listOf("pt", "por")
  "ja" -> listOf("ja", "jpn")
  "ko" -> listOf("ko", "kor")
  "hi" -> listOf("hi", "hin")
  else -> listOf("en", "eng")
}

internal fun addonStreamPlaybackIdentity(stream: AddonStream): String {
  stream.url?.trim()?.takeIf(String::isNotBlank)?.let { return "url:${it.lowercase()}" }
  stream.infoHash?.trim()?.takeIf(String::isNotBlank)?.let {
    return "torrent:${it.lowercase()}:${stream.fileIdx ?: -1}:${stream.filename.orEmpty().trim().lowercase()}"
  }
  return listOf(stream.addonId, stream.bingeGroup, stream.filename, stream.name, stream.title)
    .joinToString("|") { it.orEmpty().trim().lowercase() }
}
