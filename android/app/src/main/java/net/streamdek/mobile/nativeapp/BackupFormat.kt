package net.streamdek.mobile.nativeapp

import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * The `.streamdek` backup file: what it holds, how it is sealed, and how an old one is read.
 *
 * A backup is a single JSON document - an *envelope* - around a *payload*:
 *
 * ```
 * {
 *   "format": "streamdek-backup",
 *   "schemaVersion": 1,
 *   "createdAt": 1789740000000,
 *   "app": { "name": "StreamDek Mobile", "version": "2.1.20" },
 *   "platform": "android-mobile",
 *   "categories": ["addons", "plugins", ...],
 *   "summary": { "profiles": 2, "addons": 5, ... },
 *   "checksum": { "algorithm": "SHA-256", "value": "<hex>" },
 *   "encryption": null | { "algorithm": ..., "iterations": ..., "salt": ..., "iv": ... },
 *   "payload": { ... }            // when not encrypted
 *   "ciphertext": "<base64>"      // when encrypted
 * }
 * ```
 *
 * The payload is deliberately *not* a copy of this app's preference files. Settings travel as
 * named, typed values; add-ons, plugins and playlists travel in the same shapes StreamDek already
 * syncs to the account, which the web portal and the television app read too. That is what lets a
 * backup outlive changes to how this app happens to store things, and what [BACKUP_MIGRATIONS]
 * exists for: when the shape of the payload changes, the version goes up and a migration turns an
 * older payload into the current one, so a backup made today still restores in two years.
 *
 * Everything in this file is plain JVM code, with no Android in it, so it is tested directly.
 */

internal const val BACKUP_FORMAT_ID = "streamdek-backup"

/** Bumped whenever the payload's shape changes, with a migration added to [BACKUP_MIGRATIONS]. */
internal const val BACKUP_SCHEMA_VERSION = 1
internal const val BACKUP_FILE_EXTENSION = "streamdek"

/**
 * What the file picker is told the file is. A generic type rather than a made-up one, which some
 * pickers refuse and others rename to `.bin`; the extension is what identifies it.
 */
internal const val BACKUP_MIME_TYPE = "application/octet-stream"

/** Which StreamDek wrote a backup. Settings marked for a platform only restore on that platform. */
internal const val BACKUP_PLATFORM_MOBILE = "android-mobile"

/** Far beyond any real setup; a limit so a wrong file (a film, say) is refused rather than read. */
internal const val BACKUP_MAX_BYTES = 32 * 1024 * 1024

/** Passphrases shorter than this are refused when a backup is created. */
internal const val BACKUP_MIN_PASSPHRASE_LENGTH = 8

// ── Categories ──────────────────────────────────────────────────────────────────────────────

/**
 * The groups a backup is described and restored in.
 *
 * [id] is written into backups and is a storage contract: renaming one would make older backups
 * report that group as unknown. The order is the order they are shown in.
 */
internal enum class BackupCategory(val id: String) {
  /** Stremio-style add-ons and the custom subtitle sources beside them. */
  Addons("addons"),
  /** Plugin repositories, the sources inside them, their switches, order and settings. */
  Plugins("plugins"),
  /** M3U/IPTV playlists and the Live TV preferences around them. */
  Playlists("playlists"),
  /** The player, subtitles, audio, streams and quality, skipping, downloads, peer-to-peer. */
  Playback("playback"),
  /** Theme, language, navigation, Home and title-page layout. */
  Appearance("appearance"),
  /** Everything else: sync, startup, network, notifications. */
  General("general"),
  /** Watchlist, Continue Watching, watched history and favourite channels. */
  Library("library"),
  /** Service API keys and premium-service keys. Only ever present in an encrypted backup. */
  Credentials("credentials"),
  ;

  companion object {
    fun fromId(id: String): BackupCategory? = values().firstOrNull { it.id == id }
  }
}

// ── Settings ────────────────────────────────────────────────────────────────────────────────

