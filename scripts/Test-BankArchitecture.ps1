$ErrorActionPreference = 'Stop'
# Isolated ports: the test never stops or changes the user's current service processes.
$discoveryPort = 18761
$gatewayUrl = 'http://localhost:18888'
$configUrl = 'http://localhost:19999'
$results = [Collections.Generic.List[object]]::new()
function Check([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "FAILED: $Name" }
    $results.Add([pscustomobject]@{test=$Name;result='PASS'})
    Write-Host "PASS: $Name"
}
function Wait-Until([scriptblock]$Probe) {
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    do {
        try { if (& $Probe) { return } } catch { }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Timed out waiting for Eureka and Gateway routing.'
}
try {
    & (Join-Path $PSScriptRoot 'Start-BankServices.ps1') -DiscoveryPort $discoveryPort -ConfigPort 19999 `
        -AccountPort 18081 -TransactionPort 18082 -GatewayPort 18888 -NoDemoData
    $configuration = Invoke-RestMethod "$configUrl/transaction-service/default"
    $maxAmount = $configuration.propertySources | ForEach-Object { $_.source.'bank.transaction.max-amount' } | Where-Object { $null -ne $_ } | Select-Object -First 1
    Check ($maxAmount -eq '1000') 'Config Server shares bank.transaction.max-amount=1000'
    Wait-Until { $items = Invoke-RestMethod "$gatewayUrl/accounts" -TimeoutSec 3; @($items).Count -ge 3 }
    Wait-Until { $null -ne (Invoke-WebRequest "$gatewayUrl/transactions" -TimeoutSec 3) }
    Wait-Until {
        $registry = Invoke-RestMethod 'http://localhost:18761/eureka/apps' -Headers @{Accept='application/json'}
        @($registry.applications.application | Where-Object { $_.name -in @('CONFIG-SERVICE','ACCOUNT-SERVICE','TRANSACTION-SERVICE','GATEWAY-SERVICE') }).Count -eq 4
    }
    $apps = Invoke-RestMethod 'http://localhost:18761/eureka/apps' -Headers @{Accept='application/json'}
    foreach ($name in @('CONFIG-SERVICE','ACCOUNT-SERVICE','TRANSACTION-SERVICE','GATEWAY-SERVICE')) {
        Check (@($apps.applications.application | Where-Object { $_.name -eq $name }).Count -eq 1) "Eureka registers $name"
    }
    $accounts = Invoke-RestMethod "$gatewayUrl/accounts"
    $account = $accounts[0]
    $body = @{accountId=$account.id;amount=25.50;currency=$account.currency;merchantCategory='PART-D-TEST';country='MA'} | ConvertTo-Json
    $transaction = Invoke-RestMethod "$gatewayUrl/transactions" -Method Post -ContentType 'application/json' -Body $body
    Check ($transaction.processingStatus -eq 'COMPLETED') 'Gateway REST creates a transaction through Feign and Eureka'
    $after = Invoke-RestMethod "$gatewayUrl/accounts/$($account.id)"
    Check (([decimal]$account.balance - [decimal]$after.balance) -eq [decimal]25.50) 'Remote account balance decreases by exactly 25.50'
    $graphql = @{query='{ getAllTransactions { id amount processingStatus } }'} | ConvertTo-Json
    $graphqlResult = Invoke-RestMethod "$gatewayUrl/transaction-service/graphql" -Method Post -ContentType 'application/json' -Body $graphql
    Check (@($graphqlResult.data.getAllTransactions | Where-Object { $_.id -eq $transaction.id }).Count -eq 1) 'Gateway routes transaction GraphQL after stripping the prefix'
    $accountGraphql = @{query='{ getAllAccounts { id currency } }'} | ConvertTo-Json
    $accountResult = Invoke-RestMethod "$gatewayUrl/account-service/graphql" -Method Post -ContentType 'application/json' -Body $accountGraphql
    Check (@($accountResult.data.getAllAccounts).Count -ge 3) 'Gateway routes account GraphQL'
    foreach ($service in @('account-service','transaction-service')) {
        $headers = @{Accept='application/json, text/event-stream'}
        $initialize = '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"architecture-test","version":"1.0"}}}'
        $response = Invoke-WebRequest "$gatewayUrl/$service/mcp" -Method Post -ContentType 'application/json' -Headers $headers -Body $initialize
        $headers['Mcp-Session-Id'] = [string]($response.Headers['Mcp-Session-Id'] | Select-Object -First 1)
        $headers['MCP-Protocol-Version']='2025-03-26'
        Invoke-WebRequest "$gatewayUrl/$service/mcp" -Method Post -ContentType 'application/json' -Headers $headers `
            -Body '{"jsonrpc":"2.0","method":"notifications/initialized"}' | Out-Null
        $tool = if ($service -eq 'account-service') { 'getAllAccounts' } else { 'getAllTransactions' }
        $request = @{jsonrpc='2.0';id=2;method='tools/call';params=@{name=$tool;arguments=@{}}} | ConvertTo-Json -Depth 4
        $response = Invoke-WebRequest "$gatewayUrl/$service/mcp" -Method Post -ContentType 'application/json' -Headers $headers -Body $request
        Check ($response.Content.Contains('"isError":false')) "Gateway calls MCP $tool with session headers preserved"
    }
    $body = @{accountId=$account.id;amount=1000.01;currency=$account.currency} | ConvertTo-Json
    $rejected = $false
    try { Invoke-WebRequest "$gatewayUrl/transactions" -Method Post -ContentType 'application/json' -Body $body | Out-Null }
    catch { $rejected = [int]$_.Exception.Response.StatusCode -eq 400 }
    Check $rejected 'Transaction rejects 1000.01 using the ceiling from Config Server'
    $recordPath = Join-Path (Split-Path $PSScriptRoot -Parent) ".runtime/services-$discoveryPort.json"
    $records = @(Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json)
    $accountProcess = $records | Where-Object { $_.name -eq 'account-service' }
    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $($accountProcess.processId)"
    if ($processInfo.CommandLine.Contains($accountProcess.jar)) { Stop-Process -Id $accountProcess.processId }
    $body = @{accountId=$account.id;amount=10;currency=$account.currency} | ConvertTo-Json
    $unavailable = $false
    try { Invoke-WebRequest "$gatewayUrl/transactions" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 15 | Out-Null }
    catch { $unavailable = [int]$_.Exception.Response.StatusCode -eq 503 }
    Check $unavailable 'Account outage returns 503 through Gateway without inventing a balance'
    $reportPath = Join-Path (Split-Path $PSScriptRoot -Parent) '.runtime/part-d-verification.json'
    ConvertTo-Json -InputObject @($results.ToArray()) | Set-Content -LiteralPath $reportPath
    Write-Host "Architecture verification succeeded. Report: $reportPath"
} finally {
    & (Join-Path $PSScriptRoot 'Stop-BankServices.ps1') -DiscoveryPort $discoveryPort
}