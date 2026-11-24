#!/bin/bash
# Descarga JeroMQ si falta y compila todo en ./out
set -e
JAR=jeromq-0.5.3.jar
[ -f "$JAR" ] || curl -fL -o "$JAR" https://repo1.maven.org/maven2/org/zeromq/jeromq/0.5.3/jeromq-0.5.3.jar
rm -rf out && mkdir out
javac -cp "$JAR" -d out *.java
echo "Compilacion OK -> ./out"
