param(
    [Parameter(Mandatory=$true)][string]$BaseUrl,
    [int]$Requests = 100,
    [int]$Concurrency = 8,
    [string]$Query = '/api/v1/catalogo/publicaciones'
)
$ErrorActionPreference = 'Stop'
if ($Requests -lt 1 -or $Concurrency -lt 1) { throw 'Requests and Concurrency must be positive' }
# Read-only synthetic HTTP load. Use only an explicitly selected test deployment.
$uri = $BaseUrl.TrimEnd('/') + $Query
$samples = 1..$Requests | ForEach-Object -Parallel {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $response = Invoke-WebRequest -Uri $using:uri -TimeoutSec 20
    if ($response.StatusCode -ne 200) { throw 'Unexpected HTTP response' }
    $timer.Stop()
    $timer.Elapsed.TotalMilliseconds
} -ThrottleLimit $Concurrency
$ordered = @($samples | Sort-Object)
[pscustomobject]@{
    workload = 'synthetic-read-only'
    requests = $Requests
    concurrency = $Concurrency
    meanMs = [Math]::Round(($samples | Measure-Object -Average).Average,3)
    p50Ms = [Math]::Round($ordered[[Math]::Floor(($ordered.Count-1)*0.50)],3)
    p95Ms = [Math]::Round($ordered[[Math]::Floor(($ordered.Count-1)*0.95)],3)
} | ConvertTo-Json
