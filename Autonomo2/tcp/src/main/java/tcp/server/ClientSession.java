package tcp.server;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import tcp.protocol.JsonConnection;
import tcp.protocol.Protocol;

/**
 * Atiende a un cliente conectado: lee sus mensajes, aplica las reglas del juego y responde.
 *
 * Estados: WAITING_NAME -> LOBBY -> PLAYING -> ASK_AGAIN -> (PLAYING o CLOSED).
 * Los metodos que cambian el estado son synchronized porque el temporizador corre en otro hilo.
 */
public class ClientSession implements Runnable {

    enum State { WAITING_NAME, LOBBY, PLAYING, ASK_AGAIN, CLOSED }

    /** Largo maximo de un mensaje del cliente (los mensajes del cliente son cortos). */
    private static final int MAX_MESSAGE = 4096;
    private static final int MAX_NAME = 20;
    private static final int LEADERBOARD_SIZE = 10;

    private final JsonConnection conn;
    private final CharacterBank bank;
    private final Leaderboard leaderboard;
    private final ScheduledExecutorService timer;
    private final int durationSeconds;

    private State state = State.WAITING_NAME;
    private String playerName;
    private Game game;
    private ScheduledFuture<?> timeoutTask;

    public ClientSession(java.net.Socket socket, CharacterBank bank, Leaderboard leaderboard,
                         ScheduledExecutorService timer, int durationSeconds) throws IOException {
        this.conn = new JsonConnection(socket, MAX_MESSAGE);
        this.bank = bank;
        this.leaderboard = leaderboard;
        this.timer = timer;
        this.durationSeconds = durationSeconds;
    }

    @Override
    public void run() {
        String who = conn.remoteAddress();
        log("Cliente conectado desde " + who);
        try {
            while (!isClosed()) {
                String line;
                try {
                    line = conn.receiveLine();
                } catch (JsonConnection.MessageTooLongException e) {
                    send(Protocol.error(Protocol.INVALID_JSON, "Mensaje demasiado largo (máximo " + MAX_MESSAGE + " caracteres)"));
                    continue;
                }
                if (line == null) {
                    log("El cliente " + who + " cerro la conexion");
                    break;
                }
                if (!line.isBlank()) {
                    handle(line);
                }
            }
        } catch (IOException e) {
            log("Conexion con " + who + " terminada: " + e.getMessage());
        } finally {
            synchronized (this) {
                cancelTimer();
                state = State.CLOSED;
            }
            conn.close();
            log("Sesion cerrada (" + who + "). El servidor sigue aceptando clientes.");
        }
    }

    // ------------------------------------------------------------------ despacho de mensajes

    private synchronized void handle(String line) {
        JSONObject msg;
        try {
            msg = new JSONObject(line);
        } catch (JSONException e) {
            send(Protocol.error(Protocol.INVALID_JSON, "El mensaje no es un JSON válido"));
            return;
        }
        String type = msg.optString("type", "").trim().toUpperCase();
        if (type.isEmpty()) {
            send(Protocol.error(Protocol.MISSING_FIELD, "Falta el campo type"));
            return;
        }
        JSONObject payload = msg.optJSONObject("payload");
        if (payload == null) {
            payload = new JSONObject();
        }
        log("<- " + type + (playerName != null ? " (" + playerName + ")" : ""));
        try {
            switch (type) {
                case Protocol.HELLO -> onHello(payload);
                case Protocol.START -> onStart();
                case Protocol.GUESS -> onGuess(payload);
                case Protocol.MORE -> onMore();
                case Protocol.NEXT -> onNext();
                case Protocol.PLAY_AGAIN -> onPlayAgain(payload);
                case Protocol.QUIT -> onQuit();
                default -> send(Protocol.error(Protocol.UNKNOWN_TYPE, "Tipo de mensaje desconocido: " + type));
            }
        } catch (RuntimeException e) {
            // un error inesperado no debe tumbar el servidor
            log("Error procesando " + type + ": " + e);
            send(Protocol.error(Protocol.SERVER_ERROR, "Error interno del servidor; puedes seguir jugando"));
        }
    }

    // ------------------------------------------------------------------ comandos

