#!/bin/bash
# Nodo 2: muestra los archivos de datos cambiando en vivo (-d resalta lo que cambia)
D=${1:-datos}
watch -d -n 1 -t "
echo '=== eventos.txt (solo ocurrencias con asientos ocupados) id;oc;fecha;total;libres;ocupados ===';
awk -F';' '\$6>0' $D/eventos.txt | head -15;
echo; echo '=== reservas.txt (ultimas 10) id;usuario;evento;oc;asientos ===';
tail -10 $D/reservas.txt;
echo; echo '=== solicitudes.txt (ultimas 5) idSolicitud   resultado ===';
tail -5 $D/solicitudes.txt | cut -c1-110"
