#!/bin/bash
# Nodo 2: ./nodo2.sh [--reset] (--reset deja los datos como al inicio)
exec java -cp "out:jeromq-0.5.3.jar" ServicioPersistencia 5560 5563 datos "$@"
