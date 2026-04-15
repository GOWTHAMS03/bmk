#!/usr/bin/env pwsh
<%
Stop and remove the Docker Compose stack. Use -RemoveVolumes to delete named volumes (DESTROYS DB data).
Usage: .\stop-docker.ps1 [-RemoveVolumes]
%>
param(
    [switch]$RemoveVolumes
)

Set-Location -Path $PSScriptRoot

if ($RemoveVolumes) {
    Write-Host "Bringing down compose and removing volumes (this will delete DB data)..."
    docker compose down -v
} else {
    Write-Host "Bringing down compose (volumes preserved)..."
    docker compose down
}

