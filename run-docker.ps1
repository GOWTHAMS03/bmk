#!/usr/bin/env pwsh
<#
Start the full stack using Docker Compose from the backend folder.
Usage: Open PowerShell, cd to this folder and run: .\run-docker.ps1
#>
Set-Location -Path $PSScriptRoot

Write-Host "Stopping any existing compose stack (no volumes removed)..."
docker compose down

Write-Host "Starting services (this will build the backend image)..."
docker compose up -d --build

Write-Host "Tailing backend logs (Ctrl+C to exit)..."
docker compose logs -f backend

