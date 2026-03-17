import java.io.*;
import java.net.*;
import java.util.Set;

public class ClientManager implements Runnable {

    // ------------------------------------------------------------------ //
    //  Protocol constants  (server -> client messages)
    // ------------------------------------------------------------------ //
    public static final String MSG_WELCOME       = "Welcome to the Trivia Game!";
    public static final String MSG_LOGIN_PROMPT  = "Enter: LOGIN <username> <password>  |  REGISTER <name> <username> <password>";
    public static final String MSG_LOGIN_OK      = "LOGIN_OK";
    public static final String MSG_MENU          = "\n--- MENU ---\n[1] Single Player\n[2] Multiplayer (Teams)\n[-] Quit";
    public static final String ERR_401           = "ERROR 401 Unauthorized – wrong password.";
    public static final String ERR_404           = "ERROR 404 Not Found – username does not exist.";
    public static final String ERR_USERNAME_TAKEN = "ERROR 409 Conflict – username already taken.";
    public static final String ERR_INVALID_CMD   = "ERROR Invalid command.";

    // ------------------------------------------------------------------ //
    //  Instance fields
    // ------------------------------------------------------------------ //
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter   out;

    private Player currentPlayer;

    private volatile boolean running = true;

    // ------------------------------------------------------------------ //
    //  Constructor
    // ------------------------------------------------------------------ //
    public ClientManager(Socket socket) {
        this.socket = socket;
    }

    // ------------------------------------------------------------------ //
    //  Runnable entry point
    // ------------------------------------------------------------------ //
    @Override
    public void run() {
        try {
            // Set up I/O streams
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            send(MSG_WELCOME);

            // Phase 1 – Authentication
            if (!handleAuth()) {
                disconnect("Authentication failed or client disconnected.");
                return;
            }

            // Phase 2 – Main menu loop
            handleMenu();

        } catch (IOException e) {
            System.out.println("[INFO] Client disconnected unexpectedly: " + e.getMessage());
        } finally {
            disconnect("Session ended.");
        }
    }

    // ------------------------------------------------------------------ //
    //  Authentication phase
    // ------------------------------------------------------------------ //

    private boolean handleAuth() throws IOException {
        while (running) {
            send(MSG_LOGIN_PROMPT);
            String line = readLine();
            if (line == null) return false;          // client disconnected

            String[] parts = line.trim().split("\\s+");
            if (parts.length == 0) continue;

            String command = parts[0].toUpperCase();

            switch (command) {
                case "LOGIN":
                    if (handleLogin(parts)) return true;
                    break;

                case "REGISTER":
                    if (handleRegister(parts)) return true;
                    break;

                default:
                    send(ERR_INVALID_CMD);
            }
        }
        return false;
    }

    private boolean handleLogin(String[] parts) {
        if (parts.length < 3) { send(ERR_INVALID_CMD); return false; }

        String username = parts[1];
        String password = parts[2];

        Player player = GameServer.players.get(username);

        if (player == null) {
            send(ERR_404);
            return false;
        }
        if (!player.getPassword().equals(password)) {
            send(ERR_401);
            return false;
        }

        // Mark as online
        player.setOnline(true);
        currentPlayer = player;
        send(MSG_LOGIN_OK + " Welcome back, " + player.getName() + "!");
        return true;
    }

    private boolean handleRegister(String[] parts) {
        if (parts.length < 4) { send(ERR_INVALID_CMD); return false; }

        String name     = parts[1];
        String username = parts[2];
        String password = parts[3];

        if (GameServer.players.containsKey(username)) {
            send(ERR_USERNAME_TAKEN);
            return false;
        }

        Player newPlayer = new Player(name, username, password);
        GameServer.players.put(username, newPlayer);
        GameServer.saveUsers();         // persist immediately

        newPlayer.setOnline(true);
        currentPlayer = newPlayer;
        send(MSG_LOGIN_OK + " Account created! Welcome, " + name + "!");
        return true;
    }