/** Where a setting lives: one value for this installation, or one per profile. */
internal enum class BackupSettingScope { Device, Profile }

/** The app's own settings file. */
internal const val BACKUP_APP_STORE = "app"

/** The network settings (DNS over HTTPS), which keep a file of their own. */
internal const val BACKUP_NETWORK_STORE = "network"

/**
 * One setting a backup carries.
 *
 * [key] is the name the setting is stored under, which is already a storage contract - renaming
 * one would lose every existing value - so it doubles as the setting's stable name in a backup.
 * [store] says which file a device setting lives in. [sensitive] settings are secrets and only
 * travel in an encrypted backup.
 */
internal data class BackupSettingSpec(
  val key: String,
  val scope: BackupSettingScope,
  val category: BackupCategory,
  val sensitive: Boolean = false,
  val store: String = BACKUP_APP_STORE,
)

/**
 * Every setting a backup carries, and which group it belongs to.
 *
 * `BackupSettingsRegistryTest` reads the settings store's source and fails when a setting it reads
 * is neither listed here nor in [excludedKeys], so a new setting cannot quietly go unbacked-up.
 */
internal object BackupSettingsRegistry {
  private fun device(category: BackupCategory, vararg keys: String) =
    keys.map { BackupSettingSpec(it, BackupSettingScope.Device, category) }

  private fun profile(category: BackupCategory, vararg keys: String) =
    keys.map { BackupSettingSpec(it, BackupSettingScope.Profile, category) }

  val specs: List<BackupSettingSpec> = buildList {
    addAll(
      device(
        BackupCategory.Appearance,
        "app_appearance", "theme_preset", "header_style", "show_nav_labels", "collapsible_navigation_enabled",
        "navigation_auto_collapse_seconds", ANIMATION_SPEED_PREFERENCE, APP_LANGUAGE_PREFERENCE,
        NavigationBehaviour.TRIGGER_PREFERENCE, NavigationBehaviour.EXPANDED_HEADERS_PREFERENCE,
        VISUAL_EFFECTS_PREFERENCE, HOME_DENSITY_PREFERENCE, MEDIA_HUB_PREFERENCE,
      ),
    )
    addAll(
      device(
        BackupCategory.Playback,
        "pip_enabled", "decoder_mode", "render_surface", "player_engine",
        "preferred_audio_language", "secondary_audio_language", "preferred_subtitle_language", "secondary_subtitle_language",
        "use_forced_subtitles", "show_only_preferred_subtitle_languages", "addon_subtitle_loading",
        "hold_to_speed_enabled", "hold_to_speed_multiplier", "swipe_to_seek_enabled", "double_tap_seek_enabled",
        "double_tap_seek_seconds", "double_tap_play_pause_enabled", "show_player_control_labels", "player_control_layout",
        "fullscreen_status_bar", "player_title_display", "player_level_gestures_enabled", "dv7_hevc_fallback",
        "tunneled_playback", "downloads_enabled",
        "torrent_enabled", "torrent_streaming_mode", "torrent_profile", "torrent_cache_size_gb", "torrent_port", "torrent_run_foreground",
      ),
    )
    addAll(device(BackupCategory.General, "remember_last_profile_at_startup", "sync_on_cellular", "debrid_cloud_sync", "auto_update_checks"))
    listOf("doh_enabled", "doh_provider", "doh_custom_endpoint").forEach {
      add(BackupSettingSpec(it, BackupSettingScope.Device, BackupCategory.General, store = BACKUP_NETWORK_STORE))
    }
    addAll(
      profile(
        BackupCategory.Appearance,
        "detail_page_style", "season_tab_style", "episode_layout", "continue_watching_style", "home_card_text_mode",
        "network_card_style", "show_hero_synopsis", "vivid_ambient", "detail_background_mode", "home_background_mode",
        "ambient_tint_percent", "detail_ambient_tint_percent", "blur_unwatched_episodes", "hero_trailer_autoplay",
        "hero_trailer_resolution", "hero_trailer_delay_seconds", "hero_trailer_muted", "trailer_cache_clear_hours",
        "ratings_enabled", "external_ratings_enabled", "enabled_rating_providers", "show_new_episodes_row",
        "new_episodes_landscape", "default_app_catalogs_enabled", "home_catalog_rows", HOME_ROW_MODE_PREFERENCE,
        HOME_ROW_SOURCE_ORDER_PREFERENCE,
      ),
    )
    addAll(
      profile(
        BackupCategory.Playlists,
        "live_landscape_cards", "live_favourite_drawer_cards", "live_categories_enabled", "live_progress_bar", "live_badge",
      ),
    )
    addAll(
      profile(
        BackupCategory.Playback,
        "show_streams_list", "remember_last_source", "favorite_source_keys",
        "skip_intro_enabled", "skip_segments_enabled", "skip_recap_enabled", "skip_ending_enabled",
        "auto_skip_intro_enabled", "auto_skip_recap_enabled", "auto_skip_ending_enabled",
        "auto_play_next_episode", "prefer_binge_group", "auto_load_subtitles",
        "subtitle_text_size", "subtitle_vertical_offset", "subtitle_bold", "subtitle_text_color",
        "subtitle_background_color", "subtitle_outline", "subtitle_outline_color", "subtitle_default_source",
        "next_episode_threshold_mode", "next_episode_threshold_percent", "next_episode_threshold_minutes",
        "end_of_playback_recommendations_enabled", "recommendation_timing", "recommendation_item_count",
        "timing_provider", "timing_provider_fallback_enabled",
        "fusion_badges", "streamdek_stream_formatting", "show_size_badges", "preferred_quality", "max_file_size_gb",
        "badge_position", "fusion_badge_urls", "active_fusion_badge_url",
      ),
    )
    addAll(profile(BackupCategory.General, "primary_sync_service"))
    add(BackupSettingSpec("introdb_api_key", BackupSettingScope.Profile, BackupCategory.Credentials, sensitive = true))
  }

