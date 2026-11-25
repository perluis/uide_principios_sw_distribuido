package tcp.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import tcp.protocol.ImageCodec;

/**
 * Carga los personajes desde personajes.json y sus imagenes desde la carpeta img/.
 */
public class CharacterBank {

    public static final int QUOTES_PER_CHARACTER = 4;

    private final List<MovieCharacter> characters = new ArrayList<>();
    private final String winImageBase64;
    private final String loseImageBase64;

    /**
     * @param imageDir carpeta con las imagenes (img/)
     * @throws IOException si falta un archivo o el JSON no tiene el formato esperado
     */
    public CharacterBank(Path imageDir) throws IOException {
        JSONObject data = readResource("/personajes.json");
        JSONArray list = data.getJSONArray("personajes");
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            String name = item.getString("nombre");
            List<String> quotes = toList(item.getJSONArray("frases"));
            if (quotes.size() != QUOTES_PER_CHARACTER) {
                throw new IOException(name + " debe tener exactamente " + QUOTES_PER_CHARACTER + " frases");
            }
            List<String> aliases = toList(item.optJSONArray("alias", new JSONArray()));
            String image = loadImage(imageDir, item.getString("imagen"));
            characters.add(new MovieCharacter(name, aliases, quotes, image));
        }
        if (characters.size() < 3) {
            throw new IOException("Se necesitan al menos 3 personajes para jugar");
        }
        winImageBase64 = loadImage(imageDir, data.getString("imagenVictoria"));
        loseImageBase64 = loadImage(imageDir, data.getString("imagenDerrota"));
    }

    public List<MovieCharacter> getCharacters() {
        return characters;
    }

    /** Nombres de los personajes, para mostrarlos al inicio. */
    public List<String> getNames() {
        List<String> names = new ArrayList<>();
        for (MovieCharacter c : characters) {
            names.add(c.getName());
        }
        return names;
    }

    public String getWinImage() {
        return winImageBase64;
    }

    public String getLoseImage() {
        return loseImageBase64;
    }

    private static String loadImage(Path imageDir, String fileName) throws IOException {
        Path file = imageDir.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new IOException("No se encontro la imagen " + file.toAbsolutePath());
        }
        return ImageCodec.fileToBase64(file);
    }

    private static JSONObject readResource(String name) throws IOException {
        try (InputStream in = CharacterBank.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("No se encontro el recurso " + name);
            }
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static List<String> toList(JSONArray array) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            values.add(array.getString(i));
        }
        return values;
    }
}
