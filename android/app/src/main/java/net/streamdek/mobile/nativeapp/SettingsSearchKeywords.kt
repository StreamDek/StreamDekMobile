package net.streamdek.mobile.nativeapp

/**
 * The English search terms behind each settings page, beside its translated title and summary.
 *
 * Its own file: every string here is a constant in the class of whichever file declares it, and
 * StreamDekNativeApp.kt's is at the JVM's class-size limit.
 */
internal fun settingsRouteKeywords(route: SettingsRoute): String = when (route) {
  SettingsRoute.Player -> "player engine mpv media3 exoplayer pip picture in picture floating " +
    "gesture gestures hold speed swipe seek scrub brightness volume level dim loudness " +
    "controls labels layout status bar title"
  SettingsRoute.VideoDecoding -> "decoding decoder hardware software compatibility codec hevc h265 " +
    "dolby vision dv7 profile 7 hdr tunneled tunnelling display surface render black screen " +
    "green screen stutter video will not play won't play playback engine mpv"
  SettingsRoute.SkipAndAutoplay -> "autoplay auto play skip intro recap ending credits next episode binge threshold introdb intro db api key"
  SettingsRoute.Subtitles -> "subtitle subtitles caption captions language languages preferred secondary forced show only addon loading source position style " +
    "appearance size colour color outline bold timing delay sync synchronisation synchronization offset early late"
  SettingsRoute.Audio -> "audio sound language languages spoken dub dubbed preferred secondary delay sync synchronisation synchronization " +
    "lip sync lag latency bluetooth headphones soundbar speakers offset early late"
  SettingsRoute.Streams -> "streams stream results source quality resolution 4k 1080p size limit filter badges labels " +
    "formatting remember last source list"
  SettingsRoute.Downloads -> "download downloads offline saved save storage remove delete watch offline"
  SettingsRoute.Appearance -> "appearance language theme colour color dark light mode header navigation labels collapse scroll scrolling behaviour behavior font motion animation animations speed transitions reduce reduced cinematic visual effects glass blur transparency performance battery"
  SettingsRoute.HomeScreen -> "streamdek fuse media hub unified live vod home screen rows spotlight hero synopsis continue watching streaming networks network cards branded logo ambient glow background " +
    "layout density relaxed compact spacing card size smaller bigger tighter fit more new episodes row hide show wide cards notifications reminders upcoming before release"
  SettingsRoute.HomeLayout -> "layout rows reorder drag order arrange home catalog sections which rows mode by source mixed group interleave"
  SettingsRoute.TitlePages -> "title detail page style layout trailer autoplay season tabs episode artwork blur spoiler ratings trailer cache clear schedule stale"
  SettingsRoute.Ratings -> "rating ratings imdb tmdb rotten tomatoes metacritic mdblist badge score"
  SettingsRoute.LiveTv -> "live tv channel channels iptv category categories group landscape cards favourite favorite drawer progress bar"
  SettingsRoute.Addons -> "addon add-on catalog channel provider install configure source manifest stremio"
  SettingsRoute.Plugins -> "plugin source provider repository javascript cloudstream cs3 extension collection scraper"
  SettingsRoute.M3uPlaylists -> "m3u m3u8 iptv playlist url link xtream provider channels live vod refresh import"
  SettingsRoute.Debrid -> "premium debrid real debrid alldebrid premiumize torbox debrid-link deepbrid account cached api key keys cloud sync store device only"
  SettingsRoute.ContentServices -> "content services tmdb mdblist introdb theintrodb api key keys metadata artwork posters ratings timing intro recap credits outro enrichment own key personal key device only save to streamdek account credential"
  SettingsRoute.PeerToPeer -> "peer to peer p2p torrent magnet seed cache storage engine background service"
  SettingsRoute.SyncServices -> "sync services tracking tracker trakt simkl mdblist scrobble watchlist history connect cellular mobile data"
  SettingsRoute.Trakt -> "trakt scrobble watchlist history sync"
  SettingsRoute.Simkl -> "simkl tracking scrobble watchlist sync connect"
  SettingsRoute.Punchplay -> "punchplay tracking scrobble watchlist sync connect continue watching"
  SettingsRoute.Mdblist -> "mdblist list ratings access key tracking sync connect"
  SettingsRoute.ConnectTv -> "tv television pair pairing code connect cast handoff"
  SettingsRoute.Network -> "network dns doh dns over https privacy resolver cloudflare google adguard quad9 custom"
  SettingsRoute.Account -> "account sign in sign out email sync services subscription"
  SettingsRoute.Profiles -> "profile switch kids pin default avatar family"
  SettingsRoute.AppUpdates -> "update version apk install release changelog about"
  SettingsRoute.BackupRestore -> "backup back up restore export import save file transfer move new phone device reinstall migrate recovery undo"
}