  /**
   * Keys the settings store touches that a backup leaves out on purpose.
   *
   * `mdblist_api_key` is a legacy plaintext copy that is moved into the encrypted vault and deleted
   * on first read; restoring it would put a plaintext key back on disk.
   */
  val excludedKeys: Set<String> = setOf("mdblist_api_key")

  private val byScopeAndKey = specs.associateBy { it.scope to it.key }

  fun spec(scope: BackupSettingScope, key: String): BackupSettingSpec? = byScopeAndKey[scope to key]
}

/**
 * A stored preference as a typed backup value: `{"key", "type", "value"}`.
 *
 * Floats travel as strings. JSON numbers are doubles to most readers, and a float that went out as
 * 1.1 and came back as 1.100000023841858 would still be the same setting but would no longer match
 * the checksum it was written with.
 */
internal fun encodeBackupSetting(key: String, value: Any?): JSONObject? {
  val (type, encoded) = when (value) {
    is Boolean -> "bool" to value
    is Int -> "int" to value
    is Long -> "long" to value
    is Float -> "float" to value.toString()
    is String -> "string" to value
    is Set<*> -> "stringSet" to JSONArray(value.map { it.toString() }.sorted())
    else -> return null
  }
  return JSONObject().put("key", key).put("type", type).put("value", encoded)
}

/** The value [encodeBackupSetting] wrote, or null when it is missing or of an unknown type. */
internal fun decodeBackupSettingValue(item: JSONObject): Any? {
  if (!item.has("value") || item.isNull("value")) return null
  return when (item.optString("type")) {
    "bool" -> item.opt("value") as? Boolean
    "int" -> (item.opt("value") as? Number)?.toInt()
    "long" -> (item.opt("value") as? Number)?.toLong()
    "float" -> item.optString("value").toFloatOrNull()
    "string" -> item.opt("value") as? String
    "stringSet" -> item.optJSONArray("value")?.let { array -> (0 until array.length()).map { array.optString(it) }.toSet() }
    else -> null
  }
}

