package net.streamdek.mobile.nativeapp.mediaserver.jellyfin

import com.google.gson.annotations.SerializedName

/**
 * The parts of Jellyfin's API StreamDek reads, as Jellyfin names them (PascalCase JSON).
 *
 * Nullable throughout: Gson fills what the server sent and nothing else, and servers of different
 * versions leave different fields out. Nothing here holds a credential except [JellyfinAuthResult],
 * whose `toString` hides it.
 */

internal data class JellyfinPublicInfo(
    @SerializedName("LocalAddress") val localAddress: String? = null,
    @SerializedName("ServerName") val serverName: String? = null,
    @SerializedName("Version") val version: String? = null,
    @SerializedName("ProductName") val productName: String? = null,
    @SerializedName("Id") val id: String? = null,
    @SerializedName("StartupWizardCompleted") val startupWizardCompleted: Boolean? = null,
)

/** A server's answer to the local-network "who is JellyfinServer?" broadcast. */
internal data class JellyfinDiscoveryReply(
    @SerializedName("Address") val address: String? = null,
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("EndpointAddress") val endpointAddress: String? = null,
)

internal data class JellyfinUser(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("ServerId") val serverId: String? = null,
    @SerializedName("PrimaryImageTag") val primaryImageTag: String? = null,
)

internal data class JellyfinAuthResult(
    @SerializedName("User") val user: JellyfinUser? = null,
    @SerializedName("AccessToken") val accessToken: String? = null,
    @SerializedName("ServerId") val serverId: String? = null,
) {
    override fun toString(): String = "JellyfinAuthResult(user=${user?.id}, serverId=$serverId, token=[redacted])"
}

internal data class JellyfinQuickConnect(
    @SerializedName("Authenticated") val authenticated: Boolean? = null,
    @SerializedName("Secret") val secret: String? = null,
    @SerializedName("Code") val code: String? = null,
) {
    override fun toString(): String = "JellyfinQuickConnect(code=$code, authenticated=$authenticated)"
}

internal data class JellyfinUserData(
    @SerializedName("PlaybackPositionTicks") val playbackPositionTicks: Long? = null,
    @SerializedName("PlayCount") val playCount: Int? = null,
    @SerializedName("IsFavorite") val isFavorite: Boolean? = null,
    @SerializedName("Played") val played: Boolean? = null,
    @SerializedName("LastPlayedDate") val lastPlayedDate: String? = null,
    @SerializedName("UnplayedItemCount") val unplayedItemCount: Int? = null,
    @SerializedName("PlayedPercentage") val playedPercentage: Double? = null,
)

internal data class JellyfinPerson(
    @SerializedName("Name") val name: String? = null,
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Role") val role: String? = null,
    @SerializedName("Type") val type: String? = null,
    @SerializedName("PrimaryImageTag") val primaryImageTag: String? = null,
)

internal data class JellyfinMediaStream(
    @SerializedName("Type") val type: String? = null,
    @SerializedName("Codec") val codec: String? = null,
    @SerializedName("Language") val language: String? = null,
    @SerializedName("DisplayTitle") val displayTitle: String? = null,
    @SerializedName("Title") val title: String? = null,
    @SerializedName("Index") val index: Int? = null,
    @SerializedName("IsExternal") val isExternal: Boolean? = null,
    @SerializedName("IsTextSubtitleStream") val isTextSubtitleStream: Boolean? = null,
    @SerializedName("IsForced") val isForced: Boolean? = null,
    @SerializedName("Width") val width: Int? = null,
    @SerializedName("Height") val height: Int? = null,
    @SerializedName("Channels") val channels: Int? = null,
    @SerializedName("BitRate") val bitRate: Int? = null,
    @SerializedName("VideoRange") val videoRange: String? = null,
    @SerializedName("VideoRangeType") val videoRangeType: String? = null,
)

internal data class JellyfinMediaSource(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("Path") val path: String? = null,
    @SerializedName("Protocol") val protocol: String? = null,
    @SerializedName("Container") val container: String? = null,
    @SerializedName("Size") val size: Long? = null,
    @SerializedName("Bitrate") val bitrate: Int? = null,
    @SerializedName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerializedName("SupportsDirectPlay") val supportsDirectPlay: Boolean? = null,
    @SerializedName("SupportsDirectStream") val supportsDirectStream: Boolean? = null,
    @SerializedName("SupportsTranscoding") val supportsTranscoding: Boolean? = null,
    @SerializedName("MediaStreams") val mediaStreams: List<JellyfinMediaStream>? = null,
)

internal data class JellyfinPlaybackInfo(
    @SerializedName("MediaSources") val mediaSources: List<JellyfinMediaSource>? = null,
    @SerializedName("PlaySessionId") val playSessionId: String? = null,
)

internal data class JellyfinItem(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("OriginalTitle") val originalTitle: String? = null,
    @SerializedName("ServerId") val serverId: String? = null,
    @SerializedName("Type") val type: String? = null,
    @SerializedName("CollectionType") val collectionType: String? = null,
    @SerializedName("IsFolder") val isFolder: Boolean? = null,
    @SerializedName("ParentId") val parentId: String? = null,
    @SerializedName("Overview") val overview: String? = null,
    @SerializedName("Taglines") val taglines: List<String>? = null,
    @SerializedName("Genres") val genres: List<String>? = null,
    @SerializedName("CommunityRating") val communityRating: Double? = null,
    @SerializedName("CriticRating") val criticRating: Double? = null,
    @SerializedName("OfficialRating") val officialRating: String? = null,
    @SerializedName("ProductionYear") val productionYear: Int? = null,
    @SerializedName("PremiereDate") val premiereDate: String? = null,
    @SerializedName("DateCreated") val dateCreated: String? = null,
    @SerializedName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerializedName("Status") val status: String? = null,
    @SerializedName("IndexNumber") val indexNumber: Int? = null,
    @SerializedName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerializedName("ChildCount") val childCount: Int? = null,
    @SerializedName("RecursiveItemCount") val recursiveItemCount: Int? = null,
    @SerializedName("ProviderIds") val providerIds: Map<String, String>? = null,
    @SerializedName("People") val people: List<JellyfinPerson>? = null,
    @SerializedName("UserData") val userData: JellyfinUserData? = null,
    @SerializedName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerializedName("BackdropImageTags") val backdropImageTags: List<String>? = null,
    @SerializedName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerializedName("ParentBackdropImageTags") val parentBackdropImageTags: List<String>? = null,
    @SerializedName("ParentLogoItemId") val parentLogoItemId: String? = null,
    @SerializedName("ParentLogoImageTag") val parentLogoImageTag: String? = null,
    @SerializedName("SeriesId") val seriesId: String? = null,
    @SerializedName("SeriesName") val seriesName: String? = null,
    @SerializedName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    @SerializedName("SeasonId") val seasonId: String? = null,
    @SerializedName("SeasonName") val seasonName: String? = null,
    @SerializedName("MediaSources") val mediaSources: List<JellyfinMediaSource>? = null,
    @SerializedName("LocationType") val locationType: String? = null,
)

internal data class JellyfinItems(
    @SerializedName("Items") val items: List<JellyfinItem>? = null,
    @SerializedName("TotalRecordCount") val totalRecordCount: Int? = null,
    @SerializedName("StartIndex") val startIndex: Int? = null,
)
