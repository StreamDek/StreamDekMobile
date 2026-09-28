package net.streamdek.mobile.nativeapp.mediaserver.plex

import com.google.gson.Gson
import android.util.Log
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerEndpoint
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP to one Plex Media Server.
 *
 * Its own OkHttp client rather than StreamDek's: that one keeps a disk cache of responses, and a
 * Plex response has no business in it - it is the viewer's own library and it changes under us.
 * Redirects are not followed, so a token can never be carried to a host other than the server
 * that issued it.
 *
 * Every failure is logged by path only. Never a URL with a query, never a header, never a body.
 */
internal class PlexClient(
    private val identity: () -> PlexClientIdentity,
    private val gson: Gson = Gson(),
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .build()

    /** A shorter leash for probes, which run against several addresses at once. */
    private val probeHttp: OkHttpClient = http.newBuilder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    class UnauthorizedException : IOException("The server refused this account's token.")

    private fun request(endpoint: MediaServerEndpoint, path: String, query: Map<String, String>, start: Int?, size: Int?): Request? {
        val url = (endpoint.uri + path).toHttpUrlOrNull()?.newBuilder()?.apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }?.build() ?: return null
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                identity().headers().forEach { (name, value) -> header(name, value) }
                endpoint.accessToken?.let { header("X-Plex-Token", it) }
                if (start != null) header("X-Plex-Container-Start", start.toString())
                if (size != null) header("X-Plex-Container-Size", size.toString())
            }
            .build()
    }

    suspend fun get(
        endpoint: MediaServerEndpoint,
        path: String,
        query: Map<String, String> = emptyMap(),
        start: Int? = null,
        size: Int? = null,
    ): PlexContainer? = withContext(Dispatchers.IO) {
        val request = request(endpoint, path, query, start, size) ?: return@withContext null
        try {
            http.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) throw UnauthorizedException()
                if (!response.isSuccessful) {
                    Log.w("StreamDekPlex", "GET $path answered ${response.code}")
                    return@use null
                }
                val body = response.body?.charStream() ?: return@use null
                runCatching { gson.fromJson(body, PlexEnvelope::class.java)?.container }
                    .onFailure { Log.w("StreamDekPlex", "GET $path returned something unreadable") }
                    .getOrNull()
            }
        } catch (error: UnauthorizedException) {
            throw error
        } catch (error: IOException) {
            Log.w("StreamDekPlex", "GET $path failed: ${error.javaClass.simpleName}")
            null
        }
    }

    /** A call made for its effect (timeline, scrobble). True when the server accepted it. */
    suspend fun send(
        endpoint: MediaServerEndpoint,
        path: String,
        query: Map<String, String>,
        extraHeaders: Map<String, String> = emptyMap(),
        method: String = "GET",
    ): Boolean =
        withContext(Dispatchers.IO) {
            val base = request(endpoint, path, query, null, null) ?: return@withContext false
            val request = base.newBuilder()
                .apply { extraHeaders.forEach { (name, value) -> header(name, value) } }
                .apply { if (method != "GET") method(method, ByteArray(0).toRequestBody(null)) }
                .build()
            runCatching { http.newCall(request).execute().use { it.isSuccessful } }
                .onFailure { Log.w("StreamDekPlex", "$path failed: ${it.javaClass.simpleName}") }
                .getOrDefault(false)
        }

    /**
     * Whether [endpoint] is this server and accepts its token.
     *
     * `/identity` answers without a token, so the probe asks for the libraries' root instead: a
     * connection that reaches a server but is refused is not a working connection.
     */
    suspend fun probe(endpoint: MediaServerEndpoint, expectedServerId: String): ProbeResult = withContext(Dispatchers.IO) {
        val request = request(endpoint, "/identity", emptyMap(), null, null) ?: return@withContext ProbeResult.Unreachable
        val identityOk = runCatching {
            probeHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val machine = runCatching {
                    gson.fromJson(response.body?.charStream(), PlexEnvelope::class.java)?.container?.machineIdentifier
                }.getOrNull()
                machine == null || machine == expectedServerId
            }
        }.getOrDefault(false)
        if (!identityOk) return@withContext ProbeResult.Unreachable
        val authorised = request(endpoint, "/library/sections", emptyMap(), 0, 0) ?: return@withContext ProbeResult.Unreachable
        runCatching {
            probeHttp.newCall(authorised).execute().use { response ->
                when {
                    response.isSuccessful -> ProbeResult.Ok
                    response.code == 401 || response.code == 403 -> ProbeResult.Unauthorized
                    else -> ProbeResult.Unreachable
                }
            }
        }.getOrDefault(ProbeResult.Unreachable)
    }

    enum class ProbeResult { Ok, Unauthorized, Unreachable }
}

/** Which of a server's addresses to try, in what order. */
internal object PlexConnectionRanking {
    fun route(endpoint: MediaServerEndpoint): MediaServerRoute = when {
        endpoint.relay -> MediaServerRoute.Relay
        endpoint.local -> MediaServerRoute.Local
        else -> MediaServerRoute.Remote
    }

    /**
     * The address that worked last time first, then local before remote before relay, and HTTPS
     * before plain HTTP within each - the same address over TLS is preferred whenever it answers.
     */
    fun order(endpoints: List<MediaServerEndpoint>, preferredUri: String?): List<MediaServerEndpoint> {
        val rank = { endpoint: MediaServerEndpoint ->
            val routeRank = when (route(endpoint)) {
                MediaServerRoute.Local -> 0
                MediaServerRoute.Remote -> 2
                MediaServerRoute.Relay -> 4
            }
            routeRank + if (endpoint.uri.startsWith("https://")) 0 else 1
        }
        val sorted = endpoints.distinctBy { it.uri }.sortedBy(rank)
        val preferred = sorted.firstOrNull { it.uri == preferredUri } ?: return sorted
        return listOf(preferred) + sorted.filterNot { it === preferred }
    }
}
