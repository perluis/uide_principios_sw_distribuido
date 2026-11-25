package tcp.client;

import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Punto de entrada del cliente TCP.
 *
 * Uso: gradle :tcp:runClient [-Phost=localhost] [-Pport=8080]
 */
public class GameClient {

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
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("El cliente necesita un entorno grafico (ventanas) para mostrar el juego.");
            System.exit(1);
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // si falla se usa el aspecto por defecto de Java
        }
        final int finalPort = port;
        SwingUtilities.invokeLater(() -> new ClientGui(host, finalPort).start());
    }
}
