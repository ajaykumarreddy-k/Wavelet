# 🎵 Wavelet Features

A comprehensive guide to all capabilities, design principles, and integrations in Wavelet.

---

## 🎧 1. Core Audio Playback & Streaming
- **YouTube Music Engine**: High-fidelity audio streaming powered by NewPipeExtractor with adaptive bitrates.
- **Background Playback & MediaSession**: Complete Android `MediaSessionService` support with lockscreen controls, notification actions, and headset key listeners.
- **Smart Fallback & Auto-Skip Recovery**: Automatically recovers or switches tracks if an audio stream becomes unavailable.
- **Audio Effects & Equalizer**: Native dynamic audio effects, volume normalization, and loudness enhancements.
- **Synchronized Lyrics**: Real-time timed lyrics with optional multi-language automatic translations.

---

## 🟢 2. Spotify Integration (Read-Only)
- **Direct In-App Sign-In**:
  - Connect your standard Spotify account directly via an in-app browser window (`accounts.spotify.com/login`).
  - No Spotify Developer Account, Client ID, or custom redirect URI required.
- **High-Speed GraphQL Pathfinder Engine**:
  - Direct integration with Spotify's web player GraphQL endpoints (`api-partner.spotify.com/pathfinder/v2/query`).
  - Completely bypasses REST API rate limiting (HTTP 429) and Developer Mode limitations.
- **Progressive Stream Loading**:
  - Loads large playlists progressively — the first 100 tracks display in under 200ms while remaining tracks stream smoothly in the background.
- **Instant In-Memory Caching**:
  - Playlists and tracklists are cached in memory (10-minute TTL) for zero-latency (0ms) instant navigation between screens.
- **Liked Songs & Custom Playlists**:
  - Pinned "Liked Songs" collection offered directly at the top of your Spotify library.
  - View all personal, collaborative, and followed playlists.
- **One-Tap Playlist Import**:
  - Tap **"Import to Wavelet Playlists"** to save any Spotify playlist or Liked Songs collection into Wavelet's local library for offline browsing and streaming.
- **Spotify Canvas**:
  - Displays original vertical looping video Canvas animations for supported tracks.
- **Strict Read-Only Architecture**:
  - Zero modifications to your Spotify library, no playlist modifications, and no audio playback through Spotify.

---

## 🎮 3. Discord Rich Presence (RPC)
- **Real-Time Now Playing**:
  - Broadcasts your currently playing track, artist, album, album art, and live progress to Discord.
- **Intelligent Priority Arbiter**:
  - Wavelet local playback strictly takes precedence over Spotify playback.
- **Low-Overhead Background Polling**:
  - Process lifecycle-aware polling with exponential backoff and rate-limit safety.

---

## 📚 4. Library & Playlist Management
- **Local Playlists**:
  - Create, edit, and organize custom playlists stored securely in app storage.
- **Import from Spotify**:
  - Dedicated "Import from Spotify" tile in the Library playlist section for quick access.
- **Offline Cache**:
  - Automatically caches recently played tracks for reliable playback in poor network conditions.
- **Backup & Restore**:
  - Export and import your entire library, settings, and playback history in JSON format.

---

## 📱 5. Multi-Device Ecosystem
- **Android Phone & Tablet**:
  - Modern Material You (Material 3) user interface with dynamic backdrops, gesture controls, and responsive layouts.
- **Android TV**:
  - Dedicated 10-foot UI with Leanback navigation, D-pad controls, and high-resolution backdrop art.
- **Wear OS Companion**:
  - Dedicated smartwatch app with remote playback controls, volume slider, and track info.
- **Desktop (macOS / Linux / Windows)**:
  - Kotlin Multiplatform Compose Desktop application sharing core playback and library logic.

---

## 🔄 6. Seamless In-App Updates (Sideload OTA)
- **Automatic GitHub Release Polling**:
  - Detects new versions published to GitHub Releases without needing the Play Store.
- **In-App APK Installer**:
  - Automatically downloads the matching architecture APK (`arm64-v8a`, `armeabi-v7a`, or Universal) directly into cache and launches the system installer.
- **Manual Check**:
  - Available anytime in **Settings → Downloads → Update the app**.

