import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

// Crea usuarios.txt (usuario:hashSHA256) para user1..user8 y admin, y credenciales_clientes.properties
// con las contrasenas de prueba (solo las usa el generador de carga y el cliente en el Nodo 3).
// Las contrasenas son fijas (clave1!, clave2!, ..., admin123!), asi que se puede correr en cualquier VM.
public class GenerarUsuarios {
    public static void main(String[] args) throws IOException {
        StringBuilder usuarios = new StringBuilder("# usuario:hashSHA256\n");
        StringBuilder props = new StringBuilder();
        for (int i = 1; i <= 9; i++) {
            String u = (i == 9) ? "admin" : "user" + i;
            String clave = (i == 9) ? "admin123!" : "clave" + i + "!";
            usuarios.append(u).append(':').append(Autenticador.sha256(clave)).append('\n');
            props.append(u).append('=').append(clave).append('\n');
        }
        Files.write(Paths.get("usuarios.txt"), usuarios.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(Paths.get("credenciales_clientes.properties"), props.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Generados usuarios.txt y credenciales_clientes.properties (user1..user8 y admin)");
    }
}
