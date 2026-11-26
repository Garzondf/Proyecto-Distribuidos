# Proyecto Distribuidos: reserva de boletos con ZeroMQ

Sistema en Java (JeroMQ) para consultar, reservar, modificar y cancelar boletos. Corre en 3 máquinas virtuales. Tiene una arquitectura base y una escalable, y sigue funcionando si se cae el GT o el RMC porque cada uno tiene un respaldo.

## Las VMs

- **10.43.99.202**: almacenamiento. Corre Persistencia, que guarda los datos en archivos `.txt` dentro de `datos/`.
- **10.43.100.138**: negocio. Corre el GT (Gestor de Transacciones), el RMC, el servicio de Cancelaciones y los respaldos del GT y del RMC. En la arquitectura escalable también corren dos RMC adicionales.
- **10.43.100.39**: clientes. Corre el cliente interactivo, el cliente masivo y las pruebas de carga.

Los puertos 5555 a 5565 deben estar abiertos entre las VMs. Los usuarios de prueba son `user1` a `user8` con clave `clave1` a `clave8`, y `admin` con clave `admin123` (solo para reiniciar los datos y leer los contadores del GT en las pruebas).

Los 50 eventos (E1 a E50) están en diciembre de 2026: 20 tienen dos ocurrencias y 30 una, con 100 asientos cada una. Para consultarlos se usa el mes 12. El diagrama de la arquitectura final está en `diagrama_arquitectura_final.png`.

## 1. Requisitos (en las 3 VMs)

Revisa que haya Java 17 o mayor:

```bash
java -version
javac -version
```

Si falta algo:

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk curl wget netcat-openbsd procps bsdextrautils
```

Si la VM no tiene internet, descarga `jeromq-0.5.3.jar` en tu PC desde `https://repo1.maven.org/maven2/org/zeromq/jeromq/0.5.3/jeromq-0.5.3.jar` y cópialo a la carpeta del proyecto en cada VM.

## 2. Limpiar y compilar (en las 3 VMs)

```bash
cd ~/Downloads/Proyecto-Distribuidos-main
git pull
pkill java
rm -rf logs out
chmod +x *.sh
./compilar.sh
```

Debe terminar con `Compilacion OK -> ./out`.

## 3. Levantar la arquitectura base

Hazlo en este orden.

### VM 10.43.99.202 (almacenamiento)

Terminal A:

```bash
cd ~/Downloads/Proyecto-Distribuidos-main
./nodo2.sh --reset
```

Terminal B:

```bash
cd ~/Downloads/Proyecto-Distribuidos-main
./ver_archivos.sh
```

Terminal A muestra el estado inicial (50 eventos, 7000 asientos libres, 0 reservas) y cada operación que llega. Terminal B muestra los archivos de datos cambiando en vivo.

### VM 10.43.100.138 (negocio)

Terminal A:

```bash
cd ~/Downloads/Proyecto-Distribuidos-main
nc -zv 10.43.99.202 5560
./crear_usuarios.sh
DEMO=1 ./nodo1.sh start base
./nodo1.sh status
```

Deben aparecer 5 procesos `activo`: cancelaciones, rmc, rmc_respaldo, gt y gt_respaldo.

Terminal B:

```bash
cd ~/Downloads/Proyecto-Distribuidos-main
./ver_logs.sh
```

### VM 10.43.100.39 (clientes)

```bash
cd ~/Downloads/Proyecto-Distribuidos-main
nc -zv 10.43.100.138 5555
```

## 4. Levantar la arquitectura escalable

En la VM 10.43.100.138:

```bash
cd ~/Downloads/Proyecto-Distribuidos-main
./nodo1.sh stop
DEMO=1 ./nodo1.sh start escalable
./nodo1.sh status
```

Deben aparecer 7 procesos `activo`: los de la arquitectura base más rmc2 y rmc3. En esta arquitectura el GT reparte las solicitudes entre los tres RMC y las consultas se atienden en la réplica de lectura de Persistencia (puerto 5563) en la VM 10.43.99.202.

Para volver a la arquitectura base: `./nodo1.sh stop` y `DEMO=1 ./nodo1.sh start base`.

## 5. Prueba funcional (VM 10.43.100.39)

```bash
java -cp out:jeromq-0.5.3.jar ClienteInteractivo 10.43.100.138 user1 clave1
```

Prueba en este orden: consultar el mes 12, reservar `E1` ocurrencia 1 con 2 asientos, modificar la reserva `R1` a 5 asientos y ocurrencia 2, y cancelarla. La cancelación devuelve un ACK y después llega una notificación con el resultado. En la VM 10.43.99.202 se ven los archivos cambiar en cada paso. Un evento con una sola ocurrencia (por ejemplo `E30`) rechaza el cambio de fecha y solo admite cambiar el número de asientos.

## 6. Cliente masivo (VM 10.43.100.39)

```bash
java -cp out:jeromq-0.5.3.jar ClienteMasivo --ip 10.43.100.138 --clientes 8 --duracion 60 --rpm 40 --reset
```

