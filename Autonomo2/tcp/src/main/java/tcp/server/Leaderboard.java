package tcp.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Marcador persistente: se guarda en un archivo JSON, asi no se pierde al apagar el servidor.
 * Los metodos son synchronized porque varios clientes pueden terminar partidas al mismo tiempo.
 */
public class Leaderboard {

    /** Datos de un jugador en el marcador. */
    static class Entry {
        String name;
        int bestScore;
        int wins;
        int games;

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("bestScore", bestScore);
            o.put("wins", wins);
            o.put("games", games);
            return o;
        }
    }

    private final Path file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public Leaderboard(Path file) {
        this.file = file;
        load();
    }

    /** Registra el resultado de una partida y guarda el archivo. */
    public synchronized void record(String name, int score, boolean won) {
        Entry e = entries.computeIfAbsent(name.toLowerCase(), k -> {
            Entry n = new Entry();
            n.name = name;
            return n;
        });
        e.games++;
        if (won) {
            e.wins++;
        }
        e.bestScore = Math.max(e.bestScore, score);
        save();
    }

    /** Los mejores jugadores, ordenados por mejor puntaje y luego por victorias. */
    public synchronized JSONArray top(int limit) {
        List<Entry> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparingInt((Entry e) -> e.bestScore).reversed()
                .thenComparing(Comparator.comparingInt((Entry e) -> e.wins).reversed())
                .thenComparing(e -> e.name.toLowerCase()));
        JSONArray result = new JSONArray();
        for (int i = 0; i < list.size() && i < limit; i++) {
            JSONObject o = list.get(i).toJson();
            o.put("position", i + 1);
            result.put(o);
        }
        return result;
    }

    private void load() {
        if (!Files.exists(file)) {
            System.out.println("[marcador] No existe " + file + "; se crea un marcador vacio");
            return;
        }
        try {
            JSONArray array = new JSONArray(Files.readString(file, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Entry e = new Entry();
                e.name = o.getString("name");
                e.bestScore = o.optInt("bestScore");
                e.wins = o.optInt("wins");
                e.games = o.optInt("games");
                entries.put(e.name.toLowerCase(), e);
            }
            System.out.println("[marcador] Cargados " + entries.size() + " jugadores desde " + file);
        } catch (Exception ex) {
            System.out.println("[marcador] No se pudo leer " + file + " (" + ex.getMessage() + "); se empieza vacio");
        }
    }

    /** Guarda en un archivo temporal y luego lo reemplaza, para no dejar el archivo a medias. */
    private void save() {
        JSONArray array = new JSONArray();
        for (Entry e : entries.values()) {
            array.put(e.toJson());
        }
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, array.toString(2), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            System.out.println("[marcador] No se pudo guardar: " + ex.getMessage());
        }
    }
}
