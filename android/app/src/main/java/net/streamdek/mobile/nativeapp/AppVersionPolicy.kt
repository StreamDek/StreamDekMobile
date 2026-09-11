package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import net.streamdek.mobile.BuildConfig
import net.streamdek.mobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class AppUpdateMode { NONE, OPTIONAL, RECOMMENDED, REQUIRED }

data class AppVersionPolicy(
  val latestVersion: String,
  val minimumSupportedVersion: String,
  val updateMode: AppUpdateMode,
  val title: String,
  val message: String,
  val requiredTitle: String,
  val requiredMessage: String,
  val updateUrl: String,
  val releaseNotesUrl: String?,
)

sealed interface AppVersionGateState {
  data object Checking : AppVersionGateState
  data class Ready(val policy: AppVersionPolicy, val effectiveMode: AppUpdateMode) : AppVersionGateState
  data class Required(val policy: AppVersionPolicy) : AppVersionGateState
  data object Unavailable : AppVersionGateState
}

/** Cached, fail-open startup policy plus the process-wide target for HTTP 426 responses. */
object AppVersionPolicyRuntime {
  private const val PREFS = "streamdek_app_version_policy"
  private const val CACHED_POLICY = "cached_policy"
  private const val CACHED_AT = "cached_at"
  private const val MAX_CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000
  private val http = OkHttpClient.Builder()
    .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS).build()
  private val _state = MutableStateFlow<AppVersionGateState>(AppVersionGateState.Checking)
  val state: StateFlow<AppVersionGateState> = _state

  suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
    val url = BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/public/app-version-policy?platform=android-mobile"
    val request = Request.Builder().url(url)
      .header("Accept", "application/json")
      .header("X-StreamDek-Platform", "android-mobile")
      .header("X-StreamDek-Version", BuildConfig.VERSION_NAME)
      .header("X-StreamDek-Build", BuildConfig.VERSION_CODE.toString())
      .build()
    val raw = runCatching {
      http.newCall(request).execute().use { response ->
        check(response.isSuccessful) { "Policy service returned HTTP ${response.code}" }
        response.body?.string()?.takeIf(String::isNotBlank) ?: error("Empty version policy")
      }
    }.onSuccess { json ->
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString(CACHED_POLICY, json).putLong(CACHED_AT, System.currentTimeMillis()).apply()
    }.getOrNull() ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).let { prefs ->
      prefs.getString(CACHED_POLICY, null)?.takeIf {
        System.currentTimeMillis() - prefs.getLong(CACHED_AT, 0L) <= MAX_CACHE_AGE_MS
      }
    }
    val policy = raw?.let(::parsePolicy)
    _state.value = if (policy == null) AppVersionGateState.Unavailable else stateFor(policy)
  }

  fun acceptUnsupportedResponse(rawBody: String) {
    val json = runCatching { JSONObject(rawBody) }.getOrNull() ?: return
    val code = json.optJSONObject("error")?.optString("code") ?: json.optJSONObject("errorDetail")?.optString("code")
    if (code != "CLIENT_VERSION_UNSUPPORTED") return
    val current = when (val state = _state.value) {
      is AppVersionGateState.Ready -> state.policy
      is AppVersionGateState.Required -> state.policy
      else -> null
    }
    _state.value = AppVersionGateState.Required(
      AppVersionPolicy(
        latestVersion = json.optString("latestVersion", current?.latestVersion ?: ""),
        minimumSupportedVersion = json.optString("minimumSupportedVersion", current?.minimumSupportedVersion ?: ""),
        updateMode = AppUpdateMode.REQUIRED,
        title = current?.title ?: "Update required",
        message = current?.message ?: "",
        requiredTitle = "Update required",
        requiredMessage = json.optString("message", "This version of StreamDek is no longer supported. Update to continue."),
        updateUrl = json.optString("updateUrl", current?.updateUrl ?: ""),
        releaseNotesUrl = current?.releaseNotesUrl,
      ),
    )
  }

  fun openUpdate(context: Context, policy: AppVersionPolicy) {
    if (policy.updateUrl.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(policy.updateUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  }

  private fun stateFor(policy: AppVersionPolicy): AppVersionGateState {
    val minimum = compareAppVersions(BuildConfig.VERSION_NAME, policy.minimumSupportedVersion)
    if (minimum == null || minimum < 0) return AppVersionGateState.Required(policy)
    val latest = compareAppVersions(BuildConfig.VERSION_NAME, policy.latestVersion)
    val mode = if (latest != null && latest < 0) policy.updateMode else AppUpdateMode.NONE
    return if (mode == AppUpdateMode.REQUIRED) AppVersionGateState.Required(policy) else AppVersionGateState.Ready(policy, mode)
  }

  private fun parsePolicy(raw: String): AppVersionPolicy? = runCatching {
    val json = JSONObject(raw)
    AppVersionPolicy(
      latestVersion = json.getString("latestVersion"),
      minimumSupportedVersion = json.getString("minimumSupportedVersion"),
      updateMode = AppUpdateMode.valueOf(json.optString("updateMode", "OPTIONAL").uppercase()),
      title = json.optString("title", "Update available"),
      message = json.optString("message", "A newer version of StreamDek is available."),
      requiredTitle = json.optString("requiredTitle", "Update required"),
      requiredMessage = json.optString("requiredMessage", "This version is no longer supported. Update to continue."),
      updateUrl = json.optString("updateUrl"),
      releaseNotesUrl = json.optString("releaseNotesUrl").takeIf(String::isNotBlank),
    ).also { require(compareAppVersions(it.minimumSupportedVersion, it.latestVersion)?.let { order -> order <= 0 } == true) }
  }.getOrNull()
}

internal fun compareAppVersions(left: String, right: String): Int? {
  data class Version(val core: List<Long>, val hotfix: Int, val prerelease: List<String>)
  fun parse(value: String): Version? {
    val input = value.trim()
    if (input.isEmpty() || input.length > 64) return null
    val withoutBuild = input.substringBefore('+')
    val coreText = withoutBuild.substringBefore('-')
    val match = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)([A-Za-z]?)$").matchEntire(coreText) ?: return null
    val core = (1..3).map { match.groupValues[it].toLongOrNull() ?: return null }
    val hotfix = match.groupValues[4].lowercase().firstOrNull()?.let { it.code - 'a'.code + 1 } ?: 0
    val prerelease = withoutBuild.substringAfter('-', "").takeIf(String::isNotEmpty)?.split('.') ?: emptyList()
    if (hotfix > 0 && prerelease.isNotEmpty()) return null
    if (prerelease.any { it.isEmpty() || !it.matches(Regex("[0-9A-Za-z-]+")) }) return null
    return Version(core, hotfix, prerelease)
  }
  val a = parse(left) ?: return null
  val b = parse(right) ?: return null
  a.core.zip(b.core).firstOrNull { it.first != it.second }?.let { (x, y) -> return x.compareTo(y) }
  if (a.hotfix != b.hotfix) return a.hotfix.compareTo(b.hotfix)
  if (a.prerelease.isEmpty() || b.prerelease.isEmpty()) {
    return when {
      a.prerelease.isEmpty() && b.prerelease.isEmpty() -> 0
      a.prerelease.isEmpty() -> 1
      else -> -1
    }
  }
  repeat(maxOf(a.prerelease.size, b.prerelease.size)) { index ->
    val av = a.prerelease.getOrNull(index) ?: return -1
    val bv = b.prerelease.getOrNull(index) ?: return 1
    if (av == bv) return@repeat
    val an = av.all(Char::isDigit)
    val bn = bv.all(Char::isDigit)
    return when {
      an && bn -> (av.toLongOrNull() ?: return null).compareTo(bv.toLongOrNull() ?: return null)
      an -> -1
      bn -> 1
      else -> av.compareTo(bv)
    }
  }
  return 0
}

