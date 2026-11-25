package tcp.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Estado de una partida: personaje actual, frase actual, intentos, aciertos, puntos y tiempo.
 * No envia mensajes; eso lo hace ClientSession.
 */
public class Game {

    /** Aciertos necesarios para ganar. */
    public static final int TARGET = 3;

    /** Resultado de un intento. */
    public static class GuessOutcome {
        public final boolean correct;
        public final int points;
        public final int attempts;
        public final String characterName;

        GuessOutcome(boolean correct, int points, int attempts, String characterName) {
            this.correct = correct;
            this.points = points;
            this.attempts = attempts;
            this.characterName = characterName;
        }
    }

    private final List<MovieCharacter> all;
    private final int durationSeconds;
    private final List<MovieCharacter> queue = new ArrayList<>();
    private final Set<MovieCharacter> solvedCharacters = new HashSet<>();

    private MovieCharacter current;
    private int quoteIndex;     // 0..3: frase que se esta mostrando
    private int attempts;       // intentos con el personaje actual
    private int solved;         // personajes acertados
    private int score;          // puntos acumulados
    private long startMillis;
    private boolean finished;
    private boolean won;

    public Game(List<MovieCharacter> characters, int durationSeconds) {
        this.all = characters;
        this.durationSeconds = durationSeconds;
    }

    /** Empieza la partida: corre el tiempo y elige el primer personaje. */
    public void start() {
        startMillis = System.currentTimeMillis();
        nextCharacter();
    }

    public MovieCharacter getCurrent() {
        return current;
    }

    public String currentQuote() {
        return current.getQuotes().get(quoteIndex);
    }

    /** Numero de la frase actual, de 1 a 4. */
    public int quoteNumber() {
        return quoteIndex + 1;
    }

    public boolean hasMoreQuotes() {
        return quoteIndex < CharacterBank.QUOTES_PER_CHARACTER - 1;
    }

    /** Comando more: pasa a la siguiente frase del mismo personaje. */
    public void moreQuote() {
        if (hasMoreQuotes()) {
            quoteIndex++;
        }
    }

    /** Comando next: salta a otro personaje sin sumar puntos. */
    public void skip() {
        nextCharacter();
    }

    /** Comando guess: compara la respuesta con el personaje actual. */
    public GuessOutcome guess(String answer) {
        attempts++;
        if (!current.matches(answer)) {
            return new GuessOutcome(false, 0, attempts, null);
        }
        int points = Scoring.pointsFor(attempts);
        int usedAttempts = attempts;
        String name = current.getName();
        score += points;
        solved++;
        solvedCharacters.add(current);
        if (solved >= TARGET) {
            finished = true;
            won = true;
        } else {
            nextCharacter();
        }
        return new GuessOutcome(true, points, usedAttempts, name);
    }

    /** Termina la partida por tiempo agotado. */
    public void timeUp() {
        finished = true;
        won = false;
    }

    public boolean isTimeUp() {
        return secondsLeft() <= 0;
    }

    public int secondsLeft() {
        long elapsed = (System.currentTimeMillis() - startMillis) / 1000;
        return (int) Math.max(0, durationSeconds - elapsed);
    }

    public int secondsUsed() {
        return (int) ((System.currentTimeMillis() - startMillis) / 1000);
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isWon() {
        return won;
    }

    public int getSolved() {
        return solved;
    }

    public int getScore() {
        return score;
    }

    public int getAttempts() {
        return attempts;
    }

    /**
     * Elige el siguiente personaje sin repetir hasta agotar la lista.
     * Si se agota, vuelve a mezclar los que aun no se han acertado.
     */
    private void nextCharacter() {
        if (queue.isEmpty()) {
            List<MovieCharacter> pending = new ArrayList<>();
            for (MovieCharacter c : all) {
                if (!solvedCharacters.contains(c) && c != current) {
                    pending.add(c);
                }
            }
            if (pending.isEmpty() && current != null) {
                pending.add(current); // solo queda el actual
            }
            Collections.shuffle(pending);
            queue.addAll(pending);
        }
        current = queue.remove(0);
        quoteIndex = 0;
        attempts = 0;
    }
}
