#!/usr/bin/env bash
# Flame graph com o async-profiler enquanto a carga roda (em outro terminal).
# Uso: ./scripts/perfil.sh cpu|alloc|lock [segundos]
# Requer o comando 'asprof' no PATH: https://github.com/async-profiler/async-profiler/releases
# No Linux, uma vez por boot:
#   sudo sysctl kernel.perf_event_paranoid=1
#   sudo sysctl kernel.kptr_restrict=0
set -euo pipefail
cd "$(dirname "$0")/.."

EVENTO=${1:-cpu}
SEGUNDOS=${2:-30}
mkdir -p resultados

PID=$(jcmd | awk '/java-nao-e-lento-demo/ {print $1; exit}')
if [[ -z "$PID" ]]; then
  echo "API não encontrada. Suba antes com ./scripts/rodada.sh <rodada>" >&2
  exit 1
fi

SAIDA="resultados/flame-$EVENTO-$(date +%H%M%S).html"
asprof -d "$SEGUNDOS" -e "$EVENTO" -f "$SAIDA" "$PID"
echo ">> Flame graph em $SAIDA (abra no navegador)"
