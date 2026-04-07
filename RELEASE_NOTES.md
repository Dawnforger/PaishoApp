# Release Notes

## v0.0.24

- Fixed Android cleartext policy block for NAS/HTTP multiplayer endpoints by enabling cleartext traffic in app manifest.
- Resolves runtime error when connecting to HTTP server URLs:
  - `CLEARTEXT communication to <host> not permitted by network security policy`
- No startup-sequence changes; fix is limited to manifest network policy configuration.
- Updated app version metadata and in-app labels for v0.0.24.

## v0.0.23

- Fixed multiplayer connect runtime failure caused by missing generated serializers:
  - applied `org.jetbrains.kotlin.plugin.serialization` in `:app` module build plugins
  - resolves error: `Serializer for class 'LoginRequestDto' is not found`
- No startup-sequence changes; fix is limited to app module serialization codegen configuration.
- Updated app version metadata and in-app labels for v0.0.23.

## v0.0.22

- Reintroduced multiplayer UI flow in the app shell after startup stabilization:
  - Home + drawer navigation to Multiplayer
  - Multiplayer configuration/login/create/refresh/list/join screen
  - ViewModel wiring for multiplayer async actions with busy/error state
- Restored repository support for multiplayer list/join APIs used by the screen.
- Startup safety preserved: no changes to `MainActivity` boot sequence and no automatic network calls at app launch.
- Added distributable server container image artifact for easier NAS deployment:
  - `paisho-server-v0.0.22-docker-image.tar.gz`
  - import command: `docker load -i paisho-server-v0.0.22-docker-image.tar.gz`
- Updated app version metadata and in-app labels for v0.0.22.

## v0.0.21

- Android 16 startup-compatibility hardening release.
- Upgraded AndroidX app stack to latest stable train:
  - `androidx.compose:compose-bom:2026.03.01`
  - `androidx.core:core-ktx:1.18.0`
  - `androidx.lifecycle:lifecycle-runtime-ktx:2.10.0`
  - `androidx.lifecycle:lifecycle-runtime-compose:2.10.0`
  - `androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0`
  - `androidx.activity:activity-compose:1.13.0`
- Explicitly pinned `androidx.graphics:graphics-path:1.1.0-rc01` to pick up newer native graphics-path runtime.
- Keeps the v0.0.20 startup-stabilization rollback baseline while preserving backend/deployment artifacts in-repo.
- Updated app version metadata and in-app labels for v0.0.21.

## v0.0.20

- Stabilization rollback: restored the app-layer UI/ViewModel flow to the known-good v0.0.17 baseline to address startup regressions reported after v0.0.18+.
- Replaced platform-default launcher icon reference with an app-bundled launcher icon resource to avoid system-resource lookup inconsistencies.
- Updated app version metadata and in-app labels for v0.0.20.

## v0.0.19

- Hotfix: changed app theme parent from API-29-only `Theme.DeviceDefault.DayNight` to minSdk-compatible `Theme.Material.Light.NoActionBar` to prevent startup crashes on Android 8/9 devices (minSdk 26).
- Includes all v0.0.18 multiplayer UI + Ugreen NAS deployment additions.
- Updated app version metadata and in-app labels for v0.0.19.

## v0.0.18

- Added in-app multiplayer UI screen with controls for:
  - server URL/player configuration
  - login/token bootstrap
  - create online game
  - refresh active game
  - join by game ID
  - list available online games
- Added multiplayer navigation entry points from both Home and drawer menu.
- Expanded ViewModel/session state handling for multiplayer busy/error/success flows.
- Added Ugreen NAS deployment package:
  - `deploy/ugreen/docker-compose.yml`
  - `deploy/ugreen/README.md`
- Updated app version metadata and in-app labels for v0.0.18.

## v0.0.17

- Added a new Docker-ready multiplayer backend module at `server/` for correspondence play.
- Implemented server-authoritative move validation by reusing the existing `:core` rules engine.
- Added SQLite-backed persistence for canonical game state and move history.
- Added JWT auth flow and bearer-gated multiplayer endpoints:
  - `POST /api/v1/auth/token`
  - authenticated game create/join/list/get/legal-moves/submit
- Added correspondence API endpoints:
  - `GET /health`
  - `POST /api/v1/games`
  - `POST /api/v1/games/{gameId}/join`
  - `GET /api/v1/games`
  - `GET /api/v1/games/{gameId}`
  - `GET /api/v1/games/{gameId}/legal-moves`
  - `POST /api/v1/games/{gameId}/moves`
- Added NAS-friendly deployment artifacts:
  - `server/Dockerfile`
  - `docker-compose.server.yml`
  - `server/README.md`
- Added backend integration tests for:
  - authenticated create/join/legal-moves flow
  - non-member legal-moves access rejection
  - stale-version move submit conflict handling
- Added Android multiplayer networking scaffolding:
  - `MultiplayerApi` + `MultiplayerRepository`
  - ViewModel hooks for multiplayer configure/create/refresh
  - cleartext LAN HTTP support in manifest for NAS development

## v0.0.16

- Fixed New Game Setup opening-tile visibility on narrow screens by wrapping selection chips so all starting tile options remain accessible.
- Added explicit Harmony "No Bonus" option (visible only during Harmony bonus flow) to allow skipping bonus actions.
- Removed the in-turn Reset button to prevent accidental full game resets.
- During Harmony bonus flow, board rendering now shows projected post-arrange state to improve accent/bonus targeting decisions.
- Updated app version metadata and in-app labels for v0.0.16.

## v0.0.15

