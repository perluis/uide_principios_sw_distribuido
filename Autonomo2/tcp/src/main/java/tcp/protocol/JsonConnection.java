package tcp.protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/**
 * Envia y recibe mensajes JSON por un socket TCP, un mensaje por linea.
 * La usan el servidor y el cliente.
 */
public class JsonConnection implements Closeable {

    /** Se lanza cuando llega una linea mas larga que el limite permitido. */
    public static class MessageTooLongException extends IOException {
        public MessageTooLongException(int limit) {
            super("El mensaje supera el limite de " + limit + " caracteres");
        }
    }

    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final int maxLineLength;

    /**
     * @param socket        socket ya conectado
     * @param maxLineLength largo maximo de una linea; evita que un mensaje gigante agote la memoria
     */
    public JsonConnection(Socket socket, int maxLineLength) throws IOException {
        this.socket = socket;
        this.maxLineLength = maxLineLength;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /** Envia un mensaje. Es synchronized porque el temporizador del servidor tambien envia. */
    public synchronized void send(JSONObject message) throws IOException {
        out.write(message.toString());
        out.write('\n');
        out.flush();
    }

    /**
     * Lee la siguiente linea.
     * @return el texto de la linea, o null si la otra parte cerro la conexion
     * @throws MessageTooLongException si la linea supera el limite (el resto de la linea se descarta)
     */
    public String receiveLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        boolean tooLong = false;
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                if (tooLong) {
                    throw new MessageTooLongException(maxLineLength);
                }
                // tolera finales de linea de Windows (\r\n)
                int len = sb.length();
                if (len > 0 && sb.charAt(len - 1) == '\r') {
                    sb.setLength(len - 1);
                }
                return sb.toString();
            }
            if (!tooLong) {
                if (sb.length() >= maxLineLength) {
                    tooLong = true;
                    sb.setLength(0);
                } else {
                    sb.append((char) c);
                }
            }
        }
        // fin del flujo: si quedo texto sin salto de linea, se devuelve igual
        return sb.length() > 0 && !tooLong ? sb.toString() : null;
    }

    /** Direccion de la otra parte, para los mensajes de registro. */
    public String remoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // la conexion ya estaba cerrada
        }
    }
}
