<#
.SYNOPSIS
  Perfil de CPU, alocação ou locks enquanto a carga roda (em outro terminal).
.DESCRIPTION
  Com o async-profiler instalado (comando asprof no PATH: Linux ou macOS), gera um flame graph HTML.
  No Windows o async-profiler não roda; aí o script grava um JFR curto, focado no evento pedido,
  para abrir no JDK Mission Control (Method Profiling / Flame Graph, Memory ou Lock Instances).
.EXAMPLE
  .\scripts\perfil.ps1 cpu 30
  .\scripts\perfil.ps1 lock 30
#>
param(
    [ValidateSet('cpu', 'alloc', 'lock')]
    [string]$Evento = 'cpu',

    [int]$Segundos = 30
)

. "$PSScriptRoot/comum.ps1"

New-Item -ItemType Directory -Force -Path 'resultados' | Out-Null
$processo = Assert-DemoRodando
$hora = Get-Date -Format 'HHmmss'

if (Get-Command asprof -ErrorAction SilentlyContinue) {
    $saida = "resultados/flame-$Evento-$hora.html"
    & asprof -d $Segundos -e $Evento -f $saida $processo
    Write-Host ">> Flame graph em $saida (abra no navegador)"
    return
}

# Sem async-profiler: JFR com a configuração 'profile' e, para locks, limite de 1 ms.
$saida = "resultados/perfil-$Evento-$hora.jfr"
$opcoes = @('name=perfil', 'settings=profile', "duration=${Segundos}s", "filename=$saida")
if ($Evento -eq 'lock') { $opcoes += 'jdk.JavaMonitorEnter#threshold=1ms' }

Write-Host ">> async-profiler não encontrado; gravando JFR por $Segundos s"
& jcmd $processo JFR.start @opcoes | Out-Null
Start-Sleep -Seconds ($Segundos + 2)

switch ($Evento) {
    'cpu'   { $onde = 'Method Profiling (e a view Flame Graph)' }
    'alloc' { $onde = 'Memory › TLAB Allocations / Allocation by Class' }
    'lock'  { $onde = 'Lock Instances' }
}
Write-Host ">> Gravação em $saida. No JMC, veja: $onde"
