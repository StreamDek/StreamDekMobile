package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import android.util.Log
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Moving a guest's StreamDek into the profile they have just signed into.
 *
 * Everything a viewer builds up before they have an account - progress, watchlist, add-ons,
 * plugins, playlists, live sources, and the settings around them - is stored against an *owner key*
 * (`guest`, or `guest:<profile>`). Signing in changes which owner key the app reads, and that is
 * the whole of the bug this file exists for: nothing was lost, but everything was suddenly being
 * read from somewhere else, so a new account looked like a factory reset.
 *
 * The migration is a set of small independent steps, each of which:
 *
 * - **merges rather than replaces.** The destination profile may already hold data, from another
 *   device or from an earlier session, and none of it is allowed to disappear because a guest
 *   identity arrived. Lists are unioned on a stable identity, positions are resolved newest-first,
 *   and a setting the profile has already answered for itself is never overwritten.
 * - **is idempotent.** Every step can run twice without duplicating anything, which is what makes
 *   an interrupted migration safe to retry rather than something to clean up afterwards.
 * - **never deletes the source.** The guest keys are left exactly as they were. If the migration
 *   is interrupted, if a step fails, or if the viewer signs out again, the guest identity is still
 *   whole and still sitting there.
 *
 * Steps are recorded one at a time in [GuestMigrationJournal], so a retry resumes rather than
 * restarts, and a step that has already landed is not run again.
 */

private const val GUEST_MIGRATION_PREFS = "streamdek_guest_migration"

/** What a guest identity actually has, in the terms the prompt describes it to the viewer in. */
internal data class GuestDataSummary(
  /** Part-watched films and episodes: Continue Watching, and the positions behind it. */
  val inProgressTitles: Int = 0,
  val watchlistItems: Int = 0,
  val favouriteChannels: Int = 0,
  /** Add-ons, plugins, playlists and custom subtitle sources, counted together as "sources". */
  val sources: Int = 0,
  /** Whether anything was set that is neither content nor a source: settings, Home layout, rows. */
  val hasPreferences: Boolean = false,
) {
  val isEmpty: Boolean
    get() = inProgressTitles == 0 && watchlistItems == 0 && favouriteChannels == 0 && sources == 0 && !hasPreferences
}

/**
 * Where the guest-to-profile migration has got to, as far as the screen is concerned.
 *
 * One object rather than four properties on the app state, for the reason [HomeRowArrangement]
 * explains: that state's generated `copy` is within a few argument registers of the most a dex
 * method may be handed, and going past it produces an app that will not start.
 */
@Immutable
internal data class GuestSetupTransfer(
  /**
   * The guest identity the prompt is about, or null when there is nothing to ask.
   *
   * Set for a sign-in as well as a registration: an account can be signed into on a phone that has
   * been used as a guest for months, and the setup on it is no less theirs for the account having
   * existed first.
   */
  val pendingOwnerKey: String? = null,
  /** What that identity holds, for the prompt to describe before anything is moved. */
  val summary: GuestDataSummary? = null,
  /** True while the migration is running, which is the only time the prompt shows progress. */
  val running: Boolean = false,
  /**
   * A guest identity whose setup could still be brought into the current profile, whether or not
   * the prompt was answered. Drives the Account settings entry point, which is how someone who
   * chose "Start fresh" (or dismissed the prompt by accident) gets a second chance.
   */
  val availableOwnerKey: String? = null,
)

/**
 * A single unit of the migration, named so it can be recorded as done.
 *
 * [id] is a storage contract: renaming one makes an already-migrated device run that step again.
 * Running it again is harmless by construction, but the name is still not free to change.
 */
internal class GuestMigrationStep(val id: String, val migrate: () -> Unit)

/** What happened, for the caller to report and to decide whether to offer a retry. */
internal data class GuestMigrationOutcome(val completed: Boolean, val failedSteps: List<String>)

/**
 * Which migrations have run, and how far each got.
 *
 * Keyed by the pair of owner keys rather than by "have we migrated", because a device can carry
 * several guest identities and an account several profiles: moving one guest's setup into one
 * profile says nothing about any other pairing.
 */
