import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.util.Scanner;

public class ClienteInteractivo {

    public static void main(String[] args) {
        String ipGT = (args.length > 0) ? args[0] : "10.43.100.138";
        int puertoGT = 5555;

        System.out.println("=========================================================");
        System.out.println("                  CLIENTE INTERACTIVO");
        System.out.println("Conectando al Gestor de Transacciones en " + ipGT + ":" + puertoGT);
        System.out.println("=========================================================\n");

        try (ZContext context = new ZContext()) {
            ZMQ.Socket reqSync = context.createSocket(SocketType.REQ);
            reqSync.connect("tcp://" + ipGT + ":" + puertoGT);

            Scanner scanner = new Scanner(System.in);
            boolean salir = false;

            while (!salir) {
                System.out.println("\n=========================================================");
                System.out.println("                  MENU DE OPERACIONES");
                System.out.println("=========================================================");
                System.out.println("1. Consultar estado de eventos del mes");
                System.out.println("2. Realizar reserva de asientos");
                System.out.println("3. Salir del sistema");
                System.out.println("=========================================================");
                System.out.print("Seleccione una opcion: ");

                String opcion = scanner.nextLine().trim();

                switch (opcion) {
                    case "1": {
                        System.out.println("\n[Procesando] Enviando consulta de eventos...");
                        reqSync.send("CONSULTA;MES");
                        String respuesta = reqSync.recvStr(0);

                        System.out.println("\n--- RESPUESTA DEL SERVIDOR ---");
                        System.out.println(respuesta);
                        break;
                    }

                    case "2": {
                        System.out.print("Ingrese el ID del Evento (Ej. E1, E2 ... E50): ");
                        String id = scanner.nextLine().trim().toUpperCase();
                        System.out.print("Ingrese el numero de ocurrencia del evento (1 o 2): ");
                        String oc = scanner.nextLine().trim();
                        System.out.print("Ingrese la cantidad de asientos a reservar: ");
                        String cant = scanner.nextLine().trim();

                        String comando = "RESERVA;" + id + ";" + oc + ";" + cant;
                        System.out.println("\n[Procesando] Enviando solicitud de reserva: " + comando);
                        reqSync.send(comando);
                        String respuesta = reqSync.recvStr(0);

                        System.out.println("\n--- RESPUESTA DEL SERVIDOR ---");
                        System.out.println(respuesta);
                        break;
                    }

                    case "3":
                        salir = true;
                        System.out.println("Finalizando la ejecucion del cliente. Hasta pronto.");
                        break;

                    default:
                        System.out.println("Opcion no valida. Por favor intente de nuevo.");
                }
            }
        } catch (Exception e) {
            System.err.println("Error en la ejecucion del cliente: " + e.getMessage());
        }
    }
}