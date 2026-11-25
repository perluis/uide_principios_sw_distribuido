package tcp.server;

/**
 * Sistema de puntos: mientras mas intentos necesite el jugador para un personaje, menos puntos gana.
 *
 * Intento 1 = 10 puntos, intento 2 = 7, intento 3 = 4, intento 4 o mas = 1.
 */
public final class Scoring {

    private Scoring() {
    }

    public static int pointsFor(int attempt) {
        return Math.max(1, 10 - 3 * (attempt - 1));
    }
}
