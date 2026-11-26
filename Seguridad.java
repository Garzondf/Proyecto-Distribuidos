import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

// Seguridad: hash SHA-256 y validacion de usuarios
public class Seguridad {

    // convierte un texto en su hash SHA-256
    public static String sha256(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // lee usuarios.txt (formato usuario:hash)
    public static Map<String, String> cargarUsuarios(String ruta) {
        Map<String, String> usuarios = new HashMap<>();
        try {
            Path p = Paths.get(ruta);
            for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) continue;
                String[] t = linea.split(":", 2);
                if (t.length == 2) usuarios.put(t[0].trim(), t[1].trim().toLowerCase());
            }
        } catch (Exception e) {
            System.err.println("[Seguridad] No se pudo leer " + ruta + ": " + e.getMessage());
        }
        return usuarios;
    }

    // true si el usuario existe y la clave coincide
    public static boolean validar(Map<String, String> usuarios, String usuario, String password) {
        String hash = usuarios.get(usuario);
        // compara el hash de la clave con el guardado
        String hashPass = sha256(password == null ? "" : password);
        return hash != null && hash.equals(hashPass);
    }
}
