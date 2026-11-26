import java.io.FileInputStream;
import java.util.Properties;

// Cliente masivo por consola: lanza muchos clientes con los argumentos que se le pasen
public class ClienteMasivo {

    // imprime las opciones
    static void ayuda() {
        System.out.println("Uso: java ClienteMasivo [opciones]");
        System.out.println("  --config archivo     archivo .properties base (los demas argumentos lo sobreescriben)");
        System.out.println("  --ip IP              IP del GT                              (por defecto 10.43.100.138)");
        System.out.println("  --clientes N         cantidad de clientes simultaneos       (por defecto 3)");
        System.out.println("  --duracion S         segundos de la prueba                  (por defecto 120)");
        System.out.println("  --rpm R              solicitudes por minuto por cliente, min 20 (por defecto 30)");
        System.out.println("  --consultas P        % de consultas de los clientes mixtos  (por defecto 40)");
        System.out.println("  --reservas P         % de reservas                          (por defecto 40)");
        System.out.println("  --modificaciones P   % de modificaciones                    (por defecto 20)");
        System.out.println("  --cancelaciones P    % de cancelaciones                     (por defecto 0)");
        System.out.println("  --mes M              mes que se consulta, donde estan los eventos (por defecto 12)");
        System.out.println("  --salida carpeta     carpeta donde se guardan los CSV       (por defecto .)");
        System.out.println("  --reset              reinicia los datos de Persistencia antes de empezar");
        System.out.println("Los porcentajes deben sumar 100.");
        System.out.println("Ejemplo: java -cp out:jeromq-0.5.3.jar ClienteMasivo --ip 10.43.100.138 --clientes 8 --duracion 60 --reset");
    }

    // lee los argumentos y lanza la prueba de carga
    public static void main(String[] args) throws Exception {
        if (args.length == 0) { ayuda(); return; }
        Properties cfg = new Properties();

        // primero el archivo base, despues los argumentos encima
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--config") && i + 1 < args.length) {
                try (FileInputStream f = new FileInputStream(args[i + 1])) { cfg.load(f); }
            }
        }
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--config": i++; break;
                case "--reset": cfg.setProperty("resetAntes", "true"); break;
                case "--ayuda": case "-h": ayuda(); return;
                default: {
                    String clave = claveDe(a);
                    if (clave == null || i + 1 >= args.length) { System.err.println("Argumento no valido: " + a); ayuda(); return; }
                    cfg.setProperty(clave, args[++i]);
                }
            }
        }
        GeneradorCargaConcurrente.ejecutar(cfg);
    }

    // convierte el argumento en la clave de configuracion
    private static String claveDe(String arg) {
        switch (arg) {
            case "--ip": return "ipGT";
            case "--clientes": return "numClientes";
            case "--duracion": return "duracionSegundos";
            case "--rpm": return "solicitudesPorMinuto";
            case "--consultas": return "porcentajeConsultas";
            case "--reservas": return "porcentajeReservas";
            case "--modificaciones": return "porcentajeModificaciones";
            case "--cancelaciones": return "porcentajeCancelaciones";
            case "--mes": return "mesConsulta";
            case "--salida": return "salida";
            default: return null;
        }
    }
}
