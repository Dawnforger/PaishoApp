# Pai Sho Android App

Native Android app for playing Skud Pai Sho with:

- **Short term:** local human vs AI gameplay
- **Long term:** online multiplayer over the internet

## Release

- Current release: **v0.0.03**
- Versioning policy: increment patch as **0.0.0x** per release (`v0.0.01`, `v0.0.02`, `v0.0.03`, ...)

## Current implementation (v0.0.03)

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
- Unit tests for core rule transitions and legality scenarios

## Architecture

- `core/src/main/kotlin/com/paisho/core/game`  
  Rule-complete domain model, legal move generation, harmony/clash analysis, and end-state evaluation.
- `core/src/main/kotlin/com/paisho/core/ai`  
  AI move selection based on legal move scoring.
- `app/src/main/java/com/paisho/app/ui`  
  Jetpack Compose board UI and interaction flow.

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