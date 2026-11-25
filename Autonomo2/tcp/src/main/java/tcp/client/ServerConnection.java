package tcp.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;
import org.json.JSONException;
import org.json.JSONObject;
import tcp.protocol.JsonConnection;

/**
 * Conexion del cliente con el servidor.
 * Un hilo lee los mensajes que llegan (incluido el fin de partida por tiempo, que el servidor
 * envia sin que el cliente pregunte) y se los pasa a la ventana.
 */
public class ServerConnection {

    /** Los mensajes del servidor traen imagenes, por eso el limite es mayor que en el servidor. */
    private static final int MAX_MESSAGE = 5_000_000;
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final String host;
    private final int port;
    private final Consumer<JSONObject> onMessage;
    private final Consumer<String> onDisconnect;
    private JsonConnection conn;
    private volatile boolean closedByUs;

    public ServerConnection(String host, int port, Consumer<JSONObject> onMessage, Consumer<String> onDisconnect) {
        this.host = host;
        this.port = port;
        this.onMessage = onMessage;
        this.onDisconnect = onDisconnect;
    }

    /** Se conecta y empieza a escuchar. Lanza IOException si el servidor no responde. */
    public void connect() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        conn = new JsonConnection(socket, MAX_MESSAGE);
        Thread reader = new Thread(this::readLoop, "lector-servidor");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop() {
        String reason = "El servidor cerró la conexión";
        try {
            String line;
            while ((line = conn.receiveLine()) != null) {
                try {
                    onMessage.accept(new JSONObject(line));
                } catch (JSONException e) {
                    System.out.println("Mensaje del servidor que no es JSON: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            reason = "Se perdió la conexión con el servidor (" + e.getMessage() + ")";
        }
        if (!closedByUs) {
            onDisconnect.accept(reason);
        }
    }

    /** Envia un mensaje; si falla, avisa que se perdio la conexion. */
    public void send(JSONObject message) {
        if (conn == null) {
            return;
        }
        try {
            conn.send(message);
        } catch (IOException e) {
            onDisconnect.accept("No se pudo enviar el mensaje: " + e.getMessage());
        }
    }

    public void close() {
        closedByUs = true;
        if (conn != null) {
            conn.close();
        }
    }

    public String describe() {
        return host + ":" + port;
    }
}
