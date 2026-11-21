#!/usr/bin/env bash
# Sobe a API na configuração de uma rodada da demo.
# Uso: ./scripts/rodada.sh 0|1|2|3a|3b
#   0   baseline: código lento, platform threads, G1
#   1   código otimizado, platform threads, G1
#   2   código otimizado, virtual threads, G1   (3a é igual a 2)
#   3b  código otimizado, virtual threads, ZGC
# Flags extras para a JVM: JAVA_OPTS="-XX:+UseCompactObjectHeaders" ./scripts/rodada.sh 2
# Threads do Tomcat (padrão 200): THREADS=50 ./scripts/rodada.sh 1
# Latência simulada do serviço externo (padrão 150 ms): LATENCIA_MS=300 ./scripts/rodada.sh 1
set -euo pipefail
cd "$(dirname "$0")/.."

JAR=target/java-nao-e-lento-demo.jar
if [[ ! -f "$JAR" ]]; then
  echo "JAR não encontrado. Gere com: mvn -q package" >&2
  exit 1
fi

# Heap fixo para as rodadas serem comparáveis entre si.
HEAP="-Xms2g -Xmx2g"

case "${1:-}" in
  0)    MODO=lento;     VT=false; GC=-XX:+UseG1GC; DESC="baseline: código lento, platform threads, G1" ;;
  1)    MODO=otimizado; VT=false; GC=-XX:+UseG1GC; DESC="código otimizado, platform threads, G1" ;;
  2|3a) MODO=otimizado; VT=true;  GC=-XX:+UseG1GC; DESC="código otimizado, virtual threads, G1" ;;
  3b)   MODO=otimizado; VT=true;  GC=-XX:+UseZGC;  DESC="código otimizado, virtual threads, ZGC" ;;
  *)    echo "Uso: $0 0|1|2|3a|3b" >&2; exit 1 ;;
esac

echo ">> Rodada $1: $DESC"
# shellcheck disable=SC2086
exec java $HEAP $GC ${JAVA_OPTS:-} -jar "$JAR" \
  --demo.modo="$MODO" \
  --spring.threads.virtual.enabled="$VT" \
  --server.tomcat.threads.max="${THREADS:-200}" \
  --demo.latencia-externa-ms="${LATENCIA_MS:-150}"
