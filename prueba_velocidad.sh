#!/bin/bash
# Prueba de velocidad con 3, 5 y 8 clientes. Uso: ./prueba_velocidad.sh [ipGT] [segundosPorEscenario]
# Guarda todo en resultados/<fecha>/
IP=${1:-10.43.100.138}
DUR=${2:-120}
DIR=resultados/$(date +%Y%m%d_%H%M%S)
mkdir -p "$DIR"
echo "Resultados en $DIR"

for N in 3 5 8; do
  echo ">>> Escenario de $N clientes"
  java -cp out:jeromq-0.5.3.jar ClienteMasivo --config pruebas/escenario_${N}_clientes.properties \
       --ip "$IP" --duracion "$DUR" --salida "$DIR" | tee "$DIR/consola_${N}_clientes.txt"
  sleep 5
done

# un solo CSV con los tres escenarios
echo "Clientes,Operacion,Total,Exitosas,Promedio_ms,DesvStd_ms,Max_ms,TPM" > "$DIR/resumen_velocidad.csv"
for N in 3 5 8; do
  tail -n +2 "$DIR/metricas_${N}_clientes.csv" >> "$DIR/resumen_velocidad.csv"
done
echo; echo "=== RESUMEN ==="; column -s, -t "$DIR/resumen_velocidad.csv"