Sin argumentos muestra todas las opciones: `--clientes`, `--duracion`, `--rpm`, `--consultas`, `--reservas`, `--modificaciones`, `--cancelaciones` (suman 100), `--mes`, `--salida`, `--config` y `--reset`. La mitad de los clientes, redondeada hacia arriba, solo hace consultas; el resto hace las operaciones mixtas.

## 7. Experimentos de rendimiento

Los escenarios están en la carpeta `pruebas/` y siguen la Tabla 1 del enunciado. Con 3 clientes, 2 hacen solo consultas y el tercero consultas y reservas. Con 5 y 8 clientes, la mitad (redondeada hacia arriba) hace solo consultas y el resto reservas y modificaciones. Cada cliente envía 30 solicitudes por minuto y cada escenario dura 150 segundos.

### Especificaciones de las VMs

En cada una de las 3 VMs:

```bash
cd ~/Downloads/Proyecto-Distribuidos-main
./especificaciones.sh
```

### Arquitectura base

En la VM 10.43.100.138 debe estar corriendo la arquitectura base (punto 3). Luego, en la VM 10.43.100.39:

```bash
cd ~/Downloads/Proyecto-Distribuidos-main
./prueba_velocidad.sh 10.43.100.138 150 base
```

### Arquitectura escalable

En la VM 10.43.100.138 levanta la arquitectura escalable (punto 4). Luego, en la VM 10.43.100.39:

```bash
cd ~/Downloads/Proyecto-Distribuidos-main
./prueba_velocidad.sh 10.43.100.138 150 escalable
```

Cada ejecución tarda unos 9 minutos y deja una carpeta `resultados/base_<fecha>` o `resultados/escalable_<fecha>` con:

- `resumen_velocidad.csv`: tiempo promedio, desviación estándar y máximo de cada tipo de operación, medidos en los clientes.
- `resumen_tpm_gt.csv`: transacciones por minuto de cada tipo, medidas en el GT.
- `tiempos_N_clientes.csv`: el tiempo de cada operación.
- `consola_N_clientes.txt`: lo que salió en pantalla.

### Gráficos y tabla comparativa

Con las dos carpetas de resultados copiadas a un equipo que tenga Python y matplotlib (`pip install matplotlib`):

```bash
python3 graficar.py resultados/base_<fecha> resultados/escalable_<fecha> graficos
```

Genera los gráficos de tiempo de respuesta y de TPM por número de clientes, comparando base y escalable (`comparacion_completa.png` y uno por métrica), y `tabla_comparativa.csv` con el cambio porcentual.

Para copiar los resultados desde la VM de clientes a tu PC:

```bash
scp -r estudiante@10.43.100.39:~/Downloads/Proyecto-Distribuidos-main/resultados .
```

### Prueba de estrés

```bash
./prueba_estres.sh 10.43.100.138
```

## 8. Prueba de fallo

Con el sistema levantado como en el punto 3 o el 4, haz lo siguiente.

### VM 10.43.100.39: lanzar la carga

```bash
java -cp out:jeromq-0.5.3.jar ClienteMasivo --ip 10.43.100.138 --clientes 5 --duracion 120 --reset
```

### VM 10.43.100.138: matar el GT (a los 30 segundos)

En una terminal nueva:

```bash
cd ~/Downloads/Proyecto-Distribuidos-main
./nodo1.sh kill gt
./nodo1.sh status
```

Esto mata de verdad el proceso del GT con `kill -9`. Lo que debe verse:

- En la VM 10.43.100.138, en `ver_logs.sh`: el log del GT principal se congela. El respaldo muestra tres `PING sin respuesta`, un cuadro `PRINCIPAL CAIDO` y empieza a atender.
- En la VM 10.43.100.39: uno o dos mensajes `Reintento 1/3` y la carga continúa sola.
- En la VM 10.43.99.202: los archivos siguen cambiando, el estado no se perdió.

### VM 10.43.100.138: matar el RMC

```bash
./nodo1.sh kill rmc
tail -f logs/rmc_respaldo.log
```

### VM 10.43.100.39: ver las operaciones afectadas

Cuando termine la carga:

```bash
./picos_falla.sh tiempos_5_clientes.csv
```

Lista las operaciones que tardaron más de 4000 ms, que son las afectadas por la caída.

### VM 10.43.99.202: verificar que no hubo sobreventa

Presiona Ctrl+C en la terminal A de `nodo2.sh`. Debe aparecer `Integridad (sin sobreventa, ocupados = reservas): OK`.

### Repetir la demo (VM 10.43.100.138)

```bash
./nodo1.sh stop
DEMO=1 ./nodo1.sh start base
```

Cómo funciona el reemplazo: el proceso principal responde `PONG` a los `PING` de su respaldo. Si fallan 3 `PING` seguidos (unos 6 segundos), el respaldo abre los puertos del principal y atiende. Los clientes reintentan lo que se perdió con el mismo identificador, y Persistencia no aplica dos veces una operación repetida.

Limitación: los respaldos corren en la misma VM que el principal, y si se cae la VM 10.43.99.202 nada reemplaza a Persistencia.
