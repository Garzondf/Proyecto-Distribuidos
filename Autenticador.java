import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Valida usuario/contraseña contra usuarios.txt (formato: usuario:hashSHA256).
 * Se carga en memoria al iniciar el GT; no mantiene sesiones, así que el
 * GT de respaldo puede cargar su propia copia y validar igual.
 */
public class Autenticador {
    private final Map<String, String> hashes = new HashMap<>();

    public Autenticador(String rutaArchivo) throws IOException {
        for (String linea : Files.readAllLines(Paths.get(rutaArchivo), StandardCharsets.UTF_8)) {
            linea = linea.trim();
            if (linea.isEmpty() || linea.startsWith("#")) continue;
            String[] p = linea.split(":");
            if (p.length == 2) hashes.put(p[0].trim(), p[1].trim().toLowerCase());
        }
    }

    public boolean validar(String usuario, String contrasena) {
        if (usuario == null || contrasena == null) return false;
        String esperado = hashes.get(usuario);
        // Se calcula el hash siempre para que usuario inexistente y contraseña
        // incorrecta se comporten igual (y respondan el mismo error).
        String recibido = sha256(contrasena);
        return esperado != null && MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), recibido.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
