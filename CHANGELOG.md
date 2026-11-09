# Changelog

All notable changes to Jellio TV will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

What's New modal notifications and toasts are strictly reserved for big milestone
releases (`X.Y.0` where patch is 0). Small patch releases (`X.Y.Z` where patch > 0)
address hotfixes quietly without modal prompts.

## [0.85.0] - 2026-11-09

> Shared error handling with retry, resilient session loss detection, update package verification, and architectural cleanup.

### Added

- Unified error state with interactive 'Try again' button across Home, Library, Live TV, Detail, Search, Person, Profile, Feed, and Service unreachable views.
- Automatic Next Up episode prompt fallback near the end of video playback when Jellyfin credits markers are absent.
- Skip segment extraction supporting all web player marker sources.
- In-page episode card auto-focus starting directly on the current episode when viewing episode details.
- Resilient Jellyfin session loss detection that prompts clean re-authentication or direct password entry for saved profiles.
- Pre-install APK package verification ensuring file integrity, valid signature certificate, and matching package identity.
- Extended local network IP range detection for all addresses within 172.16.0.0/12.

### Changed

- Removed the OK / Enter button from selectable Quick Hub shortcuts to avoid navigation conflicts.
- Refined remote shortcut recorder to enforce long-press mode for critical keys, consume trailing key events, and reset hold states on window focus loss.
- Episode detail navigation automatically selects the active season and resets horizontal carousel focus on season switches.
- Refactored player architecture: decomposed constants, UI state models, failover overlays, and seek badges into modular composables.
- Up Next card state and sleep timers are isolated per episode rather than per stream.
- Dynamic versionCode calculation in Gradle derived directly from versionName (major * 100000 + minor * 1000 + patch).
- Extracted root TV application composables out of MainActivity into dedicated UI components.
- Shared single OkHttp client instance for GitHub update checks and launcher artwork requests.
- Bounded repository in-memory caches and stream hint maps with LRU evictions to conserve memory.
- Full localization for player failover notices, dialogue boost, calendar errors, profile switch errors, and scrub indicators.

### Fixed

- SyncPlay auto-reconnects when the Jellyfin server abruptly drops the TV or closes WebSockets.
- SyncPlay permission enforcement preventing non-host party guests from modifying the host queue.
- Clean state cleanup and group departure when switching user accounts during an active Group Watch.
- Stream retry teardown: halted background failover attempts when playback is stopped or dismissed.
- Deterministic cast and crew ordering regardless of asynchronous detail arrival sequence.
- Filtered out trailer entries that lack valid playback URLs.
- Preserved D-pad focus and navigation when USB keyboards or gamepads connect or disconnect.
- Separated account cache stores upon user sign-in and sign-out.
- Prevented failed Live TV channel attempts from marking subsequent channels as broken.

### Removed

- Redundant 'All Episodes' button on episode detail pages, superseded by the integrated in-page episode carousel.
- Unreachable Group Watch side panel, fully consolidating watch parties into the Quick Hub drawer.
- Deprecated navigation routes for standalone Library, Watchlist, Calendar, and Now Playing screens.
- 59 obsolete internationalization string keys, unused repository helpers, duplicate constants, and unused drawables.

## [0.84.0] - 2026-11-08

> Quick Hub Group Watch integration, Google TV remote capture, cycling streams pill, and focus navigation.

### Added

- Quick Hub Group Watch integration unifying live party tracking, group creation, invite acceptance, and code entry into the Quick Hub drawer.
- Google TV remote button capture supporting custom key codes in the interactive recorder.
- Cycling active streams pill on the home screen smoothly alternating between active streams every 4.5 seconds.
- Poster art on notification cards and Jellyfin user profile avatars on active stream cards.
- Direct upward focus navigation from Quick Hub drawer content to active section badges (Now Playing, Notifications, Group Watch).

### Changed

- Quick Hub action buttons restyled into sleek 999dp pill curves with centered icons and refined typography.
- Prioritized TV series show titles over individual episode titles on stream status cards.
- Enhanced 'Clear all' notifications action button with matching pill aesthetic.

### Fixed

- Back button long-press handling in Quick Hub drawer preventing accidental dismiss on release.
- Typing and null safety in Quick Hub Group Watch state and removed invalid keycode bindings.
- Drawer header tabs layout preventing overlapping text and badges.

## [0.83.0] - 2026-11-08

> Live updating Now Playing bar, glassy pill styling, notification timestamps & deletion.

### Added

- Live updating playback progress bar in Now Playing with real-time second-by-second updates.
- Relative reception timestamps on notification items.
- Individual notification dismissal and bulk 'Clear All' capability.
- Settings toggle under Settings > Display to control visibility of the floating Now Playing pill.

