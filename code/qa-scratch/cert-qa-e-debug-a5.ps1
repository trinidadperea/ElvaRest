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
  fechaVencimiento = '2026-01-05'; requiereVencimiento = 'true'; presentado = 'false'; _csrf = $token2
}

$m = [regex]::Matches($r.Content, '(?s)alert[^>]*>(.{0,400}?)</div>')
foreach ($x in $m) { Write-Output ('ALERT>> ' + ($x.Groups[1].Value -replace '\s+', ' ')) }

$m2 = [regex]::Matches($r.Content, 'posterior[^<>]{0,80}')
foreach ($x in $m2) {
  $t = $x.Value
  Write-Output ('MATCH>> ' + $t)
  foreach ($ch in $t.ToCharArray()) {
    if ([int]$ch -gt 127) { Write-Output ('  no-ascii: ' + [int]$ch) }
  }
}

Write-Output 'DEBUG A5 COMPLETO'
