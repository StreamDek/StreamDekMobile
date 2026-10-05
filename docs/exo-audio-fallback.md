# Media3 playing without audio, and the fallback to mpv

Investigated October 2026, after roughly eight in ten Media3 starts on mobile ended in the automatic
hand-over to mpv for want of audio.

## What was wrong

**Release builds shipped without working software audio decoders.** This is a StreamDek build
configuration fault (cause G below), not an incompatibility in the streams.

- Media3 decodes AC-3, E-AC-3, DTS and TrueHD on a phone through the bundled FFmpeg extension
  (`libs/lib-decoder-ffmpeg-release.aar`). Most phones have no decoder of their own for these, and
  they are the audio most downloaded films and episodes carry.
- The extension's native library looks up `FfmpegAudioDecoder.growOutputBuffer` by name in
  `JNI_OnLoad` and returns an error if it is missing, which makes the library fail to load.
- Nothing in Java calls that method, so R8 removed it. The upstream module ships a consumer rule
  that keeps it; the AAR in `libs/` carries no consumer rules, and `proguard-rules.pro` had none for
  it. The release `usage.txt` (the list of what R8 removed) names the method for both apps.
- With the library unloaded, `FfmpegAudioRenderer` reports every format unsupported. A release
  whose audio is E-AC-3 then has a track Media3 can see and cannot play. `ExoPlaybackView` left
  unsupported tracks out of the list it reported, the player read the empty list as "no audio
  detected" 3.5 seconds after start, and switched to mpv.
- The sources that stayed on Media3 were the ones with AAC (or another codec the phone decodes
  itself). That split is the 80/20.

Debug builds are not minified, so the fault does not appear in them.

The same rule was missing on the television app. It is less visible there because most television
boxes decode or pass through Dolby audio themselves.

### How this was established, and what is not yet confirmed

Established from the code and build outputs: the removed method in `usage.txt`, the native
library's own strings (`JNI_OnLoad: GetMethodID failed`), the upstream `ffmpeg_jni.cc` and its
ProGuard rule at Media3 1.8.0, and the path from an unsupported track to the engine switch.

Not confirmed on a device. To confirm on a build from before the fix:

    adb logcat -s ffmpeg_jni LibraryLoader StreamDekExoPlayer StreamDekPlayer

`JNI_OnLoad: GetMethodID failed` and `Failed to load ffmpegJNI` appear the first time a video opens.
After the fix the same log shows `Software audio decoders loaded: FFmpeg ...`.

## The fix

`proguard-rules.pro` (mobile and television) keeps the FFmpeg extension's classes and the method
the native library needs.

## What else the audit found

| Area | Finding |
| --- | --- |
| Renderers | `DefaultRenderersFactory` with extension renderers on (device decoder first, FFmpeg second) and decoder fallback enabled. Correct. |
| Track selection | Default parameters; tunnelling only when the viewer turns it on. Nothing disables audio. |
| Languages | `applyLanguagePreferences()` ran before the new player was assigned, so a new player started without the viewer's audio and subtitle languages and had them applied after the track list came back. Fixed: applied to the player being built. Not a cause of silence. |
| HLS | Chunkless preparation is Media3's default. A manifest whose `CODECS` names only video hides audio muxed into the segments. Now recovered in place (below). |
| Audio focus, attributes | Handled by the player screen, not by Media3. Not a cause. |
| Offload, passthrough | Offload is not enabled. Passthrough is left to Media3, which uses it only when the output reports support. |
| Data source | One HTTP factory with the request headers and a per-player cookie jar. Not a cause. |
| Engine routing and fallback | The recent libVLC and routing work did not introduce this; the "no audio" check it uses is older and was reporting the fault above correctly, only without saying what it was. |

## Telling failures apart

`ExoAudioDiagnostics.kt` reads one snapshot of the player (every audio track including the
unplayable ones, what is selected, which decoder started, decoder and output errors, how many audio
buffers and video frames have been rendered, whether the software decoders loaded) and names the
first stage of the pipeline that is wrong.

| Code | Cause | Meaning |
| --- | --- | --- |
| `EXO_AUDIO_NO_TRACK` | A, or F for HLS/DASH | No audio track found, or the manifest declares none. |
| `EXO_AUDIO_UNSUPPORTED_CODEC` | E, or G | No decoder for any track. G when the bundled decoders cover the codec and did not load. |
| `EXO_AUDIO_TRACK_SELECTION_FAILED` | B, or G | A playable track exists and none is selected. G when audio is switched off in the parameters. |
| `EXO_AUDIO_DECODER_INIT_FAILED` | C, or H | The selected track's decoder failed. H when it is the device maker's decoder. |
| `EXO_AUDIO_OUTPUT_FAILED` | D | AudioTrack initialisation or write failed. |
| `EXO_AUDIO_STALLED` | D | Picture advancing, a track selected, nothing played. |
| `EXO_PLAYBACK_FATAL_ERROR` | - | A fatal error that is not an audio fault. |

One line is logged under `StreamDekExoPlayer` when the check runs, for example:

    EXO_AUDIO_UNSUPPORTED_CODEC cause=G (StreamDek configuration or implementation) detail="bundled software decoders did not load; no decoder for eac3/6ch/48000Hz/en/unsupported-codec" container=x-matroska video=hevc/hvc1.2.4.L153 videoDecoder=c2.qti.hevc.decoder audioTracks=[eac3/6ch/48000Hz/en/unsupported-codec] audioDecoder=none softwareDecoders=NOT-LOADED audioBuffers=0 videoFrames=92

The engine switch logs the same code under `StreamDekPlayer`. A healthy start logs `EXO_AUDIO_OK`.
The audio tracks of every source are logged once as they are discovered.

## Recovery inside Media3 before another engine

Each is tried once per source, then the audio is looked at again four seconds later.

| Finding | Recovery |
| --- | --- |
| Track not selected | Audio re-enabled, overrides cleared, the first playable track chosen. |
| Decoder failed | Another playable track of the same source; then the renderers prepared again. |
| Output failed or stalled | The audio renderer rebuilt (prepare after a fatal error; audio off and on while playing). |
| HLS with no audio | The source opened again with chunkless preparation off, so its segments are read. |
| Unsupported codec, no track in a file | None. This is what the hand-over to mpv is for, and it is unchanged. |

## Not done

- Nothing here has been built or run on a device.
- The same resolved URL was not compared across Media3, mpv and libVLC on a device; the diagnosis
  above did not need it, and it cannot be done from the source alone.
- Fallback reasons go to the log only. The telemetry taxonomy has no field for them.
- The television app got the build fix only, not the diagnostics.
