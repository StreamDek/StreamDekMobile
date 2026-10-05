# libVLC playback engine (Mobile)

libVLC is a third playback engine beside Media3 (ExoPlayer) and mpv, selectable in Settings >
Player > Default player and in the player's Engine panel.

**Status: not yet built or run on a device.** The code was written without an Android SDK. What
was checked is listed under "How this was verified"; what was not is under "Still to test".

## What it was checked against

- libVLC Android bindings **3.7.5** (source of `MediaPlayer`, `Media`, `IMedia`, `LibVLC`,
  `HWDecoderUtil` and the JNI layer) and **4.0.0-eap29**.
- VLC **3.0.x** and **master (4.0)** sources: the HTTP access module, the text renderer, the
  subtitle unit, the MediaCodec decoder, the MP4 demuxer.
- VLC for Android's own `VLCOptions.kt`, for how the reference app configures libVLC.

The dependency is `org.videolan.android:libvlc-all:3.7.7`, the current 3.x release and the one
VLC for Android builds with.

### Why not libVLC 4

4.0 is still a preview series (`eap`). Its bindings replace the integer track API with string ids
(`getTracks`, `selectTrack`), which would fix the one weakness noted under track details below.
For every other limitation here it changes nothing: the HTTP module has the same two options, the
subtitle renderer is still configured at creation, and there is still no buffered-range call.
`libvlc_video_set_spu_text_scale` exists in the 4.0 C API but the Android bindings do not expose
it. Moving a shipping app to a preview for one improvement is not worth it yet.

## Capability matrix

| Area | Status | Detail |
| --- | --- | --- |
| Custom HTTP headers | **Still unsupported** - routed around | libVLC's HTTP access reads `http-user-agent` and `http-referrer` and nothing else, in 3.0 and 4.0. No option exists for `Authorization`, `Origin`, a preset `Cookie` or any other header. A source carrying such headers is **not tried on libVLC**: it plays on the Auto path (Media3, then mpv) and the viewer is told why. |
| ClearKey | **Still unsupported** - routed around | Same routing; only Media3 decrypts these. |
| Dynamic subtitle styling | **Newly implemented**, with a caveat | Every property (size, colour, opacity, background, outline, bold, position) is read once when libVLC's text renderer or subtitle unit is created; none can be changed on a running one through a supported call. A change during playback is now applied by reopening the same source at the same position about a second after the viewer stops adjusting, keeping the audio and subtitle selection. Playback pauses briefly. Shadow and font are not exposed by StreamDek's settings and are left at libVLC's defaults. |
| Buffered progress | **Still unsupported** | libVLC gives a fill percentage while rebuffering and byte counters, never a time or a range. Nothing is drawn rather than something invented. |
| Rich stream information | **Newly implemented** (partly) | Now shown: codec, profile and level, bit depth where the profile settles it, resolution, frame rate, video bitrate; audio codec, channels, sample rate, bitrate, language; transfer rate, current stream bitrate, frames decoded and dropped. **Not available from libVLC:** HDR format, Dolby Vision profile, decoder name, hardware-vs-software, passthrough state, container, buffer length. Sampled once a second and only while the info panel is open. |
| Track language metadata | **Newly implemented** | Language, title and codec come from libVLC's own track records; the `[Language]` in the display name is only the fallback. One limit: the 3.x bindings cache the first track list they return, so records are read once the picture is up, and a track added later (an external subtitle file) falls back to its name. |
| Dolby Vision | **Device-dependent** | VLC's MediaCodec decoder has no Dolby Vision handling in 3.0 or 4.0: it decodes the HEVC/AVC layer and applies no RPU. Profile 8 and profile 7 therefore play as their HDR10 base layer. Profile 5 has no compatible base layer and would show wrong colours. **Newly implemented:** an MP4 with a `dvhe`/`dvh1`/`dvav`/`dva1` sample entry (profile 5, and single-track profile 7) is handed to Media3, which uses the device's Dolby Vision decoder and already passes profile 7 to mpv. A Dolby Vision **MKV** carries no marker libVLC exposes, so it is not detected and plays as HDR10. |
| Automatic A/V fallback | **Newly implemented** | See "Engine routing". |
| Pinch zoom | Supported (view scaling) | libVLC's own scale is pixels-per-pixel with no "relative to fit", so the video view is scaled instead. The compositor does it: no frame copy, aspect unchanged, hardware decoding unaffected. |

