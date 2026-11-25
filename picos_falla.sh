#!/bin/bash
# Muestra las operaciones lentas (las afectadas por una caida). Uso: ./picos_falla.sh tiempos.csv [umbral_ms]
UMBRAL=${2:-4000}
echo "Operaciones con mas de $UMBRAL ms en $1:"
awk -F, -v u="$UMBRAL" 'NR==1 || $4>u' "$1" | column -s, -t
echo -n "Total: "; awk -F, -v u="$UMBRAL" 'NR>1 && $4>u' "$1" | wc -l
