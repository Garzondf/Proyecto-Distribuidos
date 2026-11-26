#!/bin/bash
# Nodo de Persistencia: muestra los archivos de datos cambiando en vivo (-d resalta lo que cambia)
D=${1:-datos}
watch -d -n 1 -t "
echo '=== eventos.txt, primeras 6 lineas (estado inicial) id;oc;fecha;total;libres;ocupados ===';
head -6 $D/eventos.txt;
echo; echo '=== eventos con asientos ocupados (max 12) ===';
awk -F';' '\$6>0' $D/eventos.txt | head -12;
echo; echo '=== reservas.txt, ultimas 8: id;usuario;evento;oc;asientos ===';
tail -8 $D/reservas.txt;
echo; echo '=== solicitudes.txt, ultimas 4 ===';
tail -4 $D/solicitudes.txt | cut -c1-110"
