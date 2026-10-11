$root = 'C:\Users\perla\repositorios\ElvaRest\code\Cliente\src\main\resources\templates'
$total = 0
$sospechosas = 0
Get-ChildItem -Recurse -Filter *.html $root | ForEach-Object {
  $lineas = Select-String -Path $_.FullName -Pattern 'base :: layout'
  foreach ($l in $lineas) {
    $total++
    $linea = $l.Line.Trim()
    if ($linea -match ", ''\)\}") { }
    elseif ($linea -match "~\{::#scripts\}\)\}") { }
    else {
      $sospechosas++
      Write-Output ("SOSPECHOSA>> " + $_.Name + " >> " + $linea)
    }
  }
}
Write-Output ("TOTAL llamadas layout: " + $total)
Write-Output ("SOSPECHOSAS (no 4 argumentos): " + $sospechosas)
Write-Output 'INVENTARIO COMPLETO'
