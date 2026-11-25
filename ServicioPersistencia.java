import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

// Persistencia: unico servicio que lee y escribe los archivos. Procesa un pedido a la vez, asi no hay sobreventa
public class ServicioPersistencia {

    static class Evento {
        String id; int oc; String fecha; int total; int libres; int ocupados;
        Evento(String id, int oc, String fecha, int total, int libres, int ocupados) {
            this.id = id; this.oc = oc; this.fecha = fecha;
            this.total = total; this.libres = libres; this.ocupados = ocupados;
        }
        Evento copia() { return new Evento(id, oc, fecha, total, libres, ocupados); }
        String clave() { return id + "#" + oc; }
        String linea() { return String.join(";", id, "" + oc, fecha, "" + total, "" + libres, "" + ocupados); }
    }

    static class Reserva {
        String id, usuario, evento; int oc, asientos;
        Reserva(String id, String usuario, String evento, int oc, int asientos) {
            this.id = id; this.usuario = usuario; this.evento = evento; this.oc = oc; this.asientos = asientos;
        }
        String linea() { return String.join(";", id, usuario, evento, "" + oc, "" + asientos); }
    }

    private final Path dir, dirReplica;
    private final Map<String, Evento> eventos = new LinkedHashMap<>();
    private final Map<String, Reserva> reservas = new LinkedHashMap<>();
    private final Map<String, String> solicitudes = new HashMap<>();
    private int contadorReservas = 0;
    private volatile List<Evento> replica = new ArrayList<>();

    public ServicioPersistencia(String dirDatos, boolean reset) throws IOException {
        this.dir = Paths.get(dirDatos);
        this.dirReplica = dir.resolve("replica");
        Files.createDirectories(dirReplica);
        if (reset || !Files.exists(dir.resolve("eventos.txt"))) inicializar();
        cargar();
        publicarReplica();
    }

    // ---- archivos

