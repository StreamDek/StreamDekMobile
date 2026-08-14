package net.streamdek.mobile.debrid

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * The HTTP plumbing every provider client shares.
 *
 * One client for all of them: connections and the thread pool are pooled across providers, which
 * matters when a stream list asks four services the same question at once.
 */
internal object DebridHttp {
  val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

  /**
   * Performs [request] and parses the body as JSON.
   *
   * A non-2xx answer is raised as [DebridHttpException] carrying the status, because that status
   * is what tells a rate limit apart from a dead subscription further up. Providers that report
   * failure in the body of a 200 are handled by each client, which knows its own shape.
   */
  suspend fun json(request: Request): JSONObject = withContext(Dispatchers.IO) {
    client.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw DebridHttpException(response.code, providerErrorMessage(body) ?: "HTTP ${response.code}")
      }
      parseJsonObject(body)
    }
  }

  /** As [json], for the providers that answer with a bare array at the top level. */
  suspend fun jsonArray(request: Request): JSONArray = withContext(Dispatchers.IO) {
    client.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw DebridHttpException(response.code, providerErrorMessage(body) ?: "HTTP ${response.code}")
      }
      runCatching { JSONArray(body) }.getOrElse { JSONArray() }
    }
  }

  /**
   * The final URL a link redirects to, without downloading it.
   *
   * Only Deepbrid needs this, to read the real filename off the redirect. Redirects are read
   * rather than followed so nothing is fetched from the media host.
   */
  suspend fun redirectTarget(url: String): String? = withContext(Dispatchers.IO) {
    val noRedirects = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    runCatching {
      noRedirects.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
        response.header("Location")
      }
    }.getOrNull()
  }

  /**
   * Some providers wrap the object in `{"data":…}` or `{"value":…}` and are inconsistent about
   * which, even between endpoints of the same API.
   */
  fun unwrap(json: JSONObject): JSONObject =
    json.optJSONObject("value") ?: json.optJSONObject("data") ?: json

  fun parseJsonObject(body: String): JSONObject =
    runCatching { JSONObject(body) }.getOrElse { JSONObject() }

  fun form(vararg fields: Pair<String, String>): RequestBody {
    val builder = FormBody.Builder()
    fields.forEach { (name, value) -> builder.add(name, value) }
    return builder.build()
  }

  fun multipart(vararg fields: Pair<String, String>): RequestBody {
    val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
    fields.forEach { (name, value) -> builder.addFormDataPart(name, value) }
    return builder.build()
  }

  fun jsonBody(json: JSONObject): RequestBody =
    json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

  fun url(base: String, path: String, query: Map<String, String> = emptyMap()): HttpUrl {
    val builder = (base.trimEnd('/') + path).toHttpUrlOrNull()?.newBuilder()
      ?: throw IllegalStateException("Bad provider URL: $base$path")
    query.forEach { (name, value) -> builder.addQueryParameter(name, value) }
    return builder.build()
  }

  /** Pulls a human-readable message out of an error body, whatever the provider calls the field. */
  private fun providerErrorMessage(body: String): String? {
    if (body.isBlank()) return null
    val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
    return sequenceOf("error", "message", "error_message", "detail")
      .mapNotNull { key -> json.optString(key).takeIf { it.isNotBlank() && it != "null" } }
      .firstOrNull()
  }
}

/** Objects held under arbitrary keys, which several of these APIs use in place of an array. */
internal fun JSONObject.objectValues(): List<JSONObject> =
  keys().asSequence().mapNotNull { key -> optJSONObject(key) }.toList()

internal fun JSONArray.objects(): List<JSONObject> =
  (0 until length()).mapNotNull { index -> optJSONObject(index) }

internal fun JSONArray.strings(): List<String> =
  (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
