package net.streamdek.mobile.nativeapp

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

private val trailerHttpClient = OkHttpClient.Builder()
  .connectTimeout(4, TimeUnit.SECONDS)
  .readTimeout(5, TimeUnit.SECONDS)
  .callTimeout(8, TimeUnit.SECONDS)
  .build()
private val trailerJsonMediaType = "application/json; charset=utf-8".toMediaType()
private const val trailerResolverTag = "TrailerResolver"

data class TrailerPlaybackSource(val url: String, val audioUrl: String? = null, val height: Int? = null, val requestHeaders: Map<String, String> = emptyMap())
data class TrailerPlaybackResolution(val source: TrailerPlaybackSource? = null, val youtubeLoginRequired: Boolean = false)

suspend fun resolveTrailerPlaybackSource(url: String, maxHeight: Int = 720, youtubeCookies: String? = null): TrailerPlaybackResolution = withContext(Dispatchers.IO) {
  withTimeoutOrNull(10_000) {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return@withTimeoutOrNull TrailerPlaybackResolution()
    if (isNativePlayableTrailerUrl(trimmed)) return@withTimeoutOrNull TrailerPlaybackResolution(source = TrailerPlaybackSource(trimmed))
    val youtubeKey = extractYoutubeTrailerKey(trimmed) ?: return@withTimeoutOrNull TrailerPlaybackResolution()
    resolveYoutubePlaybackSource(youtubeKey, maxHeight.coerceIn(360, 1080), youtubeCookies)
  } ?: TrailerPlaybackResolution()
}

private fun isNativePlayableTrailerUrl(url: String): Boolean {
  val lower = url.lowercase()
  return lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".webm") || lower.contains(".m3u8") || lower.contains(".mpd")
}

