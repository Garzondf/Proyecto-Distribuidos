#!/bin/bash
# Crea usuarios.txt: user1..user8 (clave1..clave8) y admin (admin123, solo para reiniciar datos en las pruebas)
: > usuarios.txt
for i in 1 2 3 4 5 6 7 8; do
  echo "user$i:$(printf 'clave%s' "$i" | sha256sum | cut -d' ' -f1)" >> usuarios.txt
done
echo "admin:$(printf 'admin123' | sha256sum | cut -d' ' -f1)" >> usuarios.txt
echo "usuarios.txt creado:"; cat usuarios.txt
