# Stream Selector Logic

The scoring and selection logic for "Play Best Stream" is implemented across two files:
- [src/utils/streamSelection.ts](src/utils/streamSelection.ts) — scoring, ranking, and audio track selection
- [src/utils/streamParser.ts](src/utils/streamParser.ts) — metadata parsing (quality, codec, audio, language, size, seeds)

---

## Pre-Filters (Applied Before Scoring)

Before any score is calculated, streams are eliminated by two hard filters:

### 1. Playability Check (`isLikelyPlayableVideoStream`)
Streams are rejected (score: **-10000**) if:
- Duration is detected and is **less than 5 minutes**
- The file extension is a known non-video type (`.rar`, `.zip`, `.srt`, `.jpg`, etc.)
- A valid video extension is expected but not present

### 2. Blacklisted Codecs
Streams containing any of these codecs are rejected (score: **-5000**):
`VC-1`, `VC1`, `WMV`, `RealVideo`, `RV40`, `H.263`, `H263`

---

## Core Scoring Algorithm

Streams that pass the pre-filters are scored across 9 categories (A–I), plus final penalties.

### A. Cache & Availability (Highest Positive Impact)
- **Debrid-cached stream:** `+400 points`
- **Each additional provider caching it:** `+45 points`
- **Direct URL (no debrid resolution needed):** `+380 points`

> Both bonuses stack — a cached stream with a direct URL scores +780 base before other factors.

### B. Language (Critical Filter)
- **English or `\bENG\b`:** `+500 points` (180 base + 320 priority boost)
- **Multi / Dual audio:** `~+410 points` (82% of the English bonus)
- **Explicit non-English language detected:** `−260 points`
- **"DUBBED" or "LATINO":** `−120 points`

### C. Resolution (Weight: 2×)
Base scores before the 2× multiplier:

| Resolution | Base Score | + HDR Bonus |
|---|---|---|
| 4K / UHD / 2160p | 100 | +20 |
| 1080p / FHD | 86 | +15 |
| 720p / HD | 62 | — |
| 480p | 35 | — |
| 360p | 18 | — |
| SD | 10 | — |

### D. Source Type
| Source | Score |
|---|---|
| WEB-DL | +26 |
| WEBRip | +18 |
| BluRay | +12 |
| HDRip | +6 |
| HDTV | +3 |
| DVD | −10 |
| TeleSync | −120 |
| CAM | −200 |

### E. Audio Format (Weight: 2×)
Base scores before the 2× multiplier:

| Format | Score | Notes |
|---|---|---|
| FLAC | +100 | Lossless |
| AAC | +95 | Best mobile compatibility |
| MP3 | +90 | Max compatibility |
| Opus | +85 | Modern efficiency |
| EAC3 / E-AC3 / DD+ | +60 | |
| AC3 / Dolby Digital | +50 | |
| DTS-HD | +20 | |
| TrueHD / True-HD | +20 | |
| DTS | −90 | |
| DTS-HD MA | −140 | Hard to handle on mobile |
| DTS:X | −160 | |

**Dolby Atmos modifier:**
- Atmos + compatible Dolby core (EAC3/AC3): `+8 points`
- Atmos without compatible core: `−80 points`

### F. Video Codec & Container

**Codec:**
| Codec | Score |
|---|---|
| H.264 / H264 / AVC | +120 |
| X264 | +95 |
| H.265 / H265 / HEVC | +30 |
| X265 | +20 |
| VP9 | −10 |
| AV1 | −80 (resource heavy on mobile) |

**Container:** `.mp4` or `.mkv` each award `+50 points`.

### G. File Size
| Condition | Penalty |
|---|---|
| > 35 GB | −180 |
| > 25 GB | −120 |
| > 15 GB | −60 |
| > 8 GB | −20 |
| < 250 MB | −100 |
| < 500 MB | −65 |
| 4K file < 4 GB | −40 |
| 1080p file < 1.2 GB | −20 |

### H. Seeders (Only for non-resolvable streams)
| Seeds | Score |
|---|---|
| ≥ 5000 | +120 |
| ≥ 1000 | +90 |
| ≥ 200 | +55 |
| ≥ 50 | +20 |
| < 5 | −60 |

### I. Stability Score (Mobile Playback)
Adjusts for practical decoding reliability on mobile hardware:

| Factor | Normal Mode | Quick Start Mode |
|---|---|---|
| 1080p content | +45 | +45 |
| 4K / UHD | −90 | −170 |
| HEVC / H.265 | −55 | −115 |
| HDR (any) | −80 | −140 |
| Dolby Vision (`DV`) | −140 | −140 |
| HDR10+ | −90 | −90 |
| 10-bit content | −120 | −120 |
| Upscaled | −220 | −220 |
| REMUX | −110 | −110 |
| Atmos without Dolby core | −45 | −45 |
| File > 8 GB | 0 | −70 |
| File > 12 GB | 0 | −120 |
| File > 20 GB | −140 | −140 |

**Quick Start bonus:** Clean 1080p (non-HEVC, non-HDR, non-10bit) gains `+70`. WEB-DL below 4K gains `+35`.

### J. Final Penalties
- Contains "SAMPLE": `−250`
- Contains "CAM" or "TELESYNC": `−200`
- Session retry penalty (configurable per stream): deducted from final score

---

## Smart Audio Track Selection (`pickBestAudioTrack`)

When playback starts, the app scores all internal audio tracks and selects the best one:

1. **Language match:** Preferred language (default `en`) gets `+500`. Unknown/undefined language gets `+35`.
2. **Track metadata:** `autoSelect` flag adds `+80`; `isDefault` flag adds `+50`.
3. **Format quality:** Applies the same audio format scoring as stream selection.
4. **Penalties for special tracks:**
   - Commentary: `−300`
   - Descriptive / Audio Description / Visually Impaired: `−220`

The highest-scoring track is selected, ensuring the file defaults to a language you understand even if it contains multiple tracks.
