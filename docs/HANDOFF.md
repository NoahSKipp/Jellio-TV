# Jellio TV handoff

Written for a fresh agent picking this repo up with zero prior context. Read
`CLAUDE.md` first (hard rules: authorship, branch/commit conventions, no em
dashes) — this doc is architecture and state, not process rules.

Note: `CLAUDE.md`'s own "Layout" section is stale — it still describes
`ui/nav/TopNavPill.kt` as the current nav chrome. That component is gone,
replaced this session by `SidebarNav.kt` (see below). Worth fixing in a
follow-up docs commit.

## What this is

A native Android TV / Jetpack Compose client for Jellio Plugin — a thin,
directly-rendered TV client over Jellio Plugin's own REST API. No local
media, no bundled catalog runtime. "No new server-side work should exist here
that Jellio Plugin doesn't already expose" is a hard rule from CLAUDE.md: if a
screen needs data the plugin doesn't serve, that's a Jellio Plugin change
first, never a workaround here. Sibling repo: `NoahSKipp/Jellio-Plugin`, see
its own `docs/HANDOFF.md`.

## App architecture

Single Activity (`MainActivity.kt`, `@AndroidEntryPoint`). `setContent {
JellioTvTheme { Surface { JellioTvRoot() } } }`.

- `JellioTvRoot()` switches on `AppViewModel.authState`: `Loading` renders an
  empty `Box` for one frame (avoids flashing the login screen while the
  DataStore session flow resolves its first value), `LoggedOut` renders
  `LoginScreen`, `LoggedIn` renders `AppBootGate`.
- `AppBootGate` hoists `HomeViewModel`/`LibraryViewModel`/`AppUpdateViewModel`
  via `hiltViewModel()` at this level so they resolve to the same
  Activity-scoped instances the real screens later obtain — kicks off
  `homeViewModel.load()`, `appUpdateViewModel.checkForUpdate()`, and a
  speculative `libraryWarmupViewModel.load()` for whichever library is first
  in nav order, all *before* the real screens mount. Holds `BootSplashMark`
  up for a minimum of 10s or until home finishes loading, whichever is
  longer, specifically so the boot mark's own pop-in animation never gets cut
  off mid-play on a fast/cached load.
- `JellioTvApp` is the real shell. Routing is a **plain hand-rolled back
  stack** (`routeStack: List<JellioRoute>`), not Navigation Compose:
  `switchTab()` resets the stack to one entry (tab switching never stacks),
  `push()` appends (Detail/Player/Person/Service push),
  `BackHandler(enabled = routeStack.size > 1)` pops. Chrome layering:
  `SeasonalEffectsOverlay` bottommost on non-immersive routes, then screen
  content padded `start = SidebarReservedWidth.scaled()`, then (non-immersive
  only) `SidebarNav`, `NowPlayingButton`/panel, `GroupWatchButton`/overlay,
  `LibraryPickerOverlay`. `StreamPickerOverlay`/`UpdateToast` render globally.

`ui/nav/JellioRoute.kt` — sealed interface. `Profile/Home/Search/Watchlist/
Calendar/Library/Settings` are the fixed tab set (`JellioNavItems`).
`Detail(itemId)/Person(personId)/Service(name)/Player(itemId,
mediaSourceId)` are pushed-only, never in nav. `isImmersive()` is true for
Detail/Person/Service/Player — no sidebar, no seasonal overlay, full-bleed.

**Hilt DI**: `@HiltAndroidApp` application class (currently a bare scaffold —
its own comment says nothing needs it yet, though the rest of the app is
fully wired through `@AndroidEntryPoint`/`@HiltViewModel`). `di/NetworkModule.kt`
and `di/GitHubModule.kt`. **ViewModel pattern**: every screen has a
`@HiltViewModel` obtained via `hiltViewModel()` in the composable's default
params, `MutableStateFlow<UiState>` exposed as `StateFlow`, a `load(session,
...)` or `start()` entry point called from `LaunchedEffect`. Because
everything hangs off the same Activity's `ViewModelStoreOwner`, the same VM
type resolves to the same singleton-per-Activity instance anywhere in the
tree — deliberately exploited by `AppBootGate` to warm Home/Library before
they're shown.

