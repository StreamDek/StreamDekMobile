package net.streamdek.mobile.nativeapp.mediaserver.plex

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Plex Media Server responses, as much of them as StreamDek reads.
 *
 * Everything is nullable: Gson fills fields one by one and leaves out whatever a server version did
 * not send, and PMS versions differ. Flags that some versions send as `true` and others as `1` are
 * [JsonElement] and read through [flag].
 */
internal data class PlexEnvelope(
    @SerializedName("MediaContainer") val container: PlexContainer? = null,
)

internal data class PlexContainer(
    val size: Int? = null,
    val totalSize: Int? = null,
    val offset: Int? = null,
    val machineIdentifier: String? = null,
    val version: String? = null,
    val librarySectionID: String? = null,
    @SerializedName("Metadata") val metadata: List<PlexMetadata>? = null,
    @SerializedName("Directory") val directories: List<PlexDirectory>? = null,
    @SerializedName("Hub") val hubs: List<PlexHub>? = null,
    // Universal transcoder decision fields.
    val generalDecisionCode: Int? = null,
    val generalDecisionText: String? = null,
    val directPlayDecisionCode: Int? = null,
    val directPlayDecisionText: String? = null,
    val transcodeDecisionCode: Int? = null,
    val transcodeDecisionText: String? = null,
)

internal data class PlexDirectory(
    val key: String? = null,
    val type: String? = null,
    val title: String? = null,
    val uuid: String? = null,
    val agent: String? = null,
    val scanner: String? = null,
    val language: String? = null,
    val hidden: Int? = null,
)

internal data class PlexHub(
    val hubIdentifier: String? = null,
    val title: String? = null,
    val type: String? = null,
    val key: String? = null,
    val size: Int? = null,
    @SerializedName("Metadata") val metadata: List<PlexMetadata>? = null,
)

internal data class PlexMetadata(
    val ratingKey: String? = null,
    val key: String? = null,
    val guid: String? = null,
    val type: String? = null,
    val subtype: String? = null,
    val title: String? = null,
    val titleSort: String? = null,
    val originalTitle: String? = null,
    val summary: String? = null,
    val tagline: String? = null,
    val year: Int? = null,
    val thumb: String? = null,
    val art: String? = null,
    val parentThumb: String? = null,
    val parentArt: String? = null,
    val grandparentThumb: String? = null,
    val grandparentArt: String? = null,
    val parentTitle: String? = null,
    val grandparentTitle: String? = null,
    val parentRatingKey: String? = null,
    val grandparentRatingKey: String? = null,
    val parentIndex: Int? = null,
    val index: Int? = null,
    val duration: Long? = null,
    val viewOffset: Long? = null,
    val viewCount: Int? = null,
    val lastViewedAt: Long? = null,
    val addedAt: Long? = null,
    val updatedAt: Long? = null,
    val originallyAvailableAt: String? = null,
    val contentRating: String? = null,
    val studio: String? = null,
    val rating: Double? = null,
    val audienceRating: Double? = null,
    val leafCount: Int? = null,
    val viewedLeafCount: Int? = null,
    val childCount: Int? = null,
    val librarySectionID: String? = null,
    val librarySectionTitle: String? = null,
    @SerializedName("Guid") val guids: List<PlexTag>? = null,
    @SerializedName("Genre") val genres: List<PlexTag>? = null,
    @SerializedName("Role") val roles: List<PlexTag>? = null,
    @SerializedName("Director") val directors: List<PlexTag>? = null,
    @SerializedName("Media") val media: List<PlexMedia>? = null,
    @SerializedName("Image") val images: List<PlexImage>? = null,
)

internal data class PlexTag(
    /** A string for Guid ("imdb://tt…"), a number for Role - Gson reads either into a String. */
    val id: String? = null,
    val tag: String? = null,
    val role: String? = null,
    val thumb: String? = null,
)

internal data class PlexImage(
    val type: String? = null,
    val url: String? = null,
)

internal data class PlexMedia(
    val id: String? = null,
    val duration: Long? = null,
    val bitrate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val audioChannels: Int? = null,
    val audioCodec: String? = null,
    val videoCodec: String? = null,
    val videoResolution: String? = null,
    val videoProfile: String? = null,
    val container: String? = null,
    val videoFrameRate: String? = null,
    @SerializedName("Part") val parts: List<PlexPart>? = null,
)

internal data class PlexPart(
    val id: String? = null,
    val key: String? = null,
    val duration: Long? = null,
    val file: String? = null,
    val size: Long? = null,
    val container: String? = null,
    val decision: String? = null,
    @SerializedName("Stream") val streams: List<PlexStream>? = null,
)

internal data class PlexStream(
    val id: String? = null,
    /** 1 video, 2 audio, 3 subtitle. */
    val streamType: Int? = null,
    val codec: String? = null,
    val index: Int? = null,
    val language: String? = null,
    val languageCode: String? = null,
    val languageTag: String? = null,
    val displayTitle: String? = null,
    val extendedDisplayTitle: String? = null,
    val title: String? = null,
    /** Present on a sidecar subtitle file: where to fetch it. */
    val key: String? = null,
    val selected: JsonElement? = null,
    val forced: JsonElement? = null,
    val channels: Int? = null,
    val bitDepth: Int? = null,
    val profile: String? = null,
    val decision: String? = null,
    @SerializedName("DOVIPresent") val doviPresent: JsonElement? = null,
    @SerializedName("DOVIProfile") val doviProfile: Int? = null,
)

internal fun JsonElement?.flag(): Boolean = runCatching {
    when {
        this == null || isJsonNull -> false
        isJsonPrimitive && asJsonPrimitive.isBoolean -> asBoolean
        isJsonPrimitive && asJsonPrimitive.isNumber -> asInt != 0
        isJsonPrimitive -> asString == "1" || asString.equals("true", ignoreCase = true)
        else -> false
    }
}.getOrDefault(false)

internal fun PlexContainer.allMetadata(): List<PlexMetadata> =
    metadata.orEmpty() + hubs.orEmpty().flatMap { it.metadata.orEmpty() }
