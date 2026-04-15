#!/usr/bin/env pwsh
<#
Run the Spring Boot backend locally (mvn spring-boot:run) with environment variables from .env.
Usage: Open PowerShell, cd to this folder and run: .\run-local.ps1
#>
Set-Location -Path $PSScriptRoot

# Load .env if present
if (Test-Path -Path "$PSScriptRoot\.env") {
    Write-Host "Loading environment variables from .env"
    Get-Content .env | ForEach-Object {
        if ($_ -and ($_ -notmatch '^#')) {
            $parts = $_ -split '=', 2
            if ($parts.Count -eq 2) {
                $name = $parts[0].Trim()
                $value = $parts[1].Trim()
                Set-Item -Path env:$name -Value $value
            }
        }
    }
}

# Always sync migration files so new .sql files are picked up without a full rebuild
Write-Host "Syncing db/migration files to target/classes..."
$src = "$PSScriptRoot\src\main\resources\db\migration"
$dst = "$PSScriptRoot\target\classes\db\migration"
if (Test-Path $dst) {
    Copy-Item "$src\*.sql" $dst -Force
    Write-Host "Migration files synced."
} else {
    Write-Host "target/classes/db/migration not found - will be created on first build."
}

Write-Host "Running mvn spring-boot:run (skip tests)..."
.\mvnw.cmd spring-boot:run

