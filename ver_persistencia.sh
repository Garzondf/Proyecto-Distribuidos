#!/bin/bash
# Panel en vivo de los archivos de datos (Nodo 2)
D=${1:-datos}
watch -n 1 -t "
echo '=== PERSISTENCIA (Nodo 2) - archivos de texto ===';
echo -n 'Reservas activas (reservas.txt):     '; wc -l < $D/reservas.txt;
echo -n 'Solicitudes procesadas:              '; wc -l < $D/solicitudes.txt;
echo -n 'Asientos ocupados (eventos.txt):     '; awk -F';' '{s+=\$6} END{print s+0}' $D/eventos.txt;
echo -n 'Asientos libres:                     '; awk -F';' '{s+=\$5} END{print s+0}' $D/eventos.txt;
echo; echo '--- ultimas 8 reservas (id;usuario;evento;oc;asientos) ---'; tail -8 $D/reservas.txt;
echo; echo '--- 5 ocurrencias con mas asientos ocupados (id;oc;fecha;total;libres;ocupados) ---';
sort -t';' -k6 -nr $D/eventos.txt | head -5;
echo; if diff -q $D/eventos.txt $D/replica/eventos.txt > /dev/null; then echo 'Replica: sincronizada'; else echo 'Replica: propagando...'; fi"
