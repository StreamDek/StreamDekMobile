package net.streamdek.mobile.nativeapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import net.streamdek.mobile.R

/**
 * Everything the player's episode browser draws, handed in by the app.
 *
 * Nothing here is the browser's own: the seasons are the detail page's, the episodes come from the
 * same three sources a season is read from there, and watched and progress are the stores the
 * detail page reads. The browser is a second view on that state, which is what keeps an episode
 * from being watched in one place and unwatched in the other.
 */
data class PlayerEpisodeBrowser(
  val detailId: String,
  val seriesTitle: String,
  val seasons: List<SeasonSummary>,
  /** Seasons read so far. A season missing from the map has not been asked for yet. */
  val episodesBySeason: Map<Int, List<EpisodeItem>>,
  val loadingSeason: Int?,
  val failedSeason: Int?,
  val currentSeason: Int,
  val currentEpisode: Int,
  /** Watched episodes, as [playerEpisodeSlot] keys. */
  val watched: Set<String>,
  /** How far into each part-watched episode the viewer is, 0 to 1, by [playerEpisodeSlot]. */
  val progress: Map<String, Float>,
  /** The Hide Episode Spoilers setting: artwork stays blurred until the episode is watched. */
  val hideUnwatchedArtwork: Boolean,
  val onLoadSeason: (Int) -> Unit,
  val onSelectEpisode: (EpisodeItem) -> Unit,
)

internal fun playerEpisodeSlot(seasonNumber: Int, episodeNumber: Int): String = "$seasonNumber:$episodeNumber"

/** Where a series stands, episode by episode: what is finished and how far the rest have got. */
internal class PlayerEpisodeStanding(val completed: Set<String>, val progress: Map<String, Float>)

/**
 * Reads one series' standing out of the two places playback progress is kept.
 *
 * The account's records and this device's resume entries can both describe the same episode, and
 * the newer of the two is the truth - a position made on the television a minute ago beats the one
 * this phone remembers from last week, and the other way round.
 *
 * A record's own `progress` is a percentage on the wire, but is tolerated as a fraction too, and a
 * position against a duration is preferred to either where both are known.
 */
internal fun playerEpisodeStanding(
  detailId: String,
  records: List<PlaybackProgressRecord>,
  localEntries: List<PlaybackMemoryEntry>,
): PlayerEpisodeStanding {
  class Mark(val fraction: Float, val completed: Boolean, val at: Long)
  val marks = HashMap<String, Mark>()
  fun offer(slot: String, mark: Mark) {
    val existing = marks[slot]
    if (existing == null || mark.at >= existing.at) marks[slot] = mark
  }
  records.forEach { record ->
    if (!record.entityType.equals("tv", ignoreCase = true) || record.entityId != detailId) return@forEach
    val season = record.seasonNumber ?: return@forEach
    val episode = record.episodeNumber ?: return@forEach
    val fraction = when {
      record.unwatched || record.dismissed -> 0.0
      record.durationSec > 0.0 && record.positionSec > 0.0 -> record.positionSec / record.durationSec
      record.progress > 1.0 -> record.progress / 100.0
      else -> record.progress
    }
    offer(
      playerEpisodeSlot(season, episode),
      Mark(fraction.coerceIn(0.0, 1.0).toFloat(), record.completed && !record.unwatched, record.updatedAt),
    )
  }
  localEntries.forEach { entry ->
    if (entry.isLive || entry.mediaId != detailId || normalizedMediaType(entry.mediaType) != "tv") return@forEach
    val season = entry.seasonNumber ?: return@forEach
    val episode = entry.episodeNumber ?: return@forEach
    val fraction = (entry.progressPercent / 100.0).coerceIn(0.0, 1.0).toFloat()
    offer(playerEpisodeSlot(season, episode), Mark(fraction, completed = false, at = entry.updatedAt))
  }
  return PlayerEpisodeStanding(
    completed = marks.filterValues { it.completed }.keys,
    // The same window the rest of the app calls "part watched": past the first moments, short of the end.
    progress = marks.filterValues { !it.completed && it.fraction > 0.01f && it.fraction < 0.95f }.mapValues { it.value.fraction },
  )
}

private val EpisodePanelShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
private val EpisodeRowShape = RoundedCornerShape(18.dp)
private val EpisodeThumbShape = RoundedCornerShape(12.dp)
private val EpisodeWatchedGreen = Color(0xFF22C55E)

