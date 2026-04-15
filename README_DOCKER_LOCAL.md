# Run Backend (Docker or Local)

This document explains how to run the BusyMumKitchen backend either using Docker Compose (recommended for full stack) or locally using Maven.

## Prerequisites
- Docker Desktop (for Docker mode)
- Java 21, Maven (or use the included Maven wrapper)
- psql client if you want to manage the local Postgres directly

## Files of interest
- `docker-compose.yml` — defines services: postgres, redis, rabbitmq, backend
- `.env` — credentials used by Docker Compose and the helper scripts
- `run-docker.ps1` — start stack and tail backend logs
- `stop-docker.ps1` — stop stack (optionally remove volumes)
- `run-local.ps1` — run backend locally with environment variables loaded from `.env`

## Docker mode (recommended for quick full-stack run)
1. From `backend` folder run:

```powershell
.\run-docker.ps1
```

2. To stop the stack (preserve volumes):

```powershell
.\stop-docker.ps1
```

3. To stop the stack and remove volumes (DESTROYS DB DATA):

```powershell
.\stop-docker.ps1 -RemoveVolumes
```

## Local mode (run backend on host and connect to local Postgres)
1. Ensure Postgres is running on `localhost:5432`. Create DB and uuid extension:

```powershell
# run in PowerShell (psql must be available)
psql -h localhost -U postgres -c "CREATE DATABASE busymumkitchen;" || Write-Host "DB may already exist"
psql -h localhost -U postgres -d busymumkitchen -c "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";"
```

2. Ensure `.env` contains the correct DB credentials (DB_USERNAME, DB_PASSWORD) and set them or let `run-local.ps1` load them.

3. Run locally (from `backend`):

```powershell
.\run-local.ps1
# or set env vars and run manually:
$env:DB_HOST='localhost'
$env:DB_PORT='5432'
$env:DB_NAME='busymumkitchen'
$env:DB_USERNAME='postgres'
$env:DB_PASSWORD='admin1234'
.\mvnw.cmd spring-boot:run
```

## Dealing with Flyway "relation already exists" errors
- If you changed DB password or recreated the DB volume incorrectly, Flyway may try to run migrations against an existing schema and fail.
- Quick dev fix: drop the problematic table and restart the backend so Flyway can recreate it:

```powershell
psql -h localhost -U postgres -d busymumkitchen -c "DROP TABLE IF EXISTS restaurants CASCADE;"
# then restart app (or run docker compose restart backend)
```

Or, for a full clean start in Docker:

```powershell
.\stop-docker.ps1 -RemoveVolumes
.\run-docker.ps1
```

If you need help with a specific error, paste the backend logs and I will guide you further.

