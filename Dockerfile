FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Descargar la libreria de ZeroMQ (JeroMQ) directamente
RUN apk add --no-cache curl && \
    curl -L -o jeromq-0.5.3.jar https://repo1.maven.org/maven2/org/zeromq/jeromq/0.5.3/jeromq-0.5.3.jar

# Copiar los archivos Java planos y la configuracion
COPY *.java ./
COPY config_carga.properties ./

# Compilar directamente los archivos fuente
RUN javac -cp jeromq-0.5.3.jar *.java
