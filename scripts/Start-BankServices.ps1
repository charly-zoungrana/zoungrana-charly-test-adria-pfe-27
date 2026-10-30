param(
    [int]$DiscoveryPort=8761,
    [int]$ConfigPort=9999,
    [int]$AccountPort=8081,
    [int]$TransactionPort=8082,
    [int]$GatewayPort=8888,
    [switch]$NoDemoData,
    [int]$StartupTimeoutSeconds=120
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$runtimeDir=Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
$recordPath=Join-Path $runtimeDir "services-$DiscoveryPort.json"
$ports=@($DiscoveryPort,$ConfigPort,$AccountPort,$TransactionPort,$GatewayPort)
if (@($ports | Select-Object -Unique).Count -ne 5) { throw 'Each service needs a different port.' }
foreach($port in $ports) {
    $probe=[Net.Sockets.TcpClient]::new()
    try {
        if($probe.ConnectAsync('localhost',$port).Wait(200) -and $probe.Connected) {
            throw "Port $port is already in use; existing applications will not be stopped."
        }
    } catch [AggregateException] {
    } finally { $probe.Dispose() }
}
$java=(Get-Command java -ErrorAction Stop).Source
# Resolve Oracle's launcher to the actual JDK executable, keeping PID ownership reliable.
$javaHomeLine=& $java -XshowSettings:properties -version 2>&1 | ForEach-Object { $_.ToString() } | Where-Object { $_.TrimStart().StartsWith('java.home =') } | Select-Object -First 1
if($javaHomeLine) {
    $actualJava=Join-Path (($javaHomeLine -split '=',2)[1].Trim()) 'bin/java.exe'
    if(Test-Path -LiteralPath $actualJava) { $java=$actualJava }
}
$records=[Collections.Generic.List[object]]::new()
[IO.File]::WriteAllText($recordPath,'[]')
$discoveryUrl="http://localhost:$DiscoveryPort/eureka/"
$configUrl="http://localhost:$ConfigPort"
function Start-BankService([string]$Name,[int]$Port,[string[]]$ExtraArguments) {
    $jar=Join-Path $projectRoot "$Name/target/$Name-0.0.1-SNAPSHOT.jar"
    if(-not (Test-Path -LiteralPath $jar)) { throw "Missing $jar. Run mvn package first." }
    $outLog=Join-Path $runtimeDir "$Name-$DiscoveryPort.log"
    $errLog=Join-Path $runtimeDir "$Name-$DiscoveryPort.err.log"
    $arguments=@('-Xmx256m','-XX:ActiveProcessorCount=2','-jar',('"'+$jar+'"'),"--server.port=$Port")+$ExtraArguments
    $process=Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $outLog -RedirectStandardError $errLog
    $records.Add([pscustomobject]@{name=$Name;processId=$process.Id;port=$Port;jar=$jar})
    ConvertTo-Json -InputObject @($records.ToArray()) | Set-Content -LiteralPath $recordPath
    Write-Host "Starting $Name on port $Port..."
    $deadline=[DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    do {
        $process.Refresh()
        if($process.HasExited) { throw "$Name exited. Check $outLog and $errLog." }
        try {
            $health=Invoke-RestMethod "http://localhost:$Port/actuator/health/readiness" -TimeoutSec 2
            if($health.status -eq 'UP') { Write-Host "$Name is ready."; return }
        } catch { }
        Start-Sleep -Milliseconds 500
    } while([DateTime]::UtcNow -lt $deadline)
    throw "$Name did not become ready. Check $outLog."
}
try {
    Start-BankService 'discovery-service' $DiscoveryPort @()
    Start-BankService 'config-service' $ConfigPort @("--eureka.client.service-url.defaultZone=$discoveryUrl")
    $clientArguments=@("--spring.cloud.config.uri=$configUrl","--eureka.client.service-url.defaultZone=$discoveryUrl","--eureka.instance.ip-address=127.0.0.1")
    Start-BankService 'account-service' $AccountPort $clientArguments
    $deadline=[DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    $registered=$false
    do {
        try {
            $app=Invoke-RestMethod "$($discoveryUrl)apps/ACCOUNT-SERVICE" -Headers @{Accept='application/json'} -TimeoutSec 2
            $registered=@($app.application.instance | Where-Object { $_.status -eq 'UP' -and $_.port.'$' -eq $AccountPort }).Count -gt 0
        } catch { }
        if(-not $registered) { Start-Sleep -Milliseconds 500 }
    } while(-not $registered -and [DateTime]::UtcNow -lt $deadline)
    if(-not $registered) { throw 'account-service is not registered in Eureka.' }
    $transactionArguments=$clientArguments
    if($NoDemoData) { $transactionArguments+='--bank.demo-data.enabled=false' }
    Start-BankService 'transaction-service' $TransactionPort $transactionArguments
    Start-BankService 'gateway-service' $GatewayPort $clientArguments
    Write-Host "Gateway: http://localhost:$GatewayPort | Eureka: http://localhost:$DiscoveryPort | Config: $configUrl"
} catch {
    & (Join-Path $PSScriptRoot 'Stop-BankServices.ps1') -DiscoveryPort $DiscoveryPort
    throw
}