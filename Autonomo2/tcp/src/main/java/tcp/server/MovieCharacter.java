package tcp.server;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Un personaje de pelicula: su nombre, los nombres alternativos que se aceptan,
 * sus 4 frases y su imagen (ya convertida a Base64 para enviarla).
 */
public class MovieCharacter {

    private final String name;
    private final List<String> acceptedNames;
    private final List<String> quotes;
    private final String imageBase64;

    public MovieCharacter(String name, List<String> aliases, List<String> quotes, String imageBase64) {
        this.name = name;
        this.quotes = List.copyOf(quotes);
        this.imageBase64 = imageBase64;
        List<String> accepted = new ArrayList<>();
        accepted.add(normalize(name));
        for (String alias : aliases) {
            accepted.add(normalize(alias));
        }
        this.acceptedNames = accepted;
    }

    public String getName() {
        return name;
    }

    public List<String> getQuotes() {
        return quotes;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    /** Indica si la respuesta corresponde a este personaje (sin importar mayusculas, tildes ni signos). */
    public boolean matches(String answer) {
        return acceptedNames.contains(normalize(answer));
    }

    /** Pasa a minusculas, quita tildes y signos, y deja un solo espacio entre palabras. */
    static String normalize(String text) {
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase()
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
