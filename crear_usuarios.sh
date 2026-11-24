#!/bin/bash
# Crea usuarios.txt con user1..user8 (claves clave1..clave8, guardadas como SHA-256)
: > usuarios.txt
for i in 1 2 3 4 5 6 7 8; do
  echo "user$i:$(printf 'clave%s' "$i" | sha256sum | cut -d' ' -f1)" >> usuarios.txt
done
echo "usuarios.txt creado:"; cat usuarios.txt