    private void inicializar() throws IOException {
        // crea 50 eventos (E1..E20 con 2 fechas) con 100 asientos cada uno
        List<String> ev = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            int mes = ((i - 1) % 12) + 1;
            int ocs = (i <= 20) ? 2 : 1;
            for (int o = 1; o <= ocs; o++) {
                String fecha = String.format("2027-%02d-%02d", mes, o == 1 ? 5 : 20);
                ev.add(new Evento("E" + i, o, fecha, 100, 100, 0).linea());
            }
        }
        escribirAtomico(dir.resolve("eventos.txt"), ev);
        escribirAtomico(dir.resolve("reservas.txt"), new ArrayList<>());
        escribirAtomico(dir.resolve("solicitudes.txt"), new ArrayList<>());
        System.out.println("[Persistencia] Archivos inicializados en " + dir.toAbsolutePath());
    }

    private void cargar() throws IOException {
        eventos.clear(); reservas.clear(); solicitudes.clear(); contadorReservas = 0;
        for (String l : leer("eventos.txt")) {
            String[] t = l.split(";");
            Evento e = new Evento(t[0], Integer.parseInt(t[1]), t[2], Integer.parseInt(t[3]),
                    Integer.parseInt(t[4]), Integer.parseInt(t[5]));
            eventos.put(e.clave(), e);
        }
        for (String l : leer("reservas.txt")) {
            String[] t = l.split(";");
            Reserva r = new Reserva(t[0], t[1], t[2], Integer.parseInt(t[3]), Integer.parseInt(t[4]));
            reservas.put(r.id, r);
            contadorReservas = Math.max(contadorReservas, Integer.parseInt(r.id.substring(1)));
        }
        for (String l : leer("solicitudes.txt")) {
            String[] t = l.split("\t", 2);
            if (t.length == 2) solicitudes.put(t[0], t[1]);
        }
    }

    private List<String> leer(String nombre) throws IOException {
        List<String> out = new ArrayList<>();
        for (String l : Files.readAllLines(dir.resolve(nombre), StandardCharsets.UTF_8))
            if (!l.trim().isEmpty()) out.add(l);
        return out;
    }

    // escribe en un archivo temporal y luego lo reemplaza, para no dejar datos a medias
    private static void escribirAtomico(Path destino, List<String> lineas) throws IOException {
        Path tmp = destino.resolveSibling(destino.getFileName() + ".tmp");
        Files.write(tmp, lineas, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void guardar(boolean datos) throws IOException {
        if (datos) {
            List<String> ev = new ArrayList<>(), rs = new ArrayList<>();
            for (Evento e : eventos.values()) ev.add(e.linea());
            for (Reserva r : reservas.values()) rs.add(r.linea());
            escribirAtomico(dir.resolve("eventos.txt"), ev);
            escribirAtomico(dir.resolve("reservas.txt"), rs);
        }
        List<String> so = new ArrayList<>();
        for (Map.Entry<String, String> s : solicitudes.entrySet()) so.add(s.getKey() + "\t" + s.getValue());
        escribirAtomico(dir.resolve("solicitudes.txt"), so);
    }

    // copia los datos a la replica
    private void publicarReplica() {
        List<Evento> snap = new ArrayList<>();
        for (Evento e : eventos.values()) snap.add(e.copia());
        replica = snap;
        try {
            Files.copy(dir.resolve("eventos.txt"), dirReplica.resolve("eventos.txt"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(dir.resolve("reservas.txt"), dirReplica.resolve("reservas.txt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[Persistencia] Aviso: no se pudo copiar a la replica: " + e.getMessage());
        }
    }

    // ---- operaciones

    // todos los pedidos pasan por aqui, uno a la vez
    private synchronized String procesar(String request) {
        String[] p = request.split(";", -1);
        if (p.length < 3) return "ERROR;Solicitud mal formada";
        String idSol = p[0], usuario = p[1], cmd = p[2];

        if (cmd.equals("CONSULTA")) {
            String mes = p.length > 3 ? p[3] : "0";
            System.out.println("[BD] CONSULTA mes=" + mes + " (" + usuario + ")");
            try { return consultar(new ArrayList<>(eventos.values()), mes); }
            catch (RuntimeException e) { return "ERROR;Parametros invalidos"; }
        }

        // RESET: deja los datos como al inicio (solo el usuario admin, para repetir pruebas)
        if (cmd.equals("RESET")) {
            if (!usuario.equals("admin")) return "RECHAZADO;Solo admin puede reiniciar los datos";
            try {
                inicializar();
                cargar();
                publicarReplica();
                System.out.println("[BD] RESET: datos reiniciados por admin");
                return "EXITO;Datos reiniciados";
            } catch (IOException e) {
                return "ERROR;No se pudo reiniciar los datos";
            }
        }

        // si la solicitud ya se proceso, se devuelve el mismo resultado
        String previo = solicitudes.get(idSol);
        if (previo != null) {
            System.out.println("[BD] Solicitud repetida " + idSol + ": se devuelve el resultado guardado.");
            return previo;
        }

        String res;
        boolean cambioDatos = false;
        try {
            switch (cmd) {
                case "RESERVA":  res = reservar(usuario, p); break;
                case "MODIFICA": res = modificar(usuario, p); break;
                case "CANCELA":  res = cancelar(usuario, p); break;
                default:         return "ERROR;Comando desconocido: " + cmd;
            }
            cambioDatos = res.startsWith("EXITO");
        } catch (RuntimeException e) {
            res = "ERROR;Parametros invalidos";
        }

        solicitudes.put(idSol, res);
        try {
            guardar(cambioDatos);
            if (cambioDatos) publicarReplica();
        } catch (IOException e) {
            System.err.println("[BD] Error de archivo: " + e.getMessage() + ". Se restaura el estado anterior.");
            try { cargar(); } catch (IOException e2) { System.err.println("[BD] No se pudo recargar: " + e2.getMessage()); }
            return "RECHAZADO;Error al escribir los archivos";
        }
        System.out.println("[BD] " + cmd + " " + usuario + " -> " + res.split(";")[0]
                + (cambioDatos ? "  | archivos actualizados: eventos.txt, reservas.txt, solicitudes.txt" : "  | solicitudes.txt actualizado"));
        return res;
    }

    private String consultar(List<Evento> lista, String mes) {
        int m = Integer.parseInt(mes.trim().isEmpty() ? "0" : mes.trim());
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Evento e : lista) {
            if (m != 0 && Integer.parseInt(e.fecha.substring(5, 7)) != m) continue;
            sb.append(String.format("%-4s oc%d | %s | libres %3d | ocupados %3d%n", e.id, e.oc, e.fecha, e.libres, e.ocupados));
            n++;
        }
        return "EXITO;" + n + " ocurrencias" + (m != 0 ? " en el mes " + m : "") + "\n" + sb;
    }

    private String reservar(String usuario, String[] p) {
        String ev = p[3]; int oc = Integer.parseInt(p[4]); int n = Integer.parseInt(p[5]);
        Evento e = eventos.get(ev + "#" + oc);
        if (e == null) return "RECHAZADO;Evento u ocurrencia no valida";
        if (n <= 0) return "RECHAZADO;Cantidad de asientos invalida";
        // verifica que haya asientos
        if (e.libres < n) return "RECHAZADO;Asientos insuficientes en " + ev + " oc" + oc + " (libres: " + e.libres + ")";
        e.libres -= n; e.ocupados += n;
        String id = "R" + (++contadorReservas);
        reservas.put(id, new Reserva(id, usuario, ev, oc, n));
        return "EXITO;Reserva idReserva=" + id + " confirmada: " + ev + " oc" + oc + ", " + n + " asiento(s), libres restantes " + e.libres;
    }

    private String modificar(String usuario, String[] p) {
        Reserva r = reservas.get(p[3]);
        int nuevaCant = Integer.parseInt(p[4]);   // 0 = dejar igual
        int nuevaOc = Integer.parseInt(p[5]);     // 0 = dejar igual
        if (r == null) return "RECHAZADO;La reserva no existe";
        if (!r.usuario.equals(usuario)) return "RECHAZADO;Solo el titular puede modificar la reserva";
        if (nuevaCant < 0 || nuevaOc < 0) return "RECHAZADO;Parametros invalidos";
        int cant = (nuevaCant == 0) ? r.asientos : nuevaCant;
        int oc = (nuevaOc == 0) ? r.oc : nuevaOc;
        Evento actual = eventos.get(r.evento + "#" + r.oc);
        Evento destino = eventos.get(r.evento + "#" + oc);
        if (destino == null) return "RECHAZADO;El evento no tiene esa ocurrencia";
        if (oc == r.oc) {
            int delta = cant - r.asientos;
            if (delta > actual.libres) return "RECHAZADO;Asientos insuficientes (libres: " + actual.libres + ")";
            actual.libres -= delta; actual.ocupados += delta;
        } else {
            if (destino.libres < cant) return "RECHAZADO;Asientos insuficientes en la nueva fecha (libres: " + destino.libres + ")";
            actual.libres += r.asientos; actual.ocupados -= r.asientos;
            destino.libres -= cant; destino.ocupados += cant;
        }
        r.oc = oc; r.asientos = cant;
        return "EXITO;Reserva idReserva=" + r.id + " modificada: " + r.evento + " oc" + oc + " (" + destino.fecha + "), " + cant + " asiento(s)";
    }

    private String cancelar(String usuario, String[] p) {
        Reserva r = reservas.get(p[3]);
        if (r == null) return "RECHAZADO;La reserva " + p[3] + " no existe";
        if (!r.usuario.equals(usuario)) return "RECHAZADO;Solo el titular puede cancelar la reserva";
        Evento e = eventos.get(r.evento + "#" + r.oc);
        e.libres += r.asientos; e.ocupados -= r.asientos;
        reservas.remove(r.id);
        return "EXITO;Reserva " + r.id + " cancelada. Se liberaron " + r.asientos + " asiento(s) de " + r.evento + " oc" + r.oc;
    }

    // ---- verificacion

    public synchronized void imprimirResumen(String titulo) {
        int tot = 0, lib = 0, ocu = 0, sumaReservas = 0; boolean ok = true;
        int dos = 0; Set<String> ids = new HashSet<>(); Map<String, Integer> oc = new HashMap<>();
        for (Evento e : eventos.values()) {
            tot += e.total; lib += e.libres; ocu += e.ocupados;
            oc.merge(e.id, 1, Integer::sum); ids.add(e.id);
            if (e.libres < 0 || e.libres + e.ocupados != e.total) ok = false;
        }
        for (int v : oc.values()) if (v == 2) dos++;
        for (Reserva r : reservas.values()) sumaReservas += r.asientos;
        if (sumaReservas != ocu) ok = false;
        System.out.println("=========================================================");
        System.out.println(" " + titulo);
        System.out.println(" Eventos: " + ids.size() + " (" + dos + " con dos ocurrencias) | Ocurrencias: " + eventos.size());
        System.out.println(" Asientos totales: " + tot + " | libres: " + lib + " | ocupados: " + ocu);
        System.out.println(" Reservas activas: " + reservas.size() + " (suma de asientos: " + sumaReservas + ")");
        System.out.println(" Integridad (sin sobreventa, ocupados = reservas): " + (ok ? "OK" : "FALLA"));
        System.out.println("=========================================================");
    }

    // ---- red

    public void iniciar(int puerto, int puertoReplica) {
        // hilo de la replica: solo responde consultas
        Thread hiloReplica = new Thread(() -> {
            try (ZContext ctx = new ZContext()) {
                ZMQ.Socket rep = ctx.createSocket(SocketType.REP);
                rep.bind("tcp://*:" + puertoReplica);
                System.out.println("[Persistencia] Replica (solo consultas) en puerto " + puertoReplica);
                while (!Thread.currentThread().isInterrupted()) {
                    String req = rep.recvStr(0);
                    if (req == null) break;
                    String[] p = req.split(";", -1);
                    String resp;
                    if (p.length >= 3 && p[2].equals("CONSULTA")) {
                        try { resp = consultar(replica, p.length > 3 ? p[3] : "0"); }
                        catch (RuntimeException e) { resp = "ERROR;Parametros invalidos"; }
                    } else {
                        resp = "ERROR;La replica solo atiende consultas";
                    }
                    rep.send(resp);
                }
            }
        }, "replica");
        hiloReplica.setDaemon(true);
        hiloReplica.start();

        // al cerrar imprime el estado final
        Runtime.getRuntime().addShutdownHook(new Thread(() -> imprimirResumen("ESTADO FINAL DE LOS DATOS")));

        try (ZContext context = new ZContext()) {
            // socket principal: recibe pedidos del RMC y de Cancelaciones
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:" + puerto);
            imprimirResumen("ESTADO INICIAL DE LOS DATOS");
            System.out.println("[Persistencia] En linea en el puerto " + puerto);
            while (!Thread.currentThread().isInterrupted()) {
                String request = socket.recvStr(0);
                if (request == null) break;
                socket.send(procesar(request));
            }
        }
    }

    public static void main(String[] args) throws IOException {
        List<String> a = new ArrayList<>(Arrays.asList(args));
        boolean reset = a.remove("--reset");
        int puerto = a.size() > 0 ? Integer.parseInt(a.get(0)) : 5560;
        int puertoReplica = a.size() > 1 ? Integer.parseInt(a.get(1)) : 5563;
        String dir = a.size() > 2 ? a.get(2) : "datos";
        new ServicioPersistencia(dir, reset).iniciar(puerto, puertoReplica);
    }
}
