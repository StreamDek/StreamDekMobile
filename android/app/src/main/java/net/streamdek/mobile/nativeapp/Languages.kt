package net.streamdek.mobile.nativeapp

import java.util.Locale

/** A language a viewer can choose, as a stable code and the name shown for it. */
data class AppLanguage(val code: String, val label: String)

/**
 * Every language the app offers, and the tools for recognising one however it was written down.
 *
 * The list is built from the JVM's own ISO 639-1 table rather than typed out. A hand-written list is
 * how the app ended up offering nine languages for audio and a different nine for subtitles, with
 * everything else falling back to English — and any list short enough to type is short enough to be
 * wrong for somebody. Built this way it covers every ISO language, names them in the viewer's own
 * display language, and cannot drift from the codes the players and add-ons actually use.
 *
 * Recognition is the harder half. The same language arrives as "en", "eng", "en-US", "English" and
 * "English (SDH)" depending on whether it came from a container, a add-on, a file name or a person,
 * so [normalize] accepts all of those and answers with the two-letter code.
 */
object Languages {
  /** Play whatever the release was made in — not a language, a decision to leave it alone. */
  const val ORIGINAL = "original"

  /** No subtitles at all. */
  const val NONE = "none"

  /**
   * ISO 639-2/B codes that differ from the JVM's 639-2/T answer.
   *
   * Both are in the wild — containers written by different tools disagree — so a French track is
   * "fra" to one and "fre" to another, and matching only the JVM's answer misses half of them.
   */
  private val bibliographicCodes = mapOf(
    "sq" to "alb", "hy" to "arm", "eu" to "baq", "my" to "bur", "zh" to "chi", "cs" to "cze",
    "nl" to "dut", "fr" to "fre", "ka" to "geo", "de" to "ger", "el" to "gre", "is" to "ice",
    "mk" to "mac", "mi" to "mao", "ms" to "may", "fa" to "per", "ro" to "rum", "sk" to "slo",
    "bo" to "tib", "cy" to "wel",
  )

  /** Extra spellings that are not ISO anything but turn up constantly in subtitle listings. */
  private val aliases = mapOf(
    "pob" to "pt", "pt-br" to "pt", "ptbr" to "pt", "brazilian" to "pt", "brazilian portuguese" to "pt",
    "spl" to "es", "es-la" to "es", "latin american spanish" to "es", "castilian" to "es",
    "zh-cn" to "zh", "zh-tw" to "zh", "zht" to "zh", "zhs" to "zh", "mandarin" to "zh", "cantonese" to "zh",
    "gre" to "el", "in" to "id", "iw" to "he", "ji" to "yi", "sh" to "sr", "cmn" to "zh",
    "und" to "", "unknown" to "", "undetermined" to "",
  )

  /** Every ISO 639-1 language, named for display and sorted the way a person would look for it. */
  val all: List<AppLanguage> by lazy {
    Locale.getISOLanguages()
      .map { code -> AppLanguage(code, Locale(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }) }
      .distinctBy { it.code }
      .sortedBy { it.label.lowercase() }
  }

  private val byCode: Map<String, AppLanguage> by lazy { all.associateBy { it.code } }

  /** Everything that could name a language, mapped to its two-letter code. */
  private val recognised: Map<String, String> by lazy {
    buildMap {
      all.forEach { language ->
        put(language.code, language.code)
        put(language.label.lowercase(), language.code)
        runCatching { Locale(language.code).isO3Language }.getOrNull()
          ?.takeIf { it.isNotBlank() }
          ?.let { put(it, language.code) }
        // The language's own name for itself, so "Deutsch" and "Español" resolve too.
        runCatching { Locale(language.code).getDisplayLanguage(Locale(language.code)) }.getOrNull()
          ?.takeIf { it.isNotBlank() }
          ?.let { put(it.lowercase(), language.code) }
      }
      bibliographicCodes.forEach { (code, bibliographic) -> put(bibliographic, code) }
      aliases.forEach { (spelling, code) -> put(spelling, code) }
    }
  }

  /**
   * The two-letter code for however a language was written, or "" when it says nothing.
   *
   * Region and variant suffixes are dropped: a viewer choosing Portuguese means Portuguese, and no
   * subtitle list is worth splitting into pt and pt-BR when both are the same choice to them.
   * Parenthesised notes ("English (SDH)", "Spanish [forced]") are stripped for the same reason.
   */
  fun normalize(raw: String?): String {
    val value = raw?.trim()?.lowercase().orEmpty()
    if (value.isEmpty()) return ""
    if (value == ORIGINAL || value == NONE) return value
    recognised[value]?.let { return it }
    // "english (sdh)", "spanish [forced]", "português - brasil"
    val stripped = value.substringBefore('(').substringBefore('[').substringBefore(" - ")
      .replace(Regex("""\s+(?:cc|sdh|forced|hearing[ _-]?impaired)\b.*$"""), "")
      .trim()
    recognised[stripped]?.let { return it }
    // "en-US", "pt_BR", "zh-Hans-CN"
    val base = stripped.split('-', '_').firstOrNull()?.trim().orEmpty()
    recognised[base]?.let { return it }
    return base.takeIf { it.length == 2 && byCode.containsKey(it) }.orEmpty()
  }

  /**
   * Every tag a player or container might use for this language, for matching against tracks.
   *
   * Both three-letter forms are included — see [bibliographicCodes].
   */
  fun tags(code: String?): List<String> {
    val normalized = normalize(code)
    if (normalized.isEmpty() || normalized == ORIGINAL || normalized == NONE) return emptyList()
    return buildList {
      add(normalized)
      runCatching { Locale(normalized).isO3Language }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::add)
      bibliographicCodes[normalized]?.let(::add)
    }.distinct()
  }

  /** The name to show for a code, including the two that are not languages. */
  fun label(code: String?): String = when (val normalized = normalize(code)) {
    ORIGINAL -> "Original language"
    NONE -> "None"
    "" -> "Unknown"
    else -> byCode[normalized]?.label ?: normalized.uppercase()
  }

  /** Whether two language names or codes mean the same language. */
  fun matches(first: String?, second: String?): Boolean {
    val a = normalize(first)
    val b = normalize(second)
    return a.isNotEmpty() && a == b
  }

  /** Choices for an audio preference: leave it alone, or pick a language. */
  fun audioOptions(): List<String> = listOf(ORIGINAL) + all.map { it.code }

  /** Choices for a subtitle preference; the secondary one may also be "None". */
  fun subtitleOptions(includeNone: Boolean): List<String> =
    (if (includeNone) listOf(NONE) else emptyList()) + all.map { it.code }
}
