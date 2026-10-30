param([int]$DiscoveryPort=8761)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$recordPath=Join-Path $projectRoot ".runtime/services-$DiscoveryPort.json"
if(-not (Test-Path -LiteralPath $recordPath)) { Write-Host 'No managed services found.'; return }
$records=@(Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json)
[array]::Reverse($records)
foreach($record in $records) {
    $matches=Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
        $_.CommandLine -and $_.CommandLine.Contains($record.jar) -and
        (' '+$_.CommandLine+' ').Contains(" --server.port=$($record.port) ")
    }
    foreach($processInfo in $matches) {
        Stop-Process -Id $processInfo.ProcessId -ErrorAction Stop
        Write-Host "Stopped $($record.name) (PID $($processInfo.ProcessId))."
    }
}