private fun extractYoutubeTrailerKey(url: String): String? {
  val raw = url.trim()
  if (raw.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return raw
  return runCatching {
    val uri = Uri.parse(raw)
    when {
      uri.host?.contains("youtu.be", ignoreCase = true) == true -> uri.lastPathSegment
      uri.host?.contains("youtube", ignoreCase = true) == true && uri.path?.startsWith("/shorts/") == true -> uri.pathSegments.getOrNull(1)
      uri.host?.contains("youtube", ignoreCase = true) == true && uri.path?.startsWith("/embed/") == true -> uri.pathSegments.getOrNull(1)
      uri.host?.contains("youtube", ignoreCase = true) == true -> uri.getQueryParameter("v")
      else -> null
    }
  }.getOrNull()?.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{11}$")) }
}

private fun resolveYoutubePlaybackSource(videoId: String, maxHeight: Int, cookies: String?): TrailerPlaybackResolution {
  val watchHtml = fetchYoutubeWatchHtml(videoId, cookies)
  val apiKey = Regex(""""INNERTUBE_API_KEY"\s*:\s*"([^"]+)"""").find(watchHtml)?.groupValues?.getOrNull(1)
    ?: "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
  val visitorData = Regex(""""VISITOR_DATA"\s*:\s*"([^"]+)"""").find(watchHtml)?.groupValues?.getOrNull(1)

  // Client selection matters: WEB/ANDROID clients are gated behind YouTube's
  // proof-of-origin (PO) token and return the "confirm you're not a bot" wall even
  // with valid sign-in cookies, so they are intentionally excluded. ANDROID_VR and
  // TVHTML5 currently work without a PO token; IOS provides an HLS manifest.
  val clients = buildList {
    if (!cookies.isNullOrBlank()) {
      add(YoutubeClient("TVHTML5", "7.20250312.16.00", osName = "Tizen", osVersion = "5.0", deviceMake = "Samsung", deviceModel = "SmartTV", userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"))
    }
    addAll(listOf(
      YoutubeClient("ANDROID_VR", "1.62.27", osName = "Android", osVersion = "12L", deviceMake = "Oculus", deviceModel = "Quest 3", userAgent = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip", androidSdkVersion = 32),
      YoutubeClient("IOS", "20.10.4", osName = "iOS", osVersion = "18.3.2.22D82", deviceMake = "Apple", deviceModel = "iPhone16,2", userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"),
      YoutubeClient("TVHTML5", "7.20250312.16.00", osName = "Tizen", osVersion = "5.0", deviceMake = "Samsung", deviceModel = "SmartTV", userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"),
    ))
  }
  var loginRequired = false
  for (client in clients) {
    val resolution = requestYoutubePlayer(videoId, apiKey, visitorData, client, maxHeight, cookies)
    resolution.source?.let { return resolution }
    loginRequired = loginRequired || resolution.youtubeLoginRequired
  }
  return TrailerPlaybackResolution(youtubeLoginRequired = loginRequired)
}
private fun fetchYoutubeWatchHtml(videoId: String, cookies: String?): String = runCatching {
  val builder = Request.Builder()
    .url("https://www.youtube.com/watch?v=$videoId&hl=en")
    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
  if (!cookies.isNullOrBlank()) builder.header("Cookie", cookies)
  val request = builder.build()
  trailerHttpClient.newCall(request).execute().use { response -> if (response.isSuccessful) response.body?.string().orEmpty() else "" }
}.getOrDefault("")

private data class YoutubeClient(
  val name: String,
  val version: String,
  val osName: String,
  val osVersion: String,
  val deviceMake: String,
  val deviceModel: String,
  val userAgent: String,
  val androidSdkVersion: Int? = null,
)

private const val youtubeOrigin = "https://www.youtube.com"

private fun youtubeAuthorizationHeader(cookies: String): String? {
  val cookieValues = cookies.split(';').mapNotNull { entry ->
    val separator = entry.indexOf('=')
    if (separator <= 0) null else entry.substring(0, separator).trim() to entry.substring(separator + 1).trim()
  }.toMap()
  val sapisid = cookieValues["SAPISID"]
    ?: cookieValues["__Secure-3PAPISID"]
    ?: cookieValues["__Secure-1PAPISID"]
    ?: return null
  val timestamp = System.currentTimeMillis() / 1000L
  val input = "$timestamp $sapisid $youtubeOrigin"
  val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
  return "SAPISIDHASH ${timestamp}_$digest"
}
private fun requestYoutubePlayer(videoId: String, apiKey: String, visitorData: String?, client: YoutubeClient, maxHeight: Int, cookies: String?): TrailerPlaybackResolution {
  val clientJson = JSONObject()
    .put("clientName", client.name)
    .put("clientVersion", client.version)
    .put("osName", client.osName)
    .put("osVersion", client.osVersion)
    .put("deviceMake", client.deviceMake)
    .put("deviceModel", client.deviceModel)
    .put("userAgent", client.userAgent)
    .put("hl", "en")
    .put("gl", "US")
  client.androidSdkVersion?.let { clientJson.put("androidSdkVersion", it) }
  if (!visitorData.isNullOrBlank()) clientJson.put("visitorData", visitorData)
  val payload = JSONObject()
    .put("videoId", videoId)
    .put("contentCheckOk", true)
    .put("racyCheckOk", true)
    .put("playbackContext", JSONObject().put("contentPlaybackContext", JSONObject().put("html5Preference", "HTML5_PREF_WANTS")))
    .put("context", JSONObject().put("client", clientJson))

  val requestBuilder = Request.Builder()
    .url("https://www.youtube.com/youtubei/v1/player?key=${Uri.encode(apiKey)}")
    .post(payload.toString().toRequestBody(trailerJsonMediaType))
    .header("User-Agent", client.userAgent)
    .header("Accept", "application/json")
  if (!cookies.isNullOrBlank() && (client.name == "WEB" || client.name == "TVHTML5")) {
    requestBuilder.header("Origin", youtubeOrigin)
    requestBuilder.header("X-Origin", youtubeOrigin)
    requestBuilder.header("Cookie", cookies)
    youtubeAuthorizationHeader(cookies)?.let { requestBuilder.header("Authorization", it) }
    requestBuilder.header("X-Goog-AuthUser", "0")
    requestBuilder.header("X-YouTube-Client-Name", if (client.name == "TVHTML5") "7" else "1")
    requestBuilder.header("X-YouTube-Client-Version", client.version)
  }
  val request = requestBuilder.build()

  return runCatching {
    trailerHttpClient.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        Log.w(trailerResolverTag, "${client.name}: HTTP ${response.code}")
        return@use TrailerPlaybackResolution()
      }
      val json = JSONObject(body)
      val playability = json.optJSONObject("playabilityStatus")
      val streamingData = json.optJSONObject("streamingData")
      if (streamingData == null) {
        val status = playability?.optString("status").orEmpty()
        val reason = playability?.optString("reason").orEmpty()
        val loginRequired = status.equals("LOGIN_REQUIRED", ignoreCase = true) || reason.contains("sign in", ignoreCase = true) || reason.contains("not a bot", ignoreCase = true)
        Log.w(trailerResolverTag, "${client.name}: status=$status reason=$reason")
        return@use TrailerPlaybackResolution(youtubeLoginRequired = loginRequired)
      }
      val formats = streamingData.optJSONArray("formats")
      val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
      Log.d(trailerResolverTag, "${client.name}: status=${playability?.optString("status")} formats=${formats?.length() ?: 0} adaptive=${adaptiveFormats?.length() ?: 0} hls=${streamingData.optString("hlsManifestUrl").isNotBlank()}")
      val playbackHeaders = buildMap {
        put("User-Agent", client.userAgent)
        put("Referer", "$youtubeOrigin/")
      }
      val hlsManifest = streamingData.optString("hlsManifestUrl")
      if (maxHeight > 360 && hlsManifest.isNotBlank()) {
        return@use TrailerPlaybackResolution(source = TrailerPlaybackSource(hlsManifest, height = maxHeight, requestHeaders = playbackHeaders))
      }
      val adaptiveVideo = selectAdaptiveVideo(adaptiveFormats, maxHeight)
      val adaptiveAudio = selectAdaptiveAudio(adaptiveFormats)
      val source = selectProgressiveTrailer(formats, maxHeight)
        ?: (if (adaptiveVideo != null && adaptiveAudio != null) TrailerPlaybackSource(adaptiveVideo.first, adaptiveAudio, adaptiveVideo.second) else null)
        ?: hlsManifest.takeIf { it.isNotBlank() }?.let { TrailerPlaybackSource(it) }
      TrailerPlaybackResolution(source = source?.copy(requestHeaders = playbackHeaders))
    }
  }.onFailure { Log.w(trailerResolverTag, "${client.name}: ${it.message}") }.getOrElse { TrailerPlaybackResolution() }
}
private fun selectProgressiveTrailer(formats: JSONArray?, maxHeight: Int): TrailerPlaybackSource? {
  if (formats == null) return null
  var selected: TrailerPlaybackSource? = null
  var selectedHeight = -1
  for (index in 0 until formats.length()) {
    val item = formats.optJSONObject(index) ?: continue
    val url = item.optString("url")
    val mime = item.optString("mimeType")
    val height = item.optInt("height", 0)
    val hasAudio = item.optString("audioQuality").isNotBlank() || item.optInt("audioChannels", 0) > 0
    if (url.isBlank() || !hasAudio || !mime.contains("avc1", true) || height > maxHeight || height <= selectedHeight) continue
    selectedHeight = height
    selected = TrailerPlaybackSource(url, height = height)
  }
  return selected
}

private fun selectAdaptiveVideo(formats: JSONArray?, maxHeight: Int): Pair<String, Int>? {
  if (formats == null) return null
  var selected: Pair<String, Int>? = null
  for (index in 0 until formats.length()) {
    val item = formats.optJSONObject(index) ?: continue
    val url = item.optString("url")
    val mime = item.optString("mimeType")
    val height = item.optInt("height", 0)
    if (url.isBlank() || !mime.contains("video/mp4", true) || !mime.contains("avc1", true) || height !in 1..maxHeight) continue
    if (selected == null || height > selected.second) selected = url to height
  }
  return selected
}

private fun selectAdaptiveAudio(formats: JSONArray?): String? {
  if (formats == null) return null
  var selectedUrl: String? = null
  var selectedBitrate = -1
  for (index in 0 until formats.length()) {
    val item = formats.optJSONObject(index) ?: continue
    val url = item.optString("url")
    val mime = item.optString("mimeType")
    val bitrate = item.optInt("bitrate", 0)
    if (url.isBlank() || !mime.contains("audio/mp4", true) || bitrate <= selectedBitrate) continue
    selectedUrl = url
    selectedBitrate = bitrate
  }
  return selectedUrl
}