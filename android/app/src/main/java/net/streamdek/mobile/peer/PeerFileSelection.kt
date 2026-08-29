package net.streamdek.mobile.peer

internal data class PeerFileCandidate(
  val index: Int,
  val path: String,
  val size: Long,
)

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "mov", "webm", "m4v", "ts", "m2ts")

/** Selects the requested episode locally; season packs must never fall back to their largest episode. */
internal fun selectPeerVideoFile(
  candidates: List<PeerFileCandidate>,
  preferredFilename: String?,
  title: String?,
  season: Int?,
  episode: Int?,
): PeerFileCandidate {
  val videos = candidates.filter { candidate ->
    candidate.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
  }
  require(videos.isNotEmpty()) { "No playable video file was found in this torrent." }

  val preferred = preferredFilename?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
  if (preferred != null) {
    videos.firstOrNull { it.path.lowercase().endsWith(preferred) || it.path.lowercase().contains(preferred) }
      ?.let { return it }
  }

  if (season != null && episode != null) {
    val s = season.toString().padStart(2, '0')
    val e = episode.toString().padStart(2, '0')
    val patterns = listOf("s${s}e${e}", "${season}x${e}", "s${season}e${episode}")
    val titleTokens = mediaTokens(title)
    val exactEpisode = videos.filter { candidate ->
      val normalized = candidate.path.lowercase().replace(Regex("[ ._\\-]+"), "")
      val pathTokens = mediaTokens(candidate.path)
      patterns.any(normalized::contains) && (titleTokens.isEmpty() || titleTokens.all(pathTokens::contains))
    }
    if (exactEpisode.isNotEmpty()) return bestTitleMatch(exactEpisode, title)
    throw IllegalStateException("This torrent does not contain S${s}E${e}; it will not play a different episode.")
  }

  return bestTitleMatch(videos.filterNot { it.path.contains("sample", ignoreCase = true) }.ifEmpty { videos }, title)
}

private fun bestTitleMatch(candidates: List<PeerFileCandidate>, title: String?): PeerFileCandidate {
  val titleTokens = mediaTokens(title)
  return candidates.maxWithOrNull(
    compareBy<PeerFileCandidate> { candidate ->
      titleTokens.count(mediaTokens(candidate.path)::contains)
    }.thenBy { it.size },
  ) ?: error("No playable video file was found in this torrent.")
}

private fun mediaTokens(value: String?): Set<String> = value.orEmpty().lowercase()
  .split(Regex("[^a-z0-9]+"))
  .filter { it.length >= 3 }
  .toSet()
