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
Check 'marca externa (filesystem prefix activo)' ($r.Content -match 'MARCA-BASE-EXTERNA')
Check 'scripts != null' ($r.Content -match 'MARCA-NOT-NULL')
Check 'scripts != null y != vacio' ($r.Content -match 'MARCA-IF-COMPLETO')
Check 'marca dentro del th:if presente' ($r.Content -match 'MARCA-IF-TRUE')
Check 'id=scripts presente' ($r.Content -match 'id="scripts"')
Check 'new Chart presente' ($r.Content -match 'new Chart\(canvas')

$colas = [regex]::Matches($r.Content, '(?s).{0,150}(MARCA-IF-TRUE|chart.umd.min.js|adminlte.min.js).{0,200}')
$i = 0
foreach ($x in $colas) {
  $i++
  if ($i -le 4) { Write-Output ('CTX' + $i + '>> ' + (($x.Value -replace '\s+', ' ').Trim())) }
}
Write-Output 'PROBE COMPLETO'