// ── What counts as a secret ─────────────────────────────────────────────────────────────────

/**
 * Whether a URL carries something private: a login, a token, or a personal configuration.
 *
 * Add-on and playlist links are where people's credentials actually end up. A configured Stremio
 * add-on keeps its settings - often a premium-service key - in a path segment before
 * `manifest.json`, and an IPTV playlist keeps the panel's username and password in its query. The
 * public links (`https://v3-cinemeta.strem.io/manifest.json`) have neither, so anything with a
 * query, a login, or a path segment that looks like encoded configuration is treated as private.
 *
 * Deliberately cautious: a false positive costs an unencrypted backup one link, which the restore
 * then names so it can be added again; a false negative writes someone's key into a plain file.
 */
internal fun urlCarriesPrivateConfig(raw: String?): Boolean {
  val value = raw?.trim().orEmpty()
  if (value.isEmpty()) return false
  val uri = runCatching { URI(value.replaceFirst(Regex("^stremio://", RegexOption.IGNORE_CASE), "https://")) }.getOrNull()
    ?: return true
  if (!uri.rawUserInfo.isNullOrEmpty() || !uri.rawQuery.isNullOrEmpty() || !uri.rawFragment.isNullOrEmpty()) return true
  val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
  return segments.dropLastWhile { it.equals("manifest.json", ignoreCase = true) }.any { segment ->
    segment.length >= 20 || segment.any { it in "=|%:;,@" }
  }
}

// ── Canonical form and checksum ─────────────────────────────────────────────────────────────

/**
 * JSON with its object keys sorted, so the same content always has the same bytes.
 *
 * What the checksum is taken over. A JSON library is free to write an object's keys in any order,
 * and one that reads a backup and writes it straight back out need not reproduce it byte for byte;
 * the canonical form is the same however the document was parsed.
 */
internal fun canonicalJson(value: Any?): String = StringBuilder().also { appendCanonical(it, value) }.toString()

private fun appendCanonical(out: StringBuilder, value: Any?) {
  when (value) {
    null, JSONObject.NULL -> out.append("null")
    is JSONObject -> {
      out.append('{')
      value.keys().asSequence().toList().sorted().forEachIndexed { index, key ->
        if (index > 0) out.append(',')
        out.append(JSONObject.quote(key)).append(':')
        appendCanonical(out, value.opt(key))
      }
      out.append('}')
    }
    is JSONArray -> {
      out.append('[')
      for (index in 0 until value.length()) {
        if (index > 0) out.append(',')
        appendCanonical(out, value.opt(index))
      }
      out.append(']')
    }
    is Boolean -> out.append(value.toString())
    is Number -> out.append(JSONObject.numberToString(value))
    else -> out.append(JSONObject.quote(value.toString()))
  }
}

internal fun sha256Hex(bytes: ByteArray): String =
  MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

// ── Encryption ──────────────────────────────────────────────────────────────────────────────

/**
 * Passphrase encryption for backups that carry secrets.
 *
 * PBKDF2-HMAC-SHA256 turns the passphrase into an AES-256 key, and AES-GCM seals the payload. The
 * envelope's readable header is bound in as additional data, so the summary shown before the
 * passphrase is asked for cannot be altered without the file failing to open.
 *
 * The iteration count is written into each backup rather than assumed, so it can be raised later
 * without older backups becoming unreadable; what a file may ask for is bounded, so a doctored one
 * cannot make the phone spend minutes deriving a key.
 */
internal object BackupCrypto {
  const val ALGORITHM = "PBKDF2-HMAC-SHA256/AES-256-GCM"
  const val DEFAULT_ITERATIONS = 310_000
  private val ITERATION_RANGE = 10_000..5_000_000
  private const val SALT_BYTES = 16
  private const val IV_BYTES = 12
  private const val TAG_BITS = 128
  private const val KEY_BYTES = 32

