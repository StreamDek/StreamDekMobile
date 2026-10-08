package net.streamdek.mobile.nativeapp.mediaserver

/*
 * A plain local address for a Plex server, for networks that will not resolve plex.direct.
 *
 * Plex lists a server's home-network connection as an HTTPS address under plex.direct, a name that
 * resolves to the server's private IP ("192-168-1-20.<id>.plex.direct" -> 192.168.1.20). Many
 * routers refuse to resolve a public name to a private address - DNS rebinding protection, on by
 * default on several of them and in some DNS services - and then that connection simply fails.
 * With remote access and Relay also unavailable, the server can never be reached from the sofa it
 * sits next to, which is how "I can't add my local server" looks from the outside.
 *
 * The IP is in the name, so the same server can be asked over plain HTTP on the network directly,
 * which Plex accepts from the local network unless secure connections are set to Required. It is
 * tried after the HTTPS address, never instead of it.
 */

private val PLEX_DIRECT_IPV4 = Regex("""^(\d{1,3}(?:-\d{1,3}){3})\.[0-9a-z]+\.plex\.direct$""", RegexOption.IGNORE_CASE)

/** "http://192.168.1.20:32400" for "https://192-168-1-20.<id>.plex.direct:32400", or null for anything else. */
internal fun plexDirectLanAddress(uri: String): String? {
    val match = Regex("""^https://([^/:]+)(?::(\d+))?/?$""", RegexOption.IGNORE_CASE).find(uri.trim()) ?: return null
    val host = match.groupValues[1]
    val port = match.groupValues[2].ifEmpty { "32400" }
    val dashed = PLEX_DIRECT_IPV4.find(host)?.groupValues?.get(1) ?: return null
    val octets = dashed.split('-').map { it.toIntOrNull() ?: return null }
    if (octets.any { it !in 0..255 }) return null
    return "http://${octets.joinToString(".")}:$port"
}

/** [endpoints] with a plain local address added after each local plex.direct one; see above. */
internal fun withPlexLanFallbacks(endpoints: List<MediaServerEndpoint>): List<MediaServerEndpoint> {
    val fallbacks = endpoints
        .filter { it.local && !it.relay }
        .mapNotNull { endpoint -> plexDirectLanAddress(endpoint.uri)?.let { endpoint.copy(uri = it) } }
    return (endpoints + fallbacks).distinctBy { it.uri }
}
