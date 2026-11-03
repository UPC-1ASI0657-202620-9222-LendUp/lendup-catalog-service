param(
    [Parameter(Mandatory=$true)][string]$MySqlContainer,
    [string]$Database = 'lendup_catalogo'
)
$ErrorActionPreference='Stop'
if ($Database -notmatch '^[A-Za-z0-9_]+$') { throw 'Invalid database name' }
if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or [string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { throw 'Load DB_USERNAME and DB_PASSWORD first' }
$containerDatabase = docker exec $MySqlContainer printenv MYSQL_DATABASE
if ($LASTEXITCODE -ne 0 -or "$containerDatabase".Trim() -ne $Database) { throw 'Selected database must match MYSQL_DATABASE of the selected container' }
# Inspect only the explicitly selected container. Refuse all existing schemas,
# including an existing Flyway history. No baseline-on-migrate or destructive DDL.
$count = docker exec $MySqlContainer sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -N -s -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"' 2>$null
if ($LASTEXITCODE -ne 0 -or "$count".Trim() -ne '0') { throw 'Database is not empty or cannot be inspected; no migration executed' }
$schemaPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../src/main/resources/db/migration'))
if (-not (Test-Path -LiteralPath (Join-Path $schemaPath 'V1__create_catalog_schema.sql'))) { throw 'Flyway V1 not found' }
$env:FLYWAY_URL="jdbc:mysql://127.0.0.1:3306/${Database}?allowPublicKeyRetrieval=true&useSSL=false"
$env:FLYWAY_USER=$env:DB_USERNAME
$env:FLYWAY_PASSWORD=$env:DB_PASSWORD
try {
    docker run --rm --network "container:$MySqlContainer" --mount "type=bind,src=$schemaPath,dst=/flyway/sql,readonly" -e FLYWAY_URL -e FLYWAY_USER -e FLYWAY_PASSWORD flyway/flyway:11.14.1 migrate
    if ($LASTEXITCODE -ne 0) { throw 'Flyway failed; inspect migration history before retrying' }
} finally {
    Remove-Item Env:FLYWAY_URL,Env:FLYWAY_USER,Env:FLYWAY_PASSWORD -ErrorAction SilentlyContinue
}