  class Sealed(val iterations: Int, val salt: ByteArray, val iv: ByteArray, val ciphertext: ByteArray)

  /**
   * Seals [plaintext]. The salt and IV are passed in rather than chosen here because they are part
   * of the header, and the header is [aad]: both have to exist before anything is sealed.
   */
  fun encrypt(plaintext: ByteArray, passphrase: CharArray, aad: ByteArray, salt: ByteArray, iv: ByteArray, iterations: Int): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(passphrase, salt, iterations), "AES"), GCMParameterSpec(TAG_BITS, iv))
    cipher.updateAAD(aad)
    return cipher.doFinal(plaintext)
  }

  fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
  fun newIv(): ByteArray = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)

  /** The plaintext, or null when the passphrase is wrong or the file has been altered. */
  fun decrypt(sealed: Sealed, passphrase: CharArray, aad: ByteArray): ByteArray? {
    if (sealed.iterations !in ITERATION_RANGE || sealed.salt.size < 8 || sealed.iv.size != IV_BYTES) return null
    return runCatching {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey(passphrase, sealed.salt, sealed.iterations), "AES"), GCMParameterSpec(TAG_BITS, sealed.iv))
      cipher.updateAAD(aad)
      cipher.doFinal(sealed.ciphertext)
    }.getOrNull()
  }

  fun isAcceptableIterationCount(iterations: Int): Boolean = iterations in ITERATION_RANGE

  /**
   * The platform's PBKDF2 where there is one, and the same function written out where there is
   * not: `PBKDF2WithHmacSHA256` only arrived in Android 8, and this app still runs on Android 7.
   * Both produce identical keys, which `BackupFormatTest` checks.
   */
  internal fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray =
    runCatching {
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(passphrase, salt, iterations, KEY_BYTES * 8)).encoded
    }.getOrNull() ?: pbkdf2HmacSha256(passphrase, salt, iterations, KEY_BYTES)

  /** RFC 8018 PBKDF2 with HMAC-SHA256, for devices without the platform implementation. */
  internal fun pbkdf2HmacSha256(passphrase: CharArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(String(passphrase).toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val out = ByteArray(length)
    var block = 1
    var offset = 0
    while (offset < length) {
      mac.update(salt)
      mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
      var u = mac.doFinal()
      val t = u.copyOf()
      repeat(iterations - 1) {
        u = mac.doFinal(u)
        for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
      }
      val count = minOf(t.size, length - offset)
      System.arraycopy(t, 0, out, offset, count)
      offset += count
      block += 1
    }
    return out
  }
}

// ── Versions ────────────────────────────────────────────────────────────────────────────────

/**
 * How to bring an older payload up to date: the entry for version N turns a version-N payload
 * into a version-(N+1) one. Empty while there has only been one version.
 *
 * A migration should rename and reshape, never discard: a setting that no longer exists is simply
 * ignored on restore, so there is no need to delete it here.
 */
internal val BACKUP_MIGRATIONS: Map<Int, (JSONObject) -> JSONObject> = emptyMap()

/** The payload as the current version describes it, or null when a step is missing. */
internal fun migrateBackupPayload(
  payload: JSONObject,
  fromVersion: Int,
  migrations: Map<Int, (JSONObject) -> JSONObject> = BACKUP_MIGRATIONS,
  toVersion: Int = BACKUP_SCHEMA_VERSION,
): JSONObject? {
  var current = payload
  for (version in fromVersion until toVersion) {
    val step = migrations[version] ?: return null
    current = step(current)
  }
  return current
}

// ── The envelope ────────────────────────────────────────────────────────────────────────────