internal class GuestMigrationJournal(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(GUEST_MIGRATION_PREFS, Context.MODE_PRIVATE)

  fun isStepDone(token: String, stepId: String): Boolean = prefs.getBoolean("step:$token:$stepId", false)

  fun markStepDone(token: String, stepId: String) {
    // commit, not apply: the point of the journal is to survive the process being killed, and a
    // step recorded in memory only would be redone on the next launch.
    prefs.edit().putBoolean("step:$token:$stepId", true).commit()
  }

  fun isComplete(token: String): Boolean = prefs.getBoolean("done:$token", false)

  fun markComplete(token: String) {
    prefs.edit().putBoolean("done:$token", true).commit()
  }

  /**
   * The viewer chose to start fresh. Recorded so they are not asked again for this pairing - but
   * deliberately *not* destructive: the guest data stays, and the Account settings keep offering
   * to bring it over for as long as it exists.
   */
  fun isDeclined(token: String): Boolean = prefs.getBoolean("declined:$token", false)

  fun markDeclined(token: String) {
    prefs.edit().putBoolean("declined:$token", true).apply()
  }

  fun clearDecision(token: String) {
    prefs.edit().remove("declined:$token").apply()
  }

  /**
   * A profile this device has just created, which has therefore never been set up by anyone.
   *
   * The one case where a guest's settings and Home layout are allowed to *replace* the profile's
   * rather than only filling in what it has never answered: everything it holds is a default this
   * app wrote a moment ago, and the viewer is about to be handed it as "their" StreamDek. A profile
   * that already existed - made on another device, or used here before - is never marked, so its
   * own choices are left exactly as they are.
   */
  fun isFreshProfile(ownerKey: String): Boolean = prefs.getBoolean("fresh:$ownerKey", false)

  fun markFreshProfile(ownerKey: String) {
    prefs.edit().putBoolean("fresh:$ownerKey", true).commit()
  }

  /** Spent once the profile has been given a guest setup, or the viewer chose to start fresh. */
  fun clearFreshProfile(ownerKey: String) {
    prefs.edit().remove("fresh:$ownerKey").apply()
  }
}

/** The journal key for one guest identity moving into one profile. */
internal fun guestMigrationToken(fromOwnerKey: String, toOwnerKey: String): String = "$fromOwnerKey>$toOwnerKey"

/**
 * Runs whichever steps have not landed yet.
 *
 * A step that throws is left unrecorded and the others carry on: one add-on manifest that will not
 * parse should not cost the viewer their watch history. The migration counts as complete only when
 * every step has been recorded, so a partial run is offered again rather than being forgotten.
 */
internal fun runGuestMigration(
  token: String,
  steps: List<GuestMigrationStep>,
  journal: GuestMigrationJournal,
): GuestMigrationOutcome {
  val failed = mutableListOf<String>()
  steps.forEach { step ->
    if (journal.isStepDone(token, step.id)) return@forEach
    runCatching(step.migrate)
      .onSuccess { journal.markStepDone(token, step.id) }
      .onFailure { error ->
        failed += step.id
        Log.w("StreamDekMigration", "Guest migration step '${step.id}' failed", error)
      }
  }
  val completed = failed.isEmpty()
  if (completed) journal.markComplete(token)
  return GuestMigrationOutcome(completed = completed, failedSteps = failed)
}

/**
 * A profile name from the address someone signed up with: `henry.okwuenu@example.com` is
 * "Henry Okwuenu".
 *
 * The local part only, with the separators people use in place of a space turned back into one. A
 * trailing disambiguator - `henry.okwuenu+streamdek@` - is dropped, since it names a mailbox rather
 * than a person. What is left is capitalised word by word, and anything that survives none of that
 * (a numeric or symbol-only address) falls back to "Profile 1" style naming by the caller.
 *
 * This is a starting point, not a decision: the profile can be renamed like any other.
 */
internal fun defaultProfileNameFromEmail(email: String?): String? {
  val local = email?.trim()?.substringBefore('@')?.substringBefore('+')?.trim().orEmpty()
  if (local.isEmpty()) return null
  val words = local.split('.', '_', '-', ' ')
    .map { part -> part.trim().filterNot(Char::isDigit).ifBlank { part.trim() } }
    .filter { it.isNotBlank() }
  if (words.isEmpty()) return null
  val name = words.joinToString(" ") { word ->
    // An address typed in capitals is an address, not somebody shouting their own name, so a word
    // with no lower case in it is brought down before being capitalised. A word that already mixes
    // the two is left alone: whoever wrote "McDonald" meant it.
    val base = if (word.none(Char::isLowerCase)) word.lowercase(Locale.getDefault()) else word
    base.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
  }.trim()
  return name.take(32).takeIf { it.isNotBlank() }
}

