package net.streamdek.mobile.nativeapp

/**
 * Keeps pornography out of everything the app shows, whatever produced it.
 *
 * Catalogues, search, live channels and stream lists all arrive from sources nobody here controls
 * — add-ons, plugin scrapers, M3U playlists — so this is applied where results come in rather than
 * trusted to any one of them. The block is on unless the platform says otherwise, and stays on when
 * the platform cannot be reached: a filter that quietly stops filtering when the network is down is
 * worse than none, because nobody notices.
 *
 * Matching is on whole words, not substrings. That distinction is the whole design: "sex" as a
 * substring hides Essex, Sussex and *Sex Education*, and a filter that hides ordinary titles gets
 * switched off, at which point it protects nobody. Every term below is one that does not appear in
 * a mainstream title by accident; anything arguable is left out and can be added by an
 * administrator instead, through [applyPolicy].
 */
object AdultContentFilter {
  /** Compiled once: this runs over every field of every stream each time a result list is ranked. */
  private val wordGap = Regex("[^\\p{L}\\p{N}+]+")


  /**
   * Whether filtering is active. On by default and restored to on whenever the platform policy
   * cannot be read, so an unreachable backend fails closed rather than open.
   */
  private data class PolicyState(val enabled: Boolean = true, val terms: Set<String> = emptySet(), val rules: List<ContentSafetyRule> = emptyList(), val version: String? = null)
  @Volatile private var policy = PolicyState()
  val enabled: Boolean get() = policy.enabled
  val revision: String? get() = policy.version
  private val adminTerms: Set<String> get() = policy.terms

  /**
   * Single words that mean one thing.
   *
   * Deliberately excludes near-misses that carry ordinary meanings — "vixen", "private", "deeper",
   * "shame", "blacked" — which would each cost a legitimate title. Studio names below cover those
   * cases when they appear as the actual brand.
   */
  private val explicitWords = setOf(
    "porn", "porno", "pornos", "pornography", "pornographic", "pornstar", "pornstars",
    "xxx", "hardcore", "softcore", "hentai", "milf", "milfs", "creampie", "blowjob", "handjob",
    "cumshot", "cumshots", "gangbang", "bukkake", "camgirl", "camgirls", "camwhore",
    "deepthroat", "fisting", "footjob", "titjob", "threesome", "orgy", "orgies",
    "masturbation", "masturbating", "squirting", "bareback", "bdsm", "fetish", "upskirt",
    "nudes", "onlyfans", "camsoda", "chaturbate", "stripchat", "livejasmin",
    "pornhub", "xvideos", "xhamster", "youporn", "redtube", "spankbang", "xnxx", "beeg",
    "brazzers", "bangbros", "mofos", "twistys", "pornstarz", "legalporno", "sexart", "metart",
    "wowgirls", "heyzo", "caribbeancom", "tokyohot", "javhd", "jav",
  )

  /**
   * Phrases that only read as adult when the words sit together.
   *
   * "adult time" and "reality kings" are both harmless as separate words, which is exactly why they
   * are matched as phrases against the normalised text rather than token by token.
   */
  private val explicitPhrases = setOf(
    "adult time", "adult film", "adult films", "adult movie", "adult movies", "adult channel",
    "adult video", "adult videos", "adults only", "reality kings", "naughty america",
    "digital playground", "evil angel", "wicked pictures", "marc dorcel", "jules jordan",
    "private black", "elegant angel", "girlfriends films", "bang bros", "x rated", "x art",
    "sex cam", "sex cams", "live sex", "porn star", "porn stars", "blue movie",
  )

  /**
   * Rating and classification markers, matched as whole tokens.
   *
   * "18" alone is not here for the obvious reason; it appears in years, episode counts and titles.
   */
  private val ratingTokens = setOf("r18", "r18+", "xxx")

  /** Category and genre labels that classify the whole thing rather than describe it. */
  private val blockedCategories = setOf("adult", "adults", "xxx", "porn", "pornography", "pornographic", "hentai")

