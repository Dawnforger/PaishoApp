# Release Notes

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
  - ...
