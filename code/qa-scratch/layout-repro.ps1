$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8090'

$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$l = Invoke-WebRequest -Uri "$base/login" -WebSession $s -UseBasicParsing
$t = [regex]::Match($l.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
Invoke-WebRequest -Uri "$base/login" -Method Post -WebSession $s -UseBasicParsing -Body @{ username = 'admin'; password = 'admin123'; _csrf = $t } | Out-Null

function Check($nombre, $cond) {
  if ($cond) { Write-Output "OK  $nombre" } else { Write-Output "FAIL $nombre" }
}

$r = Invoke-WebRequest -Uri "$base/reportes" -WebSession $s -UseBasicParsing
Check 'reportes: canvas reporteHorasChart presente' ($r.Content -match 'id="reporteHorasChart"')
Check 'reportes: id=scripts presente' ($r.Content -match 'id="scripts"')
Check 'reportes: script de inicializacion Chart presente' ($r.Content -match 'new Chart\(canvas')
Check 'reportes: config Chart.js (foot.js) presente' ($r.Content -match 'chart.umd.min.js')

$r2 = Invoke-WebRequest -Uri "$base/dashboard" -WebSession $s -UseBasicParsing
Check 'dashboard: canvas chartEstados presente' ($r2.Content -match 'id="chartEstados"')
Check 'dashboard: id=scripts presente' ($r2.Content -match 'id="scripts"')
Check 'dashboard: script de inicializacion Chart presente' ($r2.Content -match 'new Chart\(canvas')

$r3 = Invoke-WebRequest -Uri "$base/certificados/nuevo" -WebSession $s -UseBasicParsing
Check 'certificados/nuevo: id=scripts presente' ($r3.Content -match 'id="scripts"')
Check 'certificados/nuevo: script de sincronizacion inline' ($r3.Content -match 'sincronizar')

Write-Output 'REPRODUCCION COMPLETA'
