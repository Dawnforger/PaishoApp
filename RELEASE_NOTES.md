# Release Notes

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

## Versioning policy

- Increment patch version as `v0.0.0x` for each release:
  - `v0.0.01`
  - `v0.0.02`
  - `v0.0.03`
  - ...
