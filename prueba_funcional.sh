#!/bin/bash
# Corre los casos de prueba funcionales y guarda el resultado en resultados/prueba_funcional.txt
# Uso: ./prueba_funcional.sh [ipGT]
IP=${1:-10.43.100.138}
mkdir -p resultados
java -cp out:jeromq-0.5.3.jar PruebaFuncional "$IP" | tee resultados/prueba_funcional.txt
