# Spotui: Fork Features & Differences

This document outlines the custom features, improvements, and differences introduced in this fork
compared to the main Spotui repository.

---

## 🚀 Release v1.4.5

### 💾 Lossless & Stream Controls

* **Universal Downloads:** Lossless downloads now work directly from Amazon, Qobuz, Deezer, and
  SoundCloud based on provider priority.
* **Provider Toggles:** Enable or disable individual audio providers using checkboxes in Settings (
  YouTube Music remains fallback).
* **Cache Resets:** Per-song "Invalidate cache" player menu option and global cache reset for stream
  overrides.

### 🛠 Playback Polish & Fixes

* **Crossfade Sync:** Fixed UI flickering and premature metadata/artwork updates during crossfades.
* **Accurate Resolution Logs:** Trace logs report whether playback originates from memory, disk
  cache, or offline downloads.
* **Gesture & Engine Fixes:** Resolved swipe-to-skip artwork desync, YouTube cache poisoning, replay
  loops, and prefetch network restarts.

---

## 🚀 Release v1.4.4

### 🎧 Lossless Ecosystem & Deezer

* **Native Provider Integration:** Direct stream resolution for Amazon Music, Qobuz, SoundCloud, and
  Deezer.
* **Custom Provider Priority:** Rank search order for audio providers in Settings.
* **Native Deezer Login:** Secure ARL login support for direct FLAC/HQ streaming with automated
  prompts.
* **Live Bitrate Display:** Parses FLAC frames in real time to display live stream bitrates on
  screen.
* **Resilient Playback:** Instant connection recovery at the exact millisecond during network drops.

### 📱 Library, Home & Local Files

* **Local Music Import:** Import device audio files with full metadata and embedded artwork parsing.
* **Home Screen Filters & Feed:** Added category filter pills (Music, Podcasts, Audiobooks, Followed
  Artists) and a "Latest Releases" feed.
* **Fast Scrollbars:** Spotify-style draggable scrollbar thumb with floating position indicators
  across long lists.
* **Global Search & Sorting:** Inline search and multi-criteria sorting across Albums, Liked Songs,
  History, Podcasts, and Library.

### ⚙️ Player UI & System Integration

* **Audio Device Switcher:** Direct output selector (Speaker, Bluetooth, Wired) accessible from the
  player screen or Settings.
* **Mini-Player Gestures:** Swipe left or right on the mini-player to quickly skip tracks.
* **Quality-Aware Caching:** Hot-swaps playback quality immediately upon changing settings without
  stopping playback.
* **Trace Logs & Bot Detection:** Copyable provider attempt logs and a manual "Reset Session" button
  for YouTube bot blocks.

---

## 🚀 Release v1.4.3

### 🎧 Audio Engine

* **Concurrent Search:** Searches Tidal, Qobuz, Amazon, and Deezer simultaneously for instant
  lossless playback.
* **Smart Fallbacks:** Automatically switches providers if one fails or hits rate limits.
* **Custom Timeouts:** Configurable lossless timeouts for Wi-Fi, Cellular, and Downloads.
* **Stream Diagnostics:** Tap the source badge to view detailed audio routing resolution.
* **Provider Dashboard:** View real-time provider status and cooldown timers in Settings.

### 📚 Library & Offline

* **Quick Filters:** Filter library by Playlists, Albums, Artists, or Offline content.
* **Smarter Deduplication:** Improved logic for merging duplicate tracks across all sources.
* **Rename Playlists:** Edit custom local playlist names directly from the header.
* **Persistent Cover Art:** Locally caches artwork for offline display and system notifications.
* **Canvas Optimization:** Automatically disables Spotify Canvas for offline tracks.

### 🎵 UI & Experience

* **Explicit Tags:** Global visibility for explicit (🅴) badges across all lists and players.
* **Accurate Play States:** Play buttons strictly reflect the actively playing list.
* **Gesture Fixes:** Resolved duplicate swipe-to-play-next triggers.
* **Smoother Search:** Instant keyboard focus on tab tap; clean clearing via system back.
* **Visual Polish:** Modernized icons and enforced light status bar for dark UI legibility.

---

## 🚀 Release v1.4.2

### 🗂️ Local Playlists & Linking

* **On-Device Playlists:** Create and manage playlists locally to bypass API limits.
* **Backup & Restore:** Save and restore local playlists, likes, and preferences to storage.
* **Structured Library:** Offline albums and playlists retain original folder layouts.
* **Deep Linking:** Natively intercepts and opens Spotify URIs and web links.
* **Guided Setup:** Prompt for Android 12+ users to set the app as the default link handler.

### 🎨 UI & Stability

* **Edge-to-Edge:** Immersive UI with transparent system bars.
* **Lyrics Redesign:** Smoother gradients, reduced shadows, and increased text contrast.
* **Playback Stability:** Eliminated UI flickering during rapid toggles and buffering.
* **Smart Navigation:** Bottom bar remembers root tabs and prevents back-history clutter.

---

## 🚀 Release v1.4.1

### 🚗 Android Auto & Controls

* **Android Auto Support:** Projects the in-app playlist timeline to car displays.
* **In-Car Search:** Search and view results directly from Android Auto.
* **Automation:** Exposed Next/Previous controls to apps like Tasker and MacroDroid.
* **Sync & Timeouts:** Fixed display sync issues and increased browser timeouts.

### 🎵 Gestures & Updates

