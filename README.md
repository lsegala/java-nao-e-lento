# Java não é lento — demo

API de relatórios com seis gargalos plantados, usada na parte prática da palestra
“Java não é lento: explorando os limites de performance da JVM”.

`GET /relatorios/{cliente}` pega os pedidos do cliente, espera a resposta de um serviço externo
de cotação (~150 ms) e monta um relatório em texto. A versão **lenta** e a **otimizada** geram
exatamente o mesmo texto (há um teste que garante isso); só o desempenho muda.

Tudo roda num único processo, sem Docker e sem outros serviços:

- **Dados em memória** (`DadosEmMemoria`): 2.000 clientes × 1.000 pedidos, gerados na subida com
  semente fixa (sempre os mesmos), com ~2% de códigos inválidos. Nenhum dos gargalos está no banco,
  e um banco na mesma máquina só disputaria CPU com a JVM.
- **Serviço externo simulado** (`ServicoDeCotacao`): um `Thread.sleep(150)`. Para a JVM, o efeito é
  o mesmo de esperar um socket: a platform thread fica presa; a virtual thread desmonta e libera o
  carrier. Se alguém na plateia perguntar, é exatamente esse o ponto do slide 11.

## Pré-requisitos

| Ferramenta | Para quê |
| --- | --- |
| JDK 25 | Rodar a API (o bytecode é Java 21; os recursos da palestra são do runtime 25) |
| Maven 3.9+ | Build |
| [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) | Carga (no Windows: `winget install k6 --source winget`) |
| JDK Mission Control | Abrir os `.jfr` (opcional, mas recomendado) |
| [async-profiler](https://github.com/async-profiler/async-profiler/releases) | Flame graphs no Linux e macOS (opcional) |
| GraalVM 25 com `native-image` | Só para o bônus de Native Image |

## Preparar (uma vez)

```bash
mvn -q test                   # confere que lento e otimizado geram o mesmo relatório
mvn -q package -DskipTests    # gera target/java-nao-e-lento-demo.jar
```

## As rodadas

Cada rodada usa dois terminais: um com a API, outro com a carga. No Windows, use os `.ps1`
(veja a seção abaixo).

| Rodada | Terminal 1 (API) | Terminal 2 (carga) | O que mostrar |
| --- | --- | --- | --- |
| 0 · Baseline | `./scripts/rodada.sh 0` | `./scripts/carga.sh rodada-0` | CPU saturada; `hot-methods` com `Pattern.compile` no topo; `byte[]` dominando a alocação |
| 1 · Código | `./scripts/rodada.sh 1` | `./scripts/carga.sh rodada-1` | CPU despenca, mas a vazão para perto do teto das threads (Lei de Little) |
| 2 · Threads | `./scripts/rodada.sh 2` | `./scripts/carga.sh rodada-2` | A vazão alcança a taxa pedida e a latência cai para ~150 ms |
| 3a · G1 | `./scripts/rodada.sh 3a` | `./scripts/carga.sh rodada-3a 3000` | Pausas do G1 em `gc-pauses` |
| 3b · ZGC | `./scripts/rodada.sh 3b` | `./scripts/carga.sh rodada-3b 3000` | Pausas abaixo de 1 ms, mas o p99 quase igual ao do G1 (veja abaixo) |

O `carga.sh` faz 30 s de aquecimento (descartados), depois 60 s de medição com o JFR gravando.
Ele salva `resultados/<rodada>-k6.txt` e `resultados/<rodada>.jfr`.

```bash
./scripts/analisar.sh resultados/rodada-0.jfr   # resumo no terminal (jfr view)
./scripts/perfil.sh cpu 30                      # flame graph durante a carga
./scripts/perfil.sh lock 30                     # contenção de locks (gargalo 2)
```

Para trocar de rodada, pare a API (Ctrl+C) e suba com outro número; não precisa recompilar.

### Números do slide 22

| Coluna | De onde vem |
| --- | --- |
| req/s, p50, p99 | Resumo do k6 (`resultados/<rodada>-k6.txt`): `http_reqs`, `http_req_duration` |
| Alocação e Pausas GC | `java scripts/ResumoGc.java resultados/<rodada>.jfr` (também roda no fim do `analisar`) |

O `ResumoGc.java` roda direto do código-fonte, em qualquer sistema. Ele soma os eventos de amostragem
de alocação do JFR (a estimativa do próprio JFR para os bytes alocados) e divide pela duração da gravação;
para conferir, mostra também a estimativa pelo heap antes e depois de cada coleta. As pausas vêm dos
eventos `jdk.GarbageCollection`. No ZGC, ele também conta os *allocation stalls*. Como o `carga` só grava
depois do aquecimento, o resultado cobre exatamente os 60 s medidos.

### Ajustes de calibração

| Variável | Padrão | Efeito |
| --- | --- | --- |
| `RATE` (ou 2º argumento do `carga`) | 1500 | Requests por segundo do k6 |
| `THREADS` | 200 | Threads do Tomcat, o teto do gargalo 5 |
| `LATENCIA_MS` | 150 | Espera simulada do serviço externo |
| `JAVA_OPTS` | vazio | Flags extras da JVM, como `-XX:+UseCompactObjectHeaders` |

O teto do gargalo 5 é `THREADS ÷ LATENCIA` (200 ÷ 0,15 s ≈ 1.333 req/s). Para a história funcionar,
a CPU precisa aguentar mais do que esse teto na rodada 1. Se a CPU saturar antes (o Gerenciador de
Tarefas mostra perto de 100% na rodada 1), baixe o teto: `THREADS=50` dá ≈ 333 req/s; use então uma
taxa um pouco acima, como `RATE=450`. No palco: “reduzi o pool na proporção do notebook”.

## O que esperar

Medições num notebook com i7-1165G7 (4 núcleos, 8 threads), tudo na mesma máquina, 200 threads,
150 ms de espera. As rodadas 0 e 1 usaram 1.000 pedidos por cliente (o padrão atual); as rodadas 2 e 3
foram medidas com 400 e devem ser repetidas com o padrão novo. Os números da sua máquina serão outros;
o formato deve se repetir.

| Rodada | Taxa pedida | Concluídos | p50 | p99 |
| --- | --- | --- | --- | --- |
| 0 · lento | 1.500 req/s | ~500 req/s | — | — |
| 1 · otimizado | 1.500 req/s | ~1.200 req/s | — | — |
| 2 · virtual threads | 1.500 req/s | 1.491 req/s | 160 ms | 266 ms |
| 3a · G1 | 3.000 req/s | 2.932 req/s | 165 ms | 289 ms |
| 3b · ZGC | 3.000 req/s | 2.912 req/s | 165 ms | 417 ms |

- **Rodada 0 → 1:** o código lento satura a CPU bem abaixo do teto das threads; corrigido, a vazão
  sobe até perto de 1.333 req/s (200 ÷ 0,15 s) e para ali. O gargalo dominante agora é outro (slide 14).
- **Rodada 1 → 2:** sem o teto de threads, a vazão acompanha a taxa pedida e a latência vira a própria
  espera de 150 ms (slides 11 e 13).
- **Rodada 3:** com o código otimizado alocando pouco, o G1 quase não pausa, e o ZGC saiu pior no p99,
  porque gasta CPU extra numa máquina que divide a CPU com o k6. A lição é a dos slides 9 e 10:
  **depois de cortar o lixo, trocar o GC quase não muda nada, e o ZGC troca CPU por pausas curtas.**

Se o código lento não saturar a CPU na sua máquina (rodadas 0 e 1 com vazão parecida), aumente os
pedidos por cliente: `JAVA_OPTS="-Ddemo.pedidos-por-cliente=1500"`. O custo da concatenação cresce com o
quadrado do número de linhas. Use o mesmo valor em todas as rodadas.

## No Windows (PowerShell)

Cada script `.sh` tem um `.ps1` equivalente, com os mesmos argumentos:

| Bash | PowerShell |
| --- | --- |
| `./scripts/rodada.sh 0` | `.\scripts\rodada.ps1 0` |
| `THREADS=50 ./scripts/rodada.sh 1` | `$env:THREADS = '50'; .\scripts\rodada.ps1 1` |
| `./scripts/carga.sh rodada-0 1500` | `.\scripts\carga.ps1 rodada-0 1500` |
| `./scripts/analisar.sh resultados/rodada-0.jfr` | `.\scripts\analisar.ps1 resultados\rodada-0.jfr` |
| `./scripts/perfil.sh lock 30` | `.\scripts\perfil.ps1 lock 30` |
| `./scripts/startup.sh comparar` | `.\scripts\startup.ps1 comparar` |

Variáveis de ambiente definidas com `$env:` valem para o resto da sessão do terminal. Para voltar ao
padrão: `Remove-Item Env:THREADS`.

Funcionam no Windows PowerShell 5.1 e no PowerShell 7. Se o Windows bloquear a execução:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

Diferenças no Windows:

- **async-profiler não roda no Windows.** O `perfil.ps1` grava um JFR curto para abrir no JDK Mission
  Control (Method Profiling e Flame Graph para CPU; Lock Instances para locks).
- **Memória no `startup.ps1`:** usa o *working set* do processo, o equivalente do RSS no Windows.
- **Native Image:** o executável é `target\java-nao-e-lento-demo.exe`; a GraalVM no Windows precisa
  das ferramentas de build do Visual Studio (C++).
- **Nomes de rodada sem espaços** (`rodada-0`): o nome vira arquivo passado ao `jcmd`.

## Onde está cada gargalo

| # | Gargalo | Onde | Correção | Onde aparece |
| --- | --- | --- | --- | --- |
| 1 | `String +=` em laço | `RelatorioLento.montar` | `StringBuilder` (`RelatorioOtimizado`) | `allocation-by-class`: `byte[]` |
| 2 | `synchronized` em volta de `HashMap` | `RelatorioLento.nomeDaCategoria` | `ConcurrentHashMap.computeIfAbsent` | `contention-by-site`; `perfil lock` |
| 3 | `Pattern.compile` por pedido | `RelatorioLento.montar` | `static final Pattern` | `hot-methods`: `Pattern.compile`, `Pattern.expr` |
| 4 | `List<Long>` com autoboxing | `RelatorioLento.montar` | `long[]` + `Arrays.sort` | `hot-methods`: `ComparableTimSort` |
| 5 | Espera bloqueante de 150 ms | `ServicoDeCotacao.buscar` | Virtual threads (rodada 2) | k6: vazão travada perto de `THREADS ÷ 0,15 s` |
| 6 | GC escolhido sem medir | flags da JVM | Comparar G1 × ZGC (rodada 3) | `gc-pauses` + p99 do k6 |

## Bônus: startup e memória (slide 17)

```bash
./scripts/startup.sh preparar-aot       # JDK 25: extrai o JAR e grava o AOT cache
./scripts/startup.sh preparar-native    # GraalVM: gera o executável nativo (minutos)
./scripts/startup.sh comparar           # mede tempo até responder e memória de cada opção
```

O tempo inclui gerar os dados em memória (menos de 1 s), igual para todas as opções. No executável
nativo, `spring.threads.virtual.enabled` é decidido na compilação, não na execução: o Spring avalia
essas condições durante o processamento AOT.

## Microbenchmark (slide 7)

```bash
cd bench
mvn -q clean verify
java -jar target/benchmarks.jar -prof gc
```

Compara `+=` e `StringBuilder` com 100, 400 e 1.000 linhas. O `-prof gc` mostra a alocação por operação.

## Plano B para o palco

- Rode todas as rodadas no ensaio e guarde a pasta `resultados/`. Se algo falhar ao vivo, abra os
  `.jfr` e os `-k6.txt` salvos.
- Grave um vídeo da demo funcionando como último recurso.
- Ensaie na mesma máquina, na tomada, em modo de máximo desempenho, com o mínimo de programas abertos.

## Problemas comuns

- **CPU a 100% já na rodada 1:** a máquina não aguenta o teto de 1.333 req/s; veja “Ajustes de calibração”.
- **`contention-by-site` vazio ou magro:** o `carga` baixa o limite do JFR para esperas de 1 ms
  (`jdk.JavaMonitorEnter#threshold=1ms`); esperas menores não aparecem. O `perfil lock` com
  async-profiler pega todas.
- **async-profiler sem permissão (Linux):** rode os dois `sysctl` indicados em `scripts/perfil.sh`.
- **Porta 8080 ocupada:** pare o processo ou rode com `--server.port=8081` (e `BASE_URL` no k6).
