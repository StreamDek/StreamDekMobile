package net.streamdek.mobile.nativeapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.StringRes
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import net.streamdek.mobile.R

/**
 * The viewer's own TMDB and MDBList keys, and the one place that decides which one gets used.
 *
 * Two questions live here and nowhere else:
 *
 *  - **Where is a key kept?** Either on this device alone, encrypted under a key that never leaves
 *    the Android keystore, or saved to the StreamDek account so the television and the next phone
 *    have it without anyone typing it again. The viewer chooses, per service, and the choice is
 *    reversible in both directions.
 *  - **Which key answers a request?** Device key, then account key, then StreamDek's shared key
 *    while that fallback exists. The same order the backend applies, so a request the phone makes
 *    directly and one it makes through the backend resolve identically.
 *
 * No screen, repository or client is allowed to answer either question for itself. That is what
 * stops "the ratings use one key and the artwork uses another" from ever being true again.
 */

// ── The services ──────────────────────────────────────────────────────────────────────────────

/**
 * A content service the viewer can bring their own key to.
 *
 * The blurb, the bullets and the help text live on the enum rather than in the composables that
 * draw them, so the phone, the television and the portal describe the same service the same way.
 */
enum class ContentService(
  val id: String,
  val label: String,
  val tagline: String,
  val blurb: String,
  val uses: List<String>,
  val keyUrl: String,
  val howToGet: List<String>,
  val keyHint: String,
) {
  Tmdb(
    id = "tmdb",
    label = "TMDB",
    tagline = "Movies, Shows & Metadata",
    blurb = "Powers the artwork, descriptions and episode information across StreamDek.",
    uses = listOf(
      "Posters and backdrops",
      "Movie and series information",
      "Cast and crew",
      "Seasons and episodes",
      "Search and discovery",
    ),
    keyUrl = "https://www.themoviedb.org/settings/api",
    howToGet = listOf(
      "Create a free account at themoviedb.org.",
      "Open Settings, then API, and request an API key for personal use.",
      "Copy either the API Key or the API Read Access Token — StreamDek accepts both.",
    ),
    keyHint = "API key or read access token",
  ),
  Mdblist(
    id = "mdblist",
    label = "MDBList",
    tagline = "Ratings & Lists",
    blurb = "Brings in ratings from IMDb, Rotten Tomatoes and others, and keeps your lists in step.",
    uses = listOf(
      "IMDb, Rotten Tomatoes and Metacritic ratings",
      "Extra rating services on title pages",
      "Watchlist and list synchronisation",
    ),
    keyUrl = "https://mdblist.com/preferences",
    howToGet = listOf(
      "Sign in at mdblist.com.",
      "Open Preferences and scroll to the API key section.",
      "Generate a key if you have not already, then copy it.",
    ),
    keyHint = "MDBList API key",
  ),
  IntroDb(
    id = "introdb",
    label = "IntroDB",
    tagline = "Series Playback Timing",
    blurb = "Provides intro, recap and ending timestamps for series.",
    uses = listOf("Episode timing", "Intro and recap skipping", "Ending detection and next episode"),
    keyUrl = "https://introdb.app/account",
    howToGet = listOf("Sign in at introdb.app/account.", "Create or copy your API key from the account page.", "Paste the complete key into StreamDek."),
    keyHint = "IntroDB API key",
  ),
  TheIntroDb(
    id = "theintrodb",
    label = "TheIntroDB",
    tagline = "Movies, Series & Playback Timing",
    blurb = "Provides community-verified intro, recap, credits and preview timestamps for movies and series.",
    uses = listOf("Movie and episode timing", "Intro and recap skipping", "Credits, next episode and recommendations"),
    keyUrl = "https://theintrodb.org/docs",
    howToGet = listOf("Open TheIntroDB documentation.", "Follow the API-key instructions and sign in when asked.", "Copy the complete key into StreamDek."),
    keyHint = "TheIntroDB API key",
  );

  companion object {
    fun fromId(value: String?): ContentService? =
      values().firstOrNull { it.id.equals(value?.trim(), ignoreCase = true) }
  }
}

/**
 * Where a configured key is kept.
 *
 * Resource ids rather than text: the wording is read by a viewer, and holding English in a data
 * model would pin those two lines to English on a translated phone. The screen resolves them with
 * `stringResource`, which also means they re-read when the language changes.
 */
