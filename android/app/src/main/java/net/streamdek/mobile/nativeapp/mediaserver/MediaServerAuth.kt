package net.streamdek.mobile.nativeapp.mediaserver

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import java.util.concurrent.ConcurrentHashMap

/**
 * Which token belongs to which server address, so a request can carry it in a header instead of
 * in its URL.
 *
 * Plex artwork, subtitles and media are plain HTTP resources, and the usual way to fetch them is
 * `?X-Plex-Token=` on the end of the link. That puts the credential into every image URL - into
 * Coil's disk cache keys, into any log line that prints a URL, into a crash report that captured
 * one. StreamDek never builds such a URL. Links stay token-free, and the token is attached here,
 * at the moment of the request, only when the request is going to the server that issued it.
 *
 * Installed as a *network* interceptor wherever it is used, so it is consulted for every hop: a
 * redirect to some other host is a new origin, finds no token, and gets none.
 */
object MediaServerAuth {
    const val TOKEN_HEADER = "X-Plex-Token"

    /** Header name and value, by server origin. Plex's is its token header; Jellyfin's is `Authorization`. */
    private val tokensByOrigin = ConcurrentHashMap<String, Pair<String, String>>()

    /** Remembers a Plex [token] for requests to [uri]'s scheme, host and port. */
    fun register(uri: String, token: String?) {
        val origin = originOf(uri) ?: return
        if (token.isNullOrBlank()) tokensByOrigin.remove(origin) else tokensByOrigin[origin] = TOKEN_HEADER to token
    }

    /** Remembers a whole header for requests to [uri]'s origin - Jellyfin's `Authorization: MediaBrowser ...`. */
    fun registerHeader(uri: String, name: String, value: String?) {
        val origin = originOf(uri) ?: return
        if (value.isNullOrBlank()) tokensByOrigin.remove(origin) else tokensByOrigin[origin] = name to value
    }

    /** Forgets one server's addresses, when it is disconnected. */
    fun forget(uri: String) {
        originOf(uri)?.let(tokensByOrigin::remove)
    }

    /** Forgets every server: on disconnect, sign-out and profile switch. */
    fun clear() {
        tokensByOrigin.clear()
    }

    /** The header a request to [url] needs, or nothing when [url] is not a known server. */
    fun headersFor(url: String): Map<String, String> {
        val (name, value) = originOf(url)?.let(tokensByOrigin::get) ?: return emptyMap()
        return mapOf(name to value)
    }

    fun isKnownServerUrl(url: String): Boolean = originOf(url)?.let(tokensByOrigin::containsKey) == true

    val interceptor: Interceptor = Interceptor { chain ->
        val request = chain.request()
        val header = tokensByOrigin[originOf(request.url.scheme, request.url.host, request.url.port)]
            ?: return@Interceptor chain.proceed(request)
        if (request.header(header.first) != null) chain.proceed(request)
        else chain.proceed(request.newBuilder().header(header.first, header.second).build())
    }

    internal fun originOf(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        return originOf(parsed.scheme, parsed.host, parsed.port)
    }

    private fun originOf(scheme: String, host: String, port: Int): String = "${scheme.lowercase()}://${host.lowercase()}:$port"
}

/** For the few places that build their own OkHttp request to a URL that may be a media server. */
fun okhttp3.Request.Builder.withMediaServerAuth(url: String): okhttp3.Request.Builder = apply {
    MediaServerAuth.headersFor(url).forEach { (name, value) -> header(name, value) }
}