/** Composed before NativeAppViewModel exists, so blocked launches cannot load profiles or Home. */
@Composable
fun AppVersionGate(content: @Composable () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
  val state by AppVersionPolicyRuntime.state.collectAsState()
  val scope = rememberCoroutineScope()
  LaunchedEffect(Unit) { AppVersionPolicyRuntime.refresh(context) }

  when (val current = state) {
    AppVersionGateState.Checking -> GateTheme {
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      }
    }
    is AppVersionGateState.Required -> GateTheme {
      BackHandler(enabled = true) { }
      UpdateRequiredScreen(
        policy = current.policy,
        onUpdate = { AppVersionPolicyRuntime.openUpdate(context, current.policy) },
        onRetry = { scope.launch { AppVersionPolicyRuntime.refresh(context) } },
      )
    }
    else -> content() // A lookup outage fails open; any protected API can still answer with 426.
  }
}

@Composable
private fun UpdateRequiredScreen(policy: AppVersionPolicy, onUpdate: () -> Unit, onRetry: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Surface(Modifier.fillMaxSize(), color = colors.background) {
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), contentAlignment = Alignment.Center) {
      Surface(
        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.08f)),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Image(
            painter = painterResource(R.drawable.streamdek_logo_transparent),
            contentDescription = stringResource(R.string.app_version_brand),
            modifier = Modifier.size(76.dp),
          )
          Spacer(Modifier.height(24.dp))
          Text(policy.requiredTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
          Spacer(Modifier.height(10.dp))
          Text(
            policy.requiredMessage,
            color = colors.onSurface.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(28.dp))
          Button(
            onClick = onUpdate,
            enabled = policy.updateUrl.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
          ) {
            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.update_now), fontWeight = FontWeight.Bold)
          }
          Spacer(Modifier.height(10.dp))
          OutlinedButton(
            onClick = onRetry,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.16f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
            modifier = Modifier.fillMaxWidth().height(52.dp),
          ) { Text(stringResource(R.string.app_version_retry), fontWeight = FontWeight.SemiBold) }
          Spacer(Modifier.height(20.dp))
          Text(
            stringResource(R.string.app_version_installed_required, BuildConfig.VERSION_NAME, policy.minimumSupportedVersion),
            color = colors.onSurface.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}

/** The saved Theme and Appearance, so the gate matches the app it is standing in front of. */
@Composable
private fun GateTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current.applicationContext
  val systemDarkMode = deviceInDarkTheme()
  val colorScheme = remember(context, systemDarkMode) { savedAppColorScheme(context, systemDarkMode) }
  MaterialTheme(colorScheme = colorScheme, content = content)
}
