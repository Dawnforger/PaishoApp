# Release Notes

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
  - ...