enum class CredentialStorage(@StringRes val labelRes: Int, @StringRes val detailRes: Int) {
  /** Encrypted on this device, and nowhere else. StreamDek holds no copy. */
  Device(
    labelRes = R.string.credential_storage_device_label_phone,
    detailRes = R.string.credential_storage_device_detail_phone,
  ),

  /** Encrypted in the StreamDek account, so every signed-in device can use it. */
  Account(
    labelRes = R.string.content_services_account_storage,
    detailRes = R.string.credential_storage_account_detail_phone,
  ),
}

/** What the viewer is told about a service, in the order the states actually occur. */
enum class CredentialStatus {
  NotConfigured,
  Checking,
  Connected,

  /** The service refused the key that used to work. The viewer has to replace it. */
  NeedsAttention,
}

/**
 * Everything a screen needs to draw one service card, and nothing it does not.
 *
 * [maskedKey] is the only form of the key that ever reaches the UI layer. There is deliberately
 * no accessor here that returns the key itself: the one caller that needs it is the request
 * builder, which asks [ServiceCredentialManager] directly.
 */
data class ContentServiceState(
  val service: ContentService,
  val status: CredentialStatus = CredentialStatus.NotConfigured,
  val storage: CredentialStorage? = null,
  val maskedKey: String? = null,
  /** The service's own name for the account, when it tells us one. */
  val accountLabel: String? = null,
  /**
   * True when the account also holds a key for this service while the device is using its own.
   * The card offers to fall back to it rather than pretending the account copy is not there.
   */
  val accountKeyAlsoAvailable: Boolean = false,
  val lastValidatedAt: String? = null,
) {
  val configured: Boolean get() = status == CredentialStatus.Connected || status == CredentialStatus.NeedsAttention
}

/** The whole feature's state, as one object the UI observes. */
data class ContentServicesState(
  val tmdb: ContentServiceState = ContentServiceState(ContentService.Tmdb),
  val mdblist: ContentServiceState = ContentServiceState(ContentService.Mdblist),
  val introDb: ContentServiceState = ContentServiceState(ContentService.IntroDb),
  val theIntroDb: ContentServiceState = ContentServiceState(ContentService.TheIntroDb),
  /**
   * Whether StreamDek's own TMDB key still answers for viewers who have supplied none.
   *
   * Drives the difference between "add your key and this gets better" and "add your key or this
   * screen stays empty", which are not the same message.
   */
  val sharedFallbackAvailable: Boolean = true,
  val loading: Boolean = false,
  /** The service currently being checked or saved, so only its card shows a spinner. */
  val busy: ContentService? = null,
  /** The last thing that happened, in the viewer's words. Cleared when they act again. */
  val notice: String? = null,
  val noticeIsError: Boolean = false,
  /**
   * Which service the notice is about.
   *
   * Lets the card that caused it show the result in place. A failure reported only at the top of
   * a scrolling sheet is a failure the viewer never sees, because the thing they were looking at
   * when they pressed the button is somewhere else on screen.
   */
  val noticeService: ContentService? = null,
  /** True once the account state has been read at least once, so cards don't flash "not set up". */
  val loaded: Boolean = false,
) {
  fun of(service: ContentService): ContentServiceState = when (service) {
    ContentService.Tmdb -> tmdb
    ContentService.Mdblist -> mdblist
    ContentService.IntroDb -> introDb
    ContentService.TheIntroDb -> theIntroDb
  }

  fun with(state: ContentServiceState): ContentServicesState = when (state.service) {
    ContentService.Tmdb -> copy(tmdb = state)
    ContentService.Mdblist -> copy(mdblist = state)
    ContentService.IntroDb -> copy(introDb = state)
    ContentService.TheIntroDb -> copy(theIntroDb = state)
  }

  /** True when neither service has anything configured — what the setup prompt keys off. */
  val anyConfigured: Boolean get() = tmdb.configured || mdblist.configured || introDb.configured || theIntroDb.configured
  // TheIntroDB reads remain available through its public API, so its optional user key must not
  // keep the general content-service setup reminder alive.
  val allConfigured: Boolean get() = tmdb.configured && mdblist.configured
  val needsAttention: List<ContentServiceState>
    get() = listOf(tmdb, mdblist, introDb, theIntroDb).filter { it.status == CredentialStatus.NeedsAttention }
}

// ── Secure local storage ──────────────────────────────────────────────────────────────────────

