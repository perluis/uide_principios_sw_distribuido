package tcp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Servidor TCP del juego ¿Quién lo dijo?.
 *
 * Uso: gradle :tcp:runServer [-Pport=8080] [-Pseconds=60]
 * Cada cliente se atiende en su propio hilo, asi el servidor siempre queda
 * disponible para aceptar un cliente nuevo.
 */
public class GameServer {

    public static void main(String[] args) {
        int port = parseInt(args, 0, 8080, "puerto");
        int seconds = parseInt(args, 1, 60, "segundos");

        CharacterBank bank;
        try {
            bank = new CharacterBank(Path.of("img"));
        } catch (IOException e) {
            System.out.println("No se pudieron cargar los personajes: " + e.getMessage());
            System.out.println("Ejecuta el servidor desde la carpeta del proyecto (gradle :tcp:runServer).");
            System.exit(1);
            return;
        }
        Leaderboard leaderboard = new Leaderboard(Path.of("leaderboard.json"));
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "temporizador");
            t.setDaemon(true);
            return t;
        });

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Servidor TCP del juego Quien lo dijo escuchando en el puerto " + port);
            System.out.println("Personajes cargados: " + bank.getNames());
            System.out.println("Duracion de cada partida: " + seconds + " segundos");
            while (true) {
                Socket socket = server.accept();
                try {
                    ClientSession session = new ClientSession(socket, bank, leaderboard, timer, seconds);
                    new Thread(session, "cliente-" + socket.getPort()).start();
                } catch (IOException e) {
                    System.out.println("No se pudo atender al cliente: " + e.getMessage());
                    socket.close();
                }
            }
        } catch (IOException e) {
            System.out.println("No se pudo abrir el puerto " + port + ": " + e.getMessage());
            System.exit(2);
        }
    }

    private static int parseInt(String[] args, int index, int defaultValue, String label) {
        if (args.length <= index) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(args[index].trim());
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            System.out.println("Valor de " + label + " no valido (" + args[index] + "); se usa " + defaultValue);
            return defaultValue;
        }
    }
}