- Fixed AI ANR/freeze risk observed in dense board states by reducing per-turn compute cost.
- Refactored AI evaluation to a two-stage approach:
  - broad cheap scoring across legal moves
  - deeper scoring only on a bounded top candidate subset
- Replaced expensive nested opponent threat scans with capped immediate-win counting.
- Preserved aggressive win-focused behavior while improving responsiveness and stability.
- Updated app version metadata and in-app labels for v0.0.15.

## v0.0.14

- Tuned AI strategy to play more aggressively toward win conditions (harmony pressure, development, and tactical captures).
- Added bounded opponent immediate-threat checks in AI evaluation to preserve responsiveness in large branching positions.
- Added deterministic anti-stall smoke coverage to ensure AI expands board presence when planting opportunities exist.
- Added plain-English replay findings companion report:
  - `analysis/ai_findings_plain_english.md`
- Updated app version metadata and in-app labels for v0.0.14.

## v0.0.13

- Added replay-driven AI training pipeline to ingest and decode Skud replay links from CSV input.
- Generated learned policy priors from 5k+ parsed games and integrated priors into `SimpleAi` move scoring.
- Added AI move shortlist guardrail for large branching positions to reduce timeout risk.
- Added replay study artifacts:
  - `analysis/ai_priors.json` (machine-readable priors)
  - `analysis/ai_training_report.md` (data findings summary)
- Updated app version metadata and in-app labels for v0.0.13.

## v0.0.12

- Fixed Wheel accent rotation direction so surrounding tiles rotate clockwise as intended.
- Updated app version metadata and in-app labels for v0.0.12.

## v0.0.11

- Replaced Harmony bonus action list with reserve-driven bonus selection flow.
- During Harmony bonus flow, selecting a legal reserve tile now highlights legal bonus targets directly on the board.
- Undo now backs up to the beginning of Harmony bonus flow before submit, and can return to it after staging but before submitting.
- Updated app version metadata and in-app labels for v0.0.11.

## v0.0.10

- Added explicit Harmony bonus selection UX: when an Arrange forms Harmony, players now choose the exact bonus action to stage.
- Enabled practical accent-bonus execution by exposing selectable bonus options (including accent actions) instead of implicit fallback behavior.
- Updated traditional opening orientation defaults to host at bottom gate and guest at top gate.
- Moved Submit/Undo controls above reserve tiles while keeping the game log at the bottom for faster turn submission flow.
- Updated app version metadata and in-app labels for v0.0.10.

## v0.0.09

- Rebuilt board rendering with a single pixel-space coordinate transform for taps, highlights, grid, and token centers.
- Added tighter token sizing and canvas-based token drawing to reduce clipping and remove overlay drift.
- Updated app version metadata and in-app labels for v0.0.09.

## v0.0.08

- Released center-anchored (0,0 origin) board placement update to improve uniform tile alignment.
- Includes smaller on-board tokens, neutral marker culling, resume-from-existing-games support, and board background config scaffolding.

## v0.0.07

- Fixed remaining board token alignment issues for more consistent intersection anchoring.
- Reduced board token size to minimize clipping in dense local clusters.
- Enabled resume from Existing Games by tapping a saved game card.
- Culled neutral-garden circle markers while keeping border/gates/red/white zones.
- Added board visual config scaffolding to support future background image import/positioning.

## v0.0.06

- Increased board background diameter so all legal intersections are inside the board disk.
- Updated on-board placed piece visuals to mirror reserve token appearance (labeled circular tokens).
- Corrected piece centering logic so pieces align precisely on grid intersections.

## v0.0.05

- Added staged action review panel so players can see pending move/plant actions before finalizing.
- Added Submit/Undo-driven turn staging flow refinements for better tester iteration on a turn.
- Added bottom reserve tile tray polish and legal-space guidance for selected reserve tiles.

## v0.0.04

- Added basic circular token visuals for board pieces.
- Updated interaction flow for piece movement:
  - tap a piece you control,
  - legal destinations highlight on the board,
  - tap a highlighted intersection to execute the move.
- Retained gate-based planting with explicit "Plant on Gate" action.

## v0.0.03

- Fixed circular board overlay alignment issues so marker/token placement stays correctly on-grid.
- Sidebar drawer is now menu-button-only (swipe gesture disabled).
- Published updated signed APK/AAB artifacts.

## v0.0.02

- Tightened board interaction: taps now snap to legal playable **grid intersections only**.
- Added circular board UI refinements and improved start/menu/setup flow scaffolding.
- Published updated signed APK/AAB artifacts for release.

## v0.0.01

- Introduced native Android project structure using Kotlin + Jetpack Compose.
- Implemented Skud Pai Sho full-rule game engine foundation in `core`:
  - Basic and special flower tiles, gates, gardens, growing/blooming
  - Harmony and clash detection
  - Accent tile mechanics (Rock, Wheel, Knotweed, Boat)
  - Harmony bonus action modeling
  - End-of-game resolution (Harmony Ring and last-basic-tile midline scoring)
- Added local AI move selection over legal move space.
- Added unit tests for core legality and rule scenarios.
- Added first release version metadata (`v0.0.01`).
- Added Android SDK bootstrap + successful `:app:assembleDebug` verification in cloud environment.

## Versioning policy

- Increment patch version as `v0.0.0x` for each release:
  - `v0.0.01`
  - `v0.0.02`
  - `v0.0.03`
  - `v0.0.04`
  - `v0.0.05`
  - `v0.0.06`
  - `v0.0.07`
  - `v0.0.08`
  - `v0.0.09`
  - `v0.0.10`
  - `v0.0.11`
  - `v0.0.12`
  - ...
