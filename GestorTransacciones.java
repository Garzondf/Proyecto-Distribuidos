import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.util.concurrent.atomic.AtomicInteger;

public class GestorTransacciones {

    private static final AtomicInteger contadorConsultas = new AtomicInteger(0);
    private static final AtomicInteger contadorReservas = new AtomicInteger(0);

    public static void main(String[] args) {
        String ipRMC = (args.length > 0) ? args[0] : "localhost";
        int puertoRMC = 5557;
        int puertoClientes = 5555;

        // Hilo en segundo plano para medir y reportar los TPM (Transacciones por Minuto)
        Thread monitorTPM = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(60000); // Esperar 1 minuto muchisimo tiempo 
                    int totalConsultas = contadorConsultas.getAndSet(0);
                    int totalReservas = contadorReservas.getAndSet(0);
                    System.out.println("\n--- METRICAS DE RENDIMIENTO (TPM) ---");
                    System.out.println("Consultas en el ultimo minuto: " + totalConsultas + " TPM");
                    System.out.println("Reservas en el ultimo minuto: " + totalReservas + " TPM");
                    System.out.println("-------------------------------------\n");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        monitorTPM.setDaemon(true);
        monitorTPM.start();

        try (ZContext context = new ZContext()) {
            ZMQ.Socket repClientes = context.createSocket(SocketType.REP);
            repClientes.bind("tcp://*:" + puertoClientes);

            ZMQ.Socket reqRMC = context.createSocket(SocketType.REQ);
            reqRMC.connect("tcp://" + ipRMC + ":" + puertoRMC);

            System.out.println("[Gestor de Transacciones] Escuchando peticiones de clientes en puerto " + puertoClientes);

            while (!Thread.currentThread().isInterrupted()) {
                String msg = repClientes.recvStr(0);
                if (msg == null) break;

                String comando = msg.split(";")[0];
                System.out.println("[GT] Peticion recibida: " + comando + ". Enrutando a RMC...");
                
                reqRMC.send(msg);
                String resp = reqRMC.recvStr(0);
                
                repClientes.send(resp);

                if (comando.equals("CONSULTA")) {
                    contadorConsultas.incrementAndGet();
                } else if (comando.equals("RESERVA")) {
                    contadorReservas.incrementAndGet();
                }
            }
        }
    }
}