/**
 * A device-only key at rest.
 *
 * An API key is not an ordinary preference, so it is not stored as one. The value is encrypted
 * with AES-256-GCM under a key generated inside the Android keystore, which means the stored
 * ciphertext is useless on its own — a rooted read of the preferences file, an `adb backup`, or a
 * debugger attached to the process all get base64 and nothing else.
 *
 * The keystore can refuse: a device that has had its secure hardware reset invalidates the key,
 * and the ciphertext written under the old one can no longer be read. That is treated as "no key
 * configured" rather than as an error, because it is indistinguishable from one as far as the
 * viewer is concerned and there is nothing they could do about it either way.
 */
internal class SecureKeyVault(context: Context) {
  private val prefs = context.applicationContext
    .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun read(service: ContentService): String? {
    val stored = prefs.getString(storageKey(service), null)?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { decrypt(stored) }.getOrElse {
      // Unreadable ciphertext is dead weight; clearing it stops every later read retrying it.
      clear(service)
      null
    }
  }

  fun write(service: ContentService, apiKey: String): Boolean {
    val trimmed = apiKey.trim()
    if (trimmed.isEmpty()) {
      clear(service)
      return true
    }
    return runCatching {
      prefs.edit().putString(storageKey(service), encrypt(trimmed)).apply()
    }.isSuccess
  }

  fun clear(service: ContentService) {
    prefs.edit().remove(storageKey(service)).apply()
  }

  fun has(service: ContentService): Boolean = read(service) != null

  private fun storageKey(service: ContentService) = "$KEY_PREFIX${service.id}"

  // ── Crypto ──────────────────────────────────────────────────────────────────────────────────

  private fun encrypt(value: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey())
    val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    val encoder = Base64.getEncoder()
    return "$FORMAT_VERSION:${encoder.encodeToString(cipher.iv)}:${encoder.encodeToString(ciphertext)}"
  }

  private fun decrypt(stored: String): String {
    val parts = stored.split(':')
    require(parts.size == 3 && parts[0] == FORMAT_VERSION) { "Unrecognised stored credential format" }
    val decoder = Base64.getDecoder()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, decoder.decode(parts[1])))
    return String(cipher.doFinal(decoder.decode(parts[2])), Charsets.UTF_8)
  }

  private fun secretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        // Deliberately not user-authentication bound: enrichment runs on a background refresh
        // with the screen off, and a key that needs an unlock would simply fail there.
        .build(),
    )
    return generator.generateKey()
  }

  private companion object {
    const val PREFERENCES_NAME = "streamdek_service_credentials_v1"
    const val KEY_PREFIX = "credential_"
    const val KEYSTORE = "AndroidKeyStore"
    const val KEY_ALIAS = "streamdek_service_credential_aes_v1"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val FORMAT_VERSION = "v1"
    const val TAG_BITS = 128
  }
}

// ── The manager ───────────────────────────────────────────────────────────────────────────────

/**
 * How long "later" lasts.
 *
 * Offered as a choice rather than assumed. A viewer putting this off until they can find their
 * TMDB login and one who simply does not want to think about it today both press the same button,
 * and a single interval serves whichever of them it was not chosen for. Both options are short:
 * the prompt only appears while something is genuinely unconfigured, and the point is to be
 * useful rather than to be got rid of.
 */
enum class SetupDeferral(val label: String, val millis: Long) {
  Tomorrow("Tomorrow", 24L * 60 * 60 * 1000),
  InThreeDays("In 3 days", 3L * 24 * 60 * 60 * 1000),
}

/** How a key should be kept, as the viewer chose it at entry time. */
enum class StorageChoice { SaveToStreamDek, ThisDeviceOnly }

/** The masked form shown wherever a saved key appears. Never enough to use. */
internal fun maskServiceKey(apiKey: String): String {
  val trimmed = apiKey.trim()
  return "••••••••" + trimmed.takeLast(4)
}

/**
 * The device's half of the credential architecture.
 *
 * Deliberately unaware of the network: it owns the local vault and the merge rule, and is handed
 * the account's side of the story by whoever last read it from the backend. That keeps the
 * precedence logic testable and keeps it in one place, which is the whole point.
 */
internal class ServiceCredentialManager(context: Context) {
  private val vault = SecureKeyVault(context)
  private val prefs = context.applicationContext
    .getSharedPreferences("streamdek_service_credential_meta_v1", Context.MODE_PRIVATE)

