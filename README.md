# Proyecto Distribuidos: reserva de boletos

VMs:
- 10.43.99.202: Persistencia (guarda los datos en archivos .txt)
- 10.43.100.138: GT, RMC, Cancelaciones y respaldos
- 10.43.100.39: clientes y pruebas

Los usuarios son user1 a user8, sin clave. El usuario admin solo lo usan las pruebas para reiniciar los datos y leer los contadores del GT. Los 50 eventos están en el mes 12 (diciembre de 2026). Puertos 5555 a 5565 abiertos entre las VMs. Diagrama de la arquitectura: diagrama_arquitectura_final.png

## 0. Preparar (en las 3 VMs)

```bash
cd ~/Downloads/Proyecto-Distribuidos-main
git pull
pkill java
rm -rf logs out
chmod +x *.sh
./compilar.sh
```

Compila el código y deja la carpeta `out/`. Requiere Java 17 (`sudo apt install -y openjdk-17-jdk curl wget netcat-openbsd procps`). Si la VM no tiene internet, copia `jeromq-0.5.3.jar` a la carpeta del proyecto.

## 1. Arquitectura base

VM 10.43.99.202, terminal A:

```bash
./nodo2.sh --reset
```

Arranca Persistencia con los datos iniciales. Deja `datos/eventos.txt`, `datos/reservas.txt` y `datos/solicitudes.txt`.

VM 10.43.99.202, terminal B:

```bash
./ver_archivos.sh
```

Muestra los archivos de datos cambiando en vivo.

VM 10.43.100.138, terminal A:

```bash
nc -zv 10.43.99.202 5560
DEMO=1 ./nodo1.sh start base
./nodo1.sh status
```

Arranca Cancelaciones, RMC, RMC respaldo, GT y GT respaldo. Deja `logs/*.log` y `logs/*.pid`. Deben salir 5 procesos activos.

VM 10.43.100.138, terminal B:

```bash
./ver_logs.sh
```

Muestra los logs de todos los procesos.

## 2. Prueba manual

VM 10.43.100.39:

```bash
nc -zv 10.43.100.138 5555
java -cp out:jeromq-0.5.3.jar ClienteInteractivo 10.43.100.138 user1
```

Abre un menú para consultar (mes 12), reservar, modificar y cancelar. La cancelación responde un ACK y luego llega una notificación. Cambia `datos/` en la VM 10.43.99.202.

## 3. Carga con el cliente masivo

VM 10.43.100.39:

```bash
java -cp out:jeromq-0.5.3.jar ClienteMasivo --ip 10.43.100.138 --clientes 5 --duracion 120 --reset
```

Lanza los clientes en paralelo. Deja en la carpeta actual `tiempos_5_clientes.csv` (cada operación), `metricas_5_clientes.csv` (promedio, desviación y TPM) y `tpm_gt_5_clientes.csv` (TPM medido en el GT). Sin argumentos muestra todas las opciones.

## 4. Falla del GT y del RMC

VM 10.43.100.39:

```bash
java -cp out:jeromq-0.5.3.jar ClienteMasivo --ip 10.43.100.138 --clientes 5 --duracion 120 --reset
```

A los 30 segundos, VM 10.43.100.138, terminal C:

```bash
./nodo1.sh kill gt
./nodo1.sh status
```

Mata el GT con `kill -9`. El respaldo detecta la caída en unos 6 segundos, abre los puertos y atiende. En `ver_logs.sh` se ven los PING sin respuesta y el aviso de caída. En la VM 10.43.100.39 salen mensajes de reintento y la carga sigue.

Después:

```bash
./nodo1.sh kill rmc
tail -f logs/rmc_respaldo.log
```

Mata el RMC y el respaldo lo reemplaza.

VM 10.43.100.39, al terminar la carga:

```bash
./picos_falla.sh tiempos_5_clientes.csv
```

Lista las operaciones que tardaron más de 4000 ms, las afectadas por la caída.

VM 10.43.99.202: Ctrl+C en la terminal A. Imprime el resumen final y la línea de integridad, que debe decir OK.

## 5. Arquitectura escalable

VM 10.43.100.138:

```bash
./nodo1.sh stop
DEMO=1 ./nodo1.sh start escalable
./nodo1.sh status
```

Arranca los procesos de la arquitectura base y dos RMC más. Deben salir 7 procesos activos. Las consultas pasan por la réplica de Persistencia. Para volver a la base: `./nodo1.sh stop` y `DEMO=1 ./nodo1.sh start base`.

## 6. Experimentos

En las 3 VMs:

```bash
./especificaciones.sh
```

Deja `especificaciones_<nombre>.txt` en cada VM.

Con la arquitectura base corriendo, VM 10.43.100.39:

```bash
./prueba_velocidad.sh 10.43.100.138 150 base
```

Con la arquitectura escalable corriendo, VM 10.43.100.39:

```bash
./prueba_velocidad.sh 10.43.100.138 150 escalable
```

Cada una corre 3, 5 y 8 clientes (unos 9 minutos) y deja `resultados/base_<fecha>/` o `resultados/escalable_<fecha>/` con `resumen_velocidad.csv` (tiempos), `resumen_tpm_gt.csv` (TPM del GT) y los CSV de cada escenario.

## 7. Gráficos

Copiar los resultados a tu PC:

```bash
scp -r estudiante@10.43.100.39:~/Downloads/Proyecto-Distribuidos-main/resultados .
```

Generar los gráficos (necesita `pip install matplotlib`):

```bash
python3 graficar.py resultados/base_<fecha> resultados/escalable_<fecha> graficos
```

Deja los gráficos de tiempo y TPM por número de clientes y `tabla_comparativa.csv`.
