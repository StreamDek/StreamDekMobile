package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import net.streamdek.mobile.debrid.DebridKeyStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads StreamDek's setup into a backup payload, and lays a payload back over it.
 *
 * The one place that knows where each piece of a setup is stored. Those locations are private to
 * the stores that own them, and they are repeated in [Stores] rather than reached into: every one
 * of them is already a storage contract (renaming one would lose everybody's data), so they do not
 * move, and keeping the list here keeps the whole of what a backup touches readable in one place.
 *
 * What it does not do is anything that needs the network or the screen - reinstalling add-ons,
 * pushing to the account, reloading what is on screen. Those belong to [BackupRestoreController]
 * and the view model behind it.
 */
internal class BackupEngine(context: Context) {
  private val app = context.applicationContext

  private object Stores {
    const val LOCAL_ADDONS = "streamdek_local_addons"
    const val SUBTITLE_SOURCES = "streamdek_subtitle_sources"
    const val PLAYLISTS = "streamdek_m3u_playlists"
    const val WATCHLIST = "streamdek_native_watchlist"
    const val FAVOURITE_CHANNELS = "streamdek_native_favourite_channels"
    const val RESUME = "streamdek_native_playback_resume"
    const val WATCHED_EPISODES = "streamdek_native_watched_episodes"
    const val WATCHED_MOVIES = "streamdek_native_watched_movies"
    const val WATCHED_TITLES = "streamdek_native_watched_titles"
    const val NEXT_UP = "streamdek_next_up_history"
    const val DISPLAY_NAMES = "streamdek_display_name_overrides"
    const val NETWORK = "streamdek_network"
    const val JS_PLUGINS = "streamdek_plugins"
    const val CS_PLUGINS = "streamdek_cs_plugins"
    const val SKY_PLUGINS = "streamdek_sky_plugins"
    const val DEBRID_KEYS = "streamdek_debrid_keys"
    const val SERVICE_KEYS = "streamdek_service_credentials_v1"
    const val SERVICE_KEY_META = "streamdek_service_credential_meta_v1"
    const val BACKUP_META = "streamdek_backup"
  }

  private fun prefs(name: String): SharedPreferences = app.getSharedPreferences(name, Context.MODE_PRIVATE)

  private fun settingsFile(store: String): String = if (store == BACKUP_NETWORK_STORE) Stores.NETWORK else APP_SETTINGS_PREFERENCES

  // ── Building a backup ─────────────────────────────────────────────────────────────────────

  /**
   * Everything this device holds for [profiles], as a payload.
   *
   * [addonsByProfile] is the backend's add-on list for each profile, fetched by the caller; a
   * profile whose list could not be fetched is backed up without its add-ons and the omission is
   * recorded. With [includeSecrets] false, anything that is or carries a credential is left out and
   * named in `omitted`, so the restore can say what will need setting up again.
   */
  fun build(
    profiles: List<BackupProfileRef>,
    addonsByProfile: Map<String?, List<InstalledAddon>?>,
    includeSecrets: Boolean,
    appVersion: String,
    now: Long = System.currentTimeMillis(),
  ): BackupBuildResult {
    val omitted = mutableListOf<BackupOmission>()
    val counts = BackupCounter()

    val deviceSettings = JSONArray()
    BackupSettingsRegistry.specs.filter { it.scope == BackupSettingScope.Device }.forEach { spec ->
      val value = prefs(settingsFile(spec.store)).all[spec.key] ?: return@forEach
      encodeBackupSetting(spec.key, value)?.put("store", spec.store)?.let { deviceSettings.put(it); counts.settings += 1 }
    }
    val displayNames = JSONObject().apply {
      prefs(Stores.DISPLAY_NAMES).all.forEach { (key, value) -> if (value is String && value.isNotBlank()) put(key, value) }
    }
    val device = JSONObject()
      .put("platform", BACKUP_PLATFORM_MOBILE)
      .put("settings", deviceSettings)
      .put("displayNames", displayNames)

    val ownerKeys = profiles.map { it.ownerKey }
    val profileArray = JSONArray()
    profiles.forEach { profile ->
      profileArray.put(buildProfile(profile, ownerKeys, addonsByProfile, includeSecrets, omitted, counts))
    }

    val payload = JSONObject()
      .put("device", device)
      .put("profiles", profileArray)
    if (includeSecrets) payload.put("credentials", buildCredentials(counts))
    payload.put(
      "omitted",
      JSONArray().apply { omitted.forEach { put(JSONObject().put("category", it.category.id).put("name", it.name).put("reason", it.reason.name)) } },
    )

    val categories = buildSet {
      add(BackupCategory.Appearance); add(BackupCategory.Playback); add(BackupCategory.General)
      if (counts.addons > 0) add(BackupCategory.Addons)
      if (counts.pluginSources > 0 || counts.pluginRepositories > 0) add(BackupCategory.Plugins)
      add(BackupCategory.Playlists)
      if (counts.libraryItems > 0) add(BackupCategory.Library)
      if (includeSecrets && counts.credentials > 0) add(BackupCategory.Credentials)
    }
    val summary = BackupSummary(
      createdAt = now,
      appVersion = appVersion,
      platform = BACKUP_PLATFORM_MOBILE,
      schemaVersion = BACKUP_SCHEMA_VERSION,
      encrypted = includeSecrets,
      categories = categories,
      profiles = profiles.size,
      addons = counts.addons,
      pluginRepositories = counts.pluginRepositories,
      pluginSources = counts.pluginSources,
      playlists = counts.playlists,
      settings = counts.settings,
      libraryItems = counts.libraryItems,
      credentials = counts.credentials,
    )
    return BackupBuildResult(payload, summary, omitted)
  }

  private fun buildProfile(
    profile: BackupProfileRef,
    allOwners: List<String>,
    addonsByProfile: Map<String?, List<InstalledAddon>?>,
    includeSecrets: Boolean,
    omitted: MutableList<BackupOmission>,
    counts: BackupCounter,
  ): JSONObject {
    val owner = profile.ownerKey
    val settings = JSONArray()
    prefs(profileSettingsStorageName(owner)).all.forEach { (key, value) ->
      val spec = BackupSettingsRegistry.spec(BackupSettingScope.Profile, key) ?: return@forEach
      if (spec.sensitive && !includeSecrets) {
        if ((value as? String)?.isNotBlank() == true) omitted += BackupOmission(spec.category, key, BackupOmissionReason.NeedsPassphrase)
        return@forEach
      }
      encodeBackupSetting(key, value)?.let { settings.put(it); counts.settings += 1 }
    }
    val notifications = EpisodeNotificationSystem.settings(app, owner).let {
      JSONObject().put("available", it.availableEnabled).put("upcoming", it.upcomingEnabled).put("upcomingDays", it.upcomingDays)
    }

    // Add-ons: the backend's list, then the ones this device installed itself.
    val addons = JSONArray()
    val serverAddons = addonsByProfile[profile.id]
    if (serverAddons == null) {
      omitted += BackupOmission(BackupCategory.Addons, profile.name, BackupOmissionReason.CouldNotRead)
    } else {
      serverAddons.filterNot { LocalAddonManager.isLocalAddonId(it.id) }.sortedBy { it.position }.forEach { addon ->
        val url = (addon.transportUrl ?: addon.manifestUrl ?: addon.url)?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
        if (!includeSecrets && urlCarriesPrivateConfig(url)) {
          omitted += BackupOmission(BackupCategory.Addons, addon.manifest.name, BackupOmissionReason.NeedsPassphrase)
          return@forEach
        }
        addons.put(
          JSONObject().put("id", addon.id).put("manifestUrl", url).put("name", addon.manifest.name)
            .put("enabled", addon.enabled).put("position", addon.position).put("favourite", addon.favourite).put("local", false),
        )
        counts.addons += 1
      }
    }
    jsonObjects(prefs(Stores.LOCAL_ADDONS).getString("addons:$owner", null)).forEach { record ->
      val url = record.optString("manifestUrl")
      if (!includeSecrets && urlCarriesPrivateConfig(url)) {
        omitted += BackupOmission(BackupCategory.Addons, addonName(record), BackupOmissionReason.NeedsPassphrase)
        return@forEach
      }
      addons.put(JSONObject(record.toString()).put("local", true).put("name", addonName(record)))
      counts.addons += 1
    }

    val subtitleSources = filterPrivate(
      jsonObjects(prefs(Stores.SUBTITLE_SOURCES).getString("sources:$owner", null)), "baseUrl", "name",
      BackupCategory.Addons, includeSecrets, omitted,
    )
    val playlists = filterPrivate(
      jsonObjects(prefs(Stores.PLAYLISTS).getString("sources:$owner", null)), "url", "name",
      BackupCategory.Playlists, includeSecrets, omitted,
    )
    counts.playlists += playlists.length()

    val plugins = pluginDocumentFor(owner)
    if (!includeSecrets) {
      stripPluginSecrets(plugins).forEach { omitted += BackupOmission(BackupCategory.Plugins, it, BackupOmissionReason.NeedsPassphrase) }
    }
    countPluginDocument(plugins).let { (repos, sources) -> counts.pluginRepositories += repos; counts.pluginSources += sources }

    val library = buildLibrary(owner, allOwners, includeSecrets, counts)

    return JSONObject()
      .put("id", profile.id ?: JSONObject.NULL)
      .put("name", profile.name)
      .put("avatarIndex", profile.avatarIndex)
      .put("active", profile.active)
      .put("settings", settings)
      .put("notifications", notifications)
      .put("addons", addons)
      .put("subtitleSources", subtitleSources)
      .put("plugins", plugins)
      .put("playlists", playlists)
      .put("library", library)
  }

  private fun buildLibrary(owner: String, allOwners: List<String>, includeSecrets: Boolean, counts: BackupCounter): JSONObject {
    val watchlist = jsonArray(prefs(Stores.WATCHLIST).getString(owner, null))
    val favourites = jsonArray(prefs(Stores.FAVOURITE_CHANNELS).getString(owner, null))
    val resume = jsonArray(prefs(Stores.RESUME).getString("resume:$owner", null)).also { entries ->
      // The remembered stream can be a premium service's signed link, which is as good as its key.
      if (!includeSecrets) for (index in 0 until entries.length()) entries.optJSONObject(index)?.remove("stream")
    }
    val watchedEpisodes = JSONObject()
    val longerOwners = allOwners.filter { it != owner && it.startsWith("$owner:") }
    prefs(Stores.WATCHED_EPISODES).all.forEach { (key, value) ->
      if (!key.startsWith("watched:$owner:") || value !is String) return@forEach
      // "guest" is a prefix of "guest:<profile>", so a guest's keys have to be told apart from the
      // keys of each of its profiles.
      if (longerOwners.any { key.startsWith("watched:$it:") }) return@forEach
      watchedEpisodes.put(key.removePrefix("watched:$owner:"), jsonArray(value))
    }
    val watchedMovies = jsonArray(prefs(Stores.WATCHED_MOVIES).getString("watched_movies:$owner", null))
    val watchedTitles = jsonArray(prefs(Stores.WATCHED_TITLES).getString("watched_titles:$owner", null))
    val nextUp = jsonArray(prefs(Stores.NEXT_UP).getString(owner, null))
    counts.libraryItems += watchlist.length() + favourites.length() + watchedMovies.length() + watchedTitles.length() +
      (0 until resume.length()).count { resume.optJSONObject(it)?.optBoolean("isLive") == false }
    return JSONObject()
      .put("watchlist", watchlist)
      .put("favouriteChannels", favourites)
      .put("resume", resume)
      .put("watchedEpisodes", watchedEpisodes)
      .put("watchedMovies", watchedMovies)
      .put("watchedTitles", watchedTitles)
      .put("nextUp", nextUp)
  }

  private fun buildCredentials(counts: BackupCounter): JSONObject {
    val debrid = JSONArray()
    DebridKeyStore.load(app).forEach { key ->
      debrid.put(
        JSONObject().put("provider", key.provider).put("apiKey", key.apiKey).put("priority", key.priority).put("enabled", key.enabled)
          .put("username", key.username ?: JSONObject.NULL).put("refreshToken", key.refreshToken ?: JSONObject.NULL)
          .put("oauthClientId", key.oauthClientId ?: JSONObject.NULL).put("oauthClientSecret", key.oauthClientSecret ?: JSONObject.NULL),
      )
      counts.credentials += 1
    }
    val services = JSONObject()
    val manager = ServiceCredentialManager(app)
    ContentService.values().forEach { service ->
      runCatching { manager.deviceKey(service) }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
        services.put(service.id, it)
        counts.credentials += 1
      }
    }
    return JSONObject().put("debrid", debrid).put("services", services)
  }

  /**
   * One owner's plugin document, in the shape the account stores it, read from storage so it
   * works for any profile rather than only the one selected.
   *
   * Scripts are left out - they are fetched from the repository again, the same as on a device the
   * account syncs to - and so are this device's file paths. Each source's current settings are
   * laid over the copy in the document, which is only as fresh as the last time it was written.
   */
  fun pluginDocumentFor(owner: String): JSONObject {
    val stateKey = "state:${owner.ifBlank { GUEST_OWNER_KEY }}"
    val jsPrefs = prefs(Stores.JS_PLUGINS)
    val root = jsonObject(jsPrefs.getString(stateKey, null))
    root.optJSONArray("providers")?.let { providers ->
      for (index in 0 until providers.length()) {
        val provider = providers.optJSONObject(index) ?: continue
        provider.put("code", "")
        jsPrefs.getString("settings:$stateKey:${provider.optString("id")}", null)
          ?.let { runCatching { JSONObject(it) }.getOrNull() }
          ?.let { provider.put("settings", it) }
      }
    }

    prefs(Stores.CS_PLUGINS).getString(stateKey, null)?.let { raw ->
      val section = jsonObject(raw)
      section.optJSONArray("providers")?.let { providers ->
        for (index in 0 until providers.length()) providers.optJSONObject(index)?.remove("installedFilePath")
      }
      section.remove("sourceSettings")?.let { switches -> if (switches is JSONArray && switches.length() > 0) root.put(CLOUDSTREAM_SOURCE_SETTINGS_KEY, switches) }
      if (section.has("repos") || section.has("providers")) root.put("cloudstream", section)
    }

    val skyPrefs = prefs(Stores.SKY_PLUGINS)
    skyPrefs.getString(stateKey, null)?.let { raw ->
      val section = jsonObject(raw)
      section.optJSONArray("providers")?.let { providers ->
        for (index in 0 until providers.length()) {
          val provider = providers.optJSONObject(index) ?: continue
          provider.remove("installedFilePath")
          skyPrefs.getString("settings:$stateKey:${provider.optString("packageName")}", null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let { provider.put("settings", it) }
        }
      }
      if (section.has("repos") || section.has("providers")) root.put("skystream", section)
    }
    return root
  }

  private fun filterPrivate(
    items: List<JSONObject>,
    urlField: String,
    nameField: String,
    category: BackupCategory,
    includeSecrets: Boolean,
    omitted: MutableList<BackupOmission>,
  ): JSONArray = JSONArray().apply {
    items.forEach { item ->
      if (!includeSecrets && urlCarriesPrivateConfig(item.optString(urlField))) {
        omitted += BackupOmission(category, item.optString(nameField).ifBlank { item.optString(urlField).substringBefore('?') }, BackupOmissionReason.NeedsPassphrase)
      } else {
        put(item)
      }
    }
  }

  private fun addonName(record: JSONObject): String =
    record.optString("name").takeIf { it.isNotBlank() }
      ?: runCatching { JSONObject(record.optString("manifestJson")).optString("name") }.getOrNull()?.takeIf { it.isNotBlank() }
      ?: record.optString("manifestUrl")

  // ── Staging a restore ─────────────────────────────────────────────────────────────────────

  /**
   * Works out everything a restore will write, without writing any of it.
   *
   * Every section is read and merged here, so a backup that turns out to be damaged halfway
   * through is found out before anything on the device has changed. A section that cannot be read
   * is skipped and reported rather than failing the whole restore - one playlist record that no
   * longer parses should not cost the viewer their settings.
   */
  fun stage(
    payload: JSONObject,
    source: JSONObject,
    target: BackupProfileRef,
    categories: Set<BackupCategory>,
    now: Long = System.currentTimeMillis(),
  ): StagedRestore {
    val writes = LinkedHashMap<String, MutableMap<String, Any>>()
    // Only what would actually change. A value the device already holds is not written, so the
    // recovery point - and an undo - covers only the files the restore really touched, and cannot
    // put back an older copy of a file that a sync has written to since.
    fun write(file: String, key: String, value: Any?) {
      if (value == null || prefs(file).all[key] == value) return
      writes.getOrPut(file) { LinkedHashMap() }[key] = value
    }
    val report = RestoreReportBuilder()
    val owner = target.ownerKey
    val device = payload.optJSONObject("device") ?: JSONObject()
    val samePlatform = device.optString("platform", BACKUP_PLATFORM_MOBILE) == BACKUP_PLATFORM_MOBILE

    // Settings. A device setting from another kind of StreamDek describes other hardware, so only
    // a backup from this platform brings them; profile settings are shared across platforms.
    fun restoreSettings(array: JSONArray?, scope: BackupSettingScope, file: (BackupSettingSpec) -> String) {
      array ?: return
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val key = item.optString("key")
        val spec = BackupSettingsRegistry.spec(scope, key)
        if (spec == null || spec.store != item.optString("store", BACKUP_APP_STORE)) { report.settingsSkipped += 1; continue }
        if (spec.category !in categories) continue
        val value = decodeBackupSettingValue(item)
        if (value == null) { report.settingsSkipped += 1; continue }
        write(file(spec), key, value)
        report.settingsRestored += 1
      }
    }
    section(report, "settings") {
      if (samePlatform) {
        restoreSettings(device.optJSONArray("settings"), BackupSettingScope.Device) { settingsFile(it.store) }
      } else {
        report.otherPlatform = device.optString("platform")
      }
      restoreSettings(source.optJSONArray("settings"), BackupSettingScope.Profile) { profileSettingsStorageName(owner) }
      if (BackupCategory.Appearance in categories) {
        device.optJSONObject("displayNames")?.let { names ->
          names.keys().forEach { key -> names.optString(key).takeIf { it.isNotBlank() }?.let { write(Stores.DISPLAY_NAMES, key, it) } }
        }
      }
    }
    val notifications = if (BackupCategory.General in categories) {
      source.optJSONObject("notifications")?.let {
        EpisodeNotificationSettings(
          availableEnabled = it.optBoolean("available"),
          upcomingEnabled = it.optBoolean("upcoming"),
          upcomingDays = it.optInt("upcomingDays", 1).takeIf { days -> days in setOf(1, 2, 7) } ?: 1,
        )
      }
    } else null

    // Add-ons: this device's own are a local store; the backend's are handed to the caller.
    val serverAddons = mutableListOf<BackupAddonEntry>()
    if (BackupCategory.Addons in categories) {
      section(report, "addons") {
        val all = jsonObjectList(source.optJSONArray("addons"))
        val local = JSONArray().apply { all.filter { it.optBoolean("local") }.forEach { put(JSONObject(it.toString()).apply { remove("local"); remove("name") }) } }
        mergeRestoredOrderedList(local, prefs(Stores.LOCAL_ADDONS).getString("addons:$owner", null), manifestUrlIdentity("manifestUrl"))
          ?.takeIf { local.length() > 0 }?.let { write(Stores.LOCAL_ADDONS, "addons:$owner", it) }
        all.filterNot { it.optBoolean("local") }.forEach { item ->
          val url = item.optString("manifestUrl").trim().takeIf { it.isNotEmpty() } ?: return@forEach
          serverAddons += BackupAddonEntry(
            id = item.optString("id").takeIf { it.isNotBlank() },
            manifestUrl = url,
            name = item.optString("name").ifBlank { url },
            enabled = item.optBoolean("enabled", true),
            favourite = item.optBoolean("favourite", false),
            position = item.optInt("position", serverAddons.size),
          )
        }
        report.addons = all.size
      }
      section(report, "subtitle sources") {
        val sources = source.optJSONArray("subtitleSources")
        mergeRestoredOrderedList(sources, prefs(Stores.SUBTITLE_SOURCES).getString("sources:$owner", null), manifestUrlIdentity("baseUrl"))
          ?.takeIf { (sources?.length() ?: 0) > 0 }?.let { write(Stores.SUBTITLE_SOURCES, "sources:$owner", it) }
      }
    }

    if (BackupCategory.Playlists in categories) {
      section(report, "playlists") {
        val playlists = source.optJSONArray("playlists")
        mergeRestoredOrderedList(playlists, prefs(Stores.PLAYLISTS).getString("sources:$owner", null), manifestUrlIdentity("url"))
          ?.takeIf { (playlists?.length() ?: 0) > 0 }?.let { write(Stores.PLAYLISTS, "sources:$owner", it) }
        report.playlists = playlists?.length() ?: 0
      }
    }

    var pluginDocument: JSONObject? = null
    if (BackupCategory.Plugins in categories) {
      section(report, "plugins") {
        val fromBackup = source.optJSONObject("plugins")
        if (fromBackup != null && countPluginDocument(fromBackup).let { it.first + it.second } > 0) {
          pluginDocument = mergeRestoredPluginDocument(fromBackup, pluginDocumentFor(owner), now)
          report.pluginSources = countPluginDocument(fromBackup).second
        }
      }
    }

    if (BackupCategory.Library in categories) {
      section(report, "library") {
        val library = source.optJSONObject("library") ?: JSONObject()
        val watchlistPrefs = prefs(Stores.WATCHLIST)
        mergeRestoredLibraryList(library.optJSONArray("watchlist"), watchlistPrefs.getString(owner, null), ::watchlistIdentity)
          ?.let { write(Stores.WATCHLIST, owner, it) }
        mergeRestoredLibraryList(library.optJSONArray("favouriteChannels"), prefs(Stores.FAVOURITE_CHANNELS).getString(owner, null)) {
          it.optString("id").takeIf(String::isNotBlank)
        }?.let { write(Stores.FAVOURITE_CHANNELS, owner, it) }
        mergeRestoredResumeEntries(library.optJSONArray("resume"), prefs(Stores.RESUME).getString("resume:$owner", null))
          ?.let { write(Stores.RESUME, "resume:$owner", it) }
        library.optJSONObject("watchedEpisodes")?.let { shows ->
          val episodes = prefs(Stores.WATCHED_EPISODES)
          shows.keys().forEach { showId ->
            val key = "watched:$owner:$showId"
            unionJsonStringArrays(episodes.getString(key, null), shows.optJSONArray(showId)?.toString())?.let { write(Stores.WATCHED_EPISODES, key, it) }
          }
        }
        unionJsonStringArrays(prefs(Stores.WATCHED_MOVIES).getString("watched_movies:$owner", null), library.optJSONArray("watchedMovies")?.toString())
          ?.let { write(Stores.WATCHED_MOVIES, "watched_movies:$owner", it) }
        unionJsonStringArrays(prefs(Stores.WATCHED_TITLES).getString("watched_titles:$owner", null), library.optJSONArray("watchedTitles")?.toString())
          ?.let { write(Stores.WATCHED_TITLES, "watched_titles:$owner", it) }
        mergeRestoredNextUp(library.optJSONArray("nextUp"), prefs(Stores.NEXT_UP).getString(owner, null))
          ?.let { write(Stores.NEXT_UP, owner, it) }
        // Counted the way the backup counted them, so the two summaries agree: live channels in the
        // resume list remember a source, they are not something part-watched.
        report.libraryItems = listOf("watchlist", "favouriteChannels", "watchedMovies", "watchedTitles")
          .sumOf { library.optJSONArray(it)?.length() ?: 0 } +
          jsonObjectList(library.optJSONArray("resume")).count { !it.optBoolean("isLive") }
      }
    }

    val debridKeys = mutableListOf<DebridKeyStore.StoredKey>()
    val serviceKeys = LinkedHashMap<ContentService, String>()
    if (BackupCategory.Credentials in categories) {
      section(report, "credentials") {
        val credentials = payload.optJSONObject("credentials") ?: JSONObject()
        jsonObjectList(credentials.optJSONArray("debrid")).forEach { item ->
          val provider = item.optString("provider").takeIf { it.isNotBlank() } ?: return@forEach
          val apiKey = item.optString("apiKey").takeIf { it.isNotBlank() } ?: return@forEach
          fun text(name: String) = if (item.isNull(name)) null else item.optString(name).takeIf { it.isNotBlank() }
          debridKeys += DebridKeyStore.StoredKey(
            provider, apiKey, item.optInt("priority"), item.optBoolean("enabled", true),
            text("username"), text("refreshToken"), text("oauthClientId"), text("oauthClientSecret"),
          )
        }
        credentials.optJSONObject("services")?.let { services ->
          ContentService.values().forEach { service ->
            services.optString(service.id).takeIf { it.isNotBlank() }?.let { serviceKeys[service] = it }
          }
        }
        report.credentials = debridKeys.size + serviceKeys.size
      }
    }

    // What the backup itself said it had to leave out, so the summary can name it.
    jsonObjectList(payload.optJSONArray("omitted")).forEach { item ->
      val category = BackupCategory.fromId(item.optString("category")) ?: return@forEach
      if (category in categories || category == BackupCategory.Credentials) report.leftOutAtBackup += item.optString("name")
    }

    return StagedRestore(
      target = target,
      categories = categories,
      writes = writes,
      pluginDocument = pluginDocument,
      addons = serverAddons.sortedBy { it.position },
      notifications = notifications,
      debridKeys = debridKeys,
      serviceKeys = serviceKeys,
      report = report,
    )
  }

  private inline fun section(report: RestoreReportBuilder, name: String, block: () -> Unit) {
    runCatching(block).onFailure { error ->
      Log.w(TAG, "Backup section '$name' could not be restored", error)
      report.damagedSections += name
    }
  }

  // ── Committing ────────────────────────────────────────────────────────────────────────────

  /**
   * Writes a staged restore, all of it or none of it.
   *
   * First a recovery point: every preference file the restore is about to touch, copied exactly,
   * and the plugin document as it stands. Then each file is written and committed; if any commit
   * fails, the files already written are put back from the recovery point, and the restore fails
   * with nothing changed. The plugin engines, which write their own storage, are taken through
   * [applyPluginDocument] afterwards, once the rest is known to have landed.
   */
  fun commit(staged: StagedRestore, addonsBefore: List<BackupAddonEntry>, now: Long = System.currentTimeMillis()) {
    val files = staged.writes.keys.toMutableSet()
    if (staged.debridKeys.isNotEmpty()) files += Stores.DEBRID_KEYS
    if (staged.serviceKeys.isNotEmpty()) files += listOf(Stores.SERVICE_KEYS, Stores.SERVICE_KEY_META)
    if (staged.notifications != null) files += EPISODE_NOTIFICATION_PREFS
    // Favourites carry a record of edits the account has not confirmed; the restore adds to it.
    if (Stores.FAVOURITE_CHANNELS in files) files += FAVOURITE_CHANNEL_SYNC_PREFS

    val point = RecoveryPoint(
      createdAt = now,
      ownerKey = staged.target.ownerKey,
      profileName = staged.target.name,
      files = files.associateWith { name -> prefs(name).all.toMap() },
      pluginDocument = if (staged.pluginDocument != null) pluginDocumentFor(staged.target.ownerKey) else null,
      addonsBefore = addonsBefore,
    )
    saveRecoveryPoint(point)

    val written = mutableListOf<String>()
    try {
      staged.writes.forEach { (file, values) ->
        val editor = prefs(file).edit()
        values.forEach { (key, value) -> putTyped(editor, key, value) }
        check(editor.commit()) { "Could not write $file" }
        written += file
      }
    } catch (error: Throwable) {
      Log.w(TAG, "Restore failed while writing; putting ${written.size} files back", error)
      written.forEach { restoreFileExactly(it, point.files[it].orEmpty()) }
      throw error
    }

    val owner = staged.target.ownerKey
    staged.notifications?.let { runCatching { EpisodeNotificationSystem.saveSettings(app, owner, it) } }
    // Through the stores rather than around them, for what they do on the way in: favourites are
    // marked as waiting to reach the account, and resume positions are trimmed to their budgets.
    if (Stores.FAVOURITE_CHANNELS in staged.writes) {
      runCatching { FavouriteChannelStore(app).let { store -> store.save(owner, store.load(owner)) } }
    }
    if (Stores.RESUME in staged.writes) {
      runCatching { PlaybackResumeStore(app).let { store -> store.replaceAll(owner, store.loadAll(owner)) } }
    }
    if (staged.serviceKeys.isNotEmpty()) {
      val manager = ServiceCredentialManager(app)
      staged.serviceKeys.forEach { (service, key) -> runCatching { manager.saveDeviceKey(service, key) } }
    }
    prefs(Stores.BACKUP_META).edit().putLong(KEY_LAST_RESTORE_AT, now).apply()
  }

  /**
   * Hands a plugin document to the three plugin engines, for the profile that is selected.
   *
   * The same entry points the account sync uses, so a restore takes exactly the path a document
   * arriving from another device would: the engines keep any file they already have, fetch what
   * they do not, and bring switched-on sources up.
   *
   * @return the names of repositories whose scripts could not be fetched.
   */
  suspend fun applyPluginDocument(document: JSONObject): List<String> {
    val failed = mutableListOf<String>()
    runCatching {
      val manager = StreamDekPlugins.manager
      manager.restoreCloudState(document.toString())
      manager.state.repos
        .filter { repo -> manager.state.providers.none { it.repoUrl == repo.url } || manager.state.providers.any { it.repoUrl == repo.url && it.code.isBlank() } }
        .forEach { repo -> manager.refresh(repo.url).onFailure { failed += repo.name.ifBlank { repo.url } } }
    }.onFailure { Log.w(TAG, "Could not restore plugin collections", it) }
    if (CloudStreamPlugins.isInitialized) {
      runCatching {
        val manager = CloudStreamPlugins.manager
        document.optJSONObject("cloudstream")?.let { manager.restoreCloudState(it.toString()) }
        document.optJSONArray(CLOUDSTREAM_SOURCE_SETTINGS_KEY)?.let { manager.mergeCloudSourceSettings(it) }
        manager.loadEnabledProviders()
      }.onFailure { Log.w(TAG, "Could not restore CloudStream collections", it) }
    }
    if (SkyStreamPlugins.isInitialized) {
      runCatching { document.optJSONObject("skystream")?.let { SkyStreamPlugins.manager.restoreCloudState(it.toString()) } }
        .onFailure { Log.w(TAG, "Could not restore SkyStream collections", it) }
    }
    return failed
  }

  /**
   * Points the profile's Home layout at the ids its add-ons were given when they were installed
   * again: a layout names add-on rows by id, and the backend mints a new id per installation.
   */
  fun remapHomeRows(ownerKey: String, addonIds: Map<String, String>) {
    if (addonIds.isEmpty()) return
    val storage = prefs(profileSettingsStorageName(ownerKey))
    val rows = storage.getString("home_catalog_rows", null)
    val order = storage.getString(HOME_ROW_SOURCE_ORDER_PREFERENCE, null)
    storage.edit()
      .putString("home_catalog_rows", remapHomeRowAddonIds(rows, addonIds))
      .putString(HOME_ROW_SOURCE_ORDER_PREFERENCE, remapHomeRowSourceOrder(order, addonIds))
      .commit()
  }

  // ── Recovery point ────────────────────────────────────────────────────────────────────────

  private val recoveryFile: File get() = File(File(app.filesDir, "backup-recovery").apply { mkdirs() }, "before-restore.json")

  /** When the last restore happened and whether it can still be undone. */
  fun recoveryPointInfo(): RecoveryPointInfo? = runCatching {
    if (!recoveryFile.exists()) return null
    val root = JSONObject(recoveryFile.readText())
    RecoveryPointInfo(root.getLong("createdAt"), root.optString("profileName"), root.getString("ownerKey"))
  }.getOrNull()

  fun lastBackupAt(): Long = prefs(Stores.BACKUP_META).getLong(KEY_LAST_BACKUP_AT, 0L)

  fun markBackupCreated(at: Long = System.currentTimeMillis()) {
    prefs(Stores.BACKUP_META).edit().putLong(KEY_LAST_BACKUP_AT, at).apply()
  }

  fun deleteRecoveryPoint() {
    recoveryFile.delete()
  }

  /**
   * Puts every file the last restore touched back exactly as it was, and returns what the caller
   * needs to finish the job: the plugin document and the add-ons as they were.
   *
   * Exact means exact: anything changed in those files since the restore - a position recorded
   * while watching, say - goes too. The screen says so before offering it.
   */
  fun undo(now: Long = System.currentTimeMillis()): RecoveryPoint? {
    val point = loadRecoveryPoint() ?: return null
    point.files.forEach { (file, values) -> restoreFileExactly(file, values) }
    return point.copy(pluginDocument = point.pluginDocument?.let { restampPluginDocument(it, now) })
  }

  /** Records which add-ons a restore installed, so undoing it can remove them again. */
  fun recordInstalledAddons(urls: List<String>) {
    val point = loadRecoveryPoint() ?: return
    saveRecoveryPoint(point.copy(addonsInstalled = urls))
  }

  private fun restampPluginDocument(document: JSONObject, now: Long): JSONObject {
    val copy = JSONObject(document.toString()).put("updatedAt", now)
    listOf("cloudstream", "skystream").forEach { copy.optJSONObject(it)?.put("updatedAt", now) }
    return copy
  }

  private fun saveRecoveryPoint(point: RecoveryPoint) {
    val root = JSONObject()
      .put("createdAt", point.createdAt)
      .put("ownerKey", point.ownerKey)
      .put("profileName", point.profileName)
      .put("files", JSONObject().apply { point.files.forEach { (file, values) -> put(file, encodeFile(values)) } })
      .put("pluginDocument", point.pluginDocument ?: JSONObject.NULL)
      .put("addonsBefore", JSONArray().apply { point.addonsBefore.forEach { put(it.toJson()) } })
      .put("addonsInstalled", JSONArray(point.addonsInstalled))
    // Written beside and then moved over, so a process killed halfway leaves the previous point.
    val temp = File(recoveryFile.parentFile, "before-restore.tmp")
    temp.writeText(root.toString())
    if (!temp.renameTo(recoveryFile)) {
      recoveryFile.delete()
      check(temp.renameTo(recoveryFile)) { "Could not save the recovery point" }
    }
  }

  private fun loadRecoveryPoint(): RecoveryPoint? = runCatching {
    val root = JSONObject(recoveryFile.readText())
    val files = root.getJSONObject("files")
    RecoveryPoint(
      createdAt = root.getLong("createdAt"),
      ownerKey = root.getString("ownerKey"),
      profileName = root.optString("profileName"),
      files = files.keys().asSequence().associateWith { decodeFile(files.getJSONArray(it)) },
      pluginDocument = root.optJSONObject("pluginDocument"),
      addonsBefore = jsonObjectList(root.optJSONArray("addonsBefore")).mapNotNull(BackupAddonEntry::fromJson),
      addonsInstalled = root.optJSONArray("addonsInstalled")?.let { array -> (0 until array.length()).map { array.optString(it) } }.orEmpty(),
    )
  }.onFailure { Log.w(TAG, "Recovery point unreadable", it) }.getOrNull()

  private fun encodeFile(values: Map<String, Any?>): JSONArray = JSONArray().apply {
    values.forEach { (key, value) -> encodeBackupSetting(key, value)?.let(::put) }
  }

  private fun decodeFile(array: JSONArray): Map<String, Any?> = buildMap {
    jsonObjectList(array).forEach { item -> decodeBackupSettingValue(item)?.let { put(item.optString("key"), it) } }
  }

  private fun restoreFileExactly(file: String, values: Map<String, Any?>) {
    val editor = prefs(file).edit().clear()
    values.forEach { (key, value) -> if (value != null) putTyped(editor, key, value) }
    editor.commit()
  }

  private fun putTyped(editor: SharedPreferences.Editor, key: String, value: Any) {
    when (value) {
      is Boolean -> editor.putBoolean(key, value)
      is Int -> editor.putInt(key, value)
      is Long -> editor.putLong(key, value)
      is Float -> editor.putFloat(key, value)
      is String -> editor.putString(key, value)
      is Set<*> -> editor.putStringSet(key, value.map { it.toString() }.toSet())
    }
  }

  private companion object {
    const val TAG = "StreamDekBackup"
    const val KEY_LAST_BACKUP_AT = "last_backup_at"
    const val KEY_LAST_RESTORE_AT = "last_restore_at"
    /** Owned by [EpisodeNotificationSystem] and [FavouriteChannelStore]; see [Stores]. */
    const val EPISODE_NOTIFICATION_PREFS = "streamdek_native_episode_notifications_v2"
    const val FAVOURITE_CHANNEL_SYNC_PREFS = "streamdek_native_favourite_channels_sync"
  }
}

