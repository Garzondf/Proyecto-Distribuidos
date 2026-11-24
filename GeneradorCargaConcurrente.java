import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class GeneradorCargaConcurrente {

    public static void main(String[] args) throws InterruptedException {
        Properties config = new Properties();
        String rutaConfig = (args.length > 0) ? args[0] : "config_carga.properties";

        try (FileInputStream fis = new FileInputStream(rutaConfig)) {
            config.load(fis);
        } catch (IOException e) {
            System.err.println("Advertencia: No se encontro '" + rutaConfig + "'. Usando valores predeterminados.");
        }

        String ipGT = config.getProperty("ipGT", "10.43.100.138");
        int puertoGT = Integer.parseInt(config.getProperty("puertoGT", "5555"));
        int numClientes = Integer.parseInt(config.getProperty("numClientes", "3"));
        int duracionSegundos = Integer.parseInt(config.getProperty("duracionSegundos", "120"));
        int reqPorMinuto = Integer.parseInt(config.getProperty("solicitudesPorMinuto", "30"));
        int pctConsultas = Integer.parseInt(config.getProperty("porcentajeConsultas", "70"));

        long intervaloMs = (60 * 1000L) / Math.max(reqPorMinuto, 20);

        System.out.println("=========================================================");
        System.out.println("   GENERADOR DE CARGA DE CLIENTES (PRIMERA ENTREGA)");
        System.out.println("=========================================================");
        System.out.println("   Archivo Configuracion : " + rutaConfig);
        System.out.println("   Destino GT            : " + ipGT + ":" + puertoGT);
        System.out.println("   Clientes Simultaneos  : " + numClientes);
        System.out.println("   Duracion              : " + duracionSegundos + " segundos");
        System.out.println("=========================================================\n");

        CountDownLatch latch = new CountDownLatch(numClientes);

        List<Long> tiemposConsulta = Collections.synchronizedList(new ArrayList<>());
        List<Long> tiemposReserva = Collections.synchronizedList(new ArrayList<>());

        long inicioExperimentoMs = System.currentTimeMillis();

        for (int i = 1; i <= numClientes; i++) {
            final int idCliente = i;
            final boolean soloConsultas = (i <= Math.ceil(numClientes / 2.0));

            new Thread(() -> {
                Random rand = new Random();
                try (ZContext context = new ZContext()) {
                    ZMQ.Socket socketSync = context.createSocket(SocketType.REQ);
                    socketSync.connect("tcp://" + ipGT + ":" + puertoGT);

                    long tInicioHilo = System.currentTimeMillis();

                    while ((System.currentTimeMillis() - tInicioHilo) < (duracionSegundos * 1000L)) {
                        boolean hacerConsulta = soloConsultas || (rand.nextInt(100) < pctConsultas);

                        if (hacerConsulta) {
                            long t0 = System.nanoTime();
                            socketSync.send("CONSULTA;MES");
                            socketSync.recvStr(0);
                            long t1 = System.nanoTime();

                            tiemposConsulta.add((t1 - t0) / 1_000_000);
                        } else {
                            String eventoId = "E" + (rand.nextInt(50) + 1);
                            int ocurrencia = rand.nextInt(2) + 1;
                            int cantidad = rand.nextInt(3) + 1;

                            long t0 = System.nanoTime();
                            socketSync.send("RESERVA;" + eventoId + ";" + ocurrencia + ";" + cantidad);
                            socketSync.recvStr(0);
                            long t1 = System.nanoTime();

                            tiemposReserva.add((t1 - t0) / 1_000_000);
                        }
                        Thread.sleep(intervaloMs);
                    }
                } catch (Exception e) {
                    System.err.println("Error en Hilo-" + idCliente + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }, "HiloCliente-" + idCliente).start();
        }

        latch.await();
        long tiempoTotalMs = System.currentTimeMillis() - inicioExperimentoMs;

        generarReporte(tiemposConsulta, tiemposReserva, tiempoTotalMs, numClientes);
    }

    private static void generarReporte(List<Long> consultas, List<Long> reservas, long tiempoTotalMs, int numClientes) {
        double duracionMinutos = tiempoTotalMs / 60000.0;

        double avgConsulta = consultas.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double stdConsulta = calcularDevStd(consultas, avgConsulta);
        double tpmConsulta = consultas.size() / duracionMinutos;

        double avgReserva = reservas.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double stdReserva = calcularDevStd(reservas, avgReserva);
        double tpmReserva = reservas.size() / duracionMinutos;

        System.out.println("\n=========================================================");
        System.out.println("   RESULTADOS DE RENDIMIENTO (" + numClientes + " CLIENTES SIMULTANEOS)");
        System.out.println("=========================================================");
        System.out.printf("CONSULTAS:\n");
        System.out.printf("  - Total Procesadas     : %d\n", consultas.size());
        System.out.printf("  - Tiempo Promedio (ms) : %.2f ms\n", avgConsulta);
        System.out.printf("  - Desviacion Estandar  : %.2f ms\n", stdConsulta);
        System.out.printf("  - TPM (Trans/Minuto)   : %.2f\n\n", tpmConsulta);

        System.out.printf("RESERVAS:\n");
        System.out.printf("  - Total Procesadas     : %d\n", reservas.size());
        System.out.printf("  - Tiempo Promedio (ms) : %.2f ms\n", avgReserva);
        System.out.printf("  - Desviacion Estandar  : %.2f ms\n", stdReserva);
        System.out.printf("  - TPM (Trans/Minuto)   : %.2f\n", tpmReserva);
        System.out.println("=========================================================");

        String archivoSalida = "metricas_" + numClientes + "_clientes.csv";
        try (FileWriter writer = new FileWriter(archivoSalida)) {
            writer.write("Operacion,Total_Transacciones,Promedio_ms,DevStd_ms,TPM\n");
            writer.write(String.format("Consulta,%d,%.2f,%.2f,%.2f\n", consultas.size(), avgConsulta, stdConsulta, tpmConsulta));
            writer.write(String.format("Reserva,%d,%.2f,%.2f,%.2f\n", reservas.size(), avgReserva, stdReserva, tpmReserva));
            System.out.println("Archivo de texto generado exitosamente: " + archivoSalida);
        } catch (IOException e) {
            System.err.println("Fallo al escribir el archivo CSV.");
        }
    }

    private static double calcularDevStd(List<Long> datos, double promedio) {
        if (datos.isEmpty()) return 0.0;
        double sumaCuadrados = 0;
        for (long v : datos) {
            sumaCuadrados += Math.pow(v - promedio, 2);
        }
        return Math.sqrt(sumaCuadrados / datos.size());
    }
}