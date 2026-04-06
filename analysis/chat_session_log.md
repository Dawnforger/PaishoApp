# Pai Sho Project Chat Session Log

Last updated: 2026-04-06 (UTC)  
Maintainer: Cloud coding agent  
Scope: Consolidated log of the full available chat/task history for this repository, including implementation milestones, user asks, and release actions.

---

## Purpose

This file is a persistent reconstruction log so project context can be recovered if work must restart in a new environment.

It captures:
- user-request timeline,
- major engineering changes,
- release/publish actions,
- known incidents and how they were resolved.

---

## Project objective (as established in chat)

Build a native Android Skud Pai Sho app with:
- near-term: local human vs AI,
- long-term: online multiplayer/correspondence play,
- repeatable release flow with versioned APK/AAB publication.

---

## Chronological milestone summary

### Early board/UI/rules foundation (v0.0.02 through v0.0.09)
- Board coordinate system anchored at center `(0,0)` with legal position map and zone coloring.
- Circular token interaction model: tap piece, show legal highlights, tap destination.
- Iterative fixes for board alignment, token centering, clipping, neutral-zone visuals, and grid rendering.
- Existing game resume support and staged-action submit/undo flows.
- Full board alignment rewrite delivered and released as `v0.0.09`.

### Harmony bonus and turn-flow refinements (v0.0.10 through v0.0.12)
- Harmony bonus mechanics added.
- Traditional opening placement orientation set to host bottom / guest top gate.
- Submit/Undo controls moved above reserve tray for UX.
- Harmony bonus UI simplified from large action list to reserve-driven selection.
- Wheel accent direction bug fixed to clockwise.

### Replay-data AI work and reports (v0.0.13 through v0.0.15)
- Ingested replay links from CSV and trained policy priors from parsed game corpus.
- Added AI analysis artifacts:
  - `analysis/ai_priors.json`
  - `analysis/ai_training_report.md`
  - `analysis/ai_findings_plain_english.md`
- Integrated learned priors into AI scoring.
- Aggressive win-focused AI tuning pass.
- Anti-stall smoke test added.
- AI freeze/ANR hotfix with bounded two-stage evaluation and capped threat scanning.

### Harmony UX safety + setup visibility (v0.0.16)
- Added harmony-flow-only **No Bonus** choice.
- Removed dangerous in-turn Reset action.
- Added projected board preview during harmony bonus decisions.
- Fixed new-game setup tile visibility on small/narrow screens (wrapping layout).
- Released `v0.0.16`.

### Multiplayer correspondence backend + auth + app wiring (v0.0.17)
- Added new `server` module (Ktor + SQLite), Docker-ready for NAS hosting.
- Server-authoritative rules validation reused from shared `:core` game engine.
- Added correspondence endpoints:
  - `GET /health`
  - `POST /api/v1/auth/token`
  - `POST /api/v1/games`
  - `POST /api/v1/games/{gameId}/join`
  - `GET /api/v1/games`
  - `GET /api/v1/games/{gameId}`
  - `GET /api/v1/games/{gameId}/legal-moves`
  - `POST /api/v1/games/{gameId}/moves`
- Added JWT auth enforcement and membership checks.
- Added server integration tests for auth/member/version-conflict flows.
- Added Android networking layer (`MultiplayerApi`, `MultiplayerRepository`) and ViewModel hooks.
- Released `v0.0.17`.

---

## User request timeline (verbatim list captured from prior context)

