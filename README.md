# Pai Sho Android App

Native Android app for playing Skud Pai Sho with:

- **Short term:** local human vs AI gameplay
- **Long term:** online multiplayer over the internet

## Release

- Current release: **v0.0.20**
- Versioning policy: increment patch as **0.0.0x** per release (`v0.0.01`, `v0.0.02`, `v0.0.03`, `v0.0.04`, `v0.0.05`, `v0.0.06`, `v0.0.07`, `v0.0.08`, `v0.0.09`, `v0.0.10`, `v0.0.11`, `v0.0.12`, `v0.0.13`, `v0.0.14`, `v0.0.15`, `v0.0.16`, `v0.0.17`, `v0.0.18`, `v0.0.19`, `v0.0.20`, ...)

## Current implementation (v0.0.20)

This repository includes a native Android project written in Kotlin + Jetpack Compose with:

- Full-rule game engine foundation aligned with Skud Pai Sho core mechanics:
  - Basic Flowers: R3/R4/R5/W3/W4/W5 movement and garden restrictions
  - Gates and growing/blooming distinction
  - Harmony/Clash validation
  - Accent tile effects (Rock, Wheel, Knotweed, Boat) in rules engine
  - Special flowers (White Lotus, Orchid behavior)
  - Harmony bonus modeling
  - End-game detection (Harmony Ring and last-basic-tile midline scoring)
- Local AI opponent with legal-move selection on top of full-rule legality
- Compose UI for local gameplay loop against AI
- Board rendering rewritten around a single pixel-space geometry transform so intersections, highlights, hit-testing, and piece centers align from one source of truth
- Harmony bonus selection UI and logic for arrange moves that create new Harmony outcomes
- Reserve-driven Harmony bonus flow (no giant action list): choose legal reserve tile, then highlighted legal target
- Wheel accent rotation corrected to the intended clockwise behavior
- Traditional opening gate orientation (host bottom, guest top) and quicker turn controls layout
- Replay-trained AI priors integrated into move scoring, using thousands of parsed historical games
- AI move-selection path now uses bounded two-stage evaluation and capped opponent threat scans to reduce timeout/hang/ANR risk in dense positions
- Aggressive win-focused AI tuning prioritizes immediate wins, tactical pressure, and anti-stall development
- Deterministic anti-stall smoke testing ensures AI expands board presence when planting is available
- New game setup opening tile choices now wrap on small screens so all starting tile options remain visible
- Includes v0.0.20 startup-stability rollback to the last known-good v0.0.17 app UI flow while backend and deployment artifacts remain available in-repo
- Added an app-bundled launcher icon resource to avoid relying on platform-default icon resources
- Unit tests for core rule transitions and legality scenarios

## Architecture

- `core/src/main/kotlin/com/paisho/core/game`  
  Rule-complete domain model, legal move generation, harmony/clash analysis, and end-state evaluation.
- `core/src/main/kotlin/com/paisho/core/ai`  
  AI move selection based on legal move scoring.
- `app/src/main/java/com/paisho/app/ui`  
  Jetpack Compose board UI and interaction flow.
- `server/src/main/kotlin/com/paisho/server`
  Docker-ready multiplayer backend (Ktor + SQLite) for correspondence play with persistent game state.
  Includes JWT-authenticated API access, backend integration tests, and Android client wiring scaffolding for NAS-hosted multiplayer.

## Build

```bash
./gradlew test
```

To run the Android app, install Android SDK components and use Android Studio or:

```bash
./gradlew assembleDebug
```

## Next milestones

1. Add stronger AI (search/minimax, position evaluation tuning, deterministic test fixtures).
2. Expand UI for explicit Accent/Special action selection UX.
3. Add persistent match history and replay.
4. Add online multiplayer backend + matchmaking + real-time sync.