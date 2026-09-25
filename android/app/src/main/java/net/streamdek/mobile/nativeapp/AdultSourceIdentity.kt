package net.streamdek.mobile.nativeapp

/** Bounded identity normalisation shared by Mobile and TV. No URLs leave the device. */
internal object AdultSourceIdentity {
  private val marks = Regex("[\\p{M}\\p{Cf}]")
  private val gaps = Regex("[^\\p{L}\\p{N}]+")
  private val entities = Regex("&#(x[0-9a-fA-F]{1,6}|[0-9]{1,7});")
  private val repeatedLetters = Regex("([a-z])\\1+")
  private val aliases = setOf("pornmz", "xprimehub", "yespornplease", "perverzija", "brazzers", "pornhd", "eporner", "porntrex",
    "pornhub", "xvideos", "xhamster", "youporn", "redtube", "spankbang", "xnxx", "chaturbate", "camsoda", "stripchat", "livejasmin", "bangbros")
    .flatMap { listOf(it, it + "provider", it + "plugin") }.map { it.replace(repeatedLetters, "$1") }.toSet()
  private val prefixes = aliases.flatMap { id -> (1..id.length).map { id.take(it) } }.toSet()
  private val confusables = mapOf('а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'х' to 'x', 'у' to 'y', 'і' to 'i', 'ј' to 'j',
    'α' to 'a', 'ε' to 'e', 'ο' to 'o', 'ρ' to 'p', 'χ' to 'x')
  fun normalize(raw: String): String {
    var value = raw.take(4096)
    repeat(2) { value = runCatching { java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value) }
    value = entities.replace(value) { match ->
      val code = match.groupValues[1]
      val n = if (code.startsWith("x", true)) code.drop(1).toIntOrNull(16) else code.toIntOrNull()
      if (n != null && n in 0..0x10ffff) String(Character.toChars(n)) else " "
    }
    value = value.replace(Regex("&(?:amp|nbsp|quot|apos|lt|gt);", RegexOption.IGNORE_CASE), " ")
    value = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKD).replace(marks, "").lowercase(java.util.Locale.ROOT)
    return value.map { confusables[it] ?: it }.joinToString("").replace(gaps, " ").trim()
  }
  private fun skeleton(value: String) = value.replace('0', 'o').replace('1', 'i').replace('3', 'e').replace('4', 'a').replace('5', 's').replace('7', 't')
  fun matches(raw: String): Boolean {
    if (adultRepository(raw)) return true
    val words = normalize(raw).split(' ').take(256)
    for (i in words.indices) {
      var joined = ""
      for (j in i until minOf(words.size, i + 16)) {
        joined = (joined + skeleton(words[j])).replace(repeatedLetters, "$1")
        if (joined !in prefixes) break
        if (joined in aliases) return true
      }
    }
    return false
  }
  fun adultRepository(raw: String): Boolean = raw.startsWith("http", ignoreCase = true) && runCatching {
    val uri = java.net.URI(raw)
    if (uri.host?.lowercase(java.util.Locale.ROOT) !in setOf("github.com", "raw.githubusercontent.com")) false
    else uri.path.lowercase(java.util.Locale.ROOT).split('/').filter { it.isNotBlank() }.take(2).joinToString("/") in
      setOf("phisher98/cxxx", "owenconnorz/xxx", "punpunsx/cloudstream-18plus-extensions", "kraptor123/cs-gizlikeyif")
  }.getOrDefault(false)
}
