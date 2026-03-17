package trivia.server;

import trivia.model.ScoreEntry;
import trivia.model.User;
import trivia.store.UserStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Locale;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final TriviaServer server;

    private BufferedReader in;
    private PrintWriter out;

    private volatile String username;
    private volatile String currentRoomName;
    private volatile ActiveGame activeGame;

    public ClientHandler(Socket socket, TriviaServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            send("Welcome to Multiplayer Trivia Game");
            send("Use '-' to quit current game at any time.");

            if (!authenticate()) {
                return;
            }

            sendMenu();
            String line;
            while ((line = in.readLine()) != null) {
                String cmd = line.trim();
                if (cmd.isEmpty()) {
                    continue;
                }

                if ("-".equals(cmd)) {
                    if (activeGame != null) {
                        activeGame.leaveGame(this, "Player quit with '-' command");
                        activeGame = null;
                    } else {
                        send("No active game to quit.");
                    }
                    continue;
                }

                if (activeGame != null && activeGame.isRunning()) {
                    activeGame.submitAnswer(this, cmd);
                    continue;
                }

                if (handleMenuCommand(cmd)) {
                    break;
                }
            }
        } catch (IOException ignored) {
        } finally {
            cleanup();
        }
    }

    private boolean authenticate() throws IOException {
        UserStore userStore = server.getUserStore();

        send("AUTH MENU:");
        send("  LOGIN <username> <password>");
        send("  REGISTER <name> <username> <password>");

        while (true) {
            String line = in.readLine();
            if (line == null) {
                return false;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 0) {
                continue;
            }

            String action = parts[0].toUpperCase(Locale.ROOT);
            if ("LOGIN".equals(action)) {
                if (parts.length < 3) {
                    send("ERROR: Usage LOGIN <username> <password>");
                    continue;
                }
                String user = parts[1];
                String pass = parts[2];

                User u = userStore.find(user);
                if (u == null) {
                    send("404 Not Found: username not found");
                    continue;
                }
                if (!userStore.validatePassword(user, pass)) {
                    send("401 Unauthorized: wrong password");
                    continue;
                }
                this.username = user;
                if (!server.registerOnline(this)) {
                    send("ERROR: This account is already online.");
                    this.username = null;
                    continue;
                }
                send("Login successful. Welcome " + u.getName() + "!");
                return true;
            }

            if ("REGISTER".equals(action)) {
                if (parts.length < 4) {
                    send("ERROR: Usage REGISTER <name> <username> <password>");
                    continue;
                }
                String name = parts[1];
                String user = parts[2];
                String pass = parts[3];

                boolean ok = userStore.register(name, user, pass);
                if (!ok) {
                    send("CUSTOM ERROR: username is already reserved");
                    continue;
                }
                this.username = user;
                if (!server.registerOnline(this)) {
                    send("ERROR: This account is already online.");
                    this.username = null;
                    continue;
                }
                send("Registration successful. Welcome " + name + "!");
                return true;
            }

            send("ERROR: Unknown auth command.");
        }
    }

    private boolean handleMenuCommand(String cmdLine) {
        String[] parts = cmdLine.split("\\s+");
        String cmd = parts[0].toUpperCase(Locale.ROOT);

        switch (cmd) {
            case "MENU":
            case "HELP":
                sendMenu();
                return false;
            case "QUIT":
            case "EXIT":
                send("Goodbye.");
                return true;
            case "SINGLE":
                handleSingle(parts);
                return false;
            case "HISTORY":
                handleHistory();
                return false;
            case "MP":
                handleMultiplayer(parts);
                return false;
            default:
                send("ERROR: Unknown command. Type MENU for commands.");
                return false;
        }
    }

    private void handleSingle(String[] parts) {
        if (activeGame != null && activeGame.isRunning()) {
            send("ERROR: You are already in an active game.");
            return;
        }
        if (parts.length < 4) {
            send("Usage: SINGLE <category|ANY> <difficulty|ANY> <questionCount>");
            return;
        }

        String category = parts[1];
        String difficulty = parts[2];
        int count;
        try {
            count = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            send("ERROR: questionCount must be a number.");
            return;
        }

        GameSession session = GameSession.singlePlayer(this, category, difficulty, count,
                server.getConfig(), server.getQuestionBank(), server.getScoreStore(),
                () -> setActiveGame(null));
        Thread t = new Thread(session, "single-" + username + "-" + System.currentTimeMillis());
        t.start();
        send("Single-player game started.");
    }

    private void handleHistory() {
        List<ScoreEntry> entries = server.getScoreStore().latestForUser(username, 10);
        if (entries.isEmpty()) {
            send("No score history yet.");
            return;
        }
        send("Last games for " + username + ":");
        for (ScoreEntry e : entries) {
            send(String.format("- %s | mode=%s room=%s team=%s points=%d correct=%d incorrect=%d",
                    e.getTimestamp(), e.getMode(), e.getRoom(), e.getTeam(), e.getPoints(), e.getCorrect(), e.getIncorrect()));
        }
    }

    private void handleMultiplayer(String[] parts) {
        if (parts.length < 2) {
            sendMpHelp();
            return;
        }
        String sub = parts[1].toUpperCase(Locale.ROOT);

        switch (sub) {
            case "HELP":
                sendMpHelp();
                break;
            case "ROOMS":
                for (String room : server.getRoomManager().listRooms()) {
                    send(room);
                }
                break;
            case "CREATE":
                if (parts.length < 7) {
                    send("Usage: MP CREATE <roomName> <teamName> <category|ANY> <difficulty|ANY> <questionCount>");
                    return;
                }
                String createMsg = server.getRoomManager().createRoom(parts[2], parts[3], parts[4], parts[5], parseCount(parts[6]), this);
                send(createMsg);
                break;
            case "JOIN":
                if (parts.length < 4) {
                    send("Usage: MP JOIN <roomName> <teamName>");
                    return;
                }
                send(server.getRoomManager().joinRoom(parts[2], parts[3], this));
                break;
            case "LEAVE":
                if (parts.length < 3) {
                    send("Usage: MP LEAVE <roomName>");
                    return;
                }
                send(server.getRoomManager().leaveRoom(parts[2], this));
                break;
            case "START":
                if (parts.length < 3) {
                    send("Usage: MP START <roomName>");
                    return;
                }
                send(server.getRoomManager().startRoomGame(parts[2], this));
                break;
            default:
                send("ERROR: Unknown MP command.");
                sendMpHelp();
                break;
        }
    }

    private int parseCount(String raw) {
        try {
            int n = Integer.parseInt(raw);
            return Math.max(1, n);
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    private void sendMenu() {
        send("MAIN MENU:");
        send("  SINGLE <category|ANY> <difficulty|ANY> <questionCount>");
        send("  MP HELP");
        send("  HISTORY");
        send("  MENU");
        send("  QUIT");
        send("Categories available: " + server.getQuestionBank().allCategories());
    }

    private void sendMpHelp() {
        send("MULTIPLAYER COMMANDS:");
        send("  MP ROOMS");
        send("  MP CREATE <roomName> <teamName> <category|ANY> <difficulty|ANY> <questionCount>");
        send("  MP JOIN <roomName> <teamName>");
        send("  MP LEAVE <roomName>");
        send("  MP START <roomName>");
    }

    public synchronized void send(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private void cleanup() {
        try {
            if (activeGame != null) {
                activeGame.leaveGame(this, "Disconnected");
            }
        } catch (Exception ignored) {
        }
        server.unregisterOnline(this);
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public String getUsername() {
        return username;
    }

    public String getCurrentRoomName() {
        return currentRoomName;
    }

    public void setCurrentRoomName(String currentRoomName) {
        this.currentRoomName = currentRoomName;
    }

    public ActiveGame getActiveGame() {
        return activeGame;
    }

    public void setActiveGame(ActiveGame activeGame) {
        this.activeGame = activeGame;
    }
}