1. "The board still needs some work let's assign an array of legal positions for each tile to be on and graphically make the grid a representation of that. The center should be 0,0 and extend out 8 in an direction. I have included a diagram of the layout with coordinates. Black squares are Borders, green spaces are Gates, the red and white gardens are colored as such, and all other legal spaces are part of the Neutral gardens(tan)."
2. "Create a basic circle shaped token for the game pieces. Each piece on the board will be controlled by clicking on the piece then clicking on the intended target intersection. During this interaction display a highlight on all legal spaces for the selected piece."
3. "Publish this build as the next version"
4. "Newest release is showing 0.0.03"
5. "Yes add the staged actions list and then push/merge/release so our testers can download"
6. "Increase the diameter of the background board so all the spots are on it and nothing hangs off. The pieces on the board need to mirror the reserve pieces in appearance. Also the placed pieces seem off center of the intersections they are played on, let's make sure the pieces are always aligned. Also when I open the existing games screen I see the existing games listed but cannot pull those games up to resume them. Also let's cull the circles that represent the neutral garden spaces, leaving only the border lines, gates, and red/white gardens. The grid lines will suffice for the neutral spots. Also prep for being able to import and position background images for boards, allowing us to remove the need of the circles being drawn on the grid to mark the zones."
7. "Merge PR and Publish the release"
8. "Merge/publish/release"
9. "The board placed tiles are still misaligned. Also, the diameter of the tiles will make them too large once several pieces are close to each other. Let's make them a bit smaller so we can fit several tiles close to each other without too much clipping. Also when I open the existing games screen I see the existing games listed but cannot pull those games up to resume them. Also let's cull the circles that represent the neutral garden spaces, leaving only the border lines, gates, and red/white gardens. The grid lines will suffice for the neutral spots. Also prep for being able to import and position background images for boards, allowing us to remove the need of the circles being drawn on the grid to mark the zones."
10. "Merge/publish"
11. "Changed nothing at all still off center and awkward. We need this to be pixel accurate, rebuild the entire way the alignment is coded and start over"
12. "Merge PR 8 and publish the new apk"
13. "Excellent, the gameplay is massively improved and I was able to complete a game. We still lack the harmony bonus mechanic, and the starting pieces should be placed at the top and bottom gates by tradition. The host at the bottom and guest at the top. Once we start multiplayer development though, the player at the bottom, with opponent at the top. Also, move the submit and undo buttons above the reserve tiles that way the user doesn't have to scroll down to submit. Keep the log on the bottom. After tht, let's focus on getting the proper function of harmony bonuses and accent tiles."
14. "Merge and publish new apk"
15. "The bonus action list is HUGE! Let's simply, on creating a harmony and triggering a bonus , let's keep the \"Harmony formed.......\" Text but instead of generating a huge list of available actions just allow the deployment of legal tile options from the reserve display. Once the player selects the tile they want show the available space highlights like normal. The layer will then use the submit button like normal, and the Undo to back up to the begining of the harmony bonus flow if they change their mind before hitting submit."
16. "Let's get the pr merged and publish the updated apk"
17. "The AI opponent is hanging up and causing the application to time out now. I think it's time to refine the AI decision making process. What kind of data would be needed to help build an effective AI opponent for the app?"
18. "https://skudpaisho.com/?JYcwvAjAbADA7AJgQMgBZgIYBMsFMBOQA Using this link to a completed game on the Skud Pai Sho website, which has a play pause back forward control set on the bottom, can you analyze the game that was played? If so I can work on getting more links to study."
19. "I will work on getting a batch list of hundreds of games for analysis. In the mean time the behavior of the wheel seems incorrect. It rotates counterclockwise instead of clockwise as intended."
20. "Merge and publish"
21. "I now have the csv of links to be studied. Review these games and build the AI model as we discussed. Also prepare a report of some of the findings from this study of games"
22. "merge the PR and publish newest apk"
23. "The AI has taken the same tact twice in a row and froze up here twice"
24. "Yes add the smoke test then merge the PR and publish apk"
25. "We currently lack the ability to choose no bonus for harmonies. Also the reset button is dangerous, as it can reset the whole game with no confirmation. Let's remove the reset button and put a hidden No Bonus button that will be displayed shown only during harmony bonus prompts. While hidden make sure it is non responsive. Also during harmony bonuses show the projected location of the tiles that triggered the bonus. This way accent tiles choices are being made based on the board state as it will be if submitted."
26. "The AI has taken the same tact twice in a row and froze up here twice"
27. "We are missing a starting tile at the new game screen."
28. "Merge and publish"
29. "Okay the time has come. Let's get multiplayer going. What is the structure and design going to look like for this style app? Will it require a dedicated server if I want to allow persistent game states (correspondence play)?"
30. "Let plan for hosting the backend on a docker container I can host on my NAS. Execute the implementation"
31. "Execute these 3 steps"
32. "Merge and publish"
33. "Please create and keep a log file of this whole chat, and a environment log file that details all tools and asks etc that are in the code environment in case I need to start over for any reason."

---

## Incident and recovery highlights

- PR merge blocked while draft -> converted/updated PR state then merged.
- Recurrent Android/Gradle environment resets -> standardized build/sign workflow.
- Third-party replay ingestion failures (headers, decompression edge cases, malformed URLs) -> hardened parsing with safe fallbacks.
- AI freeze in dense states -> bounded evaluation and capped opponent checks.
- Merge conflicts on release metadata -> resolved by reconciling target release version.

---

## Current status at log write

- `v0.0.17` released.
- Mainline now contains:
  - correspondence backend (`server/`),
  - JWT auth + integration tests,
  - Android multiplayer network client scaffolding.

---

## Maintenance note

To keep this file useful, append a short section after each new major turn with:
- user ask,
- implementation files touched,
- validation commands,
- release/PR action.

