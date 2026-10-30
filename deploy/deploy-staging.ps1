# Nasazení na testovací server test.storyhold.cz (Windows PowerShell 5.1).
# Image se builduje tady, na server jde jako soubor přes scp (ne rourou: PowerShell by binární data poškodil).
# Předpoklady: Docker Desktop, SSH alias „storyhold“, na serveru ~/storyhold/.env (viz README).
#
#   .\deploy\deploy-staging.ps1             # build + nasazení
#   .\deploy\deploy-staging.ps1 -SkipBuild  # nasadí image storyhold-app:staging, který už je postavený

param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$image = 'storyhold-app:staging'
$tar = Join-Path $env:TEMP 'storyhold-app.tar'

function Invoke-Native([string]$what, [scriptblock]$command) {
    Write-Host "==> $what" -ForegroundColor Cyan
    & $command
    if ($LASTEXITCODE -ne 0) { throw "$what selhalo (kód $LASTEXITCODE)" }
}

# Skript se připojuje víckrát, heslo ke klíči proto musí mít ssh-agent (jinak by server čekání na heslo ukončil)
ssh -o BatchMode=yes storyhold "true"
if ($LASTEXITCODE -ne 0) { throw 'SSH se nepřipojí bez hesla. Načti klíč do ssh-agenta: ssh-add $HOME\.ssh\storyhold_ed25519 (viz README).' }
ssh -o BatchMode=yes storyhold "test -f storyhold/.env"
if ($LASTEXITCODE -ne 0) { throw 'Na serveru chybí ~/storyhold/.env, vytvoř ho podle README (první nasazení).' }

if (-not $SkipBuild) {
    $commit = git -C $root rev-parse --short HEAD
    if (git -C $root status --porcelain --untracked-files=no) {
        Write-Warning "Pracovní kopie obsahuje necommitnuté změny, nasadí se i ty (commit $commit + změny)."
    }
    Invoke-Native "Build image $image ($commit)" {
        docker build --label "org.opencontainers.image.revision=$commit" -t $image $root
    }
}

Invoke-Native 'Uložení image do souboru' { docker save -o $tar $image }

try {
    Invoke-Native 'Nahrání na server' {
        scp "$root\deploy\staging\compose.yaml" "$root\deploy\staging\Caddyfile" $tar storyhold:storyhold/
    }
    Invoke-Native 'Načtení image a spuštění' {
        ssh storyhold ("cd storyhold && chmod 600 .env && docker load -i storyhold-app.tar && rm storyhold-app.tar" +
            " && docker compose up -d --remove-orphans" +
            " && docker compose exec -T -w /etc/caddy caddy caddy reload" +
            " && docker image prune -f")
    }
} finally {
    Remove-Item $tar -ErrorAction SilentlyContinue
}

Write-Host '==> Čekám, až bude aplikace zdravá' -ForegroundColor Cyan
for ($i = 0; $i -lt 60; $i++) {
    $health = ssh storyhold "cd storyhold && docker compose ps app --format '{{.Health}}'"
    if ($health -eq 'healthy') {
        Write-Host 'Hotovo: https://test.storyhold.cz' -ForegroundColor Green
        exit 0
    }
    Start-Sleep -Seconds 5
}
throw "Aplikace není zdravá (stav: $health). Logy: ssh storyhold `"cd storyhold && docker compose logs --tail 100 app`""
