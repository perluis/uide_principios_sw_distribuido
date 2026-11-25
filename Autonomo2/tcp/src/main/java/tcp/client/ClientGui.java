package tcp.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.json.JSONArray;
import org.json.JSONObject;
import tcp.protocol.ImageCodec;
import tcp.protocol.Protocol;

/**
 * Ventana del juego. Muestra lo que envia el servidor y envia los comandos del jugador.
 *
 * Partes: PicturePanel (imagen), la frase, el panel de estado (tiempo, aciertos, puntos)
 * y OutputPanel (historial, caja de respuesta y botones).
 */
public class ClientGui {

    private final String host;
    private final int port;
    private final JFrame frame = new JFrame("¿Quién lo dijo? - cliente TCP");
    private final PicturePanel picture = new PicturePanel();
    private final OutputPanel output = new OutputPanel();
    private final JLabel quoteLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel connectionLabel = new JLabel(" ");
    private final JLabel timeLabel = new JLabel("Tiempo: --");
    private final JLabel solvedLabel = new JLabel("Aciertos: 0 de 3");
    private final JLabel scoreLabel = new JLabel("Puntos: 0");
    private final JLabel hintLabel = new JLabel("Frase: -");
    private final JTextArea charactersArea = new JTextArea(7, 14);
    private final Timer countdown;

    private ServerConnection connection;
    private String playerName;
    private int secondsLeft;
    private boolean sessionEnded;

    public ClientGui(String host, int port) {
        this.host = host;
        this.port = port;
        this.countdown = new Timer(1000, e -> tick());
        buildWindow();
    }

    // ------------------------------------------------------------------ ventana