// ── Types ───────────────────────────────────────────────────────────────────────────────────

/** A profile as the backup sees it: who it is, and the owner key its data is stored under. */
internal data class BackupProfileRef(
  val id: String?,
  val name: String,
  val avatarIndex: Int,
  val ownerKey: String,
  val active: Boolean,
)

/** The owner key a profile's data lives under; the same rule the view model uses. */
internal fun backupOwnerKey(userId: String?, profileId: String?): String = when {
  userId.isNullOrBlank() -> profileId?.let { "guest:$it" } ?: GUEST_OWNER_KEY
  profileId.isNullOrBlank() -> userId
  else -> "$userId:$profileId"
}

internal enum class BackupOmissionReason { NeedsPassphrase, CouldNotRead }

internal data class BackupOmission(val category: BackupCategory, val name: String, val reason: BackupOmissionReason)

internal class BackupBuildResult(val payload: JSONObject, val summary: BackupSummary, val omitted: List<BackupOmission>)

private class BackupCounter {
  var settings = 0
  var addons = 0
  var pluginRepositories = 0
  var pluginSources = 0
  var playlists = 0
  var libraryItems = 0
  var credentials = 0
}

/** One of the backend's add-ons, as a restore reinstalls it. */
internal data class BackupAddonEntry(
  val id: String?,
  val manifestUrl: String,
  val name: String,
  val enabled: Boolean,
  val favourite: Boolean,
  val position: Int,
) {
  fun toJson(): JSONObject = JSONObject().put("id", id ?: JSONObject.NULL).put("manifestUrl", manifestUrl).put("name", name)
    .put("enabled", enabled).put("favourite", favourite).put("position", position)

  companion object {
    fun fromJson(item: JSONObject): BackupAddonEntry? {
      val url = item.optString("manifestUrl").takeIf { it.isNotBlank() } ?: return null
      return BackupAddonEntry(
        id = if (item.isNull("id")) null else item.optString("id").takeIf { it.isNotBlank() },
        manifestUrl = url,
        name = item.optString("name").ifBlank { url },
        enabled = item.optBoolean("enabled", true),
        favourite = item.optBoolean("favourite", false),
        position = item.optInt("position"),
      )
    }
  }
}