### Changed

- Restyled Now Playing floating indicator with a dark translucent glassy design.

### Fixed

- Stream handoff restricted exclusively to the active user's session, guarding other household streams against accidental interruption.

## [0.80.0] - 2026-11-08

> Quick Hub drawer architecture and remote shortcut preferences.

### Added

- Unified Quick Hub slide-out drawer consolidating Now Playing, Notifications, and quick controls.
- Interactive remote button recorder for custom Quick Hub shortcut assignment.
- Dedicated 'Remote & Shortcuts' preference section in Settings.
- Platform-specific default remote shortcuts (Play/Pause on Fire TV, Menu on Android TV).

### Changed

- Simplified and cleaned navigation rail layout to prioritize media browsing.

### Fixed

- Debounced shortcut key event processing to prevent double activations.
- System-reserved key detection with user guidance during button mapping.

## [0.71.0] - 2026-11-08

> Multi-edition recognition, version selector, and stream filters.

### Added

- Multi-edition and cut detection for media items (Theatrical, Director's Cut, Extended, Color vs Noir / B&W).
- Interactive edition switcher overlay in detail views with stream-level filtering.
- Dynamic Island media pill on the home screen with live state indications.
- Voice dictation integration for Fire TV remotes with on-screen Alexa guidance.
- High-resolution 16:9 PNG banner asset for Fire TV launcher.

### Changed

- Edition parsing regex patterns extended to support dot, hyphen, and underscore filename separators.

### Fixed

- Prevented microphone service crash on Fire TV devices lacking native Android SpeechRecognizer.

## [0.70.0] - 2026-11-07

> Profile recommendation sync and genre management.

### Added

- Long-press deletion workflow for custom genre rows with confirmation dialog.
- Profile synchronization for customized recommendation rows across connected devices.
- One-click activation for standard library genres discovered via search.

### Changed

- Genre management settings relocated under Recommendations settings.

### Fixed

- Cross-device preference persistence for personalized home row orders.

## [0.69.0] - 2026-11-07

> In-page episode browsing, hold-to-seek, custom genres search, responsive action buttons.

### Added

- In-page episode carousel directly below action buttons on episode detail pages.
- Hold-to-seek video player controls with calibrated acceleration curves.
- Custom genre search and dynamic row creation from Settings > Recommendations.
- Automated watched state progression for skipped episodes with anthology series exemption (e.g. Black Mirror).
- Instant optimistic feedback animations on Watchlist, Watched, Like, and Dislike buttons.

### Changed

- Episode detail action row automatically shrinks the 'All Episodes' button when expanding secondary actions.
- Prefetch debounce adjusted to 600ms for responsive focus while browsing.
- Home customization entry point consolidated into Recommendations settings.

### Fixed

- Stream switching overlay dismissal reliably polling playback start.
- Detail screen synchronous reset keyed by item ID to prevent lingering previous title data.
- Row modal pagination index calculations avoiding premature list exhaustion.
- Direct TMDB HTTP/HTTPS URL resolution for discovery posters.
- Focus traps in home customization mode and settings back navigation.
- Initial startup focus recovery for sidebar navigation and home cards.

## [0.68.0] - 2026-11-06

> Initial catalog virtual rows, session capabilities, infinite loading.

### Added

- Jellyfin remote playback listener and session capabilities for local network casting.
- Infinite horizontal pagination for genre and catalog rows.
- Sliding window row deduplicator isolating personal rows from discovery rows.
- Compact TMDB rating badges with linear gradient accents across all cards and overlays.
- Google TV launcher Top Picks feed integration capped to 6 curated recommendations.

### Changed

- Catalog row assembly tiered between personal queues and library discovery.

---

[0.85.0]: https://github.com/NoahSKipp/Jellio-TV/compare/43c169d...a5cca28
[0.84.0]: https://github.com/NoahSKipp/Jellio-TV/compare/6eb916f...db23f55
[0.83.0]: https://github.com/NoahSKipp/Jellio-TV/compare/8bdc62d...97edc7a
[0.80.0]: https://github.com/NoahSKipp/Jellio-TV/compare/5b0a5e6...b203d77
[0.71.0]: https://github.com/NoahSKipp/Jellio-TV/compare/c156ea1...5a3f9bf
[0.70.0]: https://github.com/NoahSKipp/Jellio-TV/compare/0d9a593...d603cca
[0.69.0]: https://github.com/NoahSKipp/Jellio-TV/compare/e60c059...0408326
[0.68.0]: https://github.com/NoahSKipp/Jellio-TV/compare/1a414a1...49d5c64
