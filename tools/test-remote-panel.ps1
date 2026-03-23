param(
    [string]$BaseUrl = "http://127.0.0.1:18085",
    [string]$BotId = "bot-local-1",
    [string]$Token = ""
)

$headers = @{}
if ($Token -ne "") {
    $headers["Authorization"] = "Bearer $Token"
}

Write-Host "1) Health check"
Invoke-RestMethod -Method Get -Uri "$BaseUrl/health" | ConvertTo-Json -Depth 4

Write-Host "`n2) Send telemetry"
$telemetry = @{
    tick = [int64](Get-Date -UFormat %s)
    botPublicId = $BotId
    heroId = 123456
    username = "local-tester"
    botRunning = $true
    moduleStatus = "Running"
    moduleId = "npc_killer"
    mapId = 14
    mapName = "1-4"
    hpPercent = 0.92
    shieldPercent = 0.88
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$BaseUrl/v1/telemetry" -Headers $headers -ContentType "application/json" -Body $telemetry | ConvertTo-Json -Depth 4

Write-Host "`n3) Queue command"
$command = @{
    botPublicId = $BotId
    action = "set_module"
    parameter = "palladium_hangar"
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$BaseUrl/v1/commands/enqueue" -Headers $headers -ContentType "application/json" -Body $command | ConvertTo-Json -Depth 6

Write-Host "`n4) Poll command as plugin would do"
$next = Invoke-RestMethod -Method Get -Uri "$BaseUrl/v1/commands/next?botPublicId=$BotId" -Headers $headers -ErrorAction Stop
$next | ConvertTo-Json -Depth 5

Write-Host "`n5) State snapshot"
Invoke-RestMethod -Method Get -Uri "$BaseUrl/v1/state" -Headers $headers | ConvertTo-Json -Depth 6
