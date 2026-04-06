# Environment Recovery Log (Start-Over Guide)

Last updated: 2026-04-06 (UTC)  
Purpose: Rebuild execution context quickly if a new cloud/NAS/dev environment is needed.

---

## 1) Base environment snapshot

- OS: `linux 6.1.147`
- Shell: `bash`
- Workspace root: `/workspace`
- Git repo root: `/workspace`
- Primary feature branch used in development: `cursor/android-pai-sho-game-3890`
- Integration branch for releases: `main`

---

## 2) Repository structure (important modules)

- `app/`  
  Android app (Kotlin + Jetpack Compose), gameplay UI and local interaction flow.
- `core/`  
  Shared rules engine + AI logic.
- `server/`  
  Multiplayer correspondence backend (Ktor + SQLite), Docker/NAS deployable.
- `analysis/`  
  AI training artifacts and operational logs (including this file).
- `tools/`  
  Utility scripts used during replay ingestion/training.

---

## 3) Tooling inventory available in this coding environment

### Local project tools
- Gradle wrapper: `./gradlew`
- Java/Kotlin toolchain used by modules:
  - `app`: JVM target 17
  - `core`/`server`: JVM toolchain 21

### Android build/sign tooling paths (as used in this workspace)
- Build tools: `/workspace/android-sdk/build-tools/35.0.0`
  - `zipalign`
  - `apksigner`
- Release artifacts output:
  - `/workspace/release-artifacts/v0.0.xx/`

### Git/GitHub tools
- `git` for branch/merge/tag workflows
- `gh` CLI for release/pr inspection and release publication

---

## 4) Agent tool capabilities used during implementation

This project was developed with an agent runtime that provided (non-exhaustive):
- shell command execution,
- file glob/search,
- ripgrep content search,
- direct file read/edit/delete,
- TODO tracking updates,
- web lookup/fetch,
- PR management operations.

This matters for restart because most of the implementation relied on scripted edits + repeatable build commands, not manual IDE-only state.

---

## 5) Multiplayer backend architecture summary (for rebuild)

### Server
- Stack: Kotlin + Ktor (`server` module)
- Persistence: SQLite file
- Auth: JWT bearer token
- API (current):
  - `GET /health`
  - `POST /api/v1/auth/token`
  - `POST /api/v1/games`
  - `POST /api/v1/games/{gameId}/join`
  - `GET /api/v1/games`
  - `GET /api/v1/games/{gameId}`
  - `GET /api/v1/games/{gameId}/legal-moves`
  - `POST /api/v1/games/{gameId}/moves`

### Authoritative gameplay
- Server validates legal moves via shared `:core` rules engine.
- Canonical state versioning is used for optimistic conflict checks (`expectedVersion`).

### Test coverage added
- Authenticated create/join/legal moves.
- Non-member legal-move access rejection.
- Stale version submit conflict path.

---

## 6) Android multiplayer client wiring summary

Added network layer:
- `app/src/main/java/com/paisho/app/network/MultiplayerApi.kt`
- `app/src/main/java/com/paisho/app/network/MultiplayerRepository.kt`

Added ViewModel hooks:
- `configureMultiplayer(baseUrl, playerId, playerName)`
- `createOnlineGame()`
- `refreshOnlineGame()`

State additions:
- `GameUiState.multiplayerSession`
- `MultiplayerSession` model in `AppState.kt`

Manifest/network:
- `android.permission.INTERNET`
- cleartext enabled for local LAN/NAS HTTP dev scenarios

---

## 7) Build/verify command playbook

### Core/server/app compile & tests
```bash
./gradlew :server:test
./gradlew :app:compileDebugKotlin
./gradlew :server:test :app:compileDebugKotlin
```

### Release APK
```bash
./gradlew :app:assembleRelease
```

### Sign/verify release APK (example pattern)
```bash
mkdir -p "/workspace/release-artifacts/v0.0.xx"
"/workspace/android-sdk/build-tools/35.0.0/zipalign" -v -p 4 \
  "/workspace/app/build/outputs/apk/release/app-release-unsigned.apk" \
  "/workspace/release-artifacts/v0.0.xx/paisho-v0.0.xx-release-aligned.apk"
"/workspace/android-sdk/build-tools/35.0.0/apksigner" sign \
  --ks "/workspace/release-keys/<keystore>.jks" \
  --ks-key-alias "<alias>" \
  --ks-pass pass:<redacted> \
  --key-pass pass:<redacted> \
  --out "/workspace/release-artifacts/v0.0.xx/paisho-v0.0.xx-release-signed.apk" \
  "/workspace/release-artifacts/v0.0.xx/paisho-v0.0.xx-release-aligned.apk"
"/workspace/android-sdk/build-tools/35.0.0/apksigner" verify \
  "/workspace/release-artifacts/v0.0.xx/paisho-v0.0.xx-release-signed.apk"
```

---

## 8) Docker/NAS deployment checklist (server)

Files:
- `server/Dockerfile`
- `docker-compose.server.yml`
- `server/README.md`

Typical startup:
```bash
docker compose -f docker-compose.server.yml up -d --build
```

Critical env vars:
- `PAISHO_DB_PATH` (volume-backed sqlite path)
- `PAISHO_JWT_SECRET` (must be set to secure non-default value)

NAS notes:
- bind-persist `/data` to a NAS folder.
- keep JWT secret in NAS secret/env store, not hard-coded in compose checked into git.

---

## 9) Git recovery workflow

If restarting from scratch:
1. Clone repo and fetch all branches.
2. Checkout `cursor/android-pai-sho-game-3890` for active feature work.
3. Run validation commands from section 7.
4. For release:
   - bump metadata/docs,
   - commit/push feature branch,
   - merge to `main`,
   - build/sign/publish release.

---

## 10) Active session continuity files

- Chat/session log: `analysis/chat_session_log.md`
- Environment/start-over guide: `analysis/environment_recovery_log.md` (this file)

These two files are intended to be the first thing to read when continuity is needed.

