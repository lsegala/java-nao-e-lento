#!/usr/bin/env bash
# Bônus (slide 17): compara tempo de startup e memória entre runtimes.
# Uso:
#   ./scripts/startup.sh preparar-aot   # JDK 25: extrai o JAR e grava o AOT cache (execução de treino)
#   ./scripts/startup.sh preparar-native # GraalVM: compila o executável nativo (leva minutos)
#   ./scripts/startup.sh comparar        # mede o que estiver disponível
# Precisa do Postgres e do serviço externo no ar, e da porta 8080 livre.
set -euo pipefail
cd "$(dirname "$0")/.."

JAR=target/java-nao-e-lento-demo.jar
EXTRAIDO=target/extraido
AOT=target/app.aot
NATIVO=target/java-nao-e-lento-demo
ARGS=(--demo.modo=otimizado)

# Sobe o comando em segundo plano, espera /info responder e mede tempo e memória (RSS).
medir() {
  local rotulo=$1; shift
  local inicio fim pid rss
  inicio=$(date +%s%N)
  "$@" > "target/startup-$rotulo.log" 2>&1 &
  pid=$!
  until curl -sf localhost:8080/info > /dev/null; do
    if ! kill -0 "$pid" 2> /dev/null; then
      echo "$rotulo: o processo terminou antes de responder (veja target/startup-$rotulo.log)"
      return
    fi
    sleep 0.01
  done
  fim=$(date +%s%N)
  rss=$(ps -o rss= -p "$pid" | tr -d ' ')
  printf '%-22s %6d ms até responder   RSS %5d MB\n' "$rotulo" $(( (fim - inicio) / 1000000 )) $(( rss / 1024 ))
  kill "$pid"
  wait "$pid" 2> /dev/null || true
  sleep 1
}

case "${1:-comparar}" in
  preparar-aot)
    # O AOT cache só aceita JARs comuns no classpath, não os JARs aninhados do fat jar do Spring Boot.
    rm -rf "$EXTRAIDO"
    java -Djarmode=tools -jar "$JAR" extract --destination "$EXTRAIDO"
    # Execução de treino: sobe o contexto, sai logo depois e grava o cache (JEP 514, JDK 25).
    java -XX:AOTCacheOutput="$AOT" -Dspring.context.exit=onRefresh -jar "$EXTRAIDO/java-nao-e-lento-demo.jar" "${ARGS[@]}"
    echo ">> AOT cache gravado em $AOT"
    ;;
  preparar-native)
    mvn -Pnative -DskipTests native:compile
    echo ">> Executável em $NATIVO"
    ;;
  comparar)
    medir "HotSpot (fat jar)" java -jar "$JAR" "${ARGS[@]}"
    if [[ -d "$EXTRAIDO" ]]; then
      medir "HotSpot (extraído)" java -jar "$EXTRAIDO/java-nao-e-lento-demo.jar" "${ARGS[@]}"
    fi
    if [[ -f "$AOT" ]]; then
      medir "HotSpot + AOT cache" java -XX:AOTCache="$AOT" -jar "$EXTRAIDO/java-nao-e-lento-demo.jar" "${ARGS[@]}"
    fi
    if [[ -x "$NATIVO" ]]; then
      medir "Native Image" "$NATIVO" "${ARGS[@]}"
    fi
    ;;
  *)
    echo "Uso: $0 preparar-aot|preparar-native|comparar" >&2
    exit 1
    ;;
esac
