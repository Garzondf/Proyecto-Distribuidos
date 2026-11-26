#!/bin/bash
# Pruebas de la Tabla 1 con 3, 5 y 8 clientes.
# Uso: ./prueba_velocidad.sh [ipGT] [segundosPorEscenario] [base|escalable]
# Antes, en la VM 10.43.100.138 debe estar corriendo la arquitectura indicada (nodo1.sh start base|escalable)
IP=${1:-10.43.100.138}
DUR=${2:-150}
ARQ=${3:-base}
DIR=resultados/${ARQ}_$(date +%Y%m%d_%H%M%S)
mkdir -p "$DIR"
echo "Arquitectura: $ARQ | Resultados en $DIR"

for N in 3 5 8; do
  echo ">>> Escenario de $N clientes ($ARQ)"
  java -cp out:jeromq-0.5.3.jar ClienteMasivo --config pruebas/escenario_${N}_clientes.properties \
       --ip "$IP" --duracion "$DUR" --salida "$DIR" | tee "$DIR/consola_${N}_clientes.txt"
  sleep 5
done

# tiempos de respuesta medidos en los clientes
echo "Clientes,Operacion,Total,Exitosas,Promedio_ms,DesvStd_ms,Max_ms,TPM_cliente" > "$DIR/resumen_velocidad.csv"
for N in 3 5 8; do tail -n +2 "$DIR/metricas_${N}_clientes.csv" >> "$DIR/resumen_velocidad.csv"; done

# TPM medidos en el gestor de transacciones
echo "Clientes,Operacion,Total_GT,Segundos,TPM_GT" > "$DIR/resumen_tpm_gt.csv"
for N in 3 5 8; do tail -n +2 "$DIR/tpm_gt_${N}_clientes.csv" >> "$DIR/resumen_tpm_gt.csv" 2>/dev/null; done

echo; echo "=== TIEMPOS DE RESPUESTA ==="; column -s, -t "$DIR/resumen_velocidad.csv"
echo; echo "=== TPM EN EL GT ==="; column -s, -t "$DIR/resumen_tpm_gt.csv"
