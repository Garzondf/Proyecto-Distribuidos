import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// Gestor de transacciones: valida al usuario y manda cada pedido al RMC (o a Cancelaciones)
public class GestorTransacciones {

    private static final AtomicInteger consultas = new AtomicInteger();
    private static final AtomicInteger reservas = new AtomicInteger();
    private static final AtomicInteger modificaciones = new AtomicInteger();
    private static final AtomicInteger cancelaciones = new AtomicInteger();
    // totales para el modo demo
    private static final AtomicInteger totC = new AtomicInteger(), totR = new AtomicInteger(),
            totM = new AtomicInteger(), totX = new AtomicInteger(), totRechazadas = new AtomicInteger();

    public static void main(String[] args) {
        List<String> a = new ArrayList<>(Arrays.asList(args));
        boolean respaldo = a.remove("--respaldo");
        boolean demo = a.remove("--demo");
        String rmcs = a.size() > 0 ? a.get(0) : "localhost:5557";
        String ipCanc = a.size() > 1 ? a.get(1) : "localhost";
        String rutaUsuarios = a.size() > 2 ? a.get(2) : "usuarios.txt";

        if (respaldo) Util.esperarCaida("localhost", 5561, "GT");
        else Util.iniciarPong(5561, "GT");

        Map<String, String> usuarios = Seguridad.cargarUsuarios(rutaUsuarios);
        System.out.println("[GT] Usuarios cargados: " + usuarios.size());

        iniciarMonitorTPM();
        if (demo) iniciarResumenDemo(respaldo ? "GT RESPALDO" : "GT");

        try (ZContext context = new ZContext()) {
            ZMQ.Socket front = context.createSocket(SocketType.ROUTER);
            Util.bindConReintentos(front, "tcp://*:5555");
            ZMQ.Socket canc = context.createSocket(SocketType.ROUTER);
            Util.bindConReintentos(canc, "tcp://*:5556");

            // back: salida hacia los RMC
            ZMQ.Socket back = context.createSocket(SocketType.DEALER);
            for (String r : rmcs.split(",")) back.connect("tcp://" + r.trim());

            // push: salida hacia Cancelaciones
            ZMQ.Socket push = context.createSocket(SocketType.PUSH);
            push.setSendTimeOut(2000);
            push.connect("tcp://" + ipCanc + ":5558");

            System.out.println("=========================================================");
            System.out.println(" GESTOR DE TRANSACCIONES " + (respaldo ? "(RESPALDO ACTIVO) " : ""));
            System.out.println(" Clientes :5555 | Cancelaciones :5556 | RMC: " + rmcs);
            System.out.println("=========================================================");

            ZMQ.Poller poller = context.createPoller(3);
            poller.register(front, ZMQ.Poller.POLLIN);
            poller.register(back, ZMQ.Poller.POLLIN);
            poller.register(canc, ZMQ.Poller.POLLIN);

            while (!Thread.currentThread().isInterrupted()) {
                if (poller.poll(1000) < 0) break;

                // llega un pedido de un cliente
                if (poller.pollin(0)) {
                    ZMsg m = ZMsg.recvMsg(front);
                    if (m == null) break;
                    ZFrame id = m.pop();   // quien lo envio
                    m.pop();               // frame vacio del REQ
                    String payload = m.popString();
                    String[] p = payload == null ? new String[0] : payload.split(";", -1);
                    if (p.length < 4 || !Seguridad.validar(usuarios, p[1], p[2])) {
                        System.out.println("[GT] Solicitud rechazada: credenciales invalidas");
                        totRechazadas.incrementAndGet();
                        responder(front, id, true, "ERROR;Credenciales invalidas");
                        continue;
                    }
                    String cmd = p[3];
                    String reenviar = reenvio(p);
                    contar(cmd);
                    System.out.println("[GT] " + cmd + " de " + p[1] + " (" + p[0] + ") -> RMC");
                    // se manda al RMC
                    ZMsg out = new ZMsg();
                    out.add(id);
                    out.add("");
                    out.add(reenviar);
                    out.send(back);
                }

                // respuesta del RMC: se devuelve al cliente
                if (poller.pollin(1)) {
                    ZMsg r = ZMsg.recvMsg(back);
                    if (r != null) r.send(front);
                }

                // llega una cancelacion: se manda a Cancelaciones y se responde ACK
                if (poller.pollin(2)) {
                    ZMsg m = ZMsg.recvMsg(canc);
                    if (m == null) break;
                    ZFrame id = m.pop();
                    String payload = m.popString();
                    String[] p = payload == null ? new String[0] : payload.split(";", -1);
                    if (p.length < 5 || !Seguridad.validar(usuarios, p[1], p[2]) || !p[3].equals("CANCELA")) {
                        System.out.println("[GT] Cancelacion rechazada (credenciales o formato)");
                        responder(canc, id, false, "ERROR;Credenciales invalidas");
                        continue;
                    }
                    boolean enviado = push.send(reenvio(p));
                    if (enviado) {
                        cancelaciones.incrementAndGet();
                        totX.incrementAndGet();
                        System.out.println("[GT] CANCELA de " + p[1] + " (" + p[0] + ") -> Cancelaciones. ACK enviado");
                        responder(canc, id, false, "ACK;" + p[0] + ";Cancelacion recibida, se notificara el resultado");
                    } else {
                        responder(canc, id, false, "ERROR;Servicio de cancelaciones no disponible");
                    }
                }
            }
        }
    }

