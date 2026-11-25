package tcp.protocol;

import org.json.JSONObject;

/**
 * Definicion del protocolo JSON entre cliente y servidor.
 *
 * Todo mensaje tiene la forma {"type": "...", "payload": {...}} y viaja como
 * una sola linea de texto (terminada en salto de linea) por la conexion TCP.
 */
public final class Protocol {

    private Protocol() {
    }

    // ---------------------------------------------------------------- cliente -> servidor
    /** Presentarse con el nombre. payload: name */
    public static final String HELLO = "HELLO";
    /** Empezar una partida. payload vacio */
    public static final String START = "START";
    /** Intentar adivinar. payload: answer */
    public static final String GUESS = "GUESS";
    /** Pedir otra frase del mismo personaje. payload vacio */
    public static final String MORE = "MORE";
    /** Saltar a otro personaje. payload vacio */
    public static final String NEXT = "NEXT";
    /** Responder si quiere jugar otra vez. payload: answer (true/false) */
    public static final String PLAY_AGAIN = "PLAY_AGAIN";
    /** Salir en cualquier momento. payload vacio */
    public static final String QUIT = "QUIT";

    // ---------------------------------------------------------------- servidor -> cliente
    /** Saludo despues de HELLO. payload: message, characters, leaderboard */
    public static final String WELCOME = "WELCOME";
    /** Frase nueva. payload: image, quote, hint, hintsTotal, solved, target, score, secondsLeft, message */
    public static final String QUOTE = "QUOTE";
    /** Resultado de un GUESS. payload: correct, points, score, solved, attempts, message */
    public static final String RESULT = "RESULT";
    /** Fin de partida. payload: won, image, score, solved, message, leaderboard, question */
    public static final String GAME_OVER = "GAME_OVER";
    /** Despedida antes de cerrar la conexion. payload: message */
    public static final String BYE = "BYE";
    /** Error. payload: code, message */
    public static final String ERROR = "ERROR";

    // ---------------------------------------------------------------- codigos de error
    /** El mensaje no es JSON valido o es demasiado largo */
    public static final String INVALID_JSON = "INVALID_JSON";
    /** El campo type no corresponde a ningun mensaje del protocolo */
    public static final String UNKNOWN_TYPE = "UNKNOWN_TYPE";
    /** Falta un campo obligatorio (type, answer...) */
    public static final String MISSING_FIELD = "MISSING_FIELD";
    /** El comando no corresponde al momento del juego (p. ej. GUESS antes de START) */
    public static final String INVALID_STATE = "INVALID_STATE";
    /** Ya se mostraron las 4 frases del personaje */
    public static final String NO_MORE_HINTS = "NO_MORE_HINTS";
    /** Nombre vacio o de mas de 20 caracteres */
    public static final String INVALID_NAME = "INVALID_NAME";
    /** Error inesperado del servidor (el servidor sigue funcionando) */
    public static final String SERVER_ERROR = "SERVER_ERROR";

    /** Arma un mensaje del protocolo. */
    public static JSONObject message(String type, JSONObject payload) {
        JSONObject msg = new JSONObject();
        msg.put("type", type);
        msg.put("payload", payload == null ? new JSONObject() : payload);
        return msg;
    }

    /** Arma un mensaje de error. */
    public static JSONObject error(String code, String text) {
        JSONObject payload = new JSONObject();
        payload.put("code", code);
        payload.put("message", text);
        return message(ERROR, payload);
    }
}
