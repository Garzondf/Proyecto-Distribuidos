import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// RMC: recibe pedidos del GT y los manda a Persistencia
public class ServicioRMC {
    public static void main(String[] args) {
        List<String> a = new ArrayList<>(Arrays.asList(args));
        boolean respaldo = a.remove("--respaldo");
        boolean usarReplica = a.remove("--replica");
        String ipPers = a.size() > 0 ? a.get(0) : "10.43.99.202";
        int puertoEscucha = a.size() > 1 ? Integer.parseInt(a.get(1)) : 5557;
        int puertoPing = a.size() > 2 ? Integer.parseInt(a.get(2)) : 5562;

        // el respaldo espera a que caiga el principal; el principal responde los PING
        if (respaldo) Util.esperarCaida("localhost", puertoPing, "RMC");
        else if (puertoPing > 0) Util.iniciarPong(puertoPing, "RMC");

        try (ZContext context = new ZContext()) {
            ZMQ.Socket repGT = context.createSocket(SocketType.REP);
            Util.bindConReintentos(repGT, "tcp://*:" + puertoEscucha);

            EnlaceREQ principal = new EnlaceREQ(context, "tcp://" + ipPers + ":5560", 3000);
            EnlaceREQ replica = usarReplica ? new EnlaceREQ(context, "tcp://" + ipPers + ":5563", 3000) : principal;

            System.out.println("=========================================================");
            System.out.println(" RMC " + (respaldo ? "(RESPALDO ACTIVO) " : "") + "- puerto " + puertoEscucha);
            System.out.println(" Persistencia: " + ipPers + ":5560" + (usarReplica ? " | consultas -> replica :5563" : ""));
            System.out.println("=========================================================");

            while (!Thread.currentThread().isInterrupted()) {
                String peticion = repGT.recvStr(0);
                if (peticion == null) break;
                long t0 = System.nanoTime();
                String[] p = peticion.split(";", -1);
                String cmd = p.length > 2 ? p[2] : "?";
                String usuario = p.length > 1 ? p[1] : "?";

                // las consultas pueden ir a la replica, lo demas al principal
                String resp = cmd.equals("CONSULTA") ? replica.pedir(peticion) : principal.pedir(peticion);

                long ms = (System.nanoTime() - t0) / 1_000_000;
                System.out.println("[RMC] " + cmd + " de " + usuario + " -> " + resp.split(";")[0] + " (" + ms + " ms)");
                repGT.send(resp);
            }
        }
    }
}
