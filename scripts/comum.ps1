# Funções compartilhadas pelos scripts .ps1 da demo. Carregado com: . "$PSScriptRoot/comum.ps1"

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Todos os scripts rodam a partir da raiz do projeto, como a API.
# Assim, caminhos relativos (resultados/...) valem igual para o script e para a JVM.
Set-Location (Join-Path $PSScriptRoot '..')

$Script:JarDemo = 'target/java-nao-e-lento-demo.jar'

# Retorna o PID da API (procurado na saída do jcmd) ou $null.
function Get-DemoPid {
    # No Windows PowerShell 5.1, redirecionar o stderr de um programa externo com 'Stop' ativo pode virar exceção.
    $ErrorActionPreference = 'Continue'
    $linha = & jcmd 2>$null | Where-Object { $_ -match 'java-nao-e-lento-demo' } | Select-Object -First 1
    if (-not $linha) { return $null }
    return ($linha.Trim() -split '\s+')[0]
}

function Assert-DemoRodando {
    $processo = Get-DemoPid
    if (-not $processo) {
        throw 'API não encontrada. Suba antes com .\scripts\rodada.ps1 <rodada>'
    }
    return $processo
}

# Valor de variável de ambiente ou um padrão.
function Get-EnvOrDefault([string]$Nome, [string]$Padrao) {
    $valor = [Environment]::GetEnvironmentVariable($Nome)
    if ([string]::IsNullOrWhiteSpace($valor)) { return $Padrao }
    return $valor
}
