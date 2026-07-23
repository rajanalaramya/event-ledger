[CmdletBinding()]
param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AccountUrl = "http://localhost:8081"
)

$ErrorActionPreference = "Stop"
$accountId = "idempotency-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
$eventId = "$accountId-credit"
$body = @{
    eventId = $eventId; accountId = $accountId; type = "CREDIT"; amount = 10.00
    currency = "USD"; eventTimestamp = [DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json -Compress

Write-Host "Submitting 10 concurrent requests with eventId $eventId"
$jobs = 1..10 | ForEach-Object {
    Start-Job -ScriptBlock {
        param($Url, $Payload)
        Invoke-RestMethod -Method Post -Uri "$Url/events" -ContentType "application/json" -Body $Payload
    } -ArgumentList $GatewayUrl, $body
}
$responses = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job
$responses | ConvertTo-Json -Depth 10

$balance = Invoke-RestMethod -Uri "$AccountUrl/accounts/$accountId/balance"
Write-Host "Balance:"
$balance | ConvertTo-Json -Depth 10
if ([decimal]$balance.balance -ne [decimal]10.00) {
    throw "Expected one applied credit and a 10.00 balance; got $($balance.balance)."
}

Write-Host "Idempotency verified: the concurrent requests produced one applied balance change."