/**
 * Two lists joined on a stable identity, with the destination's copy winning.
 *
 * Used where the two sides hold the same *kind* of thing and one copy is as good as the other - a
 * watchlist entry, a starred channel. The profile's own copy is kept because it is the one its
 * other devices have already agreed on; the guest's entries fill in around it, in their own order.
 */
internal fun <T, K> mergePreservingTarget(target: List<T>, source: List<T>, identity: (T) -> K): List<T> {
  val seen = target.mapTo(HashSet(), identity)
  return target + source.filter { seen.add(identity(it)) }
}

/**
 * Two lists joined on identity, with the more recently touched copy winning each collision.
 *
 * For anything that records *where somebody got to* - a resume position, an episode's progress.
 * Neither side is authoritative: the guest may have watched three episodes this morning that the
 * account has never heard of, and the account may hold a position from another device that is
 * newer than anything on this one. The stamp decides, per title, and the losing copy is dropped
 * rather than being kept as a duplicate, because two positions for one episode is not a merge.
 */
internal fun <T, K> mergeNewestWins(
  target: List<T>,
  source: List<T>,
  identity: (T) -> K,
  updatedAt: (T) -> Long,
): List<T> {
  val merged = LinkedHashMap<K, T>()
  (target + source).forEach { entry ->
    val key = identity(entry)
    val existing = merged[key]
    if (existing == null || updatedAt(entry) > updatedAt(existing)) merged[key] = entry
  }
  return merged.values.toList()
}

/** The union of two JSON arrays of strings, in destination-then-source order. Nulls pass through. */
internal fun unionJsonStringArrays(targetRaw: String?, sourceRaw: String?): String? {
  if (sourceRaw.isNullOrBlank()) return targetRaw
  val values = LinkedHashSet<String>()
  listOfNotNull(targetRaw, sourceRaw).forEach { raw ->
    runCatching { JSONArray(raw) }.getOrNull()?.let { array ->
      for (index in 0 until array.length()) array.optString(index).takeIf { it.isNotBlank() }?.let(values::add)
    }
  }
  if (values.isEmpty()) return targetRaw
  return JSONArray().apply { values.forEach(::put) }.toString()
}

/**
 * Two JSON arrays of objects joined on [identity].
 *
 * [preferSource] decides collisions; by default the destination's object stays, which is the safe
 * answer for records that carry state the account has agreed on (an add-on's enabled flag, a
 * playlist's credentials). Objects with no identity are kept from the destination and dropped from
 * the source, since there is no way to tell whether they are the same thing twice.
 */
internal fun mergeJsonObjectArrays(
  targetRaw: String?,
  sourceRaw: String?,
  identity: (JSONObject) -> String?,
  preferSource: (target: JSONObject, source: JSONObject) -> Boolean = { _, _ -> false },
): String? {
  if (sourceRaw.isNullOrBlank()) return targetRaw
  fun parse(raw: String?): List<JSONObject> = runCatching {
    val array = JSONArray(raw.orEmpty())
    buildList { for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add) }
  }.getOrDefault(emptyList())

  val merged = LinkedHashMap<String, JSONObject>()
  parse(targetRaw).forEach { item -> identity(item)?.let { key -> merged.putIfAbsent(key, item) } }
  parse(sourceRaw).forEach { item ->
    val key = identity(item) ?: return@forEach
    val existing = merged[key]
    if (existing == null || preferSource(existing, item)) merged[key] = item
  }
  if (merged.isEmpty()) return targetRaw
  return JSONArray().apply { merged.values.forEach(::put) }.toString()
}

/**
 * Two plugin documents joined: collections by their URL, sources by the collection and id that name
 * them.
 *
 * A plugin document is the whole of a profile's plugin setup - the collections installed, the
 * sources inside them, whether each is switched on, and the settings each one needs to work at all
 * (a cookie, an API key). The destination's copy of a source wins a collision: it is the one the
 * account has been syncing, and taking the guest's would hand it a token the profile has since
 * replaced. Sources the profile has never seen come across whole, settings included, which is what
 * stops a migrated collection arriving switched on and silently returning nothing.
 *
 * `updatedAt` is carried forward as the later of the two, so the reconciliation that decides
 * whether to push this document or take the account's sees a document that is genuinely newer than
 * both sides it was built from.
 */
