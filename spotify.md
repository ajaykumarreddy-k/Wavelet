# 🟢 Spotify Integration in Wavelet

This document explains the architecture, security model, and inner workings of the read-only Spotify integration in Wavelet.

---

## 📌 1. Design Principles & Scope

1. **Read-Only**: Wavelet never modifies, creates, or deletes playlists or library state on Spotify.
2. **No Audio Playback from Spotify**: Audio streaming is completely powered by Wavelet's existing audio pipeline (YouTube Music / local files). Spotify is used exclusively for metadata, library synchronization, and playlist discovery.
3. **No Developer Mode Restrictions**:
   - Spotify's developer portal restricts newly created developer apps to a maximum of 5 whitelist emails and blocks `GET /playlists/{id}/items` on unowned playlists.
   - Wavelet solves this by providing direct web session authentication without requiring user-supplied Client IDs or developer whitelists.
4. **Discord Presence Priority**: Wavelet's local playback always supersedes Spotify presence.

---

## 🏗️ 2. Architectural Overview

```
┌────────────────────────────────────────────────────────┐
│                      Wavelet App                       │
│                                                        │
│  ┌──────────────────┐            ┌──────────────────┐  │
│  │ In-App Web Login │            │ SpotifyTokenStore│  │
│  │ (WebView Login)  │──[sp_dc]──>│ (EncryptedPrefs) │  │
│  └──────────────────┘            └────────┬─────────┘  │
│                                           │            │
│  ┌──────────────────┐            ┌────────▼─────────┐  │
│  │  SpotifyToken    │<───────────┤ Web Session Auth │  │
│  │ (Offscreen Hook) │            └────────┬─────────┘  │
│  └────────┬─────────┘                     │            │
│           │ [Bearer Token]                │            │
│  ┌────────▼───────────────────────────────▼─────────┐  │
│  │             SpotifyLibrary (GraphQL Engine)       │  │
│  │  • libraryV3 (Playlists)                          │  │
│  │  • fetchLibraryTracks (Liked Songs)               │  │
│  │  • fetchPlaylist (Playlist Tracks)                │  │
│  └────────────────────────┬─────────────────────────┘  │
│                           │                            │
│  ┌────────────────────────▼─────────────────────────┐  │
│  │           SpotifyRepository & Cache              │  │
│  │  • Progressive Streaming (Page 1 in ~150-200ms)   │  │
│  │  • In-Memory Cache (0ms Instant Return)           │  │
│  └────────────────────────┬─────────────────────────┘  │
│                           │                            │
│  ┌────────────────────────▼─────────────────────────┐  │
│  │                Wavelet Local Storage             │  │
│  │             (LocalPlaylistStore JSON)            │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

---

## ⚙️ 3. How It Works (Step-by-Step)

### A. Authentication via In-App Browser
1. When the user taps **"Sign in with Spotify"** or **"Import from Spotify"**, an in-app browser window opens to `accounts.spotify.com/login`.
2. The user signs in using their standard credentials (or Google/Apple/Facebook federated sign-in).
3. The app extracts the authenticated session cookie (`sp_dc`) using Android's `CookieManager`.
4. The `sp_dc` cookie is saved securely in encrypted storage (`EncryptedSharedPreferences`).

### B. Offscreen Token Minting (`SpotifyToken`)
1. To query Spotify's web player services, a bearer token is needed.
2. `SpotifyToken` initializes an offscreen WebView with the listener's `sp_dc` cookie applied to `https://open.spotify.com/`.
3. Before the web bundle initializes, a lightweight JavaScript interface intercepts the token exchange request (`/api/token`).
4. The minted token and `Client-Token` are returned to native code and cached in memory for the duration of its 1-hour lifespan.

### C. High-Speed GraphQL Pathfinder Engine (`SpotifyLibrary`)
Instead of hitting Spotify's REST API (`api.spotify.com`), which throttles non-OAuth web tokens with **HTTP 429 (Rate Limited)** and blocks endpoints with **403 Dev Mode restrictions**, Wavelet connects directly to Spotify's web player GraphQL Pathfinder v2 query service:
- **Playlists Query (`libraryV3`)**:
  - Endpoint: `https://api-partner.spotify.com/pathfinder/v2/query`
  - Fetches the user's full playlist library, artwork, owner metadata, and collaborative flags in a single batch.
- **Liked Songs (`fetchLibraryTracks`)**:
  - Fetches the user's purple heart "Liked Songs" library collection in batches of 100 tracks.
- **Playlist Items (`fetchPlaylist`)**:
  - Fetches tracks from any playlist in the user's library (owned, collaborative, or followed) in batches of 100 tracks.

### D. Progressive Streaming & Sub-Second Loading
- Rather than waiting for thousands of tracks to finish downloading before showing anything, `SpotifyLibrary.tracks()` uses a streaming callback (`onPage`).
- The first 100 tracks are delivered to the UI in **under 200ms**.
- Subsequent pages load asynchronously in the background and append to the list seamlessly.
- An in-memory cache with a 10-minute TTL guarantees that navigating back and forth between playlists is **instantaneous (0ms)**.

### E. Importing Playlists into Wavelet
- Users can click **"Import to Wavelet Playlists"** on any playlist or Liked Songs view.
- Wavelet maps each track to a local `Song` object (`videoId = "spotify_<id>"`).
- Playlists are saved directly to `LocalPlaylistStore`, allowing them to appear immediately in the main Library and be played through Wavelet's audio resolver.

---

## 🔒 4. Safety, Privacy & Terms of Service Disclaimer

> [!WARNING]
> **Account Disclaimer**:
> Wavelet interacts with Spotify solely by reading your public and private library metadata over standard HTTPS web player endpoints that your own web browser uses.
> 
> While Wavelet never modifies your Spotify account, does not alter Spotify's files, and does not pirate or bypass Spotify audio encryption, **the developer is not responsible for any Spotify account bans, suspensions, or restrictions, though this is an extremely rare case.**
> Use of this integration is at your own discretion.

---

## ❓ 5. Frequently Asked Questions (FAQ)

#### Does this download audio directly from Spotify?
No. Spotify's audio streams are protected by Widevine DRM. Wavelet uses its native audio engine (YouTube Music) to match and play audio for imported songs.

#### Do I need Spotify Premium?
No. Free Spotify accounts can also log in and synchronize their Liked Songs and playlists.

#### How do I disconnect my Spotify account?
Open **Settings → Spotify Account** (or open the Spotify Library view) and tap **"Logout"**. This immediately wipes all session cookies, tokens, and cached library data from device storage.

