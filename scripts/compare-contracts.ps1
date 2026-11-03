param(
    [Parameter(Mandatory=$true)][string]$JavaBaseUrl,
    [Parameter(Mandatory=$true)][string]$DotnetBaseUrl,
    [Parameter(Mandatory=$true)][Guid]$PublicationId,
    [Parameter(Mandatory=$true)][Guid]$CategoryId
)
$ErrorActionPreference = 'Stop'
# Both services must point at separate test databases restored from the same fixture.
$paths = @('/categorias',"/categorias/$CategoryId",'/publicaciones',"/publicaciones/$PublicationId", "/publicaciones/$PublicationId/imagenes")
function Normalize-Json($value) {
    if ($null -eq $value) { return 'null' }
    if ($value -is [System.Collections.IDictionary]) {
        $members = foreach ($key in ($value.Keys | Sort-Object)) { (ConvertTo-Json -InputObject $key -Compress) + ':' + (Normalize-Json $value[$key]) }
        return '{' + ($members -join ',') + '}'
    }
    if ($value -is [array]) { return '[' + (($value | ForEach-Object { Normalize-Json $_ }) -join ',') + ']' }
    return ConvertTo-Json -InputObject $value -Depth 20 -Compress
}
foreach ($path in $paths) {
    $java = Invoke-WebRequest ($JavaBaseUrl.TrimEnd('/') + '/api/v1/catalogo' + $path)
    $net = Invoke-WebRequest ($DotnetBaseUrl.TrimEnd('/') + '/api/v1/catalogo' + $path)
    if ($java.StatusCode -ne $net.StatusCode) { throw "HTTP contract differs: $path" }
    $a = Normalize-Json (ConvertFrom-Json -InputObject $java.Content -AsHashtable)
    $b = Normalize-Json (ConvertFrom-Json -InputObject $net.Content -AsHashtable)
    if ($a -ne $b) { throw "JSON contract differs: $path (inspect synthetic fixtures in an isolated environment)" }
    Write-Output "PASS $path"
}
