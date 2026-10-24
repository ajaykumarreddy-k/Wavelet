<div align="center">

<br/>
<br/>

<img src="Logo.png" alt="Wavelet app icon" width="200" />

# Wavelet

### Aesthetic YouTube Music Client · Custom-Fit Edition

[![Release](https://img.shields.io/github/v/release/ajaykumarreddy-k/Wavelet?color=blue&label=Latest%20Release)](https://github.com/ajaykumarreddy-k/Wavelet/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

[**Downloads & Releases**](#download--releases) · [**Features**](#features) · [**About Fork**](#about-this-fork) · [**Contributing**](#contributing) · [**Disclaimer**](#disclaimer)

</div>

> [!NOTE]
> ### 🎵 Wavelet — Personal Fork & Custom-Fit Revamp
> <a id="about-this-fork"></a>
> **Wavelet** is a customized personal fork of **BitChord**, an open-source YouTube Music client.
>
> - **App Name & Branding:** Named **Wavelet** — a custom fit built for my personal music listening needs.
> - **Feature Highlight:** Supports importing any YouTube or YouTube Music playlist directly into local device storage using lazy background fetching, so listening starts instantly without waiting for hundreds of tracks to load.
> - **Original Repository:** [kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord) created by **Kushagra Singh** ([@kushagrasinghx](https://github.com/kushagrasinghx)). Full credits and appreciation go to the original creator and contributors.
> - **Maintained & Revamped by:** **Ajay Kumar Reddy Krishnareddy Gari** ([@ajaykumarreddy-k](https://github.com/ajaykumarreddy-k)).
> - **Why this fork exists:** I am making personal changes and revamping this app with UI and UX adjustments tailored specifically for my own daily use case. I am doing this for my own sake. If someone likes these changes, feel free to use it; if not, feel free to use the upstream official release at [kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord).

> [!IMPORTANT]
> BitChord is not affiliated with, endorsed by, or connected to YouTube or Google in any way. Use it at your own discretion.

---

<div align="center">

<h1><a id="features"></a>Features</h1>

<table>
  <tr>
    <td width="50%" valign="top">

#### Playback
- **Search, browse and play** anything available on YouTube Music.
- **Hi-Res lossless audio** — FLAC/ALAC from a configured module source, with YouTube Music as fallback.
- **Gapless playback with true crossfade**, adjustable 0–12s.
- **Automix [Beta]** — DJ-style transitions with beat-matching and tempo-stretching.
- **Offline downloads** — save tracks with embedded metadata.
- **Local music library** integration.
- **Background playback** via a proper foreground media session.
- **Apple-like lyrics animation** — Fluid, synchronized lyric presentation.

#### Integrations & Scrobbling
- **Import YouTube & YouTube Music Playlists to Local** — Paste any playlist link/ID to save it fully on device; uses lazy loading so playback is instant while remaining tracks load in the background.
- **Discord Rich Presence** — in-app login, live track/artist/album and progress.
- **Scrobbling** to Last.fm and ListenBrainz.
- **Pluggable sources** — add, edit, test and health-check module sources.

    </td>
    <td width="50%" valign="top">

#### Experience
- **Animated album canvas** — motion artwork on the now-playing screen.
- **Word-synced lyrics** — word/syllable-level highlighting from multiple sources.
- **Lyrics providers** — Support for word-synced lyrics from multiple integrated providers (LRCLIB, Musixmatch, Genius, KuGou, and community sources).
- **Dynamic, artwork-driven theming** — Material palette extracted from album art.
- **Frosted-glass UI** — Telegram-style translucent bars via Haze, Material 3 theming.

#### Controls & Tweaks
- **Per-network audio quality** — separate quality ceilings for Wi-Fi and mobile data.
- **Playback speed control** (0.5×–2.0×) and **skip silence**.
- **Sleep timer** — fixed presets or "stop after this track".
- **System equalizer** integration.
- **Stats for nerds** — codec, bit depth, sample rate, and more on the now-playing screen.

    </td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="download--releases"></a>Downloads & Releases</h1>

<p>Get the latest pre-built APK packages directly from GitHub Releases:</p>

[**🚀 Download Wavelet v1.8.1 (Latest Release)**](https://github.com/ajaykumarreddy-k/Wavelet/releases/tag/v1.8.1)

</div>

### 📦 Available Packages

| Package | Description | Target |
| :--- | :--- | :--- |
| **[`Wavelet-v1.8.1_arm64-v8a.apk`](https://github.com/ajaykumarreddy-k/Wavelet/releases/download/v1.8.1/Wavelet-v1.8.1_arm64-v8a.apk)** | High-performance optimized build (ARM 64-bit) | Most modern Android phones & tablets (Recommended) |
| **[`Wavelet-v1.8.1_armeabi-v7a.apk`](https://github.com/ajaykumarreddy-k/Wavelet/releases/download/v1.8.1/Wavelet-v1.8.1_armeabi-v7a.apk)** | 32-bit legacy ARM build | Older Android devices |
| **[`Wavelet-v1.8.1-universal-debug.apk`](https://github.com/ajaykumarreddy-k/Wavelet/releases/download/v1.8.1/Wavelet-v1.8.1-universal-debug.apk)** | Universal multi-architecture build | Any Android device / Emulators |
| **[`WaveletWear-v1.8.1.apk`](https://github.com/ajaykumarreddy-k/Wavelet/releases/download/v1.8.1/WaveletWear-v1.8.1.apk)** | Wear OS companion app | Wear OS Smartwatches |

> [!TIP]
> **Which one should I install?** If you are using a modern smartphone, download **`Wavelet-v1.8.1_arm64-v8a.apk`**. Future releases can be automatically downloaded and installed right from inside the app!

---

<div align="center">

<h1><a id="download--build"></a>Building from Source</h1>

</div>

### Building & Running

You can also build and run the app directly from source:

#### Android
```bash
./gradlew assembleDevDebug
```
Windows (PowerShell):
```powershell
.\gradlew.bat assembleDevDebug
```

#### Desktop (Windows, macOS, Linux)
Run the desktop app directly:
```powershell
.\gradlew.bat :desktopApp:run
```
For packaging and platform setup, see [DESKTOP.md](DESKTOP.md).

---

<div align="center">

<h1><a id="contributing"></a>Contributing & Credits</h1>

This repository is maintained by [**Ajay Kumar Reddy Krishnareddy Gari**](https://github.com/ajaykumarreddy-k) as a personal fork focused on UI/UX revamps and optimizations.

Original project architecture and creation credits belong to [**Kushagra Singh**](https://github.com/kushagrasinghx) ([kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord)).

[**Contributing Guide**](CONTRIBUTING.md) · [**Code of Conduct**](CODE_OF_CONDUCT.md) · [**Maintainers**](MAINTAINERS.md) · [**Additional Docs**](ADDITIONAL.md)

</div>

---

<div align="center">

<h1><a id="disclaimer"></a>Disclaimer & Legal Notice</h1>

BitChord is an independent, community-driven third-party audio player and client. It is **not** associated with Google LLC, YouTube Music, Deezer, Telegram, or any of their parent companies.

* **No Media Hosting:** BitChord does not host, upload, or store copyrighted music files. It operates strictly as an interface to scan local device storage or stream media directly from public, public-facing, or user-authenticated APIs.
* **Fair Use & API Usage:** This software is created solely for personal research, educational, and fair-use purposes. The user is entirely responsible for ensuring their usage aligns with their local copyright laws and YouTube Terms of Service.
* **No Ad-Blocking Guarantee:** While BitChord focuses on providing a clean listening environment, it does not guarantee permanent bypasses or modifications to commercial third-party platform conditions.
* **Copyleft:** BitChord is free software under the GPLv3. The license does not let anyone forbid others from selling or redistributing copies, but any distribution must come with the Corresponding Source under the same license.

</div>

---

<div align="center">

<h1><a id="license"></a>License</h1>

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See the [LICENSE](LICENSE) file for details.

</div>
