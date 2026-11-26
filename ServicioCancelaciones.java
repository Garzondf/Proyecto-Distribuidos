import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

// Cancelaciones: recibe la cancelacion del GT, la manda a Persistencia y avisa al cliente
public class ServicioCancelaciones {
    // recibe cancelaciones, las manda a Persistencia y publica el resultado
    public static void main(String[] args) {
        String ipPers = args.length > 0 ? args[0] : "10.43.99.202";
        try (ZContext context = new ZContext()) {
            ZMQ.Socket pull = context.createSocket(SocketType.PULL);
            pull.bind("tcp://*:5558");   // recibe del GT
            ZMQ.Socket pub = context.createSocket(SocketType.PUB);
            pub.bind("tcp://*:5559");    // avisa al cliente
            EnlaceREQ bd = new EnlaceREQ(context, "tcp://" + ipPers + ":5560", 5000);

            System.out.println("=========================================================");
            System.out.println(" SERVICIO DE CANCELACIONES Y NOTIFICACIONES");
            System.out.println(" PULL :5558 | PUB :5559 | Persistencia " + ipPers + ":5560");
            System.out.println("=========================================================");

            while (!Thread.currentThread().isInterrupted()) {
                String msg = pull.recvStr(0);
                if (msg == null) break;
                String[] p = msg.split(";", -1);
                String usuario = p.length > 1 ? p[1] : "?";
                String resp = bd.pedir(msg);
                System.out.println("[CANCELACIONES] " + usuario + " -> " + resp.split(";")[0]);
                String texto = resp.contains(";") ? resp.substring(resp.indexOf(';') + 1) : resp;
                pub.sendMore(usuario + ":");
                pub.send("[" + resp.split(";")[0] + "] " + texto);
            }
        }
    }
}
