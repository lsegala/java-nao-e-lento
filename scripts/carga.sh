#!/usr/bin/env bash
# Aquece, grava JFR e mede uma rodada com o k6.
# Uso: ./scripts/carga.sh <nome-da-rodada> [req/s]
#   ./scripts/carga.sh rodada-0
#   ./scripts/carga.sh rodada-2 3000
# Variáveis: AQUECIMENTO=30s DURACAO=60s RATE=1500
set -euo pipefail
cd "$(dirname "$0")/.."

NOME=${1:?Uso: $0 <nome-da-rodada> [req/s]}
RATE=${2:-${RATE:-1500}}
AQUECIMENTO=${AQUECIMENTO:-30s}
DURACAO=${DURACAO:-60s}
mkdir -p resultados

PID=$(jcmd | awk '/java-nao-e-lento-demo/ {print $1; exit}')
if [[ -z "$PID" ]]; then
  echo "API não encontrada. Suba antes com ./scripts/rodada.sh <rodada>" >&2
  exit 1
fi

echo ">> Configuração atual:"
curl -s localhost:8080/info; echo

echo ">> Aquecimento: $AQUECIMENTO a $RATE req/s (resultado descartado)"
k6 run -q -e RATE="$RATE" -e DURATION="$AQUECIMENTO" k6/relatorio.js > /dev/null || true

echo ">> Medição: $DURACAO a $RATE req/s, com JFR gravando"
# O limite de 1 ms faz o JFR registrar as esperas curtas do lock do gargalo 2 (o padrão é 10 ms).
jcmd "$PID" JFR.start name=rodada settings=profile 'jdk.JavaMonitorEnter#threshold=1ms' filename="$PWD/resultados/$NOME.jfr" > /dev/null
k6 run -e RATE="$RATE" -e DURATION="$DURACAO" k6/relatorio.js | tee "resultados/$NOME-k6.txt" || true
jcmd "$PID" JFR.stop name=rodada > /dev/null

echo
echo ">> Pronto: resultados/$NOME-k6.txt e resultados/$NOME.jfr"
echo "   Análise rápida: ./scripts/analisar.sh resultados/$NOME.jfr"
echo "   Visual: abra o .jfr no JDK Mission Control"