/** What a backup says about itself, readable before any passphrase is asked for. */
internal data class BackupSummary(
  val createdAt: Long,
  val appVersion: String,
  val platform: String,
  val schemaVersion: Int,
  val encrypted: Boolean,
  val categories: Set<BackupCategory>,
  val profiles: Int,
  val addons: Int,
  val pluginRepositories: Int,
  val pluginSources: Int,
  val playlists: Int,
  val settings: Int,
  val libraryItems: Int,
  val credentials: Int,
) {
  fun toJson(): JSONObject = JSONObject()
    .put("profiles", profiles)
    .put("addons", addons)
    .put("pluginRepositories", pluginRepositories)
    .put("pluginSources", pluginSources)
    .put("playlists", playlists)
    .put("settings", settings)
    .put("libraryItems", libraryItems)
    .put("credentials", credentials)
}

/** Why a file could not be read, in terms the restore screen can explain. */
internal enum class BackupReadError {
  /** Not a StreamDek backup at all. */
  NotABackup,
  /** A StreamDek backup that is incomplete or has been altered since it was made. */
  Damaged,
  /** Made by a newer StreamDek than this one; updating the app will read it. */
  NewerVersion,
  /** Encrypted, and no passphrase has been given yet. */
  PassphraseRequired,
  /** Encrypted, and the passphrase given does not open it. */
  WrongPassphrase,
  /** Too large to be a backup. */
  TooLarge,
}

/** A file that has been read as far as it can be without a passphrase. */
internal class BackupFile(val header: JSONObject, val summary: BackupSummary, private val body: JSONObject) {
  val encrypted: Boolean get() = summary.encrypted

  /**
   * The payload, decrypted, verified against its checksum and migrated to the current version.
   * [passphrase] is only needed, and only used, for an encrypted backup.
   */
  fun open(passphrase: CharArray? = null): Result<JSONObject> {
    val plaintext: String = if (encrypted) {
      if (passphrase == null || passphrase.isEmpty()) return Result.failure(BackupReadException(BackupReadError.PassphraseRequired))
      val encryption = header.optJSONObject("encryption") ?: return failure(BackupReadError.Damaged)
      val sealed = runCatching {
        val decoder = Base64.getDecoder()
        BackupCrypto.Sealed(
          iterations = encryption.getInt("iterations"),
          salt = decoder.decode(encryption.getString("salt")),
          iv = decoder.decode(encryption.getString("iv")),
          ciphertext = decoder.decode(body.getString("ciphertext")),
        )
      }.getOrNull() ?: return failure(BackupReadError.Damaged)
      if (!BackupCrypto.isAcceptableIterationCount(sealed.iterations)) return failure(BackupReadError.Damaged)
      val bytes = BackupCrypto.decrypt(sealed, passphrase, canonicalJson(header).toByteArray(Charsets.UTF_8))
        ?: return failure(BackupReadError.WrongPassphrase)
      String(bytes, Charsets.UTF_8)
    } else {
      val payload = body.optJSONObject("payload") ?: return failure(BackupReadError.Damaged)
      canonicalJson(payload)
    }
    val expected = header.optJSONObject("checksum")?.optString("value").orEmpty()
    if (expected.isEmpty() || !expected.equals(sha256Hex(plaintext.toByteArray(Charsets.UTF_8)), ignoreCase = true)) {
      return failure(BackupReadError.Damaged)
    }
    val payload = runCatching { JSONObject(plaintext) }.getOrNull() ?: return failure(BackupReadError.Damaged)
    val migrated = migrateBackupPayload(payload, summary.schemaVersion) ?: return failure(BackupReadError.Damaged)
    return Result.success(migrated)
  }

  private fun failure(error: BackupReadError): Result<JSONObject> = Result.failure(BackupReadException(error))
}

internal class BackupReadException(val error: BackupReadError) : Exception(error.name)

/**
 * Builds the file: the header, the checksum over the payload, and the payload either as it is or
 * sealed with [passphrase].
 */
