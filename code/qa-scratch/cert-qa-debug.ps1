$base = 'http://localhost:8090'
$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$l = Invoke-WebRequest -Uri "$base/login" -WebSession $s -UseBasicParsing
$t = [regex]::Match($l.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
Invoke-WebRequest -Uri "$base/login" -Method Post -WebSession $s -UseBasicParsing -Body @{ username = 'admin'; password = 'admin123'; _csrf = $t } | Out-Null

$r = Invoke-WebRequest -Uri "$base/certificados/nuevo" -WebSession $s -UseBasicParsing
Write-Output '--- TAIL certificados/nuevo ---'
Write-Output $r.Content.Substring([Math]::Max(0, $r.Content.Length - 900))

$r2 = Invoke-WebRequest -Uri "$base/reportes" -WebSession $s -UseBasicParsing
Write-Output ('--- reportes id=scripts: ' + ($r2.Content -match 'id="scripts"'))
Write-Output ('--- reportes chart canvas: ' + ($r2.Content -match 'reporteHorasChart'))
