import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServicioPersistencia {

    public static class Evento {
        String id;
        int ocurrencias;
        int[] asientosDisponibles;
        int[] asientosOcupados;

        public Evento(String id, int ocurrencias) {
            this.id = id;
            this.ocurrencias = ocurrencias;
            this.asientosDisponibles = new int[ocurrencias];
            this.asientosOcupados = new int[ocurrencias];
            for (int i = 0; i < ocurrencias; i++) {
                this.asientosDisponibles[i] = 100;
                this.asientosOcupados[i] = 0;
            }
        }
    }

    private final Map<String, Evento> baseDatos = new ConcurrentHashMap<>();

    public ServicioPersistencia() {
        // Inicializacion de eventos 20 con dos ocurrencias y 30 con una sola y 100 en cada uno
        
        for (int i = 1; i <= 50; i++) {
            String id = "E" + i;
            int ocurrencias = (i <= 20) ? 2 : 1;
            baseDatos.put(id, new Evento(id, ocurrencias));
        }
    }
//solo para ver el inicio que si este bien
    public void imprimirEstadoInicialBD() {
        System.out.println("\n=====================================================================================");
        System.out.println("                       ESTADO INICIAL DE LA BASE DE DATOS");
        System.out.println("=====================================================================================");
        System.out.printf("%-5s | %-12s | %-55s\n", "ID", "Ocurrencias", "Disponibilidad de Asientos");
        System.out.println("-------------------------------------------------------------------------------------");
        
        for (int i = 1; i <= 50; i++) {
            Evento ev = baseDatos.get("E" + i);
            StringBuilder asientosInfo = new StringBuilder();
            for (int o = 0; o < ev.ocurrencias; o++) {
                asientosInfo.append(String.format("[Oc %d: %3d Libres, %3d Ocupados] ", 
                    (o + 1), ev.asientosDisponibles[o], ev.asientosOcupados[o]));
            }
            System.out.printf("%-5s | %-12d | %-55s\n", ev.id, ev.ocurrencias, asientosInfo.toString());
        }
        System.out.println("=====================================================================================\n");
    }

    public void iniciar(int puerto) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:" + puerto);

            imprimirEstadoInicialBD();
            System.out.println("[Persistencia] Servicio en linea y escuchando en el puerto " + puerto + "...");

            while (!Thread.currentThread().isInterrupted()) {
                String request = socket.recvStr(0);
                if (request == null) break;

                String response = procesarOperacion(request);
                socket.send(response);
            }
        }
    }

    private synchronized String procesarOperacion(String request) {
        String[] parts = request.split(";");
        String comando = parts[0];

        switch (comando) {
            case "CONSULTA": {
                System.out.println("[BD] Operacion: CONSULTA procesada exitosamente.");
                StringBuilder sb = new StringBuilder();
                sb.append("================================================================\n");
                sb.append("                 CATALOGO DE EVENTOS DEL MES\n");
                sb.append("================================================================\n");
                baseDatos.forEach((id, ev) -> {
                    sb.append(String.format("Evento ID: %-4s | Ocurrencias: %d | ", id, ev.ocurrencias));
                    for (int o = 0; o < ev.ocurrencias; o++) {
                        sb.append(String.format("[Oc %d -> Libres: %3d] ", (o + 1), ev.asientosDisponibles[o]));
                    }
                    sb.append("\n");
                });
                return sb.toString();
            }

            case "RESERVA": {
                String id = parts[1];
                int ocIdx = Integer.parseInt(parts[2]) - 1;
                int cantidad = Integer.parseInt(parts[3]);

                Evento ev = baseDatos.get(id);
                if (ev == null || ocIdx < 0 || ocIdx >= ev.ocurrencias) {
                    System.out.println("[BD] Operacion: RESERVA RECHAZADA. Evento " + id + " o fecha invalida.");
                    return "ERROR: Evento u ocurrencia no valida.";
                }

                if (ev.asientosDisponibles[ocIdx] >= cantidad) {
                    ev.asientosDisponibles[ocIdx] -= cantidad;
                    ev.asientosOcupados[ocIdx] += cantidad;
                    System.out.println(String.format("[BD] Operacion: RESERVA EXITOSA. Evento: %s (Oc %d) | Asientos reservados: %d | Libres restantes: %d",
                            id, (ocIdx + 1), cantidad, ev.asientosDisponibles[ocIdx]));
                    return "EXITO: Reserva confirmada. Asientos libres restantes en Oc " + (ocIdx + 1) + ": " + ev.asientosDisponibles[ocIdx];
                } else {
                    System.out.println(String.format("[BD] Operacion: RESERVA RECHAZADA. Evento: %s (Oc %d) | Asientos insuficientes.", id, (ocIdx + 1)));
                    return "RECHAZADO: No hay suficientes asientos disponibles.";
                }
            }

            default:
                return "ERROR: Operacion no implementada para la Primera Entrega.";
        }
    }

    public static void main(String[] args) {
        int puerto = (args.length > 0) ? Integer.parseInt(args[0]) : 5560;
        new ServicioPersistencia().iniciar(puerto);
    }
}