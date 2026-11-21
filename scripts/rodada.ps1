<#
.SYNOPSIS
  Sobe a API na configuração de uma rodada da demo.
.EXAMPLE
  .\scripts\rodada.ps1 0     # baseline: código lento, platform threads, G1
  .\scripts\rodada.ps1 1     # código otimizado, platform threads, G1
  .\scripts\rodada.ps1 2     # código otimizado, virtual threads, G1 (3a é igual a 2)
  .\scripts\rodada.ps1 3b    # código otimizado, virtual threads, ZGC
  $env:JAVA_OPTS = '-XX:+UseCompactObjectHeaders'; .\scripts\rodada.ps1 2
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('0', '1', '2', '3a', '3b')]
    [string]$Rodada
)

. "$PSScriptRoot/comum.ps1"

if (-not (Test-Path $JarDemo)) {
    throw 'JAR não encontrado. Gere com: mvn -q package'
}

switch ($Rodada) {
    '0'  { $modo = 'lento';     $vt = 'false'; $gc = '-XX:+UseG1GC'; $desc = 'baseline: código lento, platform threads, G1' }
    '1'  { $modo = 'otimizado'; $vt = 'false'; $gc = '-XX:+UseG1GC'; $desc = 'código otimizado, platform threads, G1' }
    '2'  { $modo = 'otimizado'; $vt = 'true';  $gc = '-XX:+UseG1GC'; $desc = 'código otimizado, virtual threads, G1' }
    '3a' { $modo = 'otimizado'; $vt = 'true';  $gc = '-XX:+UseG1GC'; $desc = 'código otimizado, virtual threads, G1' }
    '3b' { $modo = 'otimizado'; $vt = 'true';  $gc = '-XX:+UseZGC';  $desc = 'código otimizado, virtual threads, ZGC' }
}

# Heap fixo para as rodadas serem comparáveis entre si.
$argumentosJava = @('-Xms2g', '-Xmx2g', $gc)
$extras = Get-EnvOrDefault 'JAVA_OPTS' ''
if ($extras) {
    $argumentosJava += ($extras -split '\s+' | Where-Object { $_ })
}
$argumentosJava += @('-jar', $JarDemo, "--demo.modo=$modo", "--spring.threads.virtual.enabled=$vt")

Write-Host ">> Rodada ${Rodada}: $desc"
& java @argumentosJava