    // arma el mensaje sin la clave
    private static String reenvio(String[] p) {
        StringBuilder sb = new StringBuilder(p[0]).append(';').append(p[1]);
        for (int i = 3; i < p.length; i++) sb.append(';').append(p[i]);
        return sb.toString();
    }

    private static void responder(ZMQ.Socket s, ZFrame id, boolean conVacio, String texto) {
        ZMsg out = new ZMsg();
        out.add(id);
        if (conVacio) out.add("");
        out.add(texto);
        out.send(s);
    }

    private static void contar(String cmd) {
        switch (cmd) {
            case "CONSULTA": consultas.incrementAndGet(); totC.incrementAndGet(); break;
            case "RESERVA": reservas.incrementAndGet(); totR.incrementAndGet(); break;
            case "MODIFICA": modificaciones.incrementAndGet(); totM.incrementAndGet(); break;
            default: break;
        }
    }

    // Demo: imprime los totales cada 5 s
    private static void iniciarResumenDemo(String nombre) {
        Thread t = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(5000);
                    System.out.println("##### [" + nombre + "] TRANSACCIONES ATENDIDAS: consultas=" + totC.get()
                            + " reservas=" + totR.get() + " modificaciones=" + totM.get()
                            + " cancelaciones=" + totX.get() + " rechazadas(auth)=" + totRechazadas.get() + " #####");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "demo");
        t.setDaemon(true);
        t.start();
    }

    private static void iniciarMonitorTPM() {
        Thread t = new Thread(() -> {
            int minuto = 0;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(60_000);
                    minuto++;
                    int c = consultas.getAndSet(0), r = reservas.getAndSet(0);
                    int m = modificaciones.getAndSet(0), x = cancelaciones.getAndSet(0);
                    System.out.println("\n--- TPM minuto " + minuto + " --- consultas=" + c + " reservas=" + r
                            + " modificaciones=" + m + " cancelaciones=" + x + "\n");
                    try (FileWriter w = new FileWriter("tpm_gt.csv", true)) {
                        w.write(minuto + "," + c + "," + r + "," + m + "," + x + "\n");
                    } catch (IOException e) {
                        System.err.println("[GT] No se pudo escribir tpm_gt.csv");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "tpm");
        t.setDaemon(true);
        t.start();
    }
}
