#!/bin/bash
# Prueba de estres (8 clientes, 120 sol/min, con cancelaciones). Uso: ./prueba_estres.sh [ipGT]
IP=${1:-10.43.100.138}
DIR=resultados/estres_$(date +%Y%m%d_%H%M%S)
mkdir -p "$DIR"
java -cp out:jeromq-0.5.3.jar ClienteMasivo --config pruebas/escenario_estres.properties \
     --ip "$IP" --salida "$DIR" | tee "$DIR/consola_estres.txt"
