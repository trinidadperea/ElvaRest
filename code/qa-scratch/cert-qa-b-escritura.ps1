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

# A1: alta que requiere fecha SIN fecha -> error claro, sin persistir
$r = Post-Cert @{ 'docente.id' = 1; 'tipoCertificado.id' = 1; fechaEmision = '2026-06-01'; requiereVencimiento = 'true'; presentado = 'false' }
Check 'A1 alta sin fecha muestra error claro' ($r.Content -match 'debe indicarse la fecha de vencimiento')

# A2: alta que requiere fecha CON fecha valida -> exito
$r = Post-Cert @{ 'docente.id' = 2; 'tipoCertificado.id' = 2; fechaEmision = '2026-06-10'; fechaVencimiento = '2027-06-01'; requiereVencimiento = 'true'; presentado = 'false' }
Check 'A2 alta con fecha exitosa' ($r.Content -match 'fue realizada correctamente')

# A3: alta sin requerir vencimiento, sin fecha -> exito
$r = Post-Cert @{ 'docente.id' = 3; 'tipoCertificado.id' = 3; fechaEmision = '2026-06-11'; requiereVencimiento = 'false'; presentado = 'false' }
Check 'A3 alta sin vencimiento exitosa' ($r.Content -match 'fue realizada correctamente')

# A4: alta sin requerir vencimiento CON fecha -> rechazo del Servidor (compatibilidad)
$r = Post-Cert @{ 'docente.id' = 4; 'tipoCertificado.id' = 4; fechaEmision = '2026-06-12'; fechaVencimiento = '2027-06-12'; requiereVencimiento = 'false'; presentado = 'false' }
Check 'A4 fecha indebida rechazada por Servidor' ($r.Content -match 'no debe tener fecha de vencimiento')

# E1..E4: completar las 4 fichas existentes con sus fechas de vencimiento
$r = Post-Cert @{ id = 1; 'docente.id' = 1; 'tipoCertificado.id' = 1; fechaEmision = '2026-02-10'; fechaVencimiento = '2027-02-10'; requiereVencimiento = 'true'; presentado = 'true' }
Check 'E1 ficha 1 guardada con fecha' ($r.Content -match 'fue realizada correctamente')
$r = Post-Cert @{ id = 2; 'docente.id' = 2; 'tipoCertificado.id' = 2; fechaEmision = '2026-03-10'; fechaVencimiento = '2028-03-10'; requiereVencimiento = 'true'; presentado = 'true' }
Check 'E2 ficha 2 guardada con fecha' ($r.Content -match 'fue realizada correctamente')
$r = Post-Cert @{ id = 3; 'docente.id' = 3; 'tipoCertificado.id' = 3; fechaEmision = '2026-08-10'; fechaVencimiento = '2027-08-10'; requiereVencimiento = 'true'; presentado = 'true' }
Check 'E3 ficha 3 guardada con fecha' ($r.Content -match 'fue realizada correctamente')
$r = Post-Cert @{ id = 4; 'docente.id' = 4; 'tipoCertificado.id' = 4; fechaEmision = '2026-09-10'; fechaVencimiento = '2029-09-10'; requiereVencimiento = 'true'; presentado = 'false' }
Check 'E4 ficha 4 guardada con fecha' ($r.Content -match 'fue realizada correctamente')

# E5: edicion sin fecha -> mensaje especifico (valida el aviso del Cliente, no "Error de Sistemas")
$r = Post-Cert @{ id = 1; 'docente.id' = 1; 'tipoCertificado.id' = 1; fechaEmision = '2026-02-10'; requiereVencimiento = 'true'; presentado = 'true' }
Check 'E5 edicion sin fecha con mensaje especifico' (($r.Content -match 'debe indicarse la fecha de vencimiento') -and (-not ($r.Content -match 'Error de Sistemas')))

# V1: el formulario de edicion muestra la fecha actual y la marca obligatoria
$r = Invoke-WebRequest -Uri "$base/certificados/1/editar" -WebSession $s -UseBasicParsing
Check 'V1 edicion muestra value=2027-02-10' ($r.Content -match 'value="2027-02-10"')
Check 'V1b required presente cuando requiere vencimiento' ($r.Content -match 'id="fechaVencimiento"[^>]*required')

# V2: el detalle muestra la fecha
$r = Invoke-WebRequest -Uri "$base/certificados/1" -WebSession $s -UseBasicParsing
Check 'V2 detalle muestra 10/02/2027' (($r.Content -match 'Fecha de vencimiento') -and ($r.Content -match '10/02/2027'))

# V3: el Servidor expone el formato esperado (ISO yyyy-MM-dd)
$r = Invoke-WebRequest -Uri "$srv/certificados/1" -UseBasicParsing
Check 'V3 JSON del Servidor con fecha ISO' ($r.Content -match '"fechaVencimiento":"2027-02-10"')

Write-Output 'SCRIPT B COMPLETO'
