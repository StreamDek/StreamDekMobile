package net.streamdek.mobile.nativeapp.mediaserver.jellyfin

import com.google.gson.Gson
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Who StreamDek says it is to a Jellyfin server. None of it is secret. */
internal data class JellyfinClientIdentity(
    val client: String,
    val deviceName: String,
    val deviceId: String,
    val version: String,
) {
    /**
     * Jellyfin's `Authorization: MediaBrowser ...` header, with the token when there is one. The
     * legacy X-Emby-Token header is not used: current servers only accept it when an administrator
     * has switched legacy authorisation back on.
     */
    fun authorization(token: String?): String = buildString {
        append("MediaBrowser ")
        append("Client=\"").append(enc(client)).append("\", ")
        append("Device=\"").append(enc(deviceName)).append("\", ")
        append("DeviceId=\"").append(enc(deviceId)).append("\", ")
        append("Version=\"").append(enc(version)).append('"')
        if (!token.isNullOrBlank()) append(", Token=\"").append(token).append('"')
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}

/** One Jellyfin server as this device reaches it: an address, and the signed-in user's token. */
internal data class JellyfinEndpoint(val baseUrl: String, val token: String?) {
    override fun toString(): String = "JellyfinEndpoint(baseUrl=$baseUrl, token=${if (token.isNullOrBlank()) "none" else "[redacted]"})"
}

/**
 * HTTP to Jellyfin servers.
 *
 * Its own OkHttp client, as Plex's is: no response cache (the viewer's library changes under us)
 * and no redirects followed, so the token is never carried to a host other than the one the viewer
 * signed in to. Failures are logged by path and status only - never a URL with a query, a header,
 * a body, a username or a password.
 */
internal class JellyfinClient(
    private val identity: () -> JellyfinClientIdentity,
    private val gson: Gson = Gson(),
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val probeHttp: OkHttpClient = http.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    class UnauthorizedException : IOException("The server refused this sign-in.")

    /** The server answered, but not with success: 404 is how an older server says it has no such route. */
    class StatusException(val code: Int) : IOException("status $code")

    /** The header a request to a signed-in server carries, for requests made outside this client. */
    fun authorizationFor(token: String?): String = identity().authorization(token)

    fun deviceId(): String = identity().deviceId

    private fun url(baseUrl: String, path: String, query: Map<String, String>) =
        (baseUrl.trimEnd('/') + path).toHttpUrlOrNull()?.newBuilder()?.apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }?.build()

    private fun request(endpoint: JellyfinEndpoint, path: String, query: Map<String, String>, method: String, body: RequestBody?): Request? {
        val url = url(endpoint.baseUrl, path, query) ?: return null
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", identity().authorization(endpoint.token))
            .method(method, body ?: if (method == "GET" || method == "HEAD") null else ByteArray(0).toRequestBody(null))
            .build()
    }

    private fun json(value: Any): RequestBody = gson.toJson(value).toRequestBody(JSON)

    private suspend fun <T> call(
        client: OkHttpClient,
        endpoint: JellyfinEndpoint,
        method: String,
        path: String,
        query: Map<String, String>,
        body: Any?,
        type: Class<T>?,
    ): Result<T?> = withContext(Dispatchers.IO) {
        val request = request(endpoint, path, query, method, body?.let(::json)) ?: return@withContext Result.failure(IOException("bad address"))
        try {
            client.newCall(request).await().use { response ->
                if (response.code == 401 || response.code == 403) throw UnauthorizedException()
                if (!response.isSuccessful) {
                    Log.w("StreamDekJellyfin", "$method ${logPath(path)} answered ${response.code}")
                    return@use Result.failure(StatusException(response.code))
                }
                if (type == null) return@use Result.success(null)
                val text = response.body?.charStream() ?: return@use Result.success(null)
                Result.success(runCatching { gson.fromJson(text, type) }.onFailure {
                    Log.w("StreamDekJellyfin", "$method ${logPath(path)} returned something unreadable")
                }.getOrNull())
            }
        } catch (error: UnauthorizedException) {
            throw error
        } catch (error: IOException) {
            Log.w("StreamDekJellyfin", "$method ${logPath(path)} failed: ${error.javaClass.simpleName}")
            Result.failure(error)
        }
    }

    /**
     * The response, cancelled with its caller: a caller that stops waiting (a row's own time limit,
     * the page going away) closes the connection rather than leaving a blocked read to run out.
     */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    /** Item ids are not secret, but a path is logged without them anyway: shape is what debugging needs. */
    private fun logPath(path: String) = path.replace(Regex("[0-9a-fA-F]{32}"), "{id}")

    suspend fun <T> get(endpoint: JellyfinEndpoint, path: String, type: Class<T>, query: Map<String, String> = emptyMap()): T? =
        call(http, endpoint, "GET", path, query, null, type).getOrNull()

    /** As [get], but says why nothing came back, so a caller can try an older route on a 404. */
    suspend fun <T> fetch(endpoint: JellyfinEndpoint, path: String, type: Class<T>, query: Map<String, String> = emptyMap()): Result<T?> =
        call(http, endpoint, "GET", path, query, null, type)

    /** As [send], with the failure kept. */
    suspend fun sendResult(endpoint: JellyfinEndpoint, method: String, path: String, body: Any? = null, query: Map<String, String> = emptyMap()): Result<Any?> =
        call<Any>(http, endpoint, method, path, query, body, null)

    suspend fun <T> post(endpoint: JellyfinEndpoint, path: String, body: Any?, type: Class<T>, query: Map<String, String> = emptyMap()): T? =
        call(http, endpoint, "POST", path, query, body, type).getOrNull()

    /** A call made for its effect. True when the server accepted it. */
    suspend fun send(endpoint: JellyfinEndpoint, method: String, path: String, body: Any? = null, query: Map<String, String> = emptyMap()): Boolean =
        runCatching { call<Any>(http, endpoint, method, path, query, body, null).isSuccess }.getOrDefault(false)

    /** The server's public description, or null when nothing at [baseUrl] answers as Jellyfin. */
    suspend fun publicInfo(baseUrl: String): JellyfinPublicInfo? =
        runCatching { call(probeHttp, JellyfinEndpoint(baseUrl, null), "GET", "/System/Info/Public", emptyMap(), null, JellyfinPublicInfo::class.java).getOrNull() }
            .getOrNull()?.takeIf { !it.id.isNullOrBlank() }

    enum class ProbeResult { Ok, Unauthorized, Unreachable }

    /** Whether [endpoint] answers and still accepts the signed-in user's token. */
    suspend fun probe(endpoint: JellyfinEndpoint, userId: String): ProbeResult = try {
        var user = call(probeHttp, endpoint, "GET", "/Users/Me", emptyMap(), null, JellyfinUser::class.java)
        // A server without /Users/Me still answers for the user by id.
        if ((user.exceptionOrNull() as? StatusException)?.code == 404) {
            user = call(probeHttp, endpoint, "GET", "/Users/$userId", emptyMap(), null, JellyfinUser::class.java)
        }
        when {
            user.isSuccess && (user.getOrNull()?.id == null || user.getOrNull()?.id.equals(userId, true)) -> ProbeResult.Ok
            user.isSuccess -> ProbeResult.Unauthorized
            else -> ProbeResult.Unreachable
        }
    } catch (_: UnauthorizedException) {
        ProbeResult.Unauthorized
    }

    suspend fun authenticateByName(baseUrl: String, username: String, password: String): JellyfinAuthResult? =
        runCatching {
            call(http, JellyfinEndpoint(baseUrl, null), "POST", "/Users/AuthenticateByName", emptyMap(),
                mapOf("Username" to username, "Pw" to password), JellyfinAuthResult::class.java).getOrNull()
        }.getOrNull()

    suspend fun quickConnectEnabled(baseUrl: String): Boolean =
        runCatching { call(probeHttp, JellyfinEndpoint(baseUrl, null), "GET", "/QuickConnect/Enabled", emptyMap(), null, Boolean::class.javaObjectType).getOrNull() }
            .getOrNull() == true

    suspend fun quickConnectInitiate(baseUrl: String): JellyfinQuickConnect? =
        runCatching { call(http, JellyfinEndpoint(baseUrl, null), "POST", "/QuickConnect/Initiate", emptyMap(), null, JellyfinQuickConnect::class.java).getOrNull() }
            .getOrNull()

    suspend fun quickConnectState(baseUrl: String, secret: String): JellyfinQuickConnect? =
        runCatching { call(http, JellyfinEndpoint(baseUrl, null), "GET", "/QuickConnect/Connect", mapOf("secret" to secret), null, JellyfinQuickConnect::class.java).getOrNull() }
            .getOrNull()

    suspend fun authenticateWithQuickConnect(baseUrl: String, secret: String): JellyfinAuthResult? =
        runCatching {
            call(http, JellyfinEndpoint(baseUrl, null), "POST", "/Users/AuthenticateWithQuickConnect", emptyMap(),
                mapOf("Secret" to secret), JellyfinAuthResult::class.java).getOrNull()
        }.getOrNull()

    /** Ends this device's session on the server, so the token stops working there too. */
    suspend fun logout(endpoint: JellyfinEndpoint): Boolean = send(endpoint, "POST", "/Sessions/Logout")

    /**
     * Jellyfin servers on this network, from its own discovery broadcast (UDP 7359). A server whose
     * administrator switched discovery off simply does not answer; typing its address still works.
     */
    suspend fun discover(timeoutMs: Int = 2_500): List<JellyfinDiscoveryReply> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, JellyfinDiscoveryReply>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 400
                val message = "who is JellyfinServer?".toByteArray()
                socket.send(DatagramPacket(message, message.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT))
                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(4096)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val reply = runCatching { gson.fromJson(String(packet.data, 0, packet.length, Charsets.UTF_8), JellyfinDiscoveryReply::class.java) }.getOrNull()
                    val id = reply?.id ?: continue
                    if (!reply.address.isNullOrBlank()) found.putIfAbsent(id, reply)
                }
            }
        }.onFailure { Log.w("StreamDekJellyfin", "discovery failed: ${it.javaClass.simpleName}") }
        found.values.toList()
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val DISCOVERY_PORT = 7359

        /**
         * The addresses worth trying for what the viewer typed, most likely first. "jellyfin.example.com"
         * becomes HTTPS first; a bare local address gets Jellyfin's default port; anything with a
         * scheme is used as given. A path is kept (servers behind a reverse proxy often live under
         * one) but the web client's own pages are cut off it.
         */
        fun candidates(input: String): List<String> {
            val trimmed = input.trim().trimEnd('/').substringBefore("/web/").removeSuffix("/web")
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return emptyList()
            if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) return listOf(trimmed)
            val host = trimmed.substringBefore('/').substringBefore(':')
            val hasPort = trimmed.substringBefore('/').contains(':')
            val local = isLocalHost(host)
            return buildList {
                if (hasPort) {
                    if (local) { add("http://$trimmed"); add("https://$trimmed") } else { add("https://$trimmed"); add("http://$trimmed") }
                } else {
                    val rest = trimmed.removePrefix(host)
                    if (local) {
                        add("http://$host:8096$rest"); add("https://$host:8920$rest"); add("http://$trimmed"); add("https://$trimmed")
                    } else {
                        add("https://$trimmed"); add("http://$host:8096$rest"); add("http://$trimmed")
                    }
                }
            }.distinct()
        }

        /** Whether an address is on the viewer's own network: private ranges, link-local, .local names. */
        fun isLocalHost(host: String): Boolean {
            val value = host.lowercase(Locale.US).trim('[', ']')
            if (value == "localhost" || value.endsWith(".local") || value.endsWith(".lan") || value.endsWith(".home.arpa")) return true
            val parts = value.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.size == 4) {
                return parts[0] == 10 || (parts[0] == 192 && parts[1] == 168) || (parts[0] == 172 && parts[1] in 16..31) ||
                    (parts[0] == 169 && parts[1] == 254) || parts[0] == 127
            }
            return value.startsWith("fd") || value.startsWith("fe80")
        }
    }
}
