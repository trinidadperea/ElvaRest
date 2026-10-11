$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8090'
$srv = 'http://localhost:8080'

# Login programatico + token nuevo post-login
$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$login = Invoke-WebRequest -Uri "$base/login" -WebSession $s -UseBasicParsing
$token = [regex]::Match($login.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
Invoke-WebRequest -Uri "$base/login" -Method Post -WebSession $s -UseBasicParsing `
  -Body @{ username = 'admin'; password = 'admin123'; _csrf = $token } | Out-Null

$form = Invoke-WebRequest -Uri "$base/certificados/nuevo" -WebSession $s -UseBasicParsing
$token2 = [regex]::Match($form.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value

function Post-Cert([hashtable]$campos) {
  $body = @{}
  foreach ($k in $campos.Keys) { $body[$k] = $campos[$k] }
  $body['_csrf'] = $token2
  return Invoke-WebRequest -Uri "$base/certificados/actualizar" -Method Post -WebSession $s -UseBasicParsing -Body $body
}
function Check($nombre, $cond) {
  if ($cond) { Write-Output "OK  $nombre" } else { Write-Output "FAIL $nombre" }
}

# A4: sin requerir vencimiento pero CON fecha -> mensaje especifico del Cliente (hook), sin persistir
$r = Post-Cert @{ 'docente.id' = 4; 'tipoCertificado.id' = 4; fechaEmision = '2026-06-12'; fechaVencimiento = '2027-06-12'; requiereVencimiento = 'false'; presentado = 'false' }
Check 'A4 fecha indebida muestra mensaje especifico' (($r.Content -match 'no debe tener fecha de vencimiento') -and (-not ($r.Content -match 'Error de Sistema')))

# A5: vencimiento anterior a emision -> mensaje especifico del Cliente (hook), sin persistir
$r = Post-Cert @{ 'docente.id' = 4; 'tipoCertificado.id' = 4; fechaEmision = '2026-06-12'; fechaVencimiento = '2026-01-05'; requiereVencimiento = 'true'; presentado = 'false' }
Check 'A5 vencimiento anterior a emision muestra mensaje' (($r.Content -match 'debe ser posterior a la fecha de') -and (-not ($r.Content -match 'Error de Sistema')))

# A6: los valores se conservan en el formulario re-renderizado tras el error de A5
Check 'A6 formulario conserva valores tras error' (($r.Content -match 'value="2026-01-05"') -and ($r.Content -match 'value="2026-06-12"'))

# E5: edicion sin fecha -> mensaje especifico (hook), sin "Error de Sistema"
$r = Post-Cert @{ id = 1; 'docente.id' = 1; 'tipoCertificado.id' = 1; fechaEmision = '2026-02-10'; requiereVencimiento = 'true'; presentado = 'true' }
Check 'E5 edicion sin fecha mensaje especifico' (($r.Content -match 'debe indicarse la fecha de vencimiento') -and (-not ($r.Content -match 'Error de Sistema')))

# V1: el formulario de edicion muestra la fecha actual guardada
$r = Invoke-WebRequest -Uri "$base/certificados/1/editar" -WebSession $s -UseBasicParsing
Check 'V1 edicion muestra value=2027-02-10' ($r.Content -match 'value="2027-02-10"')

# V2: el detalle muestra la fecha
$r = Invoke-WebRequest -Uri "$base/certificados/1" -WebSession $s -UseBasicParsing
Check 'V2 detalle muestra 10/02/2027' (($r.Content -match 'Fecha de vencimiento') -and ($r.Content -match '10/02/2027'))

# V3: el Servidor expone el formato esperado (ISO yyyy-MM-dd)
$r = Invoke-WebRequest -Uri "$srv/certificados/1" -UseBasicParsing
Check 'V3 JSON del Servidor con fecha ISO' ($r.Content -match '"fechaVencimiento":"2027-02-10"')

Write-Output 'SCRIPT C COMPLETO'