    private void onHello(JSONObject payload) {
        if (state != State.WAITING_NAME) {
            send(Protocol.error(Protocol.INVALID_STATE, "Ya te presentaste como " + playerName));
            return;
        }
        String name = payload.optString("name", "").trim();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            send(Protocol.error(Protocol.INVALID_NAME, "El nombre debe tener entre 1 y " + MAX_NAME + " caracteres"));
            return;
        }
        playerName = name;
        state = State.LOBBY;
        JSONObject p = new JSONObject();
        p.put("message", "Hola " + name + ", bienvenido a ¿Quién lo dijo? Tienes " + durationSeconds
                + " segundos para adivinar " + Game.TARGET + " personajes. Pulsa Jugar para empezar.");
        p.put("characters", new JSONArray(bank.getNames()));
        p.put("leaderboard", leaderboard.top(LEADERBOARD_SIZE));
        send(Protocol.message(Protocol.WELCOME, p));
    }

    private void onStart() {
        switch (state) {
            case WAITING_NAME -> send(Protocol.error(Protocol.INVALID_STATE, "Primero envía HELLO con tu nombre"));
            case PLAYING -> send(Protocol.error(Protocol.INVALID_STATE, "Ya hay una partida en curso"));
            case ASK_AGAIN -> send(Protocol.error(Protocol.INVALID_STATE, "Responde primero si quieres jugar otra vez (PLAY_AGAIN)"));
            default -> startGame();
        }
    }

    private void onGuess(JSONObject payload) {
        if (!requirePlaying()) {
            return;
        }
        String answer = payload.optString("answer", "").trim();
        if (answer.isEmpty()) {
            send(Protocol.error(Protocol.MISSING_FIELD, "GUESS necesita el campo answer con el nombre del personaje"));
            return;
        }
        Game.GuessOutcome outcome = game.guess(answer);
        JSONObject p = new JSONObject();
        p.put("correct", outcome.correct);
        p.put("points", outcome.points);
        p.put("score", game.getScore());
        p.put("solved", game.getSolved());
        p.put("attempts", outcome.attempts);
        p.put("message", outcome.correct
                ? "¡Correcto! Era " + outcome.characterName + ". Ganaste " + outcome.points
                  + " puntos en el intento " + outcome.attempts + "."
                : "\"" + answer + "\" no es correcto. Intenta otra vez, pide otra frase (more) o salta (next).");
        send(Protocol.message(Protocol.RESULT, p));
        if (outcome.correct) {
            if (game.isFinished()) {
                finishGame();
            } else {
                sendQuote("Nuevo personaje. Llevas " + game.getSolved() + " de " + Game.TARGET + ".");
            }
        }
    }

    private void onMore() {
        if (!requirePlaying()) {
            return;
        }
        if (!game.hasMoreQuotes()) {
            send(Protocol.error(Protocol.NO_MORE_HINTS,
                    "Ya viste las 4 frases de este personaje. Adivina (guess) o salta (next)."));
            return;
        }
        game.moreQuote();
        sendQuote("Otra frase del mismo personaje (" + game.quoteNumber() + " de 4).");
    }

    private void onNext() {
        if (!requirePlaying()) {
            return;
        }
        game.skip();
        sendQuote("Saltaste al siguiente personaje.");
    }

    private void onPlayAgain(JSONObject payload) {
        if (state != State.ASK_AGAIN) {
            send(Protocol.error(Protocol.INVALID_STATE, "PLAY_AGAIN solo se usa al terminar una partida"));
            return;
        }
        Object value = payload.opt("answer");
        Boolean again = null;
        if (value instanceof Boolean b) {
            again = b;
        } else if (value instanceof String s) {
            String v = MovieCharacter.normalize(s);
            if (v.equals("si") || v.equals("yes") || v.equals("true")) {
                again = true;
            } else if (v.equals("no") || v.equals("false")) {
                again = false;
            }
        }
        if (again == null) {
            send(Protocol.error(Protocol.MISSING_FIELD, "PLAY_AGAIN necesita el campo answer con true o false"));
            return;
        }
        if (again) {
            startGame();
        } else {
            sayBye("Gracias por jugar, " + playerName + ". ¡Hasta pronto!");
        }
    }

    private void onQuit() {
        sayBye("Conexión terminada a pedido del jugador. ¡Hasta pronto" + (playerName != null ? ", " + playerName : "") + "!");
    }

    // ------------------------------------------------------------------ partida

    private void startGame() {
        game = new Game(bank.getCharacters(), durationSeconds);
        game.start();
        state = State.PLAYING;
        final Game thisGame = game;
        // el temporizador avisa cuando se acaba el minuto, aunque el jugador no haga nada
        timeoutTask = timer.schedule(() -> onTimeout(thisGame), durationSeconds, TimeUnit.SECONDS);
        sendQuote("¡Comienza la partida! Tienes " + durationSeconds + " segundos para acertar "
                + Game.TARGET + " personajes.");
    }

    private synchronized void onTimeout(Game expired) {
        // solo aplica si sigue siendo la misma partida y aun no termino
        if (state == State.PLAYING && game == expired && !game.isFinished()) {
            game.timeUp();
            log("Tiempo agotado para " + playerName);
            finishGame();
        }
    }

    private void finishGame() {
        cancelTimer();
        state = State.ASK_AGAIN;
        boolean won = game.isWon();
        leaderboard.record(playerName, game.getScore(), won);
        JSONObject p = new JSONObject();
        p.put("won", won);
        p.put("image", won ? bank.getWinImage() : bank.getLoseImage());
        p.put("score", game.getScore());
        p.put("solved", game.getSolved());
        p.put("message", won
                ? "¡Felicitaciones! Acertaste " + Game.TARGET + " personajes en " + game.secondsUsed()
                  + " segundos. Puntaje: " + game.getScore() + "."
                : "Se acabó el tiempo. Acertaste " + game.getSolved() + " de " + Game.TARGET
                  + ". Puntaje: " + game.getScore() + ".");
        p.put("leaderboard", leaderboard.top(LEADERBOARD_SIZE));
        p.put("question", "¿Quieres jugar otra vez?");
        send(Protocol.message(Protocol.GAME_OVER, p));
        log("Fin de partida de " + playerName + ": " + (won ? "gano" : "perdio") + " con " + game.getScore() + " puntos");
    }

    private void sendQuote(String note) {
        JSONObject p = new JSONObject();
        p.put("image", game.getCurrent().getImageBase64());
        p.put("quote", game.currentQuote());
        p.put("hint", game.quoteNumber());
        p.put("hintsTotal", CharacterBank.QUOTES_PER_CHARACTER);
        p.put("solved", game.getSolved());
        p.put("target", Game.TARGET);
        p.put("score", game.getScore());
        p.put("secondsLeft", game.secondsLeft());
        p.put("message", note);
        send(Protocol.message(Protocol.QUOTE, p));
    }

    // ------------------------------------------------------------------ apoyo

    private boolean requirePlaying() {
        if (state == State.PLAYING && game != null && game.isTimeUp()) {
            // el tiempo se acabo justo antes de que llegara el mensaje
            game.timeUp();
            finishGame();
            return false;
        }
        if (state != State.PLAYING) {
            String why = switch (state) {
                case WAITING_NAME -> "Primero envía HELLO con tu nombre";
                case LOBBY -> "No hay partida en curso; envía START para empezar";
                case ASK_AGAIN -> "La partida terminó; responde si quieres jugar otra vez (PLAY_AGAIN)";
                default -> "La sesión está cerrada";
            };
            send(Protocol.error(Protocol.INVALID_STATE, why));
            return false;
        }
        return true;
    }

    private void sayBye(String text) {
        cancelTimer();
        JSONObject p = new JSONObject();
        p.put("message", text);
        send(Protocol.message(Protocol.BYE, p));
        state = State.CLOSED;
        conn.close();
    }

    private void cancelTimer() {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
            timeoutTask = null;
        }
    }

    private synchronized boolean isClosed() {
        return state == State.CLOSED;
    }

    private void send(JSONObject message) {
        try {
            conn.send(message);
            log("-> " + message.getString("type"));
        } catch (IOException e) {
            log("No se pudo enviar al cliente: " + e.getMessage());
            state = State.CLOSED;
        }
    }

    private void log(String text) {
        System.out.println("[" + java.time.LocalTime.now().withNano(0) + "] " + text);
    }
}