internal fun mergePluginStateDocuments(
  targetRaw: String?,
  sourceRaw: String?,
  /**
   * The stamp the merged document carries, which is *now* rather than the later of the two it came
   * from. The document genuinely has just been written, and the reconciliation that decides whether
   * to push it or take the account's copy arbitrates on this stamp: a merge that inherited an older
   * one could be overwritten by the very cloud copy it was merged with, losing the guest's sources
   * a second after they arrived.
   */
  now: Long = System.currentTimeMillis(),
): String? {
  if (sourceRaw.isNullOrBlank() || sourceRaw == "{}") return targetRaw
  val source = runCatching { JSONObject(sourceRaw) }.getOrNull() ?: return targetRaw
  val target = runCatching { JSONObject(targetRaw.orEmpty()) }.getOrNull() ?: JSONObject()
  val merged = JSONObject(target.toString())

  // Only when one of the documents actually carries the flag: the CloudStream and SkyStream
  // documents have no top-level `enabled`, and inventing one for them would put a key in their
  // storage that nothing wrote and nothing reads.
  if (target.has("enabled") || source.has("enabled")) {
    merged.put("enabled", target.optBoolean("enabled", true) || source.optBoolean("enabled", false))
  }
  merged.put("updatedAt", maxOf(target.optLong("updatedAt", 0L), source.optLong("updatedAt", 0L), now))

  mergeJsonObjectArrays(
    targetRaw = target.optJSONArray("repos")?.toString(),
    sourceRaw = source.optJSONArray("repos")?.toString(),
    identity = { it.optString("url").takeIf(String::isNotBlank) },
  )?.let { merged.put("repos", JSONArray(it)) }

  mergeJsonObjectArrays(
    targetRaw = target.optJSONArray("providers")?.toString(),
    sourceRaw = source.optJSONArray("providers")?.toString(),
    identity = ::pluginProviderIdentity,
  )?.let { merged.put("providers", JSONArray(it)) }

  // CloudStream source switches merge value by value, newest first, like they do with the account:
  // the guest's choices and the profile's are both decisions, and neither side's should be dropped.
  if (target.has("sourceSettings") || source.has("sourceSettings")) {
    merged.put(
      "sourceSettings",
      csSourceSettingsJson(
        mergeCsSourceSettings(
          parseCsSourceSettings(target.optJSONArray("sourceSettings")),
          parseCsSourceSettings(source.optJSONArray("sourceSettings")),
        ),
      ),
    )
  }

  // Whatever else a document carries - the CloudStream section, and anything a later release adds -
  // is taken across only where the destination has nothing to say, which is the same rule the
  // settings use. A key the profile already holds is a decision it has already made.
  val keys = source.keys()
  while (keys.hasNext()) {
    val key = keys.next()
    if (key in setOf("enabled", "updatedAt", "repos", "providers", "sourceSettings")) continue
    if (!merged.has(key)) merged.put(key, source.opt(key))
  }
  return merged.toString()
}

/**
 * What identifies one source inside a plugin document: its collection and its id.
 *
 * Each plugin engine names these fields its own way - the JS document writes `repo`/`id`, the
 * CloudStream and SkyStream documents `repoUrl` with `internalName` or `packageName` - and one
 * merge serves all three, so it accepts whichever pair a document actually uses.
 */
internal fun pluginProviderIdentity(provider: JSONObject): String? {
  val repo = provider.optString("repo").takeIf(String::isNotBlank) ?: provider.optString("repoUrl").takeIf(String::isNotBlank)
  val id = listOf("id", "packageName", "internalName")
    .firstNotNullOfOrNull { field -> provider.optString(field).takeIf(String::isNotBlank) }
  return if (repo != null && id != null) "$repo:$id" else id
}

/**
 * How many sources a stored plugin document lists.
 *
 * Shared by all three plugin engines, whose documents differ in every other respect but all keep
 * their sources in a `providers` array. Only ever used to describe what a guest identity holds, so
 * a document that will not parse counts as nothing rather than failing the count.
 */
internal fun countProviders(rawDocument: String?): Int {
  if (rawDocument.isNullOrBlank()) return 0
  return runCatching { JSONObject(rawDocument).optJSONArray("providers")?.length() ?: 0 }.getOrDefault(0)
}