  /**
   * Takes the platform's policy.
   *
   * A null [blockAdult] means the policy could not be read, which leaves the block on.
   */
  @Synchronized
  fun applyPolicy(blockAdult: Boolean?, terms: Collection<String>?, rules: List<ContentSafetyRule>? = null, version: String? = null) {
    val prior = policy
    val next = PolicyState(blockAdult ?: true,
      (terms ?: prior.terms).map { it.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotBlank() }.toSet(),
      rules?.toList() ?: prior.rules, version ?: prior.version)
    policy = next
    if (next != prior) PluginCatalogSearch.clearCache()
  }

  /** Call separately for the repository before its plugin/provider, so exceptions stay scoped. */
  fun isBlockedEntity(scope: String, vararg fields: String?): Boolean {
    val current = policy
    if (!current.enabled) return false
    val values = fields.filterNotNull()
    val matching = current.rules.filter { it.matches(scope, values) }
    if (matching.any { it.status == "ADULT" }) return true
    if (matching.any { it.status == "SAFE" }) return false
    return values.any { matches(it) }
  }

  /**
   * Whether any of [fields] marks this as pornography.
   *
   * Null and blank fields are skipped, so callers can pass everything they have — title, filename,
   * genre, the source's own name — without checking each one first.
   */
  fun isBlocked(vararg fields: String?): Boolean {
    if (!enabled) return false
    return fields.any { field -> field != null && matches(field) }
  }

  /** Whether a catalogue entry should be hidden, including anything the source flagged itself. */
  fun isBlockedItem(
    adultFlag: Boolean = false,
    title: String? = null,
    genres: List<String> = emptyList(),
    vararg extra: String?,
  ): Boolean {
    if (!enabled) return false
    val matching = policy.rules.filter { it.matches("media", listOfNotNull(title) + extra.filterNotNull()) }
    if (matching.any { it.status == "ADULT" }) return true
    if (matching.any { it.status == "SAFE" }) return false
    if (adultFlag) return true
    if (genres.any { genre -> blockedCategories.contains(genre.trim().lowercase()) }) return true
    // A documentary title mentioning pornography is weak evidence, unlike source identity.
    val titleBlocked = title?.let { value ->
      AdultSourceIdentity.matches(value) || adminTerms.any { term ->
        (" " + AdultSourceIdentity.normalize(value) + " ").contains(" " + AdultSourceIdentity.normalize(term) + " ")
      }
    } == true
    return titleBlocked || isBlocked(*extra) || genres.any { genre -> matches(genre) }
  }

  /** Whether a category or genre label is an adult one outright. */
  fun isBlockedCategory(label: String?): Boolean {
    if (!enabled || label.isNullOrBlank()) return false
    val normalized = label.trim().lowercase()
    return blockedCategories.contains(normalized) || matches(label)
  }

  private fun matches(value: String): Boolean {
    if (value.isBlank()) return false
    if (policy.rules.any { it.scope == "*" && it.status == "ADULT" && it.matches("source", listOf(value)) }) return true
    if (AdultSourceIdentity.matches(value)) return true
    // Release names separate words with dots and underscores, so everything that is not a letter,
    // digit or '+' becomes a gap. '+' survives because it carries the meaning in "18+".
    val normalized = value.lowercase().replace(wordGap, " ").trim()
    if (normalized.isEmpty()) return false
    val tokens = normalized.split(' ').filter { it.isNotBlank() }

    if (tokens.any { token -> explicitWords.contains(token) || ratingTokens.contains(token) }) return true
    if (adminTerms.isNotEmpty()) {
      // An administrator's term is matched as a whole word when it is one, and as a phrase when it
      // is several, so adding "adult time" does not also hide every title containing "time".
      val padded = " $normalized "
      if (adminTerms.any { term -> if (term.contains(' ')) padded.contains(" $term ") else tokens.contains(term) }) return true
    }
    val padded = " $normalized "
    return explicitPhrases.any { phrase -> padded.contains(" $phrase ") }
  }
}
