package udp.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Servidor UDP (version reducida): recibe el nombre del jugador y responde con una imagen.
 *
 * Peticion:  {"type":"HELLO","payload":{"name":"Luis"}}
 * Respuesta: {"type":"IMAGE","payload":{"name":"Luis","message":"...","image":"<Base64>"}}
 * Error:     {"type":"ERROR","payload":{"code":"...","message":"..."}}
 *
 * La imagen se elige segun el nombre: el mismo nombre recibe siempre la misma tarjeta.
 * Uso: gradle :udp:runServer [-Pport=8080]
 */
public class UdpServer {

    /** Tamaño maximo de un datagrama UDP. La respuesta completa debe caber en uno solo. */
    static final int MAX_DATAGRAM = 65_507;
    private static final int MAX_NAME = 20;

    private final List<String> images = new ArrayList<>();

    public UdpServer(Path imageDir) throws IOException {
        for (int i = 1; i <= 4; i++) {
            Path file = imageDir.resolve("udp_" + i + ".png");
            if (!Files.isRegularFile(file)) {
                throw new IOException("No se encontro la imagen " + file.toAbsolutePath());
            }
            images.add(Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
        }
    }

    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                System.out.println("Puerto no valido (" + args[0] + "); se usa 8080");
            }
        }
        UdpServer server;
        try {
            server = new UdpServer(Path.of("img"));
        } catch (IOException e) {
            System.out.println("No se pudieron cargar las imagenes: " + e.getMessage());
            System.exit(1);
            return;
        }
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Servidor UDP escuchando en el puerto " + port);
            byte[] buffer = new byte[MAX_DATAGRAM];
            while (true) {
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                socket.receive(request);
                String text = new String(request.getData(), 0, request.getLength(), StandardCharsets.UTF_8);
                JSONObject reply = server.handle(text);
                byte[] data = reply.toString().getBytes(StandardCharsets.UTF_8);
                if (data.length > MAX_DATAGRAM) {
                    data = error("SERVER_ERROR", "La imagen no cabe en un datagrama UDP").toString()
                            .getBytes(StandardCharsets.UTF_8);
                }
                socket.send(new DatagramPacket(data, data.length, request.getSocketAddress()));
                System.out.println("[" + java.time.LocalTime.now().withNano(0) + "] " + request.getSocketAddress()
                        + " -> " + reply.getString("type") + " (" + data.length + " bytes)");
            }
        } catch (IOException e) {
            System.out.println("Error en el puerto UDP " + port + ": " + e.getMessage());
            System.exit(2);
        }
    }

    /** Procesa una peticion y devuelve la respuesta. Nunca lanza excepciones. */
    JSONObject handle(String text) {
        JSONObject msg;
        try {
            msg = new JSONObject(text);
        } catch (JSONException e) {
            return error("INVALID_JSON", "El mensaje no es un JSON válido");
        }
        String type = msg.optString("type", "").trim().toUpperCase();
        if (type.isEmpty()) {
            return error("MISSING_FIELD", "Falta el campo type");
        }
        if (!type.equals("HELLO")) {
            return error("UNKNOWN_TYPE", "El servidor UDP solo acepta HELLO");
        }
        JSONObject payload = msg.optJSONObject("payload");
        String name = payload == null ? "" : payload.optString("name", "").trim();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            return error("INVALID_NAME", "El nombre debe tener entre 1 y " + MAX_NAME + " caracteres");
        }
        int index = Math.floorMod(name.toLowerCase().hashCode(), images.size());
        JSONObject p = new JSONObject();
        p.put("name", name);
        p.put("message", "Hola " + name + ", esta es tu imagen (tarjeta " + (index + 1) + " de " + images.size() + ")");
        p.put("image", images.get(index));
        return message("IMAGE", p);
    }

    static JSONObject message(String type, JSONObject payload) {
        JSONObject m = new JSONObject();
        m.put("type", type);
        m.put("payload", payload);
        return m;
    }

    static JSONObject error(String code, String text) {
        JSONObject p = new JSONObject();
        p.put("code", code);
        p.put("message", text);
        return message("ERROR", p);
    }
}