/**
 * Copies across every setting the destination has not answered for itself.
 *
 * One-directional, and an overwrite only when [overwrite] says so. A profile that has already
 * chosen a player engine, a Home layout or a subtitle size has said what it wants, and a guest
 * identity arriving is not new information about that choice. A profile this device created a
 * moment ago ([GuestMigrationJournal.isFreshProfile]) has chosen nothing: what it holds are
 * defaults written on its first load, and those must not outrank the setup the guest built.
 *
 * @return how many settings were carried over, for the caller to report.
 */
internal fun copyMissingPreferences(source: SharedPreferences, target: SharedPreferences, overwrite: Boolean = false): Int {
  val editor = target.edit()
  var copied = 0
  source.all.forEach { (key, value) ->
    if (value == null || (!overwrite && target.contains(key))) return@forEach
    when (value) {
      is Boolean -> editor.putBoolean(key, value)
      is Int -> editor.putInt(key, value)
      is Long -> editor.putLong(key, value)
      is Float -> editor.putFloat(key, value)
      is String -> editor.putString(key, value)
      is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
      else -> return@forEach
    }
    copied += 1
  }
  if (copied > 0) editor.commit()
  return copied
}

/**
 * Merges every `"<prefix>:<owner>"` and `"<prefix>:<owner>:<suffix>"` entry from one owner to
 * another, treating each value as a JSON array of strings.
 *
 * The watched-episode, watched-movie and watched-title stores all key this way, and all hold what
 * is in effect a set: "these have been seen". A union is the whole merge - there is no version of
 * this where one side's "watched" should cancel the other's.
 */
internal fun mergeOwnerScopedStringSets(
  prefs: SharedPreferences,
  prefix: String,
  fromOwnerKey: String,
  toOwnerKey: String,
) {
  if (fromOwnerKey == toOwnerKey) return
  val fromPrefix = "$prefix:$fromOwnerKey"
  val editor = prefs.edit()
  var changed = false
  prefs.all.keys.toList().forEach { key ->
    if (key != fromPrefix && !key.startsWith("$fromPrefix:")) return@forEach
    val suffix = key.removePrefix(fromPrefix)
    val targetKey = "$prefix:$toOwnerKey$suffix"
    val merged = unionJsonStringArrays(prefs.getString(targetKey, null), prefs.getString(key, null)) ?: return@forEach
    if (merged != prefs.getString(targetKey, null)) {
      editor.putString(targetKey, merged)
      changed = true
    }
  }
  if (changed) editor.commit()
}


/**
 * The migration itself: what a guest identity holds, and every step of moving it into a profile.
 *
 * Its own object rather than more of the view model, because it is one job with one set of
 * collaborators - the owner-scoped stores - and because the view model is a class the dex verifier
 * is already unhappy about the size of. Nothing here touches the network or the UI: it merges what
 * is on the device, and the caller decides what to do with the result.
 */