## Engine routing

All of it is in `PlaybackEngineRouting.kt` and unit-tested.

- **Auto** is unchanged: Media3 first, one hand-over to mpv.
- **libVLC chosen, source it cannot open** (headers, ClearKey): never tried on libVLC; goes to
  the Auto path with a notice.
- **libVLC fails before it has played** (open error, unsupported decoder, demux failure): the next
  engine takes over, with a notice.
- **libVLC plays half a source**: about eight seconds in, libVLC's own counters are read twice. An
  audio track that is selected but has decoded nothing while pictures are shown - or the reverse -
  hands the source to the next engine. Not applied to live channels, whose audio can start late.
- **Order after libVLC:** Media3, then mpv. libVLC is never the engine taking over: it shares
  FFmpeg with mpv and rarely rescues what mpv could not play.
- **No loops:** each source keeps a trail of the engines it has been on and which failed
  (`libVLC (failed) → ExoPlayer → mpv` in the log). A failed engine is not handed the same source
  again, and that now also applies to the existing Media3/mpv hand-overs.
- A manual switch in the Engine panel is always honoured.

## Facts for the routing layer

libVLC should not be chosen for a source when any of these is true:

1. It needs a request header other than `User-Agent` or `Referer` (`headersLibVlcCannotSend`).
2. It is ClearKey- or otherwise DRM-protected.
3. It is an MP4 with a Dolby Vision sample entry (detected only after opening).

And it will be weaker than the alternatives when: the viewer relies on the buffered-range display;
subtitle appearance is being adjusted often; or the info panel's decoder and HDR lines matter.

## Where it lives

- `VlcPlaybackView.kt` - the engine.
- `LibVlcSupport.kt` - what libVLC can and cannot do, as pure functions (headers, Dolby Vision marker, codec/profile names, cache sizes, half-a-stream check).
- `PlaybackEngineRouting.kt` - which engine a source starts on and which takes over.
- `PlaybackEngineControls.kt` - the common control interface the player screen talks to.
- `NativePlayer.kt` - where those are applied.

## Battery and thermal pass

### libVLC - changed

- **Start-up buffering halved.** `network-caching` was 3 s for on-demand sources. That figure is
  latency before the first frame and after every seek, and a longer burst of radio at each start.
  Now 1.5 s for remote sources and 0.8 s for this device's loopback servers and home-network media
  servers; local files stay at libVLC's 0.3 s (the bindings would otherwise raise it to 1.5 s).
  libVLC lengthens its own delay when a stream runs late.
- **Hardware decoding confirmed, not changed.** The bindings resolve the existing setting to
  `:codec=mediacodec_ndk,all` - MediaCodec first with direct rendering to the surface
  (`mediacodec-dr` is on by default), software only when MediaCodec refuses. No frame leaves the
  GPU path. The compatibility surface uses a TextureView, which is still zero-copy.
- **No filters.** No deinterlacer, post-processing, equaliser or resampler is requested. The
  time-stretch filter is only inserted while the speed is not 1x.
- **Statistics** are on (the bindings need them for the info panel and the half-a-stream check).
  They are counters libVLC keeps as it decodes; nothing reads them unless the panel is open.
- **Leaving the app** releases the decoder, the surface and the network connection, and reopens at
  the same position on return, keeping the track selection.
- **Redundant work removed:** setters called on every recomposition no longer touch libVLC unless
  the value changed.

### mpv - changed

