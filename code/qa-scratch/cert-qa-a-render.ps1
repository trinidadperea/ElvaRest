$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8090'

# Login programatico (el token CSRF se regenera al autenticarse)
$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$login = Invoke-WebRequest -Uri "$base/login" -WebSession $s -UseBasicParsing
$token = [regex]::Match($login.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
Invoke-WebRequest -Uri "$base/login" -Method Post -WebSession $s -UseBasicParsing `
  -Body @{ username = 'admin'; password = 'admin123'; _csrf = $token } | Out-Null

function Check($nombre, $cond) {
  if ($cond) { Write-Output "OK  $nombre" } else { Write-Output "FAIL $nombre" }
}

$form = Invoke-WebRequest -Uri "$base/certificados/nuevo" -WebSession $s -UseBasicParsing
$c = $form.Content

Check 'autenticado (form visible)' ($c -match 'Guardar certificado')
Check 'label Fecha de vencimiento' ($c -match 'Fecha de vencimiento')
Check 'input id=fechaVencimiento' ($c -match 'id="fechaVencimiento"')
Check 'input type=date' ($c -match 'id="fechaVencimiento"[^>]*type="date"')
Check 'name=fechaVencimiento (binding)' ($c -match 'name="fechaVencimiento"')
Check 'sin required en alta sin vencimiento' (-not ($c -match 'id="fechaVencimiento"[^>]*required'))
Check 'script de sincronizacion inline presente' ($c -match 'sincronizar')
Check 'sin seccion scripts rota (eliminada)' (-not ($c -match 'id="scripts"'))

$form2 = Invoke-WebRequest -Uri "$base/certificados/nuevo/docente/1" -WebSession $s -UseBasicParsing
Check 'input fecha tambien en nuevo/docente/{id}' ($form2.Content -match 'id="fechaVencimiento"')

Write-Output 'SCRIPT A COMPLETO'
