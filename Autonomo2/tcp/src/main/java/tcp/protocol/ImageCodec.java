package tcp.protocol;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * Convierte imagenes a texto Base64 para enviarlas dentro de un mensaje JSON, y al reves.
 */
public final class ImageCodec {

    private ImageCodec() {
    }

    /** Lee un archivo de imagen y lo devuelve como texto Base64. */
    public static String fileToBase64(Path file) throws IOException {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(file));
    }

    /** Convierte el texto Base64 recibido en una imagen que se puede mostrar. */
    public static BufferedImage base64ToImage(String base64) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(base64);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IOException("Los datos recibidos no son una imagen valida");
        }
        return image;
    }
}