- **Interpolation and display-resample turned off.** On the standard surface mpv was set to
  `video-sync=display-resample` with `interpolation=yes`: the GPU drew a newly blended frame on
  every screen refresh (120 a second on a 120 Hz phone for a 24 fps film) and the audio was
  resampled to match. Frames are now shown at the video's own rate, as on the compatibility
  surface, on TV, and in the other two engines. This is the one change here that is visible:
  motion no longer has mpv's smoothing. To restore it, see `applyRenderSurfaceMode` in `MPVView.kt`.
- **Progress reports throttled.** mpv reports its position once per frame; each report was a
  duration lookup through JNI and a post to the main thread, 24-60 times a second. The screen is
  now told four times a second (and at once after a seek), which is still twice Media3's rate.

### mpv - found, not changed: the default decode path copies every frame

`hwdec=auto-safe` (the default, `HW+`) resolves to **`mediacodec-copy`**; only the `HW` setting (`hwdec=auto`) tries zero-copy `mediacodec` first. In mpv, `auto-safe` only allows decoders on its whitelist, and plain `mediacodec` is
not on it. `mediacodec-copy` decodes in hardware but copies each frame back to CPU memory and
uploads it to the GPU again - for 4K HEVC that is hundreds of megabytes a second of copying, and
the most likely reason mpv runs hotter than the other engines.

It was left alone deliberately. Zero-copy `mediacodec` hands mpv an already-converted texture, so
mpv can no longer tone-map HDR itself, and HDR and Dolby Vision fallback content - which is exactly
what the Media3 -> mpv hand-over sends here - would look washed out. The change worth measuring on
real devices is `hwdec=mediacodec,mediacodec-copy` for SDR content only.

### Already in good shape

- Stream statistics and buffered ranges are polled only while the panel or the seek bar that shows
  them is on screen, in all three engines.
- Only one engine's view exists at a time; switching engine releases the other's decoder and surface.
- mpv runs `profile=fast` with no deband, sharpening or scaling shaders.

### Not changed, worth a look

- mpv cache sizes are generous. They are bounded in seconds and follow the viewer's Buffer Ahead
  setting, so they were left as a product decision.
- Refresh-rate matching was not touched. With mpv now presenting at the video's own rate, a display
  that switches to 24/48 Hz gets exactly one frame per refresh.

## How this was verified

- `VlcPlaybackView.kt` type-checks with no errors against the real libVLC 3.7.5 Java sources (with
  a stand-in for the Android framework).
- `LibVlcSupportTest` and `PlaybackEngineRoutingTest` (14 cases) were compiled and run and pass.
- The rest of the player was syntax-checked and compared against a baseline type check; no new
  genuine errors.
- Translations are complete for all eight languages; the hard-coded string count did not rise.

## Still to test (none of this has been run)

Functional, per engine: H.264 1080p, HEVC 1080p, HEVC 4K, HDR10, Dolby Vision (P5 MP4, P7 MKV, P8),
AV1, Live TV (HLS and MPEG-TS), HLS VOD, DASH, Plex, Jellyfin, debrid/add-on sources with and
without custom headers, external subtitles, high-bitrate local streams.

For libVLC specifically:

- [ ] A header-protected source is routed to ExoPlayer with the notice, without libVLC opening it
- [ ] A source libVLC cannot decode hands over once and does not loop
- [ ] Changing subtitle size mid-playback reopens once, at the same position, same tracks
- [ ] Track languages appear and the preferred language is picked automatically
- [ ] Info panel values are plausible (transfer rate and bitrate units in particular)
- [ ] Leave and return; screen off and on
- [ ] Release build (R8) starts libVLC; APK size

Power, Media3 vs mpv vs libVLC on identical content: CPU use, decoder in use, dropped frames,
memory, battery drain over 30 minutes, temperature, start-up time, seek latency. The mpv
`mediacodec` versus `mediacodec-copy` comparison above is the measurement most likely to matter.

## Other notes

- **Telemetry** carries no engine field for any engine; logs and the info panel name the engine.
- **Synced preference:** the engine setting is stored as `VLC`. Confirm the server and web portal
  accept it; older app versions read it as Auto.
- **Licence:** libVLC is LGPL-2.1.