internal class StagedRestore(
  val target: BackupProfileRef,
  val categories: Set<BackupCategory>,
  val writes: Map<String, Map<String, Any>>,
  val pluginDocument: JSONObject?,
  val addons: List<BackupAddonEntry>,
  val notifications: EpisodeNotificationSettings?,
  val debridKeys: List<DebridKeyStore.StoredKey>,
  val serviceKeys: Map<ContentService, String>,
  val report: RestoreReportBuilder,
)

/** What a restore did, gathered as it goes; see [RestoreReport] for the finished form. */
internal class RestoreReportBuilder {
  var settingsRestored = 0
  var settingsSkipped = 0
  var addons = 0
  var playlists = 0
  var pluginSources = 0
  var libraryItems = 0
  var credentials = 0
  var otherPlatform: String? = null
  val damagedSections = mutableListOf<String>()
  val leftOutAtBackup = mutableListOf<String>()
}

internal data class RecoveryPoint(
  val createdAt: Long,
  val ownerKey: String,
  val profileName: String,
  val files: Map<String, Map<String, Any?>>,
  val pluginDocument: JSONObject?,
  val addonsBefore: List<BackupAddonEntry>,
  val addonsInstalled: List<String> = emptyList(),
)

internal data class RecoveryPointInfo(val createdAt: Long, val profileName: String, val ownerKey: String)

// ── Small JSON helpers ──────────────────────────────────────────────────────────────────────

private fun jsonArray(raw: String?): JSONArray = runCatching { JSONArray(raw.orEmpty().ifBlank { "[]" }) }.getOrDefault(JSONArray())

private fun jsonObject(raw: String?): JSONObject = runCatching { JSONObject(raw.orEmpty().ifBlank { "{}" }) }.getOrDefault(JSONObject())

private fun jsonObjects(raw: String?): List<JSONObject> = jsonObjectList(jsonArray(raw))

internal fun jsonObjectList(array: JSONArray?): List<JSONObject> =
  if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optJSONObject(it) }