internal class GuestDataMigrator(
  private val context: Context,
  private val journal: GuestMigrationJournal,
  private val watchlistStore: WatchlistStore,
  private val favouriteChannelStore: FavouriteChannelStore,
  private val playbackResumeStore: PlaybackResumeStore,
  private val watchedEpisodeStore: WatchedEpisodeStore,
  private val watchedMovieStore: WatchedMovieStore,
  private val watchedTitleStore: WatchedTitleStore,
  private val nextUpHistory: NextUpHistory,
  /**
   * Fills a profile's settings in from another owner's - or, with `overwrite`, replaces them -
   * and returns how many were carried over.
   */
  private val migrateSettings: (from: String, to: String, overwrite: Boolean) -> Int,
  /** Whether an owner has ever had profile settings written for it. */
  private val hasSettings: (ownerKey: String) -> Boolean,
) {

  /**
   * Runs whichever steps of this pairing have not landed yet. Safe to call again after a failure:
   * every step is idempotent, and the journal skips the ones that already worked.
   */
  fun migrate(from: String, to: String): GuestMigrationOutcome =
    runGuestMigration(guestMigrationToken(from, to), steps(from, to, journal.isFreshProfile(to)), journal)

  /**
   * What an owner key actually holds, across every store that is scoped to one.
   *
   * Both the test for "is there anything to migrate" and the description the prompt shows, so the
   * two can never disagree - a prompt that offers to move a watchlist that is not there, or a
   * silence that hides one that is, are the same bug from opposite ends.
   */
  fun summaryFor(ownerKey: String): GuestDataSummary {
    val sources = LocalAddonManager.manifestUrlsFor(ownerKey).size +
      M3uPlaylistManager.playlistCountFor(ownerKey) +
      UserSubtitleSourceStore.load(context, ownerKey).size +
      StreamDekPlugins.manager.providerCountFor(ownerKey) +
      (if (SkyStreamPlugins.isInitialized) SkyStreamPlugins.manager.providerCountFor(ownerKey) else 0) +
      (if (CloudStreamPlugins.isInitialized) CloudStreamPlugins.manager.providerCountFor(ownerKey) else 0)
    return GuestDataSummary(
      // Live entries are remembered channel sources rather than something part-watched, and
      // counting them would describe an evening of channel surfing as unfinished films.
      inProgressTitles = playbackResumeStore.loadAll(ownerKey).count { !it.isLive },
      watchlistItems = watchlistStore.load(ownerKey).size,
      favouriteChannels = favouriteChannelStore.load(ownerKey).size,
      sources = sources,
      hasPreferences = hasSettings(ownerKey),
    )
  }

  /**
   * Every part of a guest identity, as one named step each.
   *
   * The ids are storage contracts - the journal records them - so they are not free to rename. The
   * order is the order a viewer would notice things in if a step failed: what they were watching,
   * then what they had saved, then the sources behind it, then the settings around it.
   */
  private fun steps(from: String, to: String, freshTarget: Boolean): List<GuestMigrationStep> = listOf(
      GuestMigrationStep("playback-progress") {
        // Newest wins per film or episode. Neither side is authoritative: the guest may have
        // watched three episodes this morning, and the profile may hold a position set on another
        // device an hour ago. Only the losing *copy* is dropped, never the row.
        val merged = mergeNewestWins(
          target = playbackResumeStore.loadAll(to),
          source = playbackResumeStore.loadAll(from),
          identity = { playbackMemoryKey(it.mediaId, it.mediaType, it.seasonNumber, it.episodeNumber) },
          updatedAt = { it.updatedAt },
        )
        playbackResumeStore.replaceAll(to, merged)
      },
      GuestMigrationStep("watched-episodes") { watchedEpisodeStore.mergeOwner(from, to) },
      GuestMigrationStep("watched-movies") { watchedMovieStore.mergeOwner(from, to) },
      GuestMigrationStep("watched-titles") { watchedTitleStore.mergeOwner(from, to) },
      GuestMigrationStep("next-up-history") { nextUpHistory.mergeOwner(from, to) },
      GuestMigrationStep("watchlist") {
        // Type and id together, which is how a watchlist entry is identified everywhere else: the
        // same numeric id can be a film on one row and a series on another.
        watchlistStore.save(
          to,
          mergePreservingTarget(watchlistStore.load(to), watchlistStore.load(from)) { "${it.type}:${it.id}" },
        )
      },
      GuestMigrationStep("favourite-channels") {
        // Channel id alone, matching how favourites are identified everywhere else - the same
        // channel is stored as "tv" or "live" depending on which catalog it came through.
        favouriteChannelStore.save(
          to,
          mergePreservingTarget(favouriteChannelStore.load(to), favouriteChannelStore.load(from)) { it.id },
        )
      },
      GuestMigrationStep("local-addons") { LocalAddonManager.mergeProfileStorageInto(from, to) },
      GuestMigrationStep("playlists") { M3uPlaylistManager.mergeProfileStorageInto(from, to) },
      GuestMigrationStep("subtitle-sources") { UserSubtitleSourceStore.mergeOwner(context, from, to) },
      GuestMigrationStep("plugins") { StreamDekPlugins.manager.mergeProfileStorageInto(from, to) },
      GuestMigrationStep("plugins-skystream") {
        if (SkyStreamPlugins.isInitialized) SkyStreamPlugins.manager.mergeProfileStorageInto(from, to)
      },
      GuestMigrationStep("plugins-cloudstream") {
        if (CloudStreamPlugins.isInitialized) CloudStreamPlugins.manager.mergeProfileStorageInto(from, to)
      },
      // Last, and one-directional: the Home layout, the row mode and every other profile-scoped
      // setting, but only where the destination has never answered for itself - unless the
      // destination is a profile this device has only just made, whose "answers" are all defaults.
      // A profile that has already chosen is not overruled by a guest identity arriving.
      GuestMigrationStep("profile-settings") { migrateSettings(from, to, freshTarget) },
    )

  /** A resume entry as the account's progress API expects it. */
  fun playbackProgressRecordOf(entry: PlaybackMemoryEntry) = PlaybackProgressRecord(
    entityType = normalizedMediaType(entry.mediaType),
    entityId = entry.mediaId,
    // The same spelling the player writes progress under, so a migrated position lands on the
    // account's existing row for that episode rather than beside it.
    episodeKey = if (entry.seasonNumber != null && entry.episodeNumber != null) {
      "s%02de%02d".format(entry.seasonNumber, entry.episodeNumber)
    } else null,
    seasonNumber = entry.seasonNumber,
    episodeNumber = entry.episodeNumber,
    title = entry.title,
    poster = entry.poster,
    backdrop = entry.backdrop,
    year = entry.year,
    positionSec = entry.positionSeconds ?: 0.0,
    durationSec = entry.durationSeconds?.toDouble() ?: 0.0,
    progress = entry.progressPercent,
    completed = entry.progressPercent >= 95.0,
    updatedAt = entry.updatedAt,
    lastDevice = "StreamDek Mobile",
    lastPlatform = "mobile",
  )

  fun addonManifestUrl(addon: InstalledAddon): String? =
    (addon.transportUrl ?: addon.manifestUrl ?: addon.url)?.trim()?.takeIf { it.isNotEmpty() }

  /**
   * A guest identity on this device that still has something the current profile has not been given.
   *
   * What keeps the offer alive after the prompt is gone - dismissed, declined, or never seen because
   * the account was signed into on a device that had been used as a guest long before. Migrations
   * already completed for this pairing are excluded, so the Account screen offers it exactly while
   * there is something left to offer.
   */
  fun leftoverGuestOwnerKey(targetOwnerKey: String, guestProfileIds: List<String>): String? {
    val candidates = listOf(GUEST_OWNER_KEY) + guestProfileIds.map { "guest:$it" }
    return candidates.firstOrNull { candidate ->
      candidate != targetOwnerKey &&
        !journal.isComplete(guestMigrationToken(candidate, targetOwnerKey)) &&
        !summaryFor(candidate).isEmpty
    }
  }
}