## SidebarNav — the current nav chrome (replaced TopNavPill this session)

`ui/nav/SidebarNav.kt`. A vertical rail fixed at `x = 0`, collapsed
`88.dp.scaled()` / expanded `260.dp.scaled()`, ports Jellio Plugin's own
desktop `.jellio-sidebar` CSS (`position: fixed`, `:hover/:focus-within`
expansion) rather than the old mobile-pill shape. Expand/collapse is
**purely focus-driven**: `Column.modifier.onFocusChanged { state -> expanded
= state.hasFocus }` — Compose's `FocusState.hasFocus` already means "this
node or any descendant," so there's no manual per-item focus bookkeeping.
`MainActivity` always reserves `SidebarReservedWidth` (= the collapsed width)
of horizontal padding on non-immersive screens regardless of expand state —
the rail overlays content when expanded rather than reflowing the grid
underneath. Cold-start focus fix: a `LaunchedEffect(Unit) {
initialFocusRequester.requestFocus() }` claims initial focus onto whichever
item matches the selected route, so the first D-pad press on cold start
doesn't fall through to Compose's generic default. Because the rail sits at
real x=0 with genuinely reserved layout space (not an overlay while
collapsed), Compose's default spatial focus search finds it reliably on a
Left press from any screen content — this specifically avoids the flaky
`requestFocus()` bridging pattern that kept breaking Home/Library's own Down
navigation under the previous approach (see the focus history below;
`SidebarNav`'s own header comment references it directly).

## `ui/` package map

- **auth/** — `LoginScreen`/`LoginViewModel`. Server address asked before
  username/password, remembered-user grid, manual form, forgot-password/PIN
  reset flow.
- **calendar/** — upcoming release/air-date calendar.
- **common/** — `JellioTextField`, `ProgressSweep`.
- **detail/** — `DetailScreen` (hero + collapse/expand action row,
  `LazyColumn` with `focusRestorer()`), `StreamPickerOverlay`.
- **groupwatch/** — create/join/chat overlay, corner-button trigger.
- **home/** — the largest package: `HomeScreen`, `HeroSection` (rotating
  hero), `PosterRow`/`PosterCard`, `LandscapeCard` (Continue Watching, "Nm
  left" label), `ComingSoonRow`, `StudioHubRow`, `HomeCustomization`
  (reorder/hide/reset), `RowListModal` ("view all," moved off the row title
  onto its own button this session's earlier PR), `CardOptionsHost`,
  `CardShatterOverlay`.
- **library/** — `LibraryScreen`, `LibraryCoverflow` (three-wide overlapping
  stage), sort/genre filter chips.
- **nav/** — `JellioRoute`, `SidebarNav`, `LibraryPickerOverlay`, hand-ported
  `LibraryIcon`/`SearchIcon` SVG path data (Material-icon approximations were
  visibly wrong in live feedback).
- **nowplaying/** — polled active-session panel, corner trigger button.
- **person/** — filmography, `focusRestorer()`.
- **player/** — Media3/ExoPlayer, trickplay scrubbing, subtitle burn-in,
  sleep timer, progress reporting.
- **profile/**, **search/** (`focusRestorer()`), **seasonal/** (per-theme
  overlays: fireworks, frost, Friday-13, film-noir, Star Wars hyperspace —
  bottommost `pointer-events`-off layer, hidden on immersive routes),
  **service/** (per-streaming-service hub, immersive route),
  **settings/** (`SettingsScreen`, `AvatarPickerOverlay`, About section reads
  `BuildConfig.VERSION_NAME`), **theme/** (see below), **update/**
  (`AppUpdateViewModel`, `BootSplashMark`, `UpdateToast`), **watchlist/**.

## `data/` layer

`data/JellioRepository.kt` (~1090 lines) — the single `@Singleton`
chokepoint, mirroring the web plugin's `runtime/auth.js` + `runtime/api.js`
combined. Auth (`connectAndLogin`, `quickSignIn` spends one GET on a
remembered token before trusting it, password reset flow, logout), catalog
(`getLibraries`, `getLibraryNavEntries` — Anime is synthesized from a
Gelato-written `ProviderIds.Stremio` prefix when no real Anime view exists,
`getCollections` properly paginated — a real bug fix, a single `Limit: 200`
page used to silently drop collections past it), playback
(`resolvePlayback()` negotiates direct-play vs transcode against a hand-built
allowlist deliberately wider than the web client's since ExoPlayer decodes
more, builds the stream URL directly, never trusts `TranscodingUrl`), a
hand-rolled `TtlCache` (60s general / 8s short TTL, per-key `Mutex` sharing
in-flight requests across concurrent callers, nothing persists past process
restart), Group Watch, sleep timer, avatar upload, Quick Connect, admin
dashboard URL fallback (opens the native web dashboard in a device browser,
no WebView here). `data/network/JellyfinApi.kt` (Retrofit interface),
`data/network/GitHubApi.kt` (update checker), `data/session/SessionManager.kt`
(DataStore-backed: server address, token, user id/name, a once-generated
persisted device id), `data/session/RememberedUsersStore.kt`.
`di/NetworkModule.kt` builds Retrofit against a placeholder base URL (the real
server address isn't known until login) with a `@BaseUrlInterceptor` OkHttp
interceptor swapping scheme/host/port per request.

**Known gap**: `JellioRepository.imageUrl()` attaches no auth header — Coil
doesn't carry the session token on image requests. Works against a typical
open self-hosted instance, would break against a hardened one. Documented,
not hidden.

## Theme system (`ui/theme/`)

`Color.kt` — hand-copied from Jellio Plugin's `css/app.css` tokens, cross
-checked against NuvioWeb directly (deliberately monochrome: near-black
grounds + one bright `#f5f5f5` tone, no accent hue — an earlier invented
orange accent was removed once checked against Nuvio and found to have no
real source). `Type.kt` — sized for 10-foot viewing, `FontFamily.Default` is
an explicit placeholder pending a real Inter font resource. `Theme.kt` —
`JellioTvTheme` overrides only the tokens Jellio Plugin's CSS actually
defines, leaves everything else at tv-material3 defaults. `TvScale.kt` — the
`.scaled()` mechanism: `rememberTvScale()` computes `(screenWidthDp /
960f).coerceIn(0.82f, 1.3f)`, and `fun Dp.scaled(): Dp = this *
LocalTvScale.current` is called pervasively on every hardcoded size (rail
widths, icon sizes, splash mark size, card dimensions) so the UI reads
consistently across different TV panel dp-widths rather than looking right
only on the one reference viewport it was tuned against.

## Focus/navigation system — the most fragile subsystem in this repo

The phrase "real bug found live" (their convention for a bug actually found
testing on a real device, not guessed at) appears across a dozen files here,
most of them focus-related. History of recurring live-tested fixes (oldest to
newest, from commit titles): broken shared focus-requester down override →
focusGroup blocking default scroll entry → missing default D-pad entry point
→ nav-pill bounds clipping Home/Library's own Down search → cold-start focus
+ library picker trap + immersive-screen focus + full-bleed gap → a reverted
graphicsLayer full-bleed offset that caused its own regressions → D-pad dead
end + Group Watch focus trap + nav pill centering → coverflow focus leak +
nav pill centering fix → real Down-navigation + Settings scroll regressions.
This session's own sidebar migration (`SidebarNav`, replacing `TopNavPill`)
was itself done specifically to stop re-fighting this exact class of bug —
see its header comment.

Working idioms, applied consistently rather than reinvented per screen:
- **`focusRestorer()`** on every top-level scrollable screen (Home, Library,
  Watchlist, Search, Detail, Person, RowListModal). Plain Compose Foundation
  `LazyColumn`/`LazyRow` (unlike `tv-foundation`'s Tv-prefixed variants)
  advertise no default D-pad entry point until something has been focused
  inside them once; `focusRestorer()` picks a sensible default child on first
  arrival and remembers the last-focused child on return trips.
- **Explicit `FocusRequester` + `LaunchedEffect(Unit) { requester.requestFocus()
  }`** wherever a modal/overlay/rail needs guaranteed initial focus on open
  (doesn't sit inside a plain scroll container): `SidebarNav`,
  `GroupWatchOverlay`, `LibraryPickerOverlay`, `SettingsScreen`'s first row,
  `UpdateToast`, `RowListModal`.
- **`focusProperties { exit = { FocusRequester.Cancel } }`** traps focus
  inside a modal overlay so it can't escape to the screen underneath while
  open.

Any future layout change near a focusable tree should be treated as
high-risk for a regression here. Since this environment has no way to run the
app live, changes in this area deserve extra scrutiny reading the diff and,
ideally, real on-device verification before calling something done.

## Boot splash / app icon (added this session)

Two commits at the current head: real app icon first, then the animated
splash mark.

- `res/drawable/ic_jellio_mark.xml` — static base vector (400×400 viewport).
  `jellio_mark_root` group (whole mark, pivot 200,200 — pop-in target)
  contains the triangle (linear gradient fill+stroke, the round
  `strokeLineJoin` produces the shape's rounded corners, not a separate
  corner-radius property), a clip-path'd radial glow, and `jellio_mark_swim`
  (pivot 175,200 — the jellyfish silhouette sub-group at its static
  `translate(58,113.5) scale(0.31)` position, swim-loop target).
- `res/drawable/ic_jellio_mark_animated.xml` — the `<animated-vector>` wiring
  those two named groups to `@animator/jellio_mark_pop` (scale
  0.66→1.03→1.0, alpha 0→1, 850ms, plays once, an overshoot interpolator
  going past controlY=1 on purpose) and `@animator/jellio_mark_swim` (a
  gentle translate+rotate loop, 3200ms, `repeatCount="infinite"`).
- `ui/update/BootSplashMark.kt` — `AndroidView { ImageView(...).apply {
  setImageResource(...); (drawable as? Animatable)?.start() } }` rather than
  Compose's own `animation-graphics` `AnimatedImageVector` API, deliberately
  — that Compose API only drives a discrete `atEnd` boolean transition and
  has no equivalent for the XML animator's own `repeatCount="infinite"` swim
  loop, which a plain `Animatable.start()` call already handles natively.
  Renders at `240.dp.scaled()` with "Jellio"/"Loading…" text below, matching
  the web plugin's own splash. Replaces what was previously a looping
  `jellio_load.webm` clip played via ExoPlayer.
- **Explicitly scoped out**: the source SVG's background caustic ripples and
  tiny wake-trail dots were not ported — masked, individually timed micro
  shapes with no practical Android equivalent at this drawable's actual
  on-screen size, would need a dozen more clip-path groups for detail nobody
  would see. This is a permanent, acknowledged scope cut, not a TODO.
- The app icon (`res/mipmap-anydpi-v26/ic_launcher.xml`,
  `ic_launcher_foreground.xml`/`ic_launcher_background.xml`) uses the same
  mark, scaled to fit the adaptive icon's safe zone.

## Build/release pipeline

`app/build.gradle.kts`: `compileSdk`/`targetSdk = 36`, `minSdk = 24`.
`versionCode = GITHUB_RUN_NUMBER ?: 1` (always monotonic in CI, falls back to
1 locally). `versionName` is hand-bumped by `make release`/git-cliff off
conventional commits. `buildConfig = true` explicitly enabled (AGP 8 defaults
it off) because Settings' About section needs `BuildConfig.VERSION_NAME`.

`Makefile` mirrors Jellio Plugin's own (itself from Gelato).
`.github/workflows/build.yml` (push to main + PR events, skips drafts):
conventional-commits lint + `./gradlew :app:assembleDebug`.
`release.yml` (`workflow_dispatch` only, needs `RELEASE_TOKEN` since the
default `GITHUB_TOKEN` can't trigger `publish.yml`'s `release: released`
event): runs `make release`. `publish.yml` (on `release: released` or manual
dispatch): builds a **debug-signed** APK (no release keystore configured
yet), renames it, generates md5/sha256 checksums, uploads as release assets.

## Recent work (this handoff's own session)

1. **Row header redesign** (shipped before the sidebar work): row titles
   stopped being focusable/clickable (a focusable heading was blocking
   ordinary Left/Right onto a row's own first card, and real feedback asked
   for plain unfocusable text back). The "view all" action moved to a new
   `RowExpandButton`, first in the row's own `LazyRow`, sized to match that
   row's own card shape.
2. **Navigation/focus regression fixes**: cold-start focus landing on the
   wrong item, Down navigation dead-ending out of several screens, an
   unreadable language-picker overlay. Root-caused to an explicit
   `requestFocus()` bridge mechanism (the same one two earlier sessions had
   each already pulled once for the identical reason) — replaced with plain
   `top = 140.dp` clearance under the pill, the same pattern every other
   screen already used successfully.
3. **Sidebar migration**: `TopNavPill` (a mobile-style floating pill) →
   `SidebarNav` (a desktop-style collapsible rail, focus-driven expand,
   always reachable via Left from any screen's content). Retired the
   scroll-driven `onCompactChange`/`rememberNavCompact` plumbing that existed
   only to shrink the old pill on scroll, and every screen's stale
   `top = 140.dp` pill clearance (nav now reserves horizontal space on the
   left instead).
4. **Real app icon and animated boot splash**: see the section above. The
   Android build compiling this hand-authored vector/animator XML cleanly on
   the first CI run (no local Android SDK available to pre-verify) is worth
   noting as a real, if lucky, validation — future vector/animator XML edits
   here still can't be visually verified before CI, so double-check the XML
   by hand (structure, path data, gradient stops) before pushing.

## Known gaps / deliberately deferred

- `JellioTvApplication` is a bare Hilt scaffold, "no component actually needs
  it yet" per its own comment.
- No real Inter font resource yet (`Type.kt`'s `FontFamily.Default`
  placeholder).
- No release signing keystore — `publish.yml` ships debug-signed APKs only.
- Boot splash mark intentionally omits the caustic-ripple/wake-dot detail
  from the source SVG (see above) — permanent, not TODO.
- The focus/D-pad system (see above) is the single most fragile area by a
  wide margin — not unfinished, but with a demonstrated high regression rate
  any layout change nearby should be treated with real caution.

## Process notes for whoever picks this up next

- Same repo-reuse caution as Jellio Plugin's own handoff: before editing
  anything, `git fetch origin main` and branch fresh off `origin/main`, not
  off whatever's checked out — a stale already-merged branch left checked out
  from a prior task is the normal starting state here, not a red flag by
  itself, but committing onto it directly duplicates history. Stash
  uncommitted work, branch fresh, pop the stash if you find yourself on a
  stale branch mid-task.
- Never run `git reset --hard`/`checkout`/`clean` without `git status` first.
- Standing pipeline: commit (signed per CLAUDE.md, conventional prefix) →
  push → open PR → wait for CI → squash merge → trigger `release.yml` → wait
  → confirm `publish.yml` succeeded → report the new version.
- No local Android SDK/Gradle in this environment. CI's `build` job (Android
  SDK setup + `assembleDebug`) is the only real compile verification
  available before merge — there is no way to preview the running app, so UI
  changes (especially anything touching focus, layout, or hand-authored
  vector/animator XML) need extra care reading the diff since nothing here
  can be visually confirmed pre-merge.
