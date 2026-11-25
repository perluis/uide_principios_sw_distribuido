package udp.client;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Cliente UDP (version reducida): envia el nombre y muestra la imagen que responde el servidor.
 *
 * Uso: gradle :udp:runClient [-Phost=localhost] [-Pport=8080] [-Pplayer=Luis]
 * Como UDP no garantiza la entrega, espera 5 segundos y reintenta hasta 3 veces.
 */
public class UdpClient {

    private static final int TIMEOUT_MS = 5000;
    private static final int TRIES = 3;
    private static final int MAX_DATAGRAM = 65_507;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = 8080;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException e) {
                System.out.println("Puerto no valido (" + args[1] + "); se usa 8080");
            }
        }
        boolean gui = !GraphicsEnvironment.isHeadless();
        String name = args.length > 2 ? args[2] : null;
        if (name == null && gui) {
            // ventana invisible "siempre encima" para que el cuadro no quede detras de la consola
            JFrame owner = new JFrame();
            owner.setAlwaysOnTop(true);
            name = JOptionPane.showInputDialog(owner, "Escribe tu nombre:", "Cliente UDP", JOptionPane.QUESTION_MESSAGE);
            owner.dispose();
        }
        if (name == null) {
            System.out.println("No se indico un nombre. Usa -Pplayer=TuNombre");
            System.exit(1);
            return;
        }

        JSONObject request = new JSONObject();
        request.put("type", "HELLO");
        request.put("payload", new JSONObject().put("name", name.trim()));
        byte[] data = request.toString().getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            InetAddress address = InetAddress.getByName(host);
            byte[] buffer = new byte[MAX_DATAGRAM];
            for (int attempt = 1; attempt <= TRIES; attempt++) {
                System.out.println("Enviando HELLO a " + host + ":" + port + " (intento " + attempt + " de " + TRIES + ")");
                socket.send(new DatagramPacket(data, data.length, address, port));
                try {
                    DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                    socket.receive(reply);
                    String text = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8);
                    System.out.println("Respuesta recibida (" + reply.getLength() + " bytes)");
                    showReply(new JSONObject(text), gui);
                    return;
                } catch (SocketTimeoutException e) {
                    System.out.println("Sin respuesta en " + TIMEOUT_MS / 1000 + " segundos");
                } catch (JSONException e) {
                    System.out.println("La respuesta del servidor no es JSON valido");
                    return;
                }
            }
            fail("El servidor " + host + ":" + port + " no respondió después de " + TRIES + " intentos.", gui);
        } catch (IOException e) {
            fail("Error de red: " + e.getMessage(), gui);
        }
    }

    private static void showReply(JSONObject reply, boolean gui) {
        JSONObject p = reply.optJSONObject("payload");
        if (p == null) {
            p = new JSONObject();
        }
        String type = reply.optString("type");
        if ("ERROR".equals(type)) {
            fail("El servidor respondió con error " + p.optString("code") + ": " + p.optString("message"), gui);
            return;
        }
        if (!"IMAGE".equals(type)) {
            fail("Tipo de respuesta inesperado: " + type, gui);
            return;
        }
        String message = p.optString("message");
        System.out.println(message);
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(p.optString("image"))));
        } catch (IOException | IllegalArgumentException e) {
            fail("La imagen recibida no es válida", gui);
            return;
        }
        if (image == null) {
            fail("La imagen recibida no es válida", gui);
            return;
        }
        System.out.println("Imagen recibida: " + image.getWidth() + " x " + image.getHeight() + " pixeles");
        if (!gui) {
            return;
        }
        final BufferedImage img = image;
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Cliente UDP - imagen recibida");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout(6, 6));
            JLabel label = new JLabel(message, SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
            label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            frame.add(label, BorderLayout.NORTH);
            frame.add(new JLabel(new ImageIcon(img)), BorderLayout.CENTER);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            frame.toFront();   // al abrirse desde la consola, que quede delante
        });
    }

    private static void fail(String text, boolean gui) {
        // en consola sin tildes, porque algunas consolas de Windows no las muestran bien
        System.out.println(java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", ""));
        if (gui) {
            JOptionPane.showMessageDialog(null, text, "Cliente UDP", JOptionPane.ERROR_MESSAGE);
        }
        System.exit(1);
    }
}