  /**
   * The key to make a request with, or null when there is none on this device.
   *
   * Only a device-held key is returned. An account key is never handed back to the app, because
   * the app never needs it: TMDB requests are made by the backend, which resolves the account key
   * itself, and MDBList requests carry the device key when there is one and are made server-side
   * otherwise. Keeping the account copy on the server is the point of saving it there.
   */
  fun deviceKey(service: ContentService): String? = vault.read(service)

  fun hasDeviceKey(service: ContentService): Boolean = vault.has(service)

  /** Stores a key on this device, encrypted. Returns false if the keystore refused. */
  fun saveDeviceKey(service: ContentService, apiKey: String): Boolean {
    val saved = vault.write(service, apiKey)
    if (saved) {
      prefs.edit()
        .putString(maskKeyName(service), maskServiceKey(apiKey))
        .putLong(validatedAtKeyName(service), System.currentTimeMillis())
        .remove(rejectedKeyName(service))
        .apply()
    }
    return saved
  }

  fun clearDeviceKey(service: ContentService) {
    vault.clear(service)
    prefs.edit()
      .remove(maskKeyName(service))
      .remove(validatedAtKeyName(service))
      .remove(rejectedKeyName(service))
      .apply()
  }

  /** The masked form of the device key, for display. Read from metadata, not from the vault. */
  fun deviceKeyMask(service: ContentService): String? =
    prefs.getString(maskKeyName(service), null)?.takeIf { it.isNotBlank() }

  /**
   * Records that the service refused the device key.
   *
   * The key is kept, not deleted: the viewer has to see which service went wrong and be offered
   * Update Key, and silently discarding it looks like StreamDek lost it. Requests skip a rejected
   * device key so a bad one is not replayed on every screen.
   */
  fun markDeviceKeyRejected(service: ContentService) {
    prefs.edit().putBoolean(rejectedKeyName(service), true).apply()
  }

  fun deviceKeyRejected(service: ContentService): Boolean =
    prefs.getBoolean(rejectedKeyName(service), false)

  /**
   * The key this device should attach to an outgoing request, if any.
   *
   * Skips a key the service has already refused, which is what turns "one broken key" into "one
   * warning" instead of a request storm. The backend applies the identical rule for its own
   * copy — see credentialResolver on the server.
   */
  fun requestKey(service: ContentService): String? =
    if (deviceKeyRejected(service)) null else deviceKey(service)

  /**
   * Folds what the account reports together with what this device holds, into the state the UI
   * draws — the single answer to "what is the situation with TMDB", for every screen.
   *
   * The device key wins when both exist: it is the more specific statement of intent, and a
   * viewer who typed a key here expects that key to be the one in use. The account copy is not
   * hidden when that happens; the card says it is there and offers to switch to it.
   */
  fun merge(service: ContentService, account: AccountCredentialState?): ContentServiceState {
    val deviceHeld = hasDeviceKey(service)
    val accountHeld = account?.configured == true

    return when {
      deviceHeld -> ContentServiceState(
        service = service,
        status = if (deviceKeyRejected(service)) CredentialStatus.NeedsAttention else CredentialStatus.Connected,
        storage = CredentialStorage.Device,
        maskedKey = deviceKeyMask(service),
        accountKeyAlsoAvailable = accountHeld,
        lastValidatedAt = prefs.getLong(validatedAtKeyName(service), 0L)
          .takeIf { it > 0L }?.toString(),
      )

      accountHeld -> ContentServiceState(
        service = service,
        status = if (account!!.needsAttention) CredentialStatus.NeedsAttention else CredentialStatus.Connected,
        storage = CredentialStorage.Account,
        maskedKey = account.maskedKey,
        accountLabel = account.label,
        lastValidatedAt = account.lastValidatedAt,
      )

      else -> ContentServiceState(service = service)
    }
  }

  // ── Setup prompting ─────────────────────────────────────────────────────────────────────────

  /**
   * Whether the setup card should be offered right now.
   *
   * "Later" is remembered and honoured for as long as the viewer asked for, and it stops entirely
   * once both services are configured. A modal on every launch is how a genuinely useful prompt
   * gets trained into muscle-memory dismissal.
   *
   * The window is stored alongside the timestamp rather than being a constant, because the viewer
   * picks it: someone who means "not right now" and someone who means "stop asking this week" are
   * saying different things, and guessing one interval for both gets one of them wrong.
   */
  fun shouldOfferSetup(state: ContentServicesState): Boolean {
    if (state.allConfigured) return false
    val deferredAt = prefs.getLong(SETUP_DEFERRED_AT, 0L)
    if (deferredAt <= 0L) return true
    val window = prefs.getLong(SETUP_DEFERRED_FOR_MS, SetupDeferral.Tomorrow.millis)
    return System.currentTimeMillis() - deferredAt >= window
  }

