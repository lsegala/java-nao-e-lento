<#
.SYNOPSIS
  Aquece, grava JFR e mede uma rodada com o k6.
.EXAMPLE
  .\scripts\carga.ps1 rodada-0
  .\scripts\carga.ps1 rodada-2 3000
  Variáveis de ambiente opcionais: AQUECIMENTO=30s, DURACAO=60s, RATE=1500
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_-]+$')]   # sem espaços: o nome vira nome de arquivo para o jcmd
    [string]$Nome,

    [int]$Taxa = 0
)

. "$PSScriptRoot/comum.ps1"

if ($Taxa -le 0) { $Taxa = [int](Get-EnvOrDefault 'RATE' '1500') }
$aquecimento = Get-EnvOrDefault 'AQUECIMENTO' '30s'
$duracao = Get-EnvOrDefault 'DURACAO' '60s'
New-Item -ItemType Directory -Force -Path 'resultados' | Out-Null

$processo = Assert-DemoRodando

Write-Host '>> Configuração atual:'
Invoke-RestMethod -Uri 'http://localhost:8080/info' | ConvertTo-Json -Compress | Write-Host

Write-Host ">> Aquecimento: $aquecimento a $Taxa req/s (resultado descartado)"
& k6 run -q -e "RATE=$Taxa" -e "DURATION=$aquecimento" k6/relatorio.js | Out-Null

Write-Host ">> Medição: $duracao a $Taxa req/s, com JFR gravando"
# Caminho relativo: a API foi iniciada na mesma pasta (raiz do projeto), então o arquivo cai em resultados\.
# O limite de 1 ms faz o JFR registrar as esperas curtas do lock do gargalo 2 (o padrão é 10 ms).
& jcmd $processo JFR.start name=rodada settings=profile 'jdk.JavaMonitorEnter#threshold=1ms' "filename=resultados/$Nome.jfr" | Out-Null
& k6 run -e "RATE=$Taxa" -e "DURATION=$duracao" k6/relatorio.js | Tee-Object -FilePath "resultados/$Nome-k6.txt"
& jcmd $processo JFR.stop name=rodada | Out-Null

Write-Host ''
Write-Host ">> Pronto: resultados\$Nome-k6.txt e resultados\$Nome.jfr"
Write-Host "   Análise rápida: .\scripts\analisar.ps1 resultados\$Nome.jfr"
Write-Host '   Visual: abra o .jfr no JDK Mission Control'
