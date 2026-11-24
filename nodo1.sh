#!/bin/bash
# Nodo 1. Uso: ./nodo1.sh start [base|escalable] | stop | kill gt|rmc | status
# DEMO=1 hace que el GT imprima totales cada 5 s
DEMO_FLAG=${DEMO:+--demo}
IP_PERS=${IP_PERSISTENCIA:-10.43.99.202}
CP="out:jeromq-0.5.3.jar"
mkdir -p logs
lanzar() { nohup java -cp "$CP" "${@:2}" > "logs/$1.log" 2>&1 & echo $! > "logs/$1.pid"; echo "  iniciado $1 (pid $!)"; }

case "$1" in
start)
  MODO=${2:-base}
  [ -f usuarios.txt ] || ./crear_usuarios.sh > /dev/null
  echo "Iniciando Nodo 1 en modo $MODO (Persistencia en $IP_PERS)"
  lanzar cancelaciones ServicioCancelaciones "$IP_PERS"
  if [ "$MODO" = "escalable" ]; then
    lanzar rmc ServicioRMC "$IP_PERS" 5557 5562 --replica
    lanzar rmc2 ServicioRMC "$IP_PERS" 5564 0 --replica
    lanzar rmc3 ServicioRMC "$IP_PERS" 5565 0 --replica
    sleep 2
    lanzar rmc_respaldo ServicioRMC "$IP_PERS" 5557 5562 --replica --respaldo
    RMCS="localhost:5557,localhost:5564,localhost:5565"
  else
    lanzar rmc ServicioRMC "$IP_PERS" 5557 5562
    sleep 2
    lanzar rmc_respaldo ServicioRMC "$IP_PERS" 5557 5562 --respaldo
    RMCS="localhost:5557"
  fi
  lanzar gt GestorTransacciones "$RMCS" localhost usuarios.txt $DEMO_FLAG
  sleep 2
  lanzar gt_respaldo GestorTransacciones "$RMCS" localhost usuarios.txt --respaldo $DEMO_FLAG
  echo "Logs en ./logs (tail -f logs/gt.log)"
  ;;
stop)
  for f in logs/*.pid; do [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null; rm -f "$f"; done
  echo "Procesos detenidos" ;;
kill)
  [ -f "logs/$2.pid" ] && kill -9 "$(cat "logs/$2.pid")" && echo "Proceso $2 detenido (simulacion de falla). Vigile logs/${2}_respaldo.log" ;;
status)
  for f in logs/*.pid; do n=$(basename "$f" .pid); if kill -0 "$(cat "$f")" 2>/dev/null; then echo "$n: activo"; else echo "$n: CAIDO"; fi; done ;;
*) echo "Uso: $0 start [base|escalable] | stop | kill gt|rmc | status" ;;
esac
