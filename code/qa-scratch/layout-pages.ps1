$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8090'

$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$l = Invoke-WebRequest -Uri "$base/login" -WebSession $s -UseBasicParsing
$t = [regex]::Match($l.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
Invoke-WebRequest -Uri "$base/login" -Method Post -WebSession $s -UseBasicParsing -Body @{ username = 'admin'; password = 'admin123'; _csrf = $t } | Out-Null

function Check($nombre, $cond) {
  if ($cond) { Write-Output "OK  $nombre" } else { Write-Output "FAIL $nombre" }
}

$rutas = @('/', '/docentes', '/docentes/nuevo', '/cargos', '/cargos/nuevo',
  '/certificados', '/certificados/nuevo', '/concursos', '/concursos/nuevo',
  '/designaciones', '/designaciones/nuevo', '/licencias', '/licencias/nuevo',
  '/alertas', '/alertas/nuevo', '/usuarios', '/usuarios/nuevo', '/academica', '/reportes')

foreach ($ruta in $rutas) {
  try {
    $r = Invoke-WebRequest -Uri "$base$ruta" -WebSession $s -UseBasicParsing
    Check ("pagina " + $ruta + " responde 200") ($r.StatusCode -eq 200)
  } catch {
    Check ("pagina " + $ruta + " responde 200") $false
  }
}

$r = Invoke-WebRequest -Uri "$base/reportes" -WebSession $s -UseBasicParsing
Check 'reportes: canvas presente' ($r.Content -match 'id="reporteHorasChart"')
Check 'reportes: seccion scripts renderizada' ($r.Content -match 'id="scripts"')
Check 'reportes: Chart.js cargado' ($r.Content -match 'chart.umd.min.js')
Check 'reportes: inicializacion Chart presente' ($r.Content -match 'new Chart\(canvas')
Check 'reportes: sin marcas de debug' (-not ($r.Content -match 'MARCA-'))

$r2 = Invoke-WebRequest -Uri "$base/" -WebSession $s -UseBasicParsing
Check 'dashboard: canvas presente' ($r2.Content -match 'id="chartEstados"')
Check 'dashboard: seccion scripts renderizada' ($r2.Content -match 'id="scripts"')
Check 'dashboard: inicializacion Chart presente' ($r2.Content -match 'new Chart\(canvas')

Write-Output 'PAGINAS COMPLETO'
