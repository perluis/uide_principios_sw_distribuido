package tcp.client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Parte inferior de la ventana: historial de mensajes, caja para escribir la respuesta
 * y los botones de los comandos (guess, more, next).
 */
public class OutputPanel extends JPanel {

    private final JTextArea log = new JTextArea(9, 60);
    private final JTextField input = new JTextField(22);
    private final JButton startButton = new JButton("Jugar");
    private final JButton guessButton = new JButton("Adivinar (guess)");
    private final JButton moreButton = new JButton("Otra frase (more)");
    private final JButton nextButton = new JButton("Siguiente (next)");
    private final JButton quitButton = new JButton("Salir");

    public OutputPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(log);
        scroll.setPreferredSize(new Dimension(860, 190));
        add(scroll, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        controls.add(new JLabel("Tu respuesta:"));
        controls.add(input);
        controls.add(guessButton);
        controls.add(moreButton);
        controls.add(nextButton);
        controls.add(startButton);
        controls.add(quitButton);
        add(controls, BorderLayout.SOUTH);

        setGameControlsEnabled(false);
        startButton.setEnabled(false);
    }

    /** Agrega una linea al historial y baja hasta el final. */
    public void append(String text) {
        log.append(text + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    public String takeInput() {
        String text = input.getText().trim();
        input.setText("");
        return text;
    }

    public void focusInput() {
        input.requestFocusInWindow();
    }

    /** Activa o desactiva la caja de respuesta y los botones guess, more y next. */
    public void setGameControlsEnabled(boolean enabled) {
        input.setEnabled(enabled);
        guessButton.setEnabled(enabled);
        moreButton.setEnabled(enabled);
        nextButton.setEnabled(enabled);
    }

    public void setMoreEnabled(boolean enabled) {
        moreButton.setEnabled(enabled);
    }

    public void setStartEnabled(boolean enabled) {
        startButton.setEnabled(enabled);
    }

    public void setQuitEnabled(boolean enabled) {
        quitButton.setEnabled(enabled);
    }

    public void onGuess(ActionListener l) {
        guessButton.addActionListener(l);
        input.addActionListener(l); // Enter en la caja tambien envia la respuesta
    }

    public void onMore(ActionListener l) {
        moreButton.addActionListener(l);
    }

    public void onNext(ActionListener l) {
        nextButton.addActionListener(l);
    }

    public void onStart(ActionListener l) {
        startButton.addActionListener(l);
    }

    public void onQuit(ActionListener l) {
        quitButton.addActionListener(l);
    }
}
