# Pai Sho Multiplayer Server (Correspondence MVP)

Docker-ready authoritative backend for asynchronous multiplayer:

- **Ktor HTTP API**
- **SQLite persistence** (file-backed for NAS volumes)
- **server-side move validation** via shared `:core` rules engine

## API endpoints

Authentication:

- `POST /api/v1/auth/issue-token` (public)
- all game endpoints require `Authorization: Bearer <token>`

- `GET /health` (public)
- `POST /api/v1/games`
- `POST /api/v1/games/{gameId}/join`
- `GET /api/v1/games`
- `GET /api/v1/games/{gameId}`
- `GET /api/v1/games/{gameId}/legal-moves`
- `POST /api/v1/games/{gameId}/moves`

## Request/response examples

### Issue token

`POST /api/v1/auth/issue-token`

```json
{
  "playerId": "alice"
}
```

### Create game

`POST /api/v1/games`

```json
{
  "openingBasicType": "ROSE",
  "hostAccentLoadout": ["ROCK", "WHEEL", "KNOTWEED", "BOAT"],
  "guestAccentLoadout": ["ROCK", "WHEEL", "KNOTWEED", "BOAT"]
}
```

### Submit move

`POST /api/v1/games/{gameId}/moves`

```json
{
  "expectedVersion": 2,
  "move": {
    "kind": "plant",
    "tileType": "ROSE",
    "target": { "row": 0, "col": -8 }
  }
}
```

If `expectedVersion` does not match current game version, API returns **409 Conflict**.

## Run locally

```bash
./gradlew :server:run
```

Defaults:

- port: `8080`
- db path: `/data/paisho.db`

Override with env vars:

- `PAISHO_PORT=8080`
- `PAISHO_DB_PATH=/data/paisho.db`
- `PAISHO_JWT_SECRET=<long-random-secret>`

## Docker / NAS deployment

Build image:

```bash
docker build -f server/Dockerfile -t paisho-server:latest .
```

Run container:

```bash
docker run -d \
  --name paisho-server \
  --restart unless-stopped \
  -p 8080:8080 \
  -e PAISHO_PORT=8080 \
  -e PAISHO_DB_PATH=/data/paisho.db \
  -e PAISHO_JWT_SECRET=replace-with-long-random-value \
  -v /volume1/docker/paisho/data:/data \
  paisho-server:latest
```

Or use compose from repo root:

```bash
docker compose -f docker-compose.server.yml up -d --build
```
