import java.util.Scanner;

// Cliente por consola: menu para consultar, reservar, modificar y cancelar
public class ClienteInteractivo {
    // pide usuario y clave y muestra el menu
    public static void main(String[] args) {
        String ipGT = args.length > 0 ? args[0] : "10.43.100.138";
        String usuario = args.length > 1 ? args[1] : "user1";
        Scanner sc = new Scanner(System.in);
        String password;
        if (args.length > 2) password = args[2];
        else { System.out.print("Password de " + usuario + ": "); password = sc.nextLine().trim(); }

        System.out.println("=========================================================");
        System.out.println(" CLIENTE INTERACTIVO - usuario " + usuario + " - GT " + ipGT);
        System.out.println("=========================================================");

        try (ClienteBase c = new ClienteBase(ipGT, 5555, 5556, usuario, password)) {
            c.escucharNotificaciones(ipGT, msg -> System.out.println("\n>>> NOTIFICACION: " + msg));
            boolean salir = false;
            while (!salir) {
                System.out.println("\n1. Consultar eventos de un mes   2. Reservar   3. Modificar reserva");
                System.out.println("4. Cancelar reserva (asincrona)  5. Salir");
                System.out.print("Opcion: ");
                switch (sc.nextLine().trim()) {
                    case "1": {
                        System.out.print("Mes (1-12, 0 = todos): ");
                        mostrar(c.solicitar("CONSULTA;" + sc.nextLine().trim()));
                        break;
                    }
                    case "2": {
                        System.out.print("Evento (E1..E50): ");
                        String ev = sc.nextLine().trim().toUpperCase();
                        System.out.print("Ocurrencia (1 o 2): ");
                        String oc = sc.nextLine().trim();
                        System.out.print("Asientos: ");
                        String n = sc.nextLine().trim();
                        mostrar(c.solicitar("RESERVA;" + ev + ";" + oc + ";" + n));
                        break;
                    }
                    case "3": {
                        System.out.print("ID de reserva (ej. R1): ");
                        String id = sc.nextLine().trim().toUpperCase();
                        System.out.print("Nueva cantidad de asientos (0 = mantener): ");
                        String n = sc.nextLine().trim();
                        System.out.print("Nueva ocurrencia (0 = mantener, 1 o 2): ");
                        String oc = sc.nextLine().trim();
                        mostrar(c.solicitar("MODIFICA;" + id + ";" + n + ";" + oc));
                        break;
                    }
                    case "4": {
                        System.out.print("ID de reserva a cancelar (ej. R1): ");
                        mostrar(c.cancelar(sc.nextLine().trim().toUpperCase()));
                        System.out.println("(el resultado final llegara como notificacion)");
                        break;
                    }
                    case "5": salir = true; break;
                    default: System.out.println("Opcion no valida.");
                }
            }
        }
    }

    // imprime la respuesta del sistema
    private static void mostrar(String r) {
        System.out.println("\n--- RESPUESTA ---\n" + r.replaceFirst(";", ": "));
    }
}