  fun deferSetup(deferral: SetupDeferral) {
    prefs.edit()
      .putLong(SETUP_DEFERRED_AT, System.currentTimeMillis())
      .putLong(SETUP_DEFERRED_FOR_MS, deferral.millis)
      .apply()
  }

  /**
   * A one-off nudge shown where a missing key is actually costing the viewer something — a title
   * page with no ratings, say. Capped to once a day per service so it stays a hint rather than a
   * recurring interruption.
   */
  fun shouldHintFor(service: ContentService): Boolean {
    val lastHint = prefs.getLong(hintKeyName(service), 0L)
    return System.currentTimeMillis() - lastHint > HINT_INTERVAL_MS
  }

  fun noteHintShown(service: ContentService) {
    prefs.edit().putLong(hintKeyName(service), System.currentTimeMillis()).apply()
  }

  /** Signing out leaves nothing of the previous viewer's keys behind on the device. */
  fun clearAll() {
    ContentService.values().forEach(::clearDeviceKey)
    prefs.edit().clear().apply()
  }

  private fun maskKeyName(service: ContentService) = "mask_${service.id}"
  private fun validatedAtKeyName(service: ContentService) = "validated_${service.id}"
  private fun rejectedKeyName(service: ContentService) = "rejected_${service.id}"
  private fun hintKeyName(service: ContentService) = "hint_${service.id}"

  private companion object {
    const val SETUP_DEFERRED_AT = "setup_deferred_at"
    const val SETUP_DEFERRED_FOR_MS = "setup_deferred_for_ms"
    const val HINT_INTERVAL_MS = 24L * 60 * 60 * 1000
  }
}

/** What the backend reports about a key saved to the account. Never contains the key. */
data class AccountCredentialState(
  val service: ContentService,
  val configured: Boolean,
  val maskedKey: String? = null,
  val label: String? = null,
  val needsAttention: Boolean = false,
  val lastValidatedAt: String? = null,
)

/** The account side of the whole feature, as one bootstrap or credentials call returns it. */
data class AccountCredentials(
  val tmdb: AccountCredentialState? = null,
  val mdblist: AccountCredentialState? = null,
  val introDb: AccountCredentialState? = null,
  val theIntroDb: AccountCredentialState? = null,
  val sharedFallbackAvailable: Boolean = true,
) {
  fun of(service: ContentService): AccountCredentialState? = when (service) {
    ContentService.Tmdb -> tmdb
    ContentService.Mdblist -> mdblist
    ContentService.IntroDb -> introDb
    ContentService.TheIntroDb -> theIntroDb
  }
}

/**
 * How a failed check is explained.
 *
 * Never the HTTP status, and never the service's own error text: "HTTP 401" tells a viewer
 * nothing they can act on, and the difference between a wrong key and a service having a bad
 * minute is the whole point of separating these.
 */
enum class CredentialFailure(val message: String) {
  InvalidKey("That key wasn't accepted. Check you copied the whole thing, then try again."),
  ServiceUnavailable("Couldn't reach the service just now, so the key hasn't been checked. Nothing has changed — try again in a moment."),
  Malformed("That doesn't look like a key. Copy the whole key from your account page and try again."),
  NotSignedIn("Sign in to StreamDek to check and save your keys.");

  companion object {
    fun fromId(value: String?): CredentialFailure = when (value?.trim()?.lowercase()) {
      "invalid_key" -> InvalidKey
      "malformed" -> Malformed
      "service_unavailable" -> ServiceUnavailable
      else -> InvalidKey
    }
  }
}

/** The outcome of checking one key, as the entry sheet renders it. */
sealed interface CredentialCheck {
  data class Valid(val label: String?) : CredentialCheck
  data class Failed(val failure: CredentialFailure) : CredentialCheck
}

/**
 * What "remove" means, which is never left to inference.
 *
 * A key saved to the account is in use by every device signed into it, so taking it away is not
 * a local act and the viewer is told which of these they are about to do before it happens.
 */
enum class CredentialRemoval {
  /** Forget the key on this phone. An account copy, if there is one, is untouched. */
  Device,

  /** Delete the account copy. Every StreamDek device on the account loses it. */
  Account,
}
