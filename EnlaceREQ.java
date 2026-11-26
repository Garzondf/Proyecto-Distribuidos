import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

// Conexion REQ con timeout: si no responden, recrea el socket y devuelve error
public class EnlaceREQ {
    private final ZContext ctx;
    private final String endpoint;
    private final int timeoutMs;
    private ZMQ.Socket socket;

    // guarda la direccion y abre el socket
    public EnlaceREQ(ZContext ctx, String endpoint, int timeoutMs) {
        this.ctx = ctx; this.endpoint = endpoint; this.timeoutMs = timeoutMs;
        abrir();
    }

    // crea el socket REQ
    private void abrir() {
        socket = ctx.createSocket(SocketType.REQ);
        socket.setReceiveTimeOut(timeoutMs);
        socket.setLinger(0);
        socket.connect(endpoint);
    }

    // envia el mensaje y espera la respuesta
    public String pedir(String mensaje) {
        socket.send(mensaje);
        String r = socket.recvStr(0);
        if (r == null) {
            ctx.destroySocket(socket);
            abrir();
            return "ERROR;El servicio de persistencia no responde";
        }
        return r;
    }
}
