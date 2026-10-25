# Готовит локальный PostgreSQL для запуска бота без Docker (профиль local).
#  1. Если в .env пустой POSTGRES_PASSWORD — генерирует его и записывает в .env (на экран не выводит).
#  2. Создаёт пользователя и базу briefbot (повторный запуск безопасен: пароль просто обновится).
# psql спросит пароль суперпользователя postgres — его вводите вы.
#
# Запуск из папки проекта:  powershell -ExecutionPolicy Bypass -File tools\setup-local-db.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"
$psql = "C:\Program Files\PostgreSQL\16\bin\psql.exe"

if (-not (Test-Path $envFile)) { throw "Нет файла .env — скопируйте .env.example в .env и впишите BOT_TOKEN" }
if (-not (Test-Path $psql)) { throw "Не найден psql: $psql" }

$lines = [IO.File]::ReadAllLines($envFile)
$current = ($lines | Where-Object { $_ -match '^POSTGRES_PASSWORD=' } | Select-Object -First 1) -replace '^POSTGRES_PASSWORD=', ''
if ([string]::IsNullOrWhiteSpace($current)) {
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $current = [Convert]::ToBase64String($bytes) -replace '[+/=]', ''
    if ($lines -match '^POSTGRES_PASSWORD=') {
        $lines = $lines | ForEach-Object { if ($_ -match '^POSTGRES_PASSWORD=') { "POSTGRES_PASSWORD=$current" } else { $_ } }
    } else {
        $lines += "POSTGRES_PASSWORD=$current"
    }
    [IO.File]::WriteAllLines($envFile, $lines, (New-Object Text.UTF8Encoding($false)))
    Write-Host "Сгенерировал пароль базы и записал его в .env (POSTGRES_PASSWORD)"
}

$sql = @"
SELECT format('CREATE ROLE briefbot LOGIN PASSWORD %L', :'pw')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'briefbot') \gexec
SELECT format('ALTER ROLE briefbot LOGIN PASSWORD %L', :'pw') \gexec
SELECT 'CREATE DATABASE briefbot OWNER briefbot'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'briefbot') \gexec
"@
$sqlFile = Join-Path $env:TEMP "briefbot-setup.sql"
[IO.File]::WriteAllText($sqlFile, $sql, (New-Object Text.UTF8Encoding($false)))

Write-Host "Введите пароль пользователя postgres (тот, что задавали при установке PostgreSQL):"
try {
    & $psql -h 127.0.0.1 -U postgres -d postgres -v ON_ERROR_STOP=1 -v "pw=$current" -q -f $sqlFile
    if ($LASTEXITCODE -ne 0) { throw "psql завершился с ошибкой" }
} finally {
    Remove-Item $sqlFile -ErrorAction SilentlyContinue
}
Write-Host "Готово: пользователь и база briefbot настроены"
