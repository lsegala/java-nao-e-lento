<#
.SYNOPSIS
  Bônus (slide 17): compara tempo de startup e memória entre runtimes.
.EXAMPLE
  .\scripts\startup.ps1 preparar-aot      # JDK 25: extrai o JAR e grava o AOT cache (execução de treino)
  .\scripts\startup.ps1 preparar-native   # GraalVM: compila o executável nativo (leva minutos)
  .\scripts\startup.ps1 comparar          # mede o que estiver disponível
  Precisa da porta 8080 livre.
#>
param(
    [ValidateSet('preparar-aot', 'preparar-native', 'comparar')]
    [string]$Acao = 'comparar'
)

. "$PSScriptRoot/comum.ps1"

$extraido = 'target/extraido'
$jarExtraido = "$extraido/java-nao-e-lento-demo.jar"
$aot = 'target/app.aot'
$nativo = if ($env:OS -eq 'Windows_NT' -or $IsWindows) { 'target/java-nao-e-lento-demo.exe' } else { 'target/java-nao-e-lento-demo' }
$argumentosApp = @('--demo.modo=otimizado')

Add-Type -AssemblyName System.Net.Http
$http = New-Object System.Net.Http.HttpClient
$http.Timeout = [TimeSpan]::FromSeconds(1)

function Test-ApiNoAr {
    try {
        return $http.GetAsync('http://localhost:8080/info').GetAwaiter().GetResult().IsSuccessStatusCode
    } catch {
        return $false
    }
}

# Sobe o comando em segundo plano, espera /info responder e mede tempo e memória.
function Measure-Startup([string]$Rotulo, [string]$Executavel, [string[]]$Argumentos) {
    $log = "target/startup-$($Rotulo -replace '[^A-Za-z0-9]', '_')"
    $cronometro = [System.Diagnostics.Stopwatch]::StartNew()
    $proc = Start-Process -FilePath $Executavel -ArgumentList $Argumentos -PassThru -NoNewWindow `
        -RedirectStandardOutput "$log.out.log" -RedirectStandardError "$log.err.log"

    while (-not (Test-ApiNoAr)) {
        if ($proc.HasExited) {
            Write-Host "${Rotulo}: o processo terminou antes de responder (veja $log.out.log)"
            return
        }
        Start-Sleep -Milliseconds 10
    }
    $cronometro.Stop()
    $proc.Refresh()
    $memoriaMb = [math]::Round($proc.WorkingSet64 / 1MB)
    '{0,-22} {1,6} ms até responder   memória {2,5} MB' -f $Rotulo, $cronometro.ElapsedMilliseconds, $memoriaMb | Write-Host

    Stop-Process -Id $proc.Id -Force
    $proc.WaitForExit()
    Start-Sleep -Seconds 1
}

switch ($Acao) {
    'preparar-aot' {
        # O AOT cache só aceita JARs comuns no classpath, não os JARs aninhados do fat jar do Spring Boot.
        if (Test-Path $extraido) { Remove-Item -Recurse -Force $extraido }
        & java '-Djarmode=tools' -jar $JarDemo extract --destination $extraido
        # Execução de treino: sobe o contexto, sai logo depois e grava o cache (JEP 514, JDK 25).
        & java "-XX:AOTCacheOutput=$aot" '-Dspring.context.exit=onRefresh' -jar $jarExtraido @argumentosApp
        Write-Host ">> AOT cache gravado em $aot"
    }
    'preparar-native' {
        & mvn -Pnative -DskipTests native:compile
        Write-Host ">> Executável em $nativo"
    }
    'comparar' {
        Measure-Startup 'HotSpot (fat jar)' 'java' (@('-jar', $JarDemo) + $argumentosApp)
        if (Test-Path $extraido) {
            Measure-Startup 'HotSpot (extraído)' 'java' (@('-jar', $jarExtraido) + $argumentosApp)
        }
        if (Test-Path $aot) {
            Measure-Startup 'HotSpot + AOT cache' 'java' (@("-XX:AOTCache=$aot", '-jar', $jarExtraido) + $argumentosApp)
        }
        if (Test-Path $nativo) {
            Measure-Startup 'Native Image' (Resolve-Path $nativo).Path $argumentosApp
        }
    }
}
