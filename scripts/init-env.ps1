$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envFile) { throw '.env already exists; refusing to overwrite credentials.' }
function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
$dbPassword = New-LocalSecret
$jwtSecret = New-LocalSecret
$adminPassword = New-LocalSecret
@"
DB_PASSWORD=$dbPassword
JWT_SECRET=$jwtSecret
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=$adminPassword
"@ | Set-Content -LiteralPath $envFile -Encoding ascii
Write-Host 'Created .env with random local secrets. Keep it private. The admin password is inside .env.'
