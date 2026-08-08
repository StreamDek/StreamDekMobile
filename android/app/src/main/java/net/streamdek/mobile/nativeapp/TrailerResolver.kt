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
    resolveYoutubePlaybackSource(youtubeKey, normalizeTrailerMaxHeight(maxHeight), youtubeCookies)
  } ?: TrailerPlaybackResolution()
}

internal fun normalizeTrailerMaxHeight(maxHeight: Int): Int = maxHeight.coerceIn(360, 2160)

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
  //
  // IOS is tried first because it is the only one of these that can reach 4K. It answers with an
  // HLS manifest, which carries the full ladder and lets the player pick a rendition; the others
  // hand back discrete formats, and the picker below only accepts avc1 — a codec YouTube stops
  // publishing above 1080p. Whichever client used to answer first therefore capped every trailer
  // at 1080p regardless of the resolution setting.
  //
  // This loop stops at the first client that returns a playable source, so putting IOS in front
  // costs nothing when it cannot serve a video: age-restricted trailers come back LOGIN_REQUIRED
  // and fall through to the cookie-authenticated TVHTML5 attempt exactly as before.
  val iosClient = YoutubeClient("IOS", "20.10.4", osName = "iOS", osVersion = "18.3.2.22D82", deviceMake = "Apple", deviceModel = "iPhone16,2", userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)")
  val tvClient = YoutubeClient("TVHTML5", "7.20250312.16.00", osName = "Tizen", osVersion = "5.0", deviceMake = "Samsung", deviceModel = "SmartTV", userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version")
  val clients = buildList {
    add(iosClient)
    // Signed in, the authenticated TV client is the one that can reach restricted content, so it
    // stays ahead of the anonymous fallbacks.
    if (!cookies.isNullOrBlank()) add(tvClient)
    addAll(listOf(
      YoutubeClient("ANDROID_VR", "1.62.27", osName = "Android", osVersion = "12L", deviceMake = "Oculus", deviceModel = "Quest 3", userAgent = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip", androidSdkVersion = 32),
      tvClient,
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
      // Says which rendition actually won, so "is this really playing in 4K" is answerable from a
      // log line rather than by guessing at which decoder the device happened to spin up.
      Log.d(
        trailerResolverTag,
        "${client.name}: selected height=${source?.height ?: -1} cap=$maxHeight " +
          "bestAdaptive=${adaptiveVideo?.second ?: -1} separateAudio=${source?.audioUrl != null}",
      )
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

/**
 * How much a codec is preferred at the *same* height. Higher wins.
 *
 * AVC decodes everywhere, so it stays the first choice whenever it can match the resolution.
 * It is also the reason this used to cap at 1080p: YouTube publishes nothing above that in AVC,
 * and accepting only avc1 silently threw away every 1440p and 2160p rendition the iOS client
 * offers. VP9 is hardware-decoded on essentially anything modern; AV1 is accepted last because
 * on mid-range hardware it can fall back to a software decoder.
 */
internal fun trailerCodecRank(mime: String): Int = when {
  mime.contains("avc1", true) -> 3
  mime.contains("vp9", true) || mime.contains("vp09", true) -> 2
  mime.contains("av01", true) -> 1
  else -> 0
}

internal fun selectAdaptiveVideo(formats: JSONArray?, maxHeight: Int): Pair<String, Int>? {
  if (formats == null) return null
  var selected: Pair<String, Int>? = null
  var selectedRank = 0
  for (index in 0 until formats.length()) {
    val item = formats.optJSONObject(index) ?: continue
    val url = item.optString("url")
    val mime = item.optString("mimeType")
    val height = item.optInt("height", 0)
    if (url.isBlank() || !mime.startsWith("video/", true) || height !in 1..maxHeight) continue
    val rank = trailerCodecRank(mime)
    if (rank == 0) continue
    val current = selected
    // Resolution first, codec preference only as a tie-break — the point of going past AVC is to
    // reach a height AVC cannot serve, so a taller VP9 must beat a shorter AVC.
    val better = current == null || height > current.second || (height == current.second && rank > selectedRank)
    if (!better) continue
    selected = url to height
    selectedRank = rank
  }
  return selected
}

/**
 * Best audio track to pair with the chosen video.
 *
 * m4a is preferred, but Opus in WebM is accepted as a fallback: now that video selection can pick
 * a VP9 rendition, a response whose only audio is WebM would otherwise leave the pair incomplete
 * and drop the whole result. The player merges the two streams regardless of container.
 */
internal fun selectAdaptiveAudio(formats: JSONArray?): String? {
  if (formats == null) return null
  var selectedUrl: String? = null
  var selectedBitrate = -1
  var selectedIsMp4 = false
  for (index in 0 until formats.length()) {
    val item = formats.optJSONObject(index) ?: continue
    val url = item.optString("url")
    val mime = item.optString("mimeType")
    val bitrate = item.optInt("bitrate", 0)
    if (url.isBlank() || !mime.startsWith("audio/", true)) continue
    val isMp4 = mime.contains("audio/mp4", true)
    if (!isMp4 && !mime.contains("audio/webm", true)) continue
    // An m4a track always beats a WebM one; past that, take the highest bitrate.
    val better = selectedUrl == null || (isMp4 && !selectedIsMp4) || (isMp4 == selectedIsMp4 && bitrate > selectedBitrate)
    if (!better) continue
    selectedUrl = url
    selectedBitrate = bitrate
    selectedIsMp4 = isMp4
  }
  return selectedUrl
}