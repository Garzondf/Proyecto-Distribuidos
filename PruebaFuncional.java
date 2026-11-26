import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Prueba funcional automatica: corre los casos de prueba contra el sistema y dice cuales pasan
// Uso: java PruebaFuncional [ipGT]
public class PruebaFuncional {

    static int total = 0, ok = 0;

    // imprime el resultado de un caso
    static void caso(String nombre, boolean paso, String detalle) {
        total++;
        if (paso) ok++;
        System.out.println((paso ? "[OK]    " : "[FALLA] ") + nombre + (paso ? "" : "  -> " + detalle));
    }

    // devuelve la linea de una ocurrencia en la respuesta de una consulta (ej. "E1" oc 2)
    static String linea(String consulta, String evento, int oc) {
        for (String l : consulta.split("\n"))
            if (l.startsWith(evento + " ") && l.contains("oc" + oc + " ")) return l;
        return "";
    }

    public static void main(String[] args) throws Exception {
        String ip = args.length > 0 ? args[0] : "10.43.100.138";
        GeneradorCargaConcurrente.cargarCredenciales();
        String c1 = GeneradorCargaConcurrente.clave("user1"), c2 = GeneradorCargaConcurrente.clave("user2");

        System.out.println("=========================================================");
        System.out.println(" PRUEBA FUNCIONAL - GT " + ip);
        System.out.println("=========================================================");

        try (ClienteBase admin = new ClienteBase(ip, 5555, 5556, "admin", GeneradorCargaConcurrente.clave("admin"));
             ClienteBase u1 = new ClienteBase(ip, 5555, 5556, "user1", c1);
             ClienteBase u2 = new ClienteBase(ip, 5555, 5556, "user2", c2);
             ClienteBase malo = new ClienteBase(ip, 5555, 5556, "user1", "clave-incorrecta")) {

            // deja los datos como al inicio
            String reset = admin.solicitar("RESET");
            caso("Reiniciar datos (admin)", reset.startsWith("EXITO"), reset);

            // notificaciones de cancelacion de cada usuario
            List<String> notif1 = new ArrayList<>(), notif2 = new ArrayList<>();
            u1.escucharNotificaciones(ip, m -> { synchronized (notif1) { notif1.add(m); } });
            u2.escucharNotificaciones(ip, m -> { synchronized (notif2) { notif2.add(m); } });
            Thread.sleep(1500);   // tiempo para que las suscripciones queden activas

            // ---- consultas
            String r = u1.solicitar("CONSULTA;12");
            caso("Consulta del mes 12 devuelve los 50 eventos (70 ocurrencias)", r.startsWith("EXITO;70 ocurrencias"), r.split("\n")[0]);
            r = u1.solicitar("CONSULTA;1");
            caso("Consulta de un mes sin eventos devuelve 0", r.startsWith("EXITO;0 ocurrencias"), r.split("\n")[0]);

            // ---- reservas
            r = u1.solicitar("RESERVA;E1;1;2");
            boolean r1 = r.startsWith("EXITO") && r.contains("idReserva=R1");
            caso("Reserva de 2 asientos (E1, ocurrencia 1)", r1, r);
            r = u1.solicitar("RESERVA;E1;1;99");
            caso("Reserva con asientos insuficientes es rechazada", r.startsWith("RECHAZADO"), r);
            r = u1.solicitar("RESERVA;E99;1;1");
            caso("Reserva de un evento inexistente es rechazada", r.startsWith("RECHAZADO"), r);

            // ---- modificaciones
            r = u1.solicitar("MODIFICA;R1;5;0");
            caso("Modificar el numero de asientos (2 a 5)", r.startsWith("EXITO"), r);
            r = u1.solicitar("MODIFICA;R1;0;2");
            caso("Cambiar de fecha en un evento con dos ocurrencias", r.startsWith("EXITO"), r);
            String c = u1.solicitar("CONSULTA;12");
            caso("Tras el cambio, la ocurrencia 1 queda libre y la 2 con 5 ocupados",
                    linea(c, "E1", 1).contains("libres 100") && linea(c, "E1", 2).contains("ocupados   5"), linea(c, "E1", 1) + " / " + linea(c, "E1", 2));
            r = u1.solicitar("RESERVA;E30;1;1");
            r = u1.solicitar("MODIFICA;R2;0;2");
            caso("Evento de una sola ocurrencia rechaza el cambio de fecha", r.startsWith("RECHAZADO"), r);
            r = u2.solicitar("MODIFICA;R1;3;0");
            caso("Solo el titular puede modificar una reserva", r.startsWith("RECHAZADO"), r);

            // ---- seguridad
            r = malo.solicitar("CONSULTA;12");
            caso("Clave incorrecta es rechazada", r.contains("Credenciales invalidas"), r);

            // ---- solicitud repetida (mismo identificador): no se aplica dos veces
            String a = u1.solicitarConId("repetida-001", "RESERVA;E2;1;3");
            String b = u1.solicitarConId("repetida-001", "RESERVA;E2;1;3");
            c = u1.solicitar("CONSULTA;12");
            caso("Solicitud repetida devuelve el mismo resultado y no duplica la reserva",
                    a.equals(b) && linea(c, "E2", 1).contains("ocupados   3"), a + " | " + b + " | " + linea(c, "E2", 1));

            // ---- cancelaciones asincronas
            r = u2.cancelar("R1");
            caso("Cancelacion devuelve ACK inmediato", r.startsWith("ACK"), r);
            boolean llego = esperar(notif2, "RECHAZADO");
            caso("Cancelar una reserva ajena: la notificacion llega rechazada", llego, notif2.toString());
            r = u1.cancelar("R1");
            caso("Cancelacion del titular devuelve ACK inmediato", r.startsWith("ACK"), r);
            llego = esperar(notif1, "EXITO");
            caso("La notificacion de cancelacion exitosa llega al cliente", llego, notif1.toString());
            c = u1.solicitar("CONSULTA;12");
            caso("Tras cancelar, los asientos quedan libres", linea(c, "E1", 2).contains("libres 100"), linea(c, "E1", 2));

            // ---- concurrencia: 8 clientes piden mas asientos de los que hay, no debe haber sobreventa
            AtomicInteger exitos = new AtomicInteger();
            CountDownLatch fin = new CountDownLatch(8);
            for (int i = 1; i <= 8; i++) {
                final String usuario = "user" + i;
                new Thread(() -> {
                    try (ClienteBase cl = new ClienteBase(ip, 5555, 5556, usuario, GeneradorCargaConcurrente.clave(usuario))) {
                        for (int k = 0; k < 5; k++)   // 8 x 5 x 5 asientos = 200 pedidos sobre 100 asientos
                            if (cl.solicitar("RESERVA;E3;1;5").startsWith("EXITO")) exitos.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("Error en " + usuario + ": " + e);
                    } finally {
                        fin.countDown();
                    }
                }).start();
            }
            fin.await();
            c = u1.solicitar("CONSULTA;12");
            caso("Concurrencia: exactamente 20 reservas de 5 asientos sobre 100 (sin sobreventa)",
                    exitos.get() == 20 && linea(c, "E3", 1).contains("libres   0"), "exitos=" + exitos.get() + " " + linea(c, "E3", 1));
        }

        System.out.println("=========================================================");
        System.out.println(" RESULTADO: " + ok + " de " + total + " casos pasaron");
        System.out.println("=========================================================");
    }

    // espera hasta 6 s a que llegue una notificacion que contenga el texto
    static boolean esperar(List<String> lista, String texto) throws InterruptedException {
        long limite = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < limite) {
            synchronized (lista) { for (String m : lista) if (m.contains(texto)) return true; }
            Thread.sleep(200);
        }
        return false;
    }
}
