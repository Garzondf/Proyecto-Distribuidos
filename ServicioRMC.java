import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class ServicioRMC {

    public static void main(String[] args) {
        String ipPersistencia = (args.length > 0) ? args[0] : "10.43.99.202";
        int puertoPersistencia = 5560;
        int puertoEscucha = (args.length > 1) ? Integer.parseInt(args[1]) : 5557;

        try (ZContext context = new ZContext()) {
            ZMQ.Socket repGT = context.createSocket(SocketType.REP);
            repGT.bind("tcp://*:" + puertoEscucha);

            ZMQ.Socket reqBD = context.createSocket(SocketType.REQ);
            reqBD.connect("tcp://" + ipPersistencia + ":" + puertoPersistencia);

            System.out.println("=========================================================");
            System.out.println("   SERVICIO RMC (RESERVAS, MODIFICACIONES Y CONSULTAS)");
            System.out.println("   Escuchando en puerto: " + puertoEscucha);
            System.out.println("   Conectado a Persistencia: " + ipPersistencia + ":" + puertoPersistencia);
            System.out.println("=========================================================\n");

            while (!Thread.currentThread().isInterrupted()) {
                String peticion = repGT.recvStr(0);
                if (peticion == null) break;

                long tiempoInicio = System.nanoTime();
                String tipoOperacion = peticion.split(";")[0];
                
                System.out.println("[RMC TIEMPO REAL] Recibida peticion de " + tipoOperacion + " desde el GT.");
                System.out.println("[RMC TRAMA] " + peticion);

                // Reenviar la solicitud hacia el Servicio de Persistencia (Base de Datos)
                reqBD.send(peticion);
                String respuestaBD = reqBD.recvStr(0);

                long tiempoFin = System.nanoTime();
                long latenciaProceso = (tiempoFin - tiempoInicio) / 1_000_000;

                System.out.println("[RMC TIEMPO REAL] Respuesta obtenida de la BD en " + latenciaProceso + " ms.");
                System.out.println("[RMC RESPUESTA] " + respuestaBD.split(";")[0]);
                System.out.println("---------------------------------------------------------");

                // Retornar la respuesta al Gestor de Transacciones
                repGT.send(respuestaBD);
            }
        }
    }
}
