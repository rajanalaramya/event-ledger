[CmdletBinding()]
param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AccountId = "outage-account"
)

$ErrorActionPreference = "Stop"
$eventId = "outage-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
$body = @{
    eventId = $eventId; accountId = $AccountId; type = "CREDIT"; amount = 10.00
    currency = "USD"; eventTimestamp = [DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json -Compress

Write-Host "Stopping account-service"
docker compose stop account-service

Write-Host "Submitting event while account-service is stopped (expect HTTP 503)"
$statusCode = 0
try {
    Invoke-RestMethod -Method Post -Uri "$GatewayUrl/events" -ContentType "application/json" -Body $body
} catch {
    if ($_.Exception.Response) {
        $statusCode = [int]$_.Exception.Response.StatusCode
    } else {
        throw
    }
}
Write-Host "HTTP status: $statusCode"
if ($statusCode -ne 503) {
    throw "Expected HTTP 503 while account-service is unavailable."
}

Write-Host "Reading the persisted gateway event"
Invoke-RestMethod -Uri "$GatewayUrl/events/$eventId" | ConvertTo-Json -Depth 10

Write-Host "Starting account-service and waiting for it to become healthy"
docker compose start account-service
do {
    Start-Sleep -Seconds 2
    $containerId = (docker compose ps -q account-service).Trim()
    $health = if ($containerId) { (docker inspect --format '{{.State.Health.Status}}' $containerId).Trim() } else { "" }
} while ($health -ne "healthy")

Write-Host "Resubmitting the event"
Invoke-RestMethod -Method Post -Uri "$GatewayUrl/events" -ContentType "application/json" -Body $body |
    ConvertTo-Json -Depth 10
