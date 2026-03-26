# Pai Sho Android App

Native Android app for playing Pai Sho with a roadmap for:

- **Short term:** local human vs AI gameplay
- **Long term:** online multiplayer over the internet

## Current MVP scope

This repository now includes a native Android project written in Kotlin + Jetpack Compose with:

- A playable local prototype loop
- A game-domain rules engine abstraction for Skud Pai Sho-inspired rules
- A simple AI opponent using heuristic move selection
- Unit tests for core game-state transitions

> Note: Full official Skud Pai Sho rules from skudpaisho.com are extensive.  
> The current implementation is a foundation with simplified, explicit MVP rules in code and can be iteratively expanded to full compliance.

## Architecture

- `core/src/main/kotlin/com/paisho/core/game`  
  Core domain model, legal move generation, and game-state transitions.
- `core/src/main/kotlin/com/paisho/core/ai`  
  AI move selection.
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

## Suggested next milestones

1. Implement complete, official Skud Pai Sho rule parity (tiles, accents, clashes, harmonies, victory conditions).
2. Add stronger AI (minimax + pruning + deterministic test positions).
3. Add persistent match history and replay.
4. Add online multiplayer backend + matchmaking + real-time sync.