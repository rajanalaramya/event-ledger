[CmdletBinding()]
param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AccountUrl = "http://localhost:8081",
    [string]$AccountId = "demo-account"
)

$ErrorActionPreference = "Stop"
$prefix = "demo-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
$now = [DateTime]::UtcNow.ToString("o")
$older = [DateTime]::UtcNow.AddMinutes(-5).ToString("o")

function Submit-Event {
    param([string]$EventId, [string]$Type, [decimal]$Amount, [string]$Timestamp)
    $body = @{
        eventId = $EventId; accountId = $AccountId; type = $Type; amount = $Amount
        currency = "USD"; eventTimestamp = $Timestamp
    } | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Post -Uri "$GatewayUrl/events" -ContentType "application/json" -Body $body |
        ConvertTo-Json -Depth 10
}

Write-Host "Submitting credit"
Submit-Event "$prefix-credit" "CREDIT" 100.00 $now
Write-Host "Submitting debit"
Submit-Event "$prefix-debit" "DEBIT" 25.00 $now
Write-Host "Submitting duplicate debit"
Submit-Event "$prefix-debit" "DEBIT" 25.00 $now
Write-Host "Submitting an out-of-order credit"
Submit-Event "$prefix-out-of-order" "CREDIT" 10.00 $older

Write-Host "Balance:"
Invoke-RestMethod -Uri "$AccountUrl/accounts/$AccountId/balance" | ConvertTo-Json -Depth 10
Write-Host "Events:"
Invoke-RestMethod -Uri "$GatewayUrl/events?account=$AccountId&size=100" | ConvertTo-Json -Depth 10