    // ------------------------------------------------------------------ //
    //  Main menu phase
    // ------------------------------------------------------------------ //
    private void handleMenu() throws IOException {
        while (running) {
            send(MSG_MENU);
            String line = readLine();
            if (line == null) break;

            switch (line.trim()) {
                case "1":
                    startSinglePlayer();
                    break;

                case "2":
                    handleMultiplayerMenu();
                    break;

                case "-":
                    send("Goodbye, " + currentPlayer.getName() + "!");
                    running = false;
                    break;

                default:
                    send(ERR_INVALID_CMD);
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Single Player
    // ------------------------------------------------------------------ //
    private void startSinglePlayer() throws IOException {
        send("--- SINGLE PLAYER SETUP ---");
        send("Available categories: " + getCategories());
        send("Enter category (or ALL for mixed):");
        String category = readLine();
        if (category == null) return;

        send("Choose difficulty: EASY | MEDIUM | HARD | ALL");
        String difficulty = readLine();
        if (difficulty == null) return;

        send("How many questions? (1-" + GameServer.questionBank.size() + ")");
        String numStr = readLine();
        if (numStr == null) return;

        int numQuestions;
        try {
            numQuestions = Integer.parseInt(numStr.trim());
        } catch (NumberFormatException e) {
            send("Invalid number. Returning to menu.");
            return;
        }

        // Build a single-player game room with just this player
        GameRoom room = new GameRoom("single_" + currentPlayer.getUsername(), GameServer.config);
        room.addPlayer(currentPlayer, this);
        room.setCategory(category.trim().toUpperCase());
        room.setDifficulty(difficulty.trim().toUpperCase());
        room.setQuestionCount(numQuestions);
        room.startGame(GameServer.questionBank);
    }

    // ------------------------------------------------------------------ //
    //  Multiplayer / Teams menu
    // ------------------------------------------------------------------ //
    private void handleMultiplayerMenu() throws IOException {
        send("\n--- MULTIPLAYER ---\n[1] Create Team\n[2] Join Team\n[B] Back");

        String line = readLine();
        if (line == null) return;

        switch (line.trim().toUpperCase()) {
            case "1":
                createTeam();
                break;
            case "2":
                joinTeam();
                break;
            case "B":
                // fall through to menu
                break;
            default:
                send(ERR_INVALID_CMD);
        }
    }

    /** Team creator flow */
    private void createTeam() throws IOException {
        send("Enter your team name:");
        String teamName = readLine();
        if (teamName == null) return;
        teamName = teamName.trim();

        if (GameServer.waitingRooms.containsKey(teamName)) {
            send("ERROR Team name already in use. Choose another.");
            return;
        }

        send("Available categories: " + getCategories());
        send("Enter category (or ALL for mixed):");
        String category = readLine();
        if (category == null) return;

        send("Choose difficulty: EASY | MEDIUM | HARD | ALL");
        String difficulty = readLine();
        if (difficulty == null) return;

        send("How many questions? (1-" + GameServer.questionBank.size() + ")");
        String numStr = readLine();
        if (numStr == null) return;

        int numQuestions;
        try {
            numQuestions = Integer.parseInt(numStr.trim());
        } catch (NumberFormatException e) {
            send("Invalid number. Returning to menu.");
            return;
        }

        // Create the room and register it
        GameRoom room = new GameRoom(teamName, GameServer.config);
        room.setCategory(category.trim().toUpperCase());
        room.setDifficulty(difficulty.trim().toUpperCase());
        room.setQuestionCount(numQuestions);
        room.addPlayer(currentPlayer, this);

        GameServer.waitingRooms.put(teamName, room);

        send("Team '" + teamName + "' created. Waiting for the opposing team to join...");

        // Block this thread until the game is ready and started
        room.waitForGame();
    }

    private void joinTeam() throws IOException {
        if (GameServer.waitingRooms.isEmpty()) {
            send("No teams are currently waiting. Try creating one.");
            return;
        }

        send("Available teams: " + String.join(", ", GameServer.waitingRooms.keySet()));
        send("Enter team name to join:");
        String teamName = readLine();
        if (teamName == null) return;
        teamName = teamName.trim();

        GameRoom room = GameServer.waitingRooms.get(teamName);
        if (room == null) {
            send("ERROR Team not found.");
            return;
        }

        room.addPlayer(currentPlayer, this);
        send("Joined team '" + teamName + "'. Waiting for the game to start...");

        // This thread also waits
        room.waitForGame();
    }

    // ------------------------------------------------------------------ //
    //  I/O helpers
    // ------------------------------------------------------------------ //

    public synchronized void send(String message) {
        out.println(message);
    }

    public String readLine() throws IOException {
        try {
            return in.readLine();
        } catch (IOException e) {
            running = false;
            throw e;
        }
    }

    private void disconnect(String reason) {
        System.out.println("[INFO] Disconnecting client"
                + (currentPlayer != null ? " (" + currentPlayer.getUsername() + ")" : "")
                + ": " + reason);

        if (currentPlayer != null) {
            currentPlayer.setOnline(false);
        }

        try { socket.close(); } catch (IOException ignored) {}
    }

    // ------------------------------------------------------------------ //
    //  Misc helpers
    // ------------------------------------------------------------------ //
    private String getCategories() {
        Set<String> cats = new java.util.LinkedHashSet<>();
        for (Question q : GameServer.questionBank) cats.add(q.getCategory());
        return String.join(", ", cats);
    }
}