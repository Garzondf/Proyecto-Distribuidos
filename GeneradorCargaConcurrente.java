import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Generador de carga: cada cliente es un hilo que envia solicitudes y mide el tiempo de cada una
public class GeneradorCargaConcurrente {

    record Registro(int cliente, String usuario, String tipo, long ms, String resultado, long instanteMs) {}

    private static final Pattern ID_RESERVA = Pattern.compile("idReserva=(R\\d+)");

    public static void main(String[] args) throws Exception {
        Properties cfg = new Properties();
        String ruta = args.length > 0 ? args[0] : "config_carga.properties";
        try (FileInputStream f = new FileInputStream(ruta)) { cfg.load(f); }
        catch (IOException e) { System.err.println("Aviso: no se encontro " + ruta + ", se usan valores por defecto"); }

        String ipGT = cfg.getProperty("ipGT", "10.43.100.138");
        int n = Integer.parseInt(cfg.getProperty("numClientes", "3"));
        int duracion = Integer.parseInt(cfg.getProperty("duracionSegundos", "120"));
        int reqMin = Math.max(20, Integer.parseInt(cfg.getProperty("solicitudesPorMinuto", "30")));
        int pctCons = Integer.parseInt(cfg.getProperty("porcentajeConsultas", "40"));
        int pctRes = Integer.parseInt(cfg.getProperty("porcentajeReservas", "40"));
        int pctMod = Integer.parseInt(cfg.getProperty("porcentajeModificaciones", "20"));
        long intervaloMs = 60_000L / reqMin;

        System.out.println("=========================================================");
        System.out.println(" GENERADOR DE CARGA - " + n + " clientes, " + duracion + " s, " + reqMin + " sol/min por cliente");
        System.out.println(" Destino GT: " + ipGT + " | mixtos: " + pctCons + "% consulta / " + pctRes + "% reserva / " + pctMod + "% modificacion");
        System.out.println("=========================================================");

        List<Registro> registros = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch fin = new CountDownLatch(n);
        long inicioGlobal = System.currentTimeMillis();

        for (int i = 1; i <= n; i++) {
            final int idx = i;
            final int numUsuario = ((i - 1) % 8) + 1;
            final String usuario = "user" + numUsuario;
            final String password = cfg.getProperty("password." + usuario, "clave" + numUsuario);
            final boolean soloConsultas = i <= Math.ceil(n / 2.0);   // la mitad solo consulta

            new Thread(() -> {
                Random rnd = new Random();
                List<String> misReservas = new ArrayList<>();
                try (ClienteBase c = new ClienteBase(ipGT, 5555, 5556, usuario, password)) {
                    long t0Hilo = System.currentTimeMillis();
                    while (System.currentTimeMillis() - t0Hilo < duracion * 1000L) {
                        long ini = System.currentTimeMillis();
                        String tipo, cmd;
                        int dado = rnd.nextInt(100);   // escoge el tipo de operacion
                        if (soloConsultas || dado < pctCons) {
                            tipo = "consulta"; cmd = "CONSULTA;" + (rnd.nextInt(12) + 1);
                        } else if (dado < pctCons + pctRes || misReservas.isEmpty()) {
                            tipo = "reserva";
                            cmd = "RESERVA;E" + (rnd.nextInt(50) + 1) + ";" + (rnd.nextInt(2) + 1) + ";" + (rnd.nextInt(3) + 1);
                        } else {
                            tipo = "modificacion";
                            String id = misReservas.get(rnd.nextInt(misReservas.size()));
                            cmd = "MODIFICA;" + id + ";" + (rnd.nextInt(3) + 1) + ";0";
                        }
                        long a = System.nanoTime();
                        String resp = c.solicitar(cmd);   // el tiempo incluye reintentos
                        long ms = (System.nanoTime() - a) / 1_000_000;
                        registros.add(new Registro(idx, usuario, tipo, ms, resp.split(";")[0], ini));
                        if (tipo.equals("reserva") && resp.startsWith("EXITO")) {
                            Matcher m = ID_RESERVA.matcher(resp);
                            if (m.find()) misReservas.add(m.group(1));
                        }
                        long resto = intervaloMs - (System.currentTimeMillis() - ini);
                        if (resto > 0) Thread.sleep(resto);
                    }
                } catch (Exception e) {
                    System.err.println("Error en cliente " + idx + ": " + e);
                } finally {
                    fin.countDown();
                }
            }, "cliente-" + i).start();
        }

        fin.await();
        double minutos = (System.currentTimeMillis() - inicioGlobal) / 60000.0;
        reportar(registros, n, minutos);
    }

    private static void reportar(List<Registro> regs, int n, double minutos) throws IOException {
        // CSV con el tiempo de cada operacion
        String detalle = "tiempos_" + n + "_clientes.csv";
        try (FileWriter w = new FileWriter(detalle)) {
            w.write("cliente,usuario,tipo,ms,resultado,instante_ms\n");
            for (Registro r : regs) w.write(r.cliente() + "," + r.usuario() + "," + r.tipo() + "," + r.ms() + "," + r.resultado() + "," + r.instanteMs() + "\n");
        }
        // CSV con promedio, desviacion y TPM por tipo
        String resumen = "metricas_" + n + "_clientes.csv";
        System.out.println("\n=========================================================");
        System.out.println(" RESULTADOS CON " + n + " CLIENTES (" + String.format("%.2f", minutos) + " min)");
        System.out.println("=========================================================");
        try (FileWriter w = new FileWriter(resumen)) {
            w.write("Operacion,Total,Exitosas,Promedio_ms,DesvStd_ms,TPM\n");
            for (String tipo : new String[]{"consulta", "reserva", "modificacion"}) {
                List<Long> t = new ArrayList<>();
                int ok = 0;
                for (Registro r : regs) if (r.tipo().equals(tipo)) { t.add(r.ms()); if (r.resultado().equals("EXITO")) ok++; }
                double prom = t.stream().mapToLong(Long::longValue).average().orElse(0);
                double sd = 0;
                if (t.size() > 1) {
                    double s = 0; for (long v : t) s += (v - prom) * (v - prom);
                    sd = Math.sqrt(s / (t.size() - 1));
                }
                double tpm = t.size() / minutos;
                System.out.printf("%-13s total=%d exitosas=%d promedio=%.2f ms desv.std=%.2f ms TPM=%.2f%n", tipo, t.size(), ok, prom, sd, tpm);
                w.write(String.format(Locale.US, "%s,%d,%d,%.2f,%.2f,%.2f%n", tipo, t.size(), ok, prom, sd, tpm));
            }
        }
        System.out.println("Archivos generados: " + detalle + " y " + resumen);
    }
}