/**
 * A saved Home layout with every guest add-on id swapped for the id the same add-on has under the
 * profile.
 *
 * An add-on row is named `addon:<add-on id>:<type>:<catalogue>:<index>`, and the add-on id is the
 * backend's record id - which is minted per owner. The guest's add-ons are installed again under
 * the profile and come back with new ids, so a layout carried across unchanged points at rows that
 * no longer exist: its add-on rows would be dropped as unknown and the add-ons' own rows would turn
 * up afresh at the bottom, which is the "layout did not move" the migration is supposed to prevent.
 *
 * Rows that end up naming the same catalogue twice keep the first, which is the one the layout put
 * higher. Returns [raw] unchanged when there is nothing to swap or it will not parse.
 */
internal fun remapHomeRowAddonIds(raw: String?, addonIds: Map<String, String>): String? {
  if (raw.isNullOrBlank() || addonIds.isEmpty()) return raw
  val source = runCatching { JSONArray(raw) }.getOrNull() ?: return raw
  val seen = HashSet<String>()
  val out = JSONArray()
  for (index in 0 until source.length()) {
    val item = source.optJSONObject(index) ?: continue
    val id = remapAddonRowId(item.optString("id"), addonIds)
    if (id.isBlank() || !seen.add(id)) continue
    out.put(JSONObject(item.toString()).put("id", id))
  }
  return out.toString()
}

/** The same swap for the Home source order, whose add-on entries are bare add-on ids. */
internal fun remapHomeRowSourceOrder(raw: String?, addonIds: Map<String, String>): String? {
  if (raw.isNullOrBlank() || addonIds.isEmpty()) return raw
  val source = runCatching { JSONArray(raw) }.getOrNull() ?: return raw
  val values = LinkedHashSet<String>()
  for (index in 0 until source.length()) {
    val key = source.optString(index).trim().takeIf { it.isNotEmpty() } ?: continue
    values += addonIds[key] ?: key
  }
  return JSONArray().apply { values.forEach(::put) }.toString()
}

private fun remapAddonRowId(id: String, addonIds: Map<String, String>): String {
  if (!id.startsWith("addon:")) return id
  val parts = id.split(":").toMutableList()
  val replacement = parts.getOrNull(1)?.let(addonIds::get) ?: return id
  parts[1] = replacement
  return parts.joinToString(":")
}