/**
 * The episode browser, as a sheet on the trailing edge of the player.
 *
 * A side sheet rather than a centred dialog because the player is held in landscape: the list gets
 * the full height of the screen, which is the long axis of a list, and the picture stays visible
 * beside it so the viewer never feels they have left what they were watching.
 *
 * It opens on the season and the episode that are playing. Choosing another episode hands straight
 * back to the player, which shows its own loading state over the picture; nothing navigates.
 *
 * Drawn in the dark scheme in every appearance, like the rest of the player's controls: the surface
 * underneath is the video, not a page.
 */
@Composable
internal fun PlayerEpisodePanel(
  browser: PlayerEpisodeBrowser,
  /** How far through the playing episode the player is right now, which is newer than any store. */
  currentFraction: Float?,
  onClose: () -> Unit,
  onSelectEpisode: (EpisodeItem) -> Unit,
) {
  MaterialTheme(colorScheme = LocalDarkColorScheme.current ?: MaterialTheme.colorScheme) {
    val accent = MaterialTheme.colorScheme.primary
    var season by rememberSaveable(browser.detailId) { mutableIntStateOf(browser.currentSeason) }
    val episodes = browser.episodesBySeason[season]
    LaunchedEffect(season, episodes == null) {
      if (episodes == null) browser.onLoadSeason(season)
    }
    val reveal = remember { MutableTransitionState(false).apply { targetState = true } }
    val reducedMotion = LocalReducedMotion.current
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
      contentAlignment = Alignment.CenterEnd,
    ) {
      AnimatedVisibility(
        visibleState = reveal,
        enter = if (reducedMotion) fadeIn(tween(120)) else fadeIn(tween(160)) + slideInHorizontally(tween(260)) { it / 3 },
      ) {
        Column(
          modifier = Modifier
            .widthIn(max = 500.dp)
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(EpisodePanelShape)
            .background(Color(0xF20E131C))
            // Swallows taps on the sheet itself, so only the picture beside it dismisses.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .statusBarsPadding()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
            .padding(top = 18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                stringResource(R.string.detail_episodes),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
              )
              if (browser.seriesTitle.isNotBlank()) {
                Text(
                  browser.seriesTitle,
                  color = Color.White.copy(alpha = 0.62f),
                  style = MaterialTheme.typography.bodySmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .clickable(onClick = onClose),
              contentAlignment = Alignment.Center,
            ) {
              Icon(StreamDekPlayerIcons.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
            }
          }

          if (browser.seasons.size > 1) {
            val seasonRowState = rememberLazyListState()
            LaunchedEffect(browser.detailId) {
              val index = browser.seasons.indexOfFirst { it.seasonNumber == season }
              if (index > 0) seasonRowState.scrollToItem(index)
            }
            LazyRow(
              state = seasonRowState,
              contentPadding = PaddingValues(horizontal = 20.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              itemsIndexed(browser.seasons) { _, summary ->
                val selected = summary.seasonNumber == season
                val label = summary.name.ifBlank { stringResource(R.string.detail_season_number, summary.seasonNumber) }
                Box(
                  modifier = Modifier
                    .clip(StreamDekRadius.pill)
                    .background(if (selected) accent else Color.White.copy(alpha = 0.08f))
                    .clickable { season = summary.seasonNumber }
                    .semantics { this.selected = selected }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                  Text(
                    label,
                    color = if (selected) readableOn(accent) else Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                    maxLines = 1,
                  )
                }
              }
            }
          }

          Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
              episodes == null && browser.failedSeason == season -> Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  stringResource(R.string.error_episode_list_failed),
                  color = Color.White.copy(alpha = 0.78f),
                  style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { browser.onLoadSeason(season) }) { Text(stringResource(R.string.action_retry)) }
              }
              episodes == null -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(30.dp),
                strokeWidth = 2.5.dp,
                color = Color.White.copy(alpha = 0.86f),
              )
              episodes.isEmpty() -> Text(
                stringResource(R.string.player_episodes_empty),
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
              )
              else -> {
                val listState = rememberLazyListState()
                // Opens on what is playing; another season opens at its first episode.
                LaunchedEffect(season, episodes.size) {
                  val playing = if (season == browser.currentSeason) episodes.indexOfFirst { it.episodeNumber == browser.currentEpisode } else -1
                  listState.scrollToItem(playing.coerceAtLeast(0))
                }
                LazyColumn(
                  state = listState,
                  modifier = Modifier.fillMaxSize(),
                  contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 18.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  itemsIndexed(episodes) { _, episode ->
                    val slot = playerEpisodeSlot(episode.seasonNumber, episode.episodeNumber)
                    val playing = episode.seasonNumber == browser.currentSeason && episode.episodeNumber == browser.currentEpisode
                    val watched = slot in browser.watched
                    PlayerEpisodeRow(
                      episode = episode,
                      playing = playing,
                      watched = watched,
                      fraction = when {
                        playing -> currentFraction?.takeIf { it > 0.01f }
                        watched -> null
                        else -> browser.progress[slot]
                      },
                      hideArtwork = browser.hideUnwatchedArtwork && !watched && !playing,
                      accent = accent,
                      // The episode already on screen has nowhere to go: choosing it is "back to it".
                      onClick = { if (playing) onClose() else onSelectEpisode(episode) },
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * One episode in the browser.
 *
 * The still carries the states that are about the picture - how far in, playing now, not out yet -
 * and the text beside it carries the ones that are about the episode. An unaired episode is shown,
 * so the season reads complete, but cannot be chosen: there is nothing to play, and the player's
 * own next-episode flow refuses it for the same reason.
 */
@Composable
private fun PlayerEpisodeRow(
  episode: EpisodeItem,
  playing: Boolean,
  watched: Boolean,
  fraction: Float?,
  hideArtwork: Boolean,
  accent: Color,
  onClick: () -> Unit,
) {
  val unreleased = isEpisodeUnreleased(episode)
  val stateLabel = stringResource(
    when {
      playing -> R.string.player_now_playing
      unreleased -> R.string.detail_upcoming
      watched -> R.string.player_watched
      fraction != null -> R.string.episode_part_watched
      else -> R.string.episode_not_watched
    },
  )
  val name = episode.name.ifBlank { stringResource(R.string.detail_episode_number, episode.episodeNumber) }
  val heading = episode.episodeNumber.toString() + ". " + name
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .graphicsLayer { alpha = if (unreleased) 0.55f else 1f }
      .clip(EpisodeRowShape)
      .background(if (playing) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
      .then(if (playing) Modifier.border(1.5.dp, accent, EpisodeRowShape) else Modifier)
      .clickable(enabled = !unreleased, onClick = onClick)
      .semantics {
        selected = playing
        stateDescription = stateLabel
      }
      .padding(8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier = Modifier
        .width(128.dp)
        .aspectRatio(16f / 9f)
        .clip(EpisodeThumbShape)
        .background(Color.White.copy(alpha = 0.06f)),
    ) {
      AsyncImage(
        model = episode.still,
        contentDescription = null,
        modifier = Modifier.fillMaxSize().then(if (hideArtwork) Modifier.blur(10.dp) else Modifier),
        contentScale = ContentScale.Crop,
      )
      if (playing || unreleased) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 6.dp, bottom = if (fraction != null) 9.dp else 6.dp)
            .clip(StreamDekRadius.pill)
            .background(if (playing) accent else Color.Black.copy(alpha = 0.66f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
          Text(
            stateLabel,
            color = if (playing) readableOn(accent) else Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
          )
        }
      }
      if (fraction != null) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(3.dp)
            .background(Color.Black.copy(alpha = 0.5f)),
        ) {
          Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.02f, 1f)).background(accent))
        }
      }
    }
    Column(modifier = Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          heading,
          modifier = Modifier.weight(1f),
          color = Color.White,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Black,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (watched) {
          Icon(
            StreamDekPlayerIcons.Watched,
            contentDescription = null,
            tint = EpisodeWatchedGreen,
            modifier = Modifier.size(18.dp),
          )
        }
      }
      val meta = listOfNotNull(
        episode.airDate?.takeIf { it.isNotBlank() }?.let { formatEpisodeAirDateLabel(it) },
        episode.runtime?.takeIf { it > 0 }?.let { stringResource(R.string.detail_runtime_minutes, it) },
        stateLabel.takeIf { fraction != null && !playing },
      )
      if (meta.isNotEmpty()) {
        Text(
          meta.joinToString("  ·  "),
          color = Color.White.copy(alpha = 0.62f),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (episode.overview.isNotBlank()) {
        Text(
          episode.overview,
          color = Color.White.copy(alpha = 0.72f),
          style = MaterialTheme.typography.bodySmall,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
