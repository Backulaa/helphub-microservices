param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(
        "api-gateway",
        "identity-service",
        "notification-service"
    )]
    [string]$Service
)

$ErrorActionPreference = "Stop"

# Always run from repository root
Set-Location $PSScriptRoot

Write-Host "Rebuilding $Service..." -ForegroundColor Cyan

docker compose `
    -f compose.yaml `
    -f compose.dev.yaml `
    up -d --no-deps --build $Service

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed: $Service" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Service updated: $Service" -ForegroundColor Green

docker compose `
    -f compose.yaml `
    -f compose.dev.yaml `
    ps $Service