* **Swipe Action:** Swiping now sets items to "Play next" instead of adding to the bottom.
* **Auto-Play:** Optional setting to resume the last played track on app launch.
* **Rich Updates:** In-app prompts render zoomable images and HTML/URL formatting.

---

## 🚀 Release v1.4.0

### 🌐 Offline Lyrics Translation

* **On-Device Translation:** Translate lyrics into 50+ languages securely via Google ML Kit.
* **Inline Auto-Save:** Translations save locally and appear below original lyrics.
* **Floating Controls:** Manage translations and languages via a new lyrics screen panel.
* **Offline Availability:** Lyrics auto-save to disk when downloading a song.

### 🎵 Queue & Profiles

* **Swipe-to-Queue:** Global left-to-right swipe to add any track to the queue.
* **Batch Queueing:** "Add all to queue" buttons on Playlist, Album, and Liked headers.
* **Strict Repeat:** "Repeat All" loops the exact queue without appending radio tracks.
* **Artist Navigation:** Tap artist names for profiles or to view collaboration sheets.
* **Quick Creation:** Long-press "Like" to instantly create public or private playlists.

### ⚡ Performance

* **Parallel Resolution:** Resolves lossless and standard streams simultaneously.
* **Search Caching:** Caches YouTube video IDs for faster subsequent loads.
* **Live Sleep Timer:** Real-time countdown displayed in the sleep timer menu.

---

## 🚀 Release v1.3.8

### 🔍 Alternative Streams

* **In-App Search:** Find alternative YouTube streams directly within the player.
* **Audio Previews:** Transient player to preview streams without disrupting the main queue.
* **Rich Cards:** View video thumbnails and external links in the stream editor.
* **Streamlined UI:** Editor auto-dismisses on selection; back button stops active previews.

### 📱 UI & Notifications

* **Swipe-Up Feedback:** Progressive fading and spring animations for mini player gestures.
* **Compact Player:** Reduced mini player height and optimized seekbar touch targets.
* **Persistent Repeat:** Repeat modes (Off, All, One) persist across app restarts.
* **Sync Fixes:** Immediate system notification updates for play/pause and repeat states.
* **Scroll Fix:** Resolved accidental player dismissals from the lyrics view.

---

## 🚀 Release v1.3.7

### 🔔 Notifications & Routing

* **Notification Repeat:** Functional repeat button (Off/One/All) in system notifications.
* **Smart Routing:** Checks SpotiFLAC health before probing; falls back to YouTube instantly.
* **UI Fixes:** Stabilized mini-player buffering spinner and fixed navigation bar clipping.

---

## 🚀 Release v1.3.6

### 🎨 Seekbar & Timestamps

* **Custom Seekbar:** Replaced broken Material3 slider with a custom Canvas-drawn track.
* **Timestamp Fix:** Prevented negative timestamps when dragging before track load finishes.

---

## 🚀 Release v1.3.5

### ⚙️ Under the Hood

* **Persistent Queue:** Restores the full playback session across app restarts.
* **Modern Build:** Upgraded to SDK 37, Gradle 9.6.1, and Kotlin 2.4.0.
* **Codebase Cleanup:** Resolved Kotlin warnings and removed deprecated Media3 calls.

---

## 🚀 Release v1.3.4

### 🔋 Background & History

* **Battery Bypass:** Setting to request exemption from Android background restrictions.
* **Device Guides:** Specific instructions for MIUI, One UI, EMUI, etc., to disable restrictions.
* **History Redesign:** Tap-to-replay history tracks with a new gradient stats dashboard.
* **Visual Polish:** Progress bars, rankings, and circular thumbnails for top artists/tracks.

### ⚡ Updates & Stability

* **Configurable Source:** Point the update checker to custom GitHub repository URLs.
* **Markdown Notes:** Fully renders GitHub-flavored Markdown in update dialogs.
* **Background Safety:** Timeouts added to PoToken (12s) and signatures (10s) to prevent hangs.

---

## 🚀 Release v1.3.3

### ⚡ Performance & Gestures

* **Instant Playback:** Persistent caching skips lookups for recently played/searched tracks.
* **Seamless Transitions:** Background prefetching and pre-warmed playback pipeline.
* **Tri-State Repeat:** Full support for Repeat Off, All, and One with visual indicators.
* **Fluid Animations:** Spring/tween swipe gestures and full edge-to-edge player rendering.

### 🛡️ Reliability

* **Error Badges:** Explicit red error badges replacing silent load failures.
* **Background Stability:** Placeholder media states resolve background crash issues.
* **Battery Optimized:** UI polling slows down when paused to conserve battery life.

---

## 🚀 Release v1.3.2

### 💾 Offline & Playlists

* **Offline Access:** Home screen shortcut for unauthenticated or offline access.
* **Local Search:** Dedicated search bars inside Downloads and specific Playlists.
* **Advanced Sorting:** Sort by Date, Title, or Artist with persistent preferences per playlist.
* **Safe Deletion:** Confirmation dialogs added when clearing downloaded tracks.

### 🎨 System Integration

* **Themed Icons:** Monochrome icon support for Android 13+ material themes.
* **Fluid Navigation:** Swipe-up to expand and slide-to-dismiss player gestures.
* **Notification Exit:** Dedicated "Close" button to cleanly exit the app from notifications.
* **Concurrent Installs:** Unique application IDs allow Debug and Release parallel installations.