# Java não é lento — demo

API de relatórios com seis gargalos plantados, usada na parte prática da palestra
“Java não é lento: explorando os limites de performance da JVM”.

`GET /relatorios/{cliente}` busca os pedidos do cliente no PostgreSQL, chama um serviço
externo lento (~150 ms) e monta um relatório em texto. A versão **lenta** e a **otimizada**
geram exatamente o mesmo texto (há um teste que garante isso); só o desempenho muda.

## Pré-requisitos

| Ferramenta | Para quê |
| --- | --- |
| JDK 25 | Rodar a API (o bytecode é Java 21; os recursos da palestra são do runtime 25) |
| Maven 3.9+ | Build |
| Docker | PostgreSQL |
| [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) | Carga |
| JDK Mission Control | Abrir os `.jfr` (opcional, mas recomendado) |
| [async-profiler](https://github.com/async-profiler/async-profiler/releases) | Flame graphs (opcional) |
| GraalVM 25 com `native-image` | Só para o bônus de Native Image |

## Preparar (uma vez)

```bash
docker compose up -d                 # Postgres com 2.000 clientes e 800 mil pedidos
mvn -q test                          # confere que lento e otimizado geram o mesmo relatório
mvn -q package -DskipTests           # gera target/java-nao-e-lento-demo.jar
```

Em um terminal separado, deixe o serviço externo rodando (não precisa compilar):

```bash
java scripts/ServicoExterno.java     # porta 9090, atraso de 150 ms
```

## As rodadas

Cada rodada usa dois terminais: um com a API, outro com a carga.

| Rodada | Terminal 1 (API) | Terminal 2 (carga) | O que mostrar |
| --- | --- | --- | --- |
| 0 · Baseline | `./scripts/rodada.sh 0` | `./scripts/carga.sh rodada-0` | Flame graph e `hot-methods`: `Pattern.compile` no topo; `byte[]` dominando a alocação; locks |
| 1 · Código | `./scripts/rodada.sh 1` | `./scripts/carga.sh rodada-1` | CPU e alocação despencam, mas req/s quase não muda: o teto agora são as 200 threads |
| 2 · Threads | `./scripts/rodada.sh 2` | `./scripts/carga.sh rodada-2` | A vazão passa do teto da Lei de Little; `/info` mostra `threadVirtual: true` |
| 3a · G1 | `./scripts/rodada.sh 3a` | `./scripts/carga.sh rodada-3a 3000` | p99 e pausas com G1 sob taxa alta |
| 3b · ZGC | `./scripts/rodada.sh 3b` | `./scripts/carga.sh rodada-3b 3000` | Mesma carga com ZGC: compare p99, pausas e CPU |

O `carga.sh` faz 30 s de aquecimento (descartados), depois 60 s de medição com o JFR
gravando. Ele salva `resultados/<rodada>-k6.txt` e `resultados/<rodada>.jfr`.

Para analisar uma gravação:

```bash
./scripts/analisar.sh resultados/rodada-0.jfr   # resumo no terminal (jfr view)
./scripts/perfil.sh cpu 30                      # flame graph com async-profiler, durante a carga
./scripts/perfil.sh lock 30                     # contenção de locks (gargalo 2)
```

Para trocar de rodada, basta parar a API (Ctrl+C) e subir com outro número; não precisa
recompilar. A configuração vem de `--demo.modo` e `--spring.threads.virtual.enabled`.

## Onde está cada gargalo

| # | Gargalo | Onde | Correção | Onde aparece |
| --- | --- | --- | --- | --- |
| 1 | `String +=` em laço | `RelatorioLento.montar` | `StringBuilder` (`RelatorioOtimizado`) | `allocation-by-class`: `byte[]` |
| 2 | `synchronized` em volta de `HashMap` | `RelatorioLento.nomeDaCategoria` | `ConcurrentHashMap.computeIfAbsent` | `perfil.sh lock`; JMC › Lock Instances |
| 3 | `Pattern.compile` por pedido | `RelatorioLento.montar` | `static final Pattern` | `hot-methods`: `Pattern.compile`, `Pattern.expr` |
| 4 | `List<Long>` com autoboxing | `RelatorioLento.montar` | `long[]` + `Arrays.sort` | `hot-methods`: `ComparableTimSort` |
| 5 | Chamada bloqueante de 150 ms | `CotacaoClient.buscar` | Virtual threads (rodada 2) | k6: req/s travado em ~1.333 |
| 6 | GC escolhido sem medir | flags da JVM | Comparar G1 × ZGC (rodada 3) | `gc-pauses` + p99 do k6 |

Num teste local isolado, a montagem do relatório levou cerca de 0,64 ms na versão lenta e
0,06 ms na otimizada, por relatório, num único thread. Os números da sua máquina serão outros.

## O que esperar

A taxa padrão é 1.500 req/s, um pouco acima do teto da Lei de Little com 200 platform threads
(200 ÷ 0,15 s ≈ 1.333 req/s). Por isso:

- **Rodadas 0 e 1** não conseguem atender 1.500 req/s: o k6 mostra latência crescendo e
  `dropped_iterations`. A rodada 1 gasta bem menos CPU e aloca bem menos memória, mas a vazão quase
  não muda. É o momento de dizer: “o gargalo dominante agora é outro”.
- **Rodada 2** atende a taxa toda com latência perto de 150 ms. Suba a taxa (`./scripts/carga.sh rodada-2 3000`)
  para achar o próximo limite: CPU, pool de conexões (20) ou o banco.
- **Rodada 3** compara GCs com a mesma taxa alta. Espere pausas bem menores no ZGC; a vazão e
  a CPU podem piorar um pouco. Essa é a troca do slide 9.

Ajuste a taxa à máquina do palco: a rodada 0 deve ficar visivelmente saturada e a rodada 2, folgada.

## Bônus: startup e memória (slide 17)

```bash
./scripts/startup.sh preparar-aot       # JDK 25: extrai o JAR e grava o AOT cache
./scripts/startup.sh preparar-native    # GraalVM: gera o executável nativo (minutos)
./scripts/startup.sh comparar           # mede tempo até responder e RSS de cada opção
```

No executável nativo, `spring.threads.virtual.enabled` é decidido na compilação, não na
execução: o Spring avalia essas condições durante o processamento AOT.

## Microbenchmark (slide 7)

```bash
cd bench
mvn -q clean verify
java -jar target/benchmarks.jar -prof gc
```

Compara `+=` e `StringBuilder` com 100, 400 e 1.000 linhas. O `-prof gc` mostra a alocação por
operação.

## Plano B para o palco

- Rode todas as rodadas no ensaio e guarde a pasta `resultados/`. Se algo falhar ao vivo, abra
  os `.jfr` e os `-k6.txt` salvos.
- Grave um vídeo da demo funcionando como último recurso.
- Teste na mesma máquina, na tomada, com outros programas fechados.

## Problemas comuns

- **`contention-by-site` vazio:** com `settings=profile`, o JFR só registra esperas por lock
  acima de 10 ms, e as esperas deste gargalo costumam ser mais curtas. Use `./scripts/perfil.sh lock`,
  que pega todas.
- **k6 sem instalar:** no Linux, `docker run --rm -i --network host grafana/k6 run - < k6/relatorio.js`.
- **async-profiler sem permissão:** rode os dois `sysctl` indicados em `scripts/perfil.sh`.
- **Porta 8080, 9090 ou 5432 ocupada:** pare o processo ou ajuste `application.properties`
  e o `docker-compose.yml`.
- **Recriar a massa de dados:** `docker compose down -v && docker compose up -d`.
