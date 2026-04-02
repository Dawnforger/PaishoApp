# Release Notes

## v0.0.17

- Added a new Docker-ready multiplayer backend module at `server/` for correspondence play.
- Implemented server-authoritative move validation by reusing the existing `:core` rules engine.
- Added SQLite-backed persistence for canonical game state and move history.
- Added correspondence API endpoints:
  - `GET /health`
  - `POST /api/v1/games`
  - `POST /api/v1/games/{gameId}/join`
  - `GET /api/v1/games?playerId={id}`
  - `GET /api/v1/games/{gameId}`
  - `GET /api/v1/games/{gameId}/legal-moves?playerId={id}`
  - `POST /api/v1/games/{gameId}/moves`
- Added NAS-friendly deployment artifacts:
  - `server/Dockerfile`
  - `docker-compose.server.yml`
  - `server/README.md`

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
