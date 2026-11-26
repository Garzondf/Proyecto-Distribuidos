import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.UUID;
import java.util.function.Consumer;

// Cliente base: envia las solicitudes al GT y reintenta si no hay respuesta
public class ClienteBase implements AutoCloseable {
    public static final int TIMEOUT_MS = 5000;
    public static final int REINTENTOS = 3;

    private final ZContext ctx = new ZContext();
    private final String ip, usuario;
    private final int puertoSync, puertoCancel;
    private ZMQ.Socket req, dealer;

    // crea los sockets hacia el GT (5555 consultas/reservas, 5556 cancelaciones)
    public ClienteBase(String ip, int puertoSync, int puertoCancel, String usuario) {
        this.ip = ip; this.puertoSync = puertoSync; this.puertoCancel = puertoCancel;
        this.usuario = usuario;
        req = abrirReq();
        dealer = abrirDealer();
    }

    // abre el socket de solicitudes con timeout
    private ZMQ.Socket abrirReq() {
        ZMQ.Socket s = ctx.createSocket(SocketType.REQ);
        s.setReceiveTimeOut(TIMEOUT_MS);
        s.setLinger(0);
        s.connect("tcp://" + ip + ":" + puertoSync);
        return s;
    }

    // abre el socket de cancelaciones con timeout
    private ZMQ.Socket abrirDealer() {
        ZMQ.Socket s = ctx.createSocket(SocketType.DEALER);
        s.setReceiveTimeOut(TIMEOUT_MS);
        s.setLinger(0);
        s.connect("tcp://" + ip + ":" + puertoCancel);
        return s;
    }

    // identificador unico de la solicitud, sirve para no repetir operaciones
    private String nuevoId() {
        return usuario + "-" + UUID.randomUUID().toString().substring(0, 12);
    }

    // Envia una solicitud al GT (ej. "RESERVA;E3;1;2") y espera la respuesta
    public String solicitar(String comando) {
        String payload = nuevoId() + ";" + usuario + ";" + comando;
        for (int intento = 0; intento <= REINTENTOS; intento++) {
            req.send(payload);
            String r = req.recvStr(0);
            if (r != null) return r;
            // sin respuesta: se cierra el socket y se reenvia lo mismo
            System.err.println("[Cliente " + usuario + "] Sin respuesta en " + (TIMEOUT_MS / 1000)
                    + " s. Reintento " + (intento + 1) + "/" + REINTENTOS);
            ctx.destroySocket(req);
            req = abrirReq();
        }
        return "ERROR;Sin respuesta del sistema tras " + REINTENTOS + " reintentos";
    }

    // Envia la cancelacion; aqui solo llega el ACK, el resultado llega por notificacion
    public String cancelar(String idReserva) {
        String payload = nuevoId() + ";" + usuario + ";CANCELA;" + idReserva;
        for (int intento = 0; intento <= REINTENTOS; intento++) {
            dealer.send(payload);
            String r = dealer.recvStr(0);
            if (r != null) return r;
            System.err.println("[Cliente " + usuario + "] Sin ACK de cancelacion. Reintento " + (intento + 1) + "/" + REINTENTOS);
            ctx.destroySocket(dealer);
            dealer = abrirDealer();
        }
        return "ERROR;Sin respuesta del sistema tras " + REINTENTOS + " reintentos";
    }

    // Hilo que escucha las notificaciones de este usuario
    public Thread escucharNotificaciones(String ipNotificaciones, Consumer<String> alRecibir) {
        Thread t = new Thread(() -> {
            try (ZContext c = new ZContext()) {
                ZMQ.Socket sub = c.createSocket(SocketType.SUB);
                sub.connect("tcp://" + ipNotificaciones + ":5559");
                sub.subscribe((usuario + ":").getBytes());
                while (!Thread.currentThread().isInterrupted()) {
                    String topico = sub.recvStr(0);
                    if (topico == null) break;
                    String cuerpo = sub.recvStr(0);
                    alRecibir.accept(cuerpo);
                }
            } catch (Exception e) {
                // se cerro el cliente
            }
        }, "notificaciones-" + usuario);
        t.setDaemon(true);
        t.start();
        return t;
    }

    @Override
    // cierra los sockets
    public void close() {
        ctx.close();
    }
}