    private void buildWindow() {
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeAndExit();
            }
        });
        frame.setLayout(new BorderLayout(8, 8));

        JLabel title = new JLabel("¿Quién lo dijo?", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        connectionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        top.add(title, BorderLayout.CENTER);
        top.add(connectionLabel, BorderLayout.SOUTH);
        frame.add(top, BorderLayout.NORTH);

        quoteLabel.setFont(quoteLabel.getFont().deriveFont(Font.BOLD | Font.ITALIC, 20f));
        quoteLabel.setPreferredSize(new Dimension(560, 80));
        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        center.add(picture, BorderLayout.CENTER);
        center.add(quoteLabel, BorderLayout.SOUTH);
        frame.add(center, BorderLayout.CENTER);

        JPanel status = new JPanel(new GridLayout(0, 1, 4, 4));
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Partida"), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        for (JLabel l : new JLabel[] {timeLabel, solvedLabel, scoreLabel, hintLabel}) {
            l.setFont(l.getFont().deriveFont(Font.BOLD, 16f));
            status.add(l);
        }
        charactersArea.setEditable(false);
        charactersArea.setOpaque(false);
        charactersArea.setFocusable(false);   // sin cursor de texto
        charactersArea.setFont(timeLabel.getFont().deriveFont(Font.PLAIN, 14f));
        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        right.add(status, BorderLayout.NORTH);
        JPanel chars = new JPanel(new BorderLayout());
        chars.setBorder(BorderFactory.createTitledBorder("Personajes en juego"));
        chars.add(charactersArea, BorderLayout.CENTER);
        right.add(chars, BorderLayout.CENTER);
        frame.add(right, BorderLayout.EAST);

        frame.add(output, BorderLayout.SOUTH);

        output.onGuess(e -> sendGuess());
        output.onMore(e -> send(Protocol.MORE, null));
        output.onNext(e -> send(Protocol.NEXT, null));
        output.onStart(e -> {
            output.setStartEnabled(false);
            send(Protocol.START, null);
        });
        output.onQuit(e -> {
            if (sessionEnded) {
                closeAndExit();
            } else {
                send(Protocol.QUIT, null);
            }
        });

        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationRelativeTo(null);
    }

    /** Muestra la ventana, pide el nombre y se conecta al servidor. */
    public void start() {
        frame.setVisible(true);
        frame.toFront();   // al abrirse desde la consola, que quede delante
        picture.setPlaceholder("Conectando con " + host + ":" + port + " ...");
        playerName = askName();
        if (playerName == null) {
            System.exit(0);
            return;
        }
        connectionLabel.setText("Jugador: " + playerName + "   |   Servidor: " + host + ":" + port);
        connection = new ServerConnection(host, port,
                msg -> SwingUtilities.invokeLater(() -> handle(msg)),
                reason -> SwingUtilities.invokeLater(() -> onDisconnected(reason)));
        // la conexion se hace fuera del hilo de la ventana para que no se congele
        new Thread(() -> {
            try {
                connection.connect();
                JSONObject p = new JSONObject();
                p.put("name", playerName);
                connection.send(Protocol.message(Protocol.HELLO, p));
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame,
                            "No se pudo conectar con el servidor " + host + ":" + port + "\n" + e.getMessage()
                                    + "\n\nRevisa que el servidor esté encendido y la IP y el puerto sean correctos.",
                            "Sin conexión", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                });
            }
        }, "conexion").start();
    }

    private String askName() {
        while (true) {
            String name = JOptionPane.showInputDialog(frame, "Escribe tu nombre (máximo 20 caracteres):",
                    "¿Quién lo dijo?", JOptionPane.QUESTION_MESSAGE);
            if (name == null) {
                return null;
            }
            name = name.trim();
            if (!name.isEmpty() && name.length() <= 20) {
                return name;
            }
            JOptionPane.showMessageDialog(frame, "El nombre debe tener entre 1 y 20 caracteres.");
        }
    }

    // ------------------------------------------------------------------ mensajes del servidor

    private void handle(JSONObject msg) {
        String type = msg.optString("type");
        JSONObject p = msg.optJSONObject("payload");
        if (p == null) {
            p = new JSONObject();
        }
        switch (type) {
            case Protocol.WELCOME -> onWelcome(p);
            case Protocol.QUOTE -> onQuote(p);
            case Protocol.RESULT -> onResult(p);
            case Protocol.GAME_OVER -> onGameOver(p);
            case Protocol.BYE -> onBye(p);
            case Protocol.ERROR -> onError(p);
            default -> output.append("Mensaje desconocido del servidor: " + type);
        }
    }

    private void onWelcome(JSONObject p) {
        output.append(p.optString("message"));
        JSONArray names = p.optJSONArray("characters");
        StringBuilder sb = new StringBuilder();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                sb.append("- ").append(names.optString(i)).append('\n');
            }
        }
        charactersArea.setText(sb.toString());
        output.append("Personajes en juego: " + (names == null ? "" : String.join(", ", toList(names))));
        printLeaderboard(p.optJSONArray("leaderboard"));
        picture.setPlaceholder("Pulsa Jugar para empezar");
        output.setStartEnabled(true);
    }

    private void onQuote(JSONObject p) {
        showImage(p.optString("image", null));
        quoteLabel.setText(html("“" + p.optString("quote") + "”"));
        secondsLeft = p.optInt("secondsLeft");
        if (!countdown.isRunning()) {
            countdown.start();
        }
        updateTime();
        solvedLabel.setText("Aciertos: " + p.optInt("solved") + " de " + p.optInt("target", 3));
        scoreLabel.setText("Puntos: " + p.optInt("score"));
        int hint = p.optInt("hint");
        int total = p.optInt("hintsTotal", 4);
        hintLabel.setText("Frase: " + hint + " de " + total);
        output.append(p.optString("message"));
        output.setGameControlsEnabled(true);
        output.setMoreEnabled(hint < total);
        output.setStartEnabled(false);
        output.focusInput();
    }

    private void onResult(JSONObject p) {
        output.append(p.optString("message"));
        solvedLabel.setText("Aciertos: " + p.optInt("solved") + " de 3");
        scoreLabel.setText("Puntos: " + p.optInt("score"));
        output.focusInput();
    }

    private void onGameOver(JSONObject p) {
        countdown.stop();
        boolean won = p.optBoolean("won");
        if (!won) {
            secondsLeft = 0;
            updateTime();
        }
        showImage(p.optString("image", null));
        quoteLabel.setText(html(won ? "¡Ganaste!" : "Se acabó el tiempo"));
        scoreLabel.setText("Puntos: " + p.optInt("score"));
        solvedLabel.setText("Aciertos: " + p.optInt("solved") + " de 3");
        output.setGameControlsEnabled(false);
        output.append("=== " + p.optString("message") + " ===");
        printLeaderboard(p.optJSONArray("leaderboard"));
        String question = p.optString("question", "¿Quieres jugar otra vez?");
        // se pregunta despues de pintar la imagen; el dialogo se centra sobre el historial
        // para no tapar la imagen de victoria o derrota
        SwingUtilities.invokeLater(() -> {
            Object[] options = {"Sí, otra partida", "No, salir"};
            int option = JOptionPane.showOptionDialog(output, p.optString("message") + "\n\n" + question,
                    won ? "¡Felicitaciones!" : "Fin de la partida", JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            JSONObject answer = new JSONObject();
            answer.put("answer", option == 0);
            send(Protocol.PLAY_AGAIN, answer);
        });
    }

    private void onBye(JSONObject p) {
        sessionEnded = true;
        countdown.stop();
        output.append(p.optString("message"));
        output.append("Conexión cerrada. Pulsa Salir para cerrar la ventana.");
        output.setGameControlsEnabled(false);
        output.setStartEnabled(false);
        picture.setPlaceholder("Sesión terminada");
        quoteLabel.setText(" ");
        connection.close();
    }

    private void onError(JSONObject p) {
        String code = p.optString("code");
        output.append("[Error " + code + "] " + p.optString("message"));
        if (Protocol.NO_MORE_HINTS.equals(code)) {
            output.setMoreEnabled(false);
        }
    }

    private void onDisconnected(String reason) {
        if (sessionEnded) {
            return;
        }
        sessionEnded = true;
        countdown.stop();
        output.append(reason);
        output.setGameControlsEnabled(false);
        output.setStartEnabled(false);
        picture.setPlaceholder("Sin conexión con el servidor");
        JOptionPane.showMessageDialog(frame, reason, "Conexión perdida", JOptionPane.WARNING_MESSAGE);
    }

    // ------------------------------------------------------------------ comandos del jugador

    private void sendGuess() {
        String answer = output.takeInput();
        if (answer.isEmpty()) {
            output.append("Escribe el nombre del personaje antes de adivinar.");
            return;
        }
        output.append("> Tu respuesta: " + answer);
        JSONObject p = new JSONObject();
        p.put("answer", answer);
        send(Protocol.GUESS, p);
    }

    private void send(String type, JSONObject payload) {
        if (connection != null && !sessionEnded) {
            connection.send(Protocol.message(type, payload));
        }
    }

    private void closeAndExit() {
        if (connection != null && !sessionEnded) {
            connection.send(Protocol.message(Protocol.QUIT, null));
            connection.close();
        }
        frame.dispose();
        System.exit(0);
    }

    // ------------------------------------------------------------------ apoyo

    private void tick() {
        if (secondsLeft > 0) {
            secondsLeft--;
        }
        updateTime();
    }

    private void updateTime() {
        timeLabel.setText("Tiempo: " + secondsLeft + " s");
        timeLabel.setForeground(secondsLeft <= 10 ? new Color(200, 30, 30) : getDefaultColor());
    }

    private Color getDefaultColor() {
        return scoreLabel.getForeground();
    }

    private void showImage(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return;
        }
        try {
            picture.setImage(ImageCodec.base64ToImage(base64));
        } catch (IOException | IllegalArgumentException e) {
            output.append("No se pudo mostrar la imagen recibida: " + e.getMessage());
        }
    }

    private void printLeaderboard(JSONArray board) {
        if (board == null || board.isEmpty()) {
            output.append("Marcador: aún no hay partidas registradas.");
            return;
        }
        output.append("Marcador (mejores puntajes):");
        output.append(String.format(" %-3s %-20s %6s %10s %9s", "#", "Jugador", "Mejor", "Victorias", "Partidas"));
        for (int i = 0; i < board.length(); i++) {
            JSONObject e = board.getJSONObject(i);
            output.append(String.format(" %-3d %-20s %6d %10d %9d", e.optInt("position", i + 1),
                    e.optString("name"), e.optInt("bestScore"), e.optInt("wins"), e.optInt("games")));
        }
    }

    private static java.util.List<String> toList(JSONArray array) {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            list.add(array.optString(i));
        }
        return list;
    }

    /** Envuelve el texto en HTML para que la etiqueta haga saltos de linea. */
    private static String html(String text) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><div style='text-align:center; width:520px'>" + safe + "</div></html>";
    }
}
