# Ugreen NAS Docker Deployment (Pai Sho Server)

This directory contains a Ugreen-friendly Docker Compose file for the multiplayer server.

## Files

- `docker-compose.yml` - build-from-repo compose (requires repository files on NAS)
- `docker-compose.nas.yml` - paste-friendly compose using prebuilt GHCR image
- `docker-compose.nas-build.yml` - paste-friendly compose that builds locally from an absolute NAS path

## 1) Prepare on Ugreen NAS

Create a folder structure on your NAS (example):

```text
/volume1/docker/paisho/
  ├── compose/
  └── data/
```

Copy this whole repository to your NAS so Docker can build `server/Dockerfile`, then use:

```text
/volume1/docker/paisho/compose/docker-compose.yml
```

## 2) Update JWT secret

Open `docker-compose.yml` and change:

```yaml
PAISHO_JWT_SECRET: "replace-this-with-a-long-random-secret"
```

Use a long random string.

## 3) Start in Ugreen Docker app

### Option A: Compose import (recommended)

1. Open Ugreen Docker app.
2. Choose **Compose / Stack**.
3. Import or paste one of these files from `deploy/ugreen/`:
   - `docker-compose.nas.yml` (pulls `ghcr.io/dawnforger/paisho-server:latest`)
   - `docker-compose.nas-build.yml` (builds from local repo path on NAS)
   - `docker-compose.yml` (build-from-repo compose)
4. Deploy stack.

### Recommended for zero-manual updates

Use `docker-compose.nas.yml` and keep:

```yaml
image: ghcr.io/dawnforger/paisho-server:latest
pull_policy: always
```

Then each update only requires a stack redeploy/restart; Docker will pull the newest image automatically.

### If you see `Head ... denied` during deploy

This means GHCR denied anonymous pull for `ghcr.io/dawnforger/paisho-server:latest`.
Use `docker-compose.nas-build.yml` and set:

```yaml
build:
  context: /volume1/docker/paisho/PaishoApp
```

to your real absolute repo path on NAS.

To keep using `docker-compose.nas.yml` without local builds:

1. Make package `ghcr.io/dawnforger/paisho-server` public **or**
2. Configure GHCR credentials in NAS Docker (`docker login ghcr.io`) for pull access.

This repository includes GitHub Actions workflow `.github/workflows/publish-server-image.yml` so each pushed release tag (`v*`) publishes:

- `ghcr.io/dawnforger/paisho-server:<tag>`
- `ghcr.io/dawnforger/paisho-server:latest`

### Option B: CLI (if available)

```bash
cd /volume1/docker/paisho/compose
docker compose up -d
```

## 4) Verify server health

From LAN:

```bash
curl http://<NAS_LAN_IP>:8080/health
```

Expected:

```json
{"status":"ok"}
```

## 5) Persisted data

SQLite database is stored in:

```text
/volume1/docker/paisho/data/paisho.db
```

Back this file up regularly (nightly recommended).

## 6) Test with live players

Use these endpoints:

- `POST /api/v1/auth/token`
- `POST /api/v1/games`
- `POST /api/v1/games/{gameId}/join`
- `GET /api/v1/games`
- `GET /api/v1/games/{gameId}`
- `GET /api/v1/games/{gameId}/legal-moves`
- `POST /api/v1/games/{gameId}/moves`

## 7) Internet exposure recommendation

For internet play, place a reverse proxy (TLS) in front and expose only 443 publicly.
Keep port 8080 private to LAN/proxy.

## Optional: pre-built image flow

If you prefer not to build on NAS, replace the `build:` block in compose with:

```yaml
image: ghcr.io/dawnforger/paisho-server:latest
```

Then deploy once an image is published to that registry.