internal fun writeBackupFile(
  payload: JSONObject,
  summary: BackupSummary,
  appName: String,
  passphrase: CharArray?,
  iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
): ByteArray {
  val canonicalPayload = canonicalJson(payload)
  val header = JSONObject()
    .put("format", BACKUP_FORMAT_ID)
    .put("schemaVersion", BACKUP_SCHEMA_VERSION)
    .put("createdAt", summary.createdAt)
    .put("app", JSONObject().put("name", appName).put("version", summary.appVersion))
    .put("platform", summary.platform)
    .put("categories", JSONArray(summary.categories.sortedBy { it.ordinal }.map { it.id }))
    .put("summary", summary.toJson())
    .put("checksum", JSONObject().put("algorithm", "SHA-256").put("value", sha256Hex(canonicalPayload.toByteArray(Charsets.UTF_8))))
  val document = if (passphrase != null) {
    val salt = BackupCrypto.newSalt()
    val iv = BackupCrypto.newIv()
    val encoder = Base64.getEncoder()
    header.put(
      "encryption",
      JSONObject()
        .put("algorithm", BackupCrypto.ALGORITHM)
        .put("iterations", iterations)
        .put("salt", encoder.encodeToString(salt))
        .put("iv", encoder.encodeToString(iv)),
    )
    val ciphertext = BackupCrypto.encrypt(
      plaintext = canonicalPayload.toByteArray(Charsets.UTF_8),
      passphrase = passphrase,
      aad = canonicalJson(header).toByteArray(Charsets.UTF_8),
      salt = salt,
      iv = iv,
      iterations = iterations,
    )
    JSONObject(header.toString()).put("ciphertext", encoder.encodeToString(ciphertext))
  } else {
    header.put("encryption", JSONObject.NULL)
    JSONObject(header.toString()).put("payload", payload)
  }
  return document.toString(2).toByteArray(Charsets.UTF_8)
}

/**
 * Reads a file as far as its header: what it is, whether this version of StreamDek can read it,
 * and what it says it contains. Opening the payload is [BackupFile.open].
 */
internal fun readBackupFile(bytes: ByteArray): Result<BackupFile> {
  if (bytes.size > BACKUP_MAX_BYTES) return Result.failure(BackupReadException(BackupReadError.TooLarge))
  val document = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
    ?: return Result.failure(BackupReadException(BackupReadError.NotABackup))
  if (document.optString("format") != BACKUP_FORMAT_ID) return Result.failure(BackupReadException(BackupReadError.NotABackup))
  val schemaVersion = document.optInt("schemaVersion", 0)
  if (schemaVersion < 1) return Result.failure(BackupReadException(BackupReadError.Damaged))
  if (schemaVersion > BACKUP_SCHEMA_VERSION) return Result.failure(BackupReadException(BackupReadError.NewerVersion))
  val encrypted = document.optJSONObject("encryption") != null
  if (encrypted && !document.has("ciphertext")) return Result.failure(BackupReadException(BackupReadError.Damaged))
  if (!encrypted && document.optJSONObject("payload") == null) return Result.failure(BackupReadException(BackupReadError.Damaged))

  // The header is everything but the body, in the form it was sealed with.
  val header = JSONObject(document.toString()).apply {
    remove("payload")
    remove("ciphertext")
  }
  val counts = document.optJSONObject("summary") ?: JSONObject()
  val categories = document.optJSONArray("categories")?.let { array ->
    (0 until array.length()).mapNotNull { BackupCategory.fromId(array.optString(it)) }.toSet()
  }.orEmpty()
  val summary = BackupSummary(
    createdAt = document.optLong("createdAt", 0L),
    appVersion = document.optJSONObject("app")?.optString("version").orEmpty(),
    platform = document.optString("platform"),
    schemaVersion = schemaVersion,
    encrypted = encrypted,
    categories = categories,
    profiles = counts.optInt("profiles"),
    addons = counts.optInt("addons"),
    pluginRepositories = counts.optInt("pluginRepositories"),
    pluginSources = counts.optInt("pluginSources"),
    playlists = counts.optInt("playlists"),
    settings = counts.optInt("settings"),
    libraryItems = counts.optInt("libraryItems"),
    credentials = counts.optInt("credentials"),
  )
  return Result.success(BackupFile(header, summary, document))
}
