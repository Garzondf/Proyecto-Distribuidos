import java.io.File;
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

    // uso: java GeneradorCargaConcurrente [config.properties] [carpetaSalida]
    public static void main(String[] args) throws Exception {
        Properties cfg = new Properties();
        String ruta = args.length > 0 ? args[0] : "config_carga.properties";
        try (FileInputStream f = new FileInputStream(ruta)) { cfg.load(f); }
        catch (IOException e) { System.err.println("Aviso: no se encontro " + ruta + ", se usan valores por defecto"); }
        if (args.length > 1) cfg.setProperty("salida", args[1]);
        ejecutar(cfg);
    }

    // corre la prueba completa con la configuracion recibida
    public static void ejecutar(Properties cfg) throws Exception {
        String ipGT = cfg.getProperty("ipGT", "10.43.100.138");
        int n = Integer.parseInt(cfg.getProperty("numClientes", "3"));
        int duracion = Integer.parseInt(cfg.getProperty("duracionSegundos", "120"));
        int reqMin = Math.max(20, Integer.parseInt(cfg.getProperty("solicitudesPorMinuto", "30")));
        int pctCons = Integer.parseInt(cfg.getProperty("porcentajeConsultas", "40"));
        int pctRes = Integer.parseInt(cfg.getProperty("porcentajeReservas", "40"));
        int pctMod = Integer.parseInt(cfg.getProperty("porcentajeModificaciones", "20"));
        int pctCanc = Integer.parseInt(cfg.getProperty("porcentajeCancelaciones", "0"));
        String salida = cfg.getProperty("salida", ".");
        boolean resetAntes = Boolean.parseBoolean(cfg.getProperty("resetAntes", "false"));
        String adminPass = cfg.getProperty("adminPass", "admin123");
        long intervaloMs = 60_000L / reqMin;

        if (pctCons + pctRes + pctMod + pctCanc != 100) {
            System.err.println("Los porcentajes deben sumar 100 (suman " + (pctCons + pctRes + pctMod + pctCanc) + ")");
            return;
        }
        new File(salida).mkdirs();

        System.out.println("=========================================================");
        System.out.println(" PRUEBA DE CARGA - " + n + " clientes, " + duracion + " s, " + reqMin + " sol/min por cliente");
        System.out.println(" GT: " + ipGT + " | mixtos: " + pctCons + "% consulta, " + pctRes + "% reserva, "
                + pctMod + "% modificacion, " + pctCanc + "% cancelacion");
        System.out.println(" Resultados en: " + salida);
        System.out.println("=========================================================");

        // deja los datos como al inicio antes de medir
        if (resetAntes) {
            try (ClienteBase admin = new ClienteBase(ipGT, 5555, 5556, "admin", adminPass)) {
                System.out.println("[RESET] " + admin.solicitar("RESET"));
            }
        }

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
                        int dado = rnd.nextInt(100);   // escoge el tipo de operacion
                        String tipo, cmd = null;
                        if (soloConsultas || dado < pctCons) {
                            tipo = "consulta"; cmd = "CONSULTA;" + (rnd.nextInt(12) + 1);
                        } else if (dado < pctCons + pctRes || misReservas.isEmpty()) {
                            tipo = "reserva";
                            cmd = "RESERVA;E" + (rnd.nextInt(50) + 1) + ";" + (rnd.nextInt(2) + 1) + ";" + (rnd.nextInt(3) + 1);
                        } else if (dado < pctCons + pctRes + pctMod) {
                            tipo = "modificacion";
                            cmd = "MODIFICA;" + misReservas.get(rnd.nextInt(misReservas.size())) + ";" + (rnd.nextInt(3) + 1) + ";0";
                        } else {
                            tipo = "cancelacion";
                        }
                        long a = System.nanoTime();
                        String resp;
                        if (tipo.equals("cancelacion")) {
                            // la cancelacion es asincrona: aqui se mide hasta el ACK
                            resp = c.cancelar(misReservas.remove(rnd.nextInt(misReservas.size())));
                        } else {
                            resp = c.solicitar(cmd);   // el tiempo incluye reintentos
                        }
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
        reportar(registros, n, minutos, salida);
    }

    private static void reportar(List<Registro> regs, int n, double minutos, String salida) throws IOException {
        // CSV con el tiempo de cada operacion
        String detalle = salida + File.separator + "tiempos_" + n + "_clientes.csv";
        try (FileWriter w = new FileWriter(detalle)) {
            w.write("cliente,usuario,tipo,ms,resultado,instante_ms\n");
            for (Registro r : regs) w.write(r.cliente() + "," + r.usuario() + "," + r.tipo() + "," + r.ms() + "," + r.resultado() + "," + r.instanteMs() + "\n");
        }
        // CSV con promedio, desviacion y TPM por tipo
        String resumen = salida + File.separator + "metricas_" + n + "_clientes.csv";
        System.out.println("\n=========================================================");
        System.out.println(" RESULTADOS CON " + n + " CLIENTES (" + String.format("%.2f", minutos) + " min)");
        System.out.println("=========================================================");
        try (FileWriter w = new FileWriter(resumen)) {
            w.write("Clientes,Operacion,Total,Exitosas,Promedio_ms,DesvStd_ms,Max_ms,TPM\n");
            for (String tipo : new String[]{"consulta", "reserva", "modificacion", "cancelacion"}) {
                List<Long> t = new ArrayList<>();
                int ok = 0;
                for (Registro r : regs) if (r.tipo().equals(tipo)) {
                    t.add(r.ms());
                    if (r.resultado().equals("EXITO") || r.resultado().equals("ACK")) ok++;
                }
                if (t.isEmpty()) continue;
                double prom = t.stream().mapToLong(Long::longValue).average().orElse(0);
                long max = t.stream().mapToLong(Long::longValue).max().orElse(0);
                double sd = 0;
                if (t.size() > 1) {
                    double s = 0; for (long v : t) s += (v - prom) * (v - prom);
                    sd = Math.sqrt(s / (t.size() - 1));
                }
                double tpm = t.size() / minutos;
                System.out.printf("%-13s total=%d exitosas=%d promedio=%.2f ms desv.std=%.2f ms max=%d ms TPM=%.2f%n", tipo, t.size(), ok, prom, sd, max, tpm);
                w.write(String.format(Locale.US, "%d,%s,%d,%d,%.2f,%.2f,%d,%.2f%n", n, tipo, t.size(), ok, prom, sd, max, tpm));
            }
        }
        System.out.println("Archivos generados: " + detalle + " y " + resumen);
    }
}
