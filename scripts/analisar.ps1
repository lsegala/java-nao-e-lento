<#
.SYNOPSIS
  Resumo de uma gravação JFR no terminal (jfr view, JDK 21+).
.EXAMPLE
  .\scripts\analisar.ps1 resultados\rodada-0.jfr
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$Arquivo
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path $Arquivo)) { throw "Arquivo não encontrado: $Arquivo" }

# Cada view liga a gravação a um conceito da palestra:
#   hot-methods                -> gargalos 3 e 4 (CPU)
#   allocation-by-class        -> gargalo 1 (alocação)
#   contention-by-site         -> gargalo 2 (synchronized)
#   gc-pauses                  -> gargalo 6 (G1 x ZGC)
#   deoptimizations-by-reason  -> slide 5 (deoptimização)
#   pinned-threads             -> slide 13 (pinning de virtual threads)
$views = @('hot-methods', 'allocation-by-class', 'contention-by-site', 'gc-pauses', 'deoptimizations-by-reason', 'pinned-threads')

foreach ($view in $views) {
    Write-Host ''
    Write-Host "================ $view ================"
    & jfr view --width 160 $view $Arquivo
    if ($LASTEXITCODE -ne 0) {
        Write-Host "(view '$view' não disponível neste JDK)"
    }
}
