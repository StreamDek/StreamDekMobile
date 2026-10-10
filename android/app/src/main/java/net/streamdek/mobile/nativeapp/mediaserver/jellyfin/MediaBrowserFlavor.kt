package net.streamdek.mobile.nativeapp.mediaserver.jellyfin

import net.streamdek.mobile.nativeapp.mediaserver.EMBY_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.JELLYFIN_PROVIDER_ID

/**
 * Jellyfin and Emby: one family of servers, one implementation.
 *
 * Jellyfin began as a fork of Emby, and the two still share their REST API: the same sign-in, the
 * same item, image, playback-info and session-reporting routes, the same ticks and GUIDs. So the
 * client, provider and mapping in this package serve both, and what actually differs is listed
 * here and nowhere else:
 *
 * - how a client says who it is and carries its token (Jellyfin: one `Authorization: MediaBrowser
 *   ..., Token=` header; Emby: `Authorization: Emby ...` for the client and `X-Emby-Token`);
 * - whether the newer user-scoped routes (`/UserViews`, `/UserItems/...`) exist - Jellyfin 10.9+
 *   only - or the older `/Users/{id}/...` ones are the only ones;
 * - whether `/Users/Me` exists (Jellyfin only);
 * - whether Quick Connect is offered (Jellyfin) or Emby Connect is (Emby);
 * - the local-network discovery message, and the name the server gives itself.
 *
 * Anything that is the same stays shared. A third member of the family would be one more entry.
 */
// Product names and log tags, not interface text: they are the same in every language.
private const val JELLYFIN_LABEL = "Jellyfin"
private const val EMBY_LABEL = "Emby"
private const val JELLYFIN_LOG_TAG = "StreamDekJellyfin"
private const val EMBY_LOG_TAG = "StreamDekEmby"
private const val AUTHORIZATION = "Authorization"
private const val EMBY_SCHEME = "Emby"

enum class MediaBrowserFlavor(
    val providerId: String,
    val label: String,
    val quickConnect: Boolean,
    val embyConnect: Boolean,
    /** Whether to try the newer user-scoped routes before the older ones. */
    val currentRoutes: Boolean,
    val hasUsersMe: Boolean,
    val discoveryMessage: String,
    /** A word `/System/Info/Public`'s ProductName contains for this server. */
    val productWord: String,
    val logTag: String,
) {
    Jellyfin(
        providerId = JELLYFIN_PROVIDER_ID, label = JELLYFIN_LABEL, quickConnect = true, embyConnect = false,
        currentRoutes = true, hasUsersMe = true, discoveryMessage = "who is JellyfinServer?", productWord = "jellyfin",
        logTag = JELLYFIN_LOG_TAG,
    ),
    Emby(
        providerId = EMBY_PROVIDER_ID, label = EMBY_LABEL, quickConnect = false, embyConnect = true,
        currentRoutes = false, hasUsersMe = false, discoveryMessage = "who is EmbyServer?", productWord = "emby",
        logTag = EMBY_LOG_TAG,
    ),
    ;

    /** The headers an API request carries. */
    internal fun requestHeaders(identity: JellyfinClientIdentity, token: String?): Map<String, String> = when (this) {
        Jellyfin -> mapOf(AUTHORIZATION to identity.authorization(token))
        Emby -> buildMap {
            put(AUTHORIZATION, identity.clientDescription(EMBY_SCHEME))
            if (!token.isNullOrBlank()) put(EMBY_TOKEN_HEADER, token)
        }
    }

    /**
     * The one header a media, image or subtitle request carries, registered per server address with
     * MediaServerAuth so no URL ever holds a token. Emby takes the token alone; Jellyfin wants the
     * whole `MediaBrowser` header, as its legacy token header is off by default.
     */
    internal fun mediaHeader(identity: JellyfinClientIdentity, token: String): Pair<String, String> = when (this) {
        Jellyfin -> AUTHORIZATION to identity.authorization(token)
        Emby -> EMBY_TOKEN_HEADER to token
    }

    /**
     * Whether a server's own name for itself rules it out: a Jellyfin server typed in as Emby, or the
     * other way round. An unnamed answer is given the benefit of the doubt.
     */
    fun isSibling(productName: String?): Boolean {
        val product = productName?.lowercase() ?: return false
        return !product.contains(productWord) && entries.any { it != this && product.contains(it.productWord) }
    }

    /** The other member of the family, to name when the viewer typed its address in the wrong place. */
    val sibling: MediaBrowserFlavor get() = if (this == Jellyfin) Emby else Jellyfin

    companion object {
        const val EMBY_TOKEN_HEADER = "X-Emby-Token"

        fun of(providerId: String?): MediaBrowserFlavor? = entries.firstOrNull { it.providerId == providerId }
    }
}
