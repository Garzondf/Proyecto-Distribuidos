#!/bin/bash
# Guarda las especificaciones de hardware y software de esta VM para el informe
OUT=especificaciones_$(hostname).txt
{
  echo "Equipo: $(hostname)"
  echo "IP: $(hostname -I | cut -d' ' -f1)"
  echo "Sistema: $(grep PRETTY_NAME /etc/os-release | cut -d= -f2 | tr -d '"')"
  echo "Kernel: $(uname -r)"
  echo "CPU: $(lscpu | grep 'Model name' | sed 's/.*: *//')"
  echo "Nucleos: $(nproc)"
  echo "RAM: $(free -h | awk '/Mem:/ {print $2}')"
  echo "Disco: $(df -h / | awk 'NR==2 {print $2" total, "$4" libre"}')"
  echo "Java: $(java -version 2>&1 | head -1)"
  echo "ZeroMQ: JeroMQ 0.5.3"
} | tee "$OUT"
