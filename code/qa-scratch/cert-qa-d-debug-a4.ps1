$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8090'

$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$login = Invoke-WebRequest -Uri "$base/login" -WebSession $s -UseBasicParsing
$token = [regex]::Match($login.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
Invoke-WebRequest -Uri "$base/login" -Method Post -WebSession $s -UseBasicParsing `
  -Body @{ username = 'admin'; password = 'admin123'; _csrf = $token } | Out-Null

$form = Invoke-WebRequest -Uri "$base/certificados/nuevo" -WebSession $s -UseBasicParsing
$token2 = [regex]::Match($form.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value

$r = Invoke-WebRequest -Uri "$base/certificados/actualizar" -Method Post -WebSession $s -UseBasicParsing -Body @{
  'docente.id' = 4; 'tipoCertificado.id' = 4; fechaEmision = '2026-06-12'
  fechaVencimiento = '2027-06-12'; requiereVencimiento = 'false'; presentado = 'false'; _csrf = $token2
}

Write-Output ('STATUS: ' + $r.StatusCode)
Write-Output ('URL efectiva: ' + $r.BaseResponse.ResponseUri)
Write-Output ('LARGO: ' + $r.Content.Length)

$m = [regex]::Matches($r.Content, '(?s)alert[^>]*>(.{0,300}?)</div>')
foreach ($x in $m) { Write-Output ('ALERT>> ' + ($x.Groups[1].Value -replace '\s+', ' ')) }

$m2 = [regex]::Matches($r.Content, '(?s)([^<>]{0,120}(vencimiento|correctamente|Error de Sistemas)[^<>]{0,120})')
foreach ($x in $m2) {
  $t = ($x.Groups[1].Value -replace '\s+', ' ').Trim()
  if ($t.Length -gt 10) { Write-Output ('TXT>> ' + $t) }
}
