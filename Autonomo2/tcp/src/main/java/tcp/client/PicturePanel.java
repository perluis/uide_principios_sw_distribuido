package tcp.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/**
 * Panel que muestra la imagen recibida del servidor, ajustada al tamaño del panel.
 */
public class PicturePanel extends JPanel {

    private BufferedImage image;
    private String placeholder = "La imagen aparecerá aquí";

    public PicturePanel() {
        setPreferredSize(new Dimension(480, 300));
        setBackground(new Color(30, 30, 36));
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    public void setPlaceholder(String text) {
        this.image = null;
        this.placeholder = text;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (image == null) {
            g2.setColor(new Color(200, 200, 210));
            g2.setFont(getFont().deriveFont(Font.PLAIN, 16f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(placeholder, (getWidth() - fm.stringWidth(placeholder)) / 2, getHeight() / 2);
            return;
        }
        // escala la imagen sin deformarla y la centra
        double scale = Math.min((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
        int w = (int) (image.getWidth() * scale);
        int h = (int) (image.getHeight() * scale);
        g2.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
    }
}
