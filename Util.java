import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

// Utilidades: abrir puertos y detectar si el principal cayo (PING/PONG)
public class Util {

    // abre el puerto, reintenta por si el principal acaba de caer
    public static void bindConReintentos(ZMQ.Socket s, String endpoint) {
        for (int i = 0; i < 20; i++) {
            try {
                s.bind(endpoint);
                return;
            } catch (Exception e) {
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
        throw new RuntimeException("No se pudo abrir " + endpoint);
    }

    // el principal responde PONG a cada PING del respaldo
    public static void iniciarPong(int puerto, String nombre) {
        Thread t = new Thread(() -> {
            try (ZContext ctx = new ZContext()) {
                ZMQ.Socket rep = ctx.createSocket(SocketType.REP);
                rep.bind("tcp://*:" + puerto);
                while (!Thread.currentThread().isInterrupted()) {
                    String m = rep.recvStr(0);
                    if (m == null) break;
                    rep.send("PONG");
                }
            } catch (Exception e) {
                System.err.println("[" + nombre + "] Error en hilo PONG: " + e.getMessage());
            }
        }, "pong-" + nombre);
        t.setDaemon(true);
        t.start();
    }

    // el respaldo manda PING cada 2 s; con 3 fallos seguidos el principal se da por caido
    public static void esperarCaida(String ipPrincipal, int puertoPing, String nombre) {
        try (ZContext ctx = new ZContext()) {
            ZMQ.Socket req = nuevoReq(ctx, ipPrincipal, puertoPing);
            int fallos = 0;
            System.out.println("[" + nombre + " RESPALDO] Monitoreando principal en " + ipPrincipal + ":" + puertoPing);
            while (fallos < 3) {
                req.send("PING");
                String r = req.recvStr(0);   // espera 1 s
                if (r == null) {
                    fallos++;
                    System.out.println("[" + nombre + " RESPALDO] Sin PONG (" + fallos + "/3)");
                    ctx.destroySocket(req);
                    req = nuevoReq(ctx, ipPrincipal, puertoPing);
                } else {
                    fallos = 0;
                }
                Thread.sleep(2000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("[" + nombre + " RESPALDO] PRINCIPAL CAIDO. Asumiendo su funcion...");
    }

    private static ZMQ.Socket nuevoReq(ZContext ctx, String ip, int puerto) {
        ZMQ.Socket s = ctx.createSocket(SocketType.REQ);
        s.setReceiveTimeOut(1000);
        s.setSendTimeOut(1000);
        s.setLinger(0);
        s.connect("tcp://" + ip + ":" + puerto);
        return s;
    }
}
