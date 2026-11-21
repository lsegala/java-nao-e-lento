#!/usr/bin/env bash
# Resumo de uma gravação JFR no terminal (jfr view, JDK 21+).
# Uso: ./scripts/analisar.sh resultados/rodada-0.jfr
set -euo pipefail
ARQUIVO=${1:?Uso: $0 <arquivo.jfr>}

# Cada view liga a gravação a um conceito da palestra:
#   hot-methods                -> gargalos 1 e 3 (CPU)
#   allocation-by-class        -> gargalos 1 e 4 (alocação)
#   contention-by-site         -> gargalo 2 (synchronized)
#   gc-pauses                  -> gargalo 6 (G1 x ZGC)
#   deoptimizations-by-reason  -> slide 5 (deoptimização)
#   pinned-threads             -> slide 13 (pinning de virtual threads)
for VIEW in hot-methods allocation-by-class contention-by-site gc-pauses deoptimizations-by-reason pinned-threads; do
  echo
  echo "================ $VIEW ================"
  jfr view --width 160 "$VIEW" "$ARQUIVO" || echo "(view '$VIEW' não disponível neste JDK)"
done

echo
echo "================ resumo para o slide 22 ================"
java "$(dirname "$0")/ResumoGc.java" "$ARQUIVO"
