import java.util.*;
import java.util.concurrent.*;

public class GameRoom {

    // ------------------------------------------------------------------ //
    //  Room identity
    // ------------------------------------------------------------------ //
    private final String     roomName;
    private final Config config;

    // ------------------------------------------------------------------ //
    //  Players  (player -> their ClientHandler for I/O)
    // ------------------------------------------------------------------ //
    private final Map<Player, ClientManager> playerHandlers = new LinkedHashMap<>();

    // ------------------------------------------------------------------ //
    //  Game settings (set by team creator or single-player setup)
    // ------------------------------------------------------------------ //
    private String category      = "ALL";
    private String difficulty    = "ALL";
    private int    questionCount = 5;

    // ------------------------------------------------------------------ //
    //  Synchronization: all threads wait here until game starts
    // ------------------------------------------------------------------ //
    private final CountDownLatch startLatch = new CountDownLatch(1);

    // ------------------------------------------------------------------ //
    //  Constructor
    // ------------------------------------------------------------------ //
    public GameRoom(String roomName, Config config) {
        this.roomName = roomName;
        this.config   = config;
    }

    // ------------------------------------------------------------------ //
    //  Player management
    // ------------------------------------------------------------------ //
    public synchronized void addPlayer(Player player, ClientManager handler) {
        playerHandlers.put(player, handler);
        broadcast("[ROOM] " + player.getName() + " joined room '" + roomName + "'. "
                + "Players: " + playerHandlers.size());
    }

    public synchronized int getPlayerCount() {
        return playerHandlers.size();
    }

    // ------------------------------------------------------------------ //
    //  Starting the game
    // ------------------------------------------------------------------ //

    /**
     * Called by a ClientHandler thread to block until the game is over.
     * The LAST player to join (or the single player) will trigger the game start.
     */
    public void waitForGame() {
        // If it's a single-player room or room is full enough, start immediately
        // (For now: start as soon as called — team logic will add waiting for
        //  the opposing team in the next iteration.)
        startLatch.countDown();   // release everyone waiting

        // Run game on the thread that triggered it (the last to join)
        startGame(GameServer.questionBank);
    }

    /**
     * Builds the question list, resets scores, then hands off to TriviaGame.
     */
    public void startGame(List<Question> bank) {
        // Wait for the latch (other threads block here until game starts)
        try { startLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Remove from waiting rooms map (game is now active)
        GameServer.waitingRooms.remove(roomName);
        GameServer.activeGames.add(this);

        // Reset all player scores for this game
        for (Player p : playerHandlers.keySet()) p.resetCurrentScore();

        // Filter and shuffle the question list
        List<Question> gameQuestions = filterQuestions(bank);
        if (gameQuestions.isEmpty()) {
            broadcast("[ERROR] No questions found for the selected category/difficulty. Returning to menu.");
            GameServer.activeGames.remove(this);
            return;
        }

        // Trim to requested count
        int count = Math.min(questionCount, gameQuestions.size());
        gameQuestions = gameQuestions.subList(0, count);

        broadcast("\n=== GAME STARTING: " + count + " questions | "
                + category + " | " + difficulty + " ===\n");

        // Run the game loop
        TriviaGame game = new TriviaGame(this, gameQuestions, config);
        game.run();

        GameServer.activeGames.remove(this);
    }

    // ------------------------------------------------------------------ //
    //  Broadcast / messaging
    // ------------------------------------------------------------------ //

    /** Send a message to every player in this room. */
    public void broadcast(String message) {
        for (ClientManager handler : playerHandlers.values()) {
            handler.send(message);
        }
    }

    /** Send a message to one specific player. */
    public void sendTo(Player player, String message) {
        ClientManager h = playerHandlers.get(player);
        if (h != null) h.send(message);
    }

    /** Read a line from a specific player (blocking). Returns null on disconnect. */
    public String readFrom(Player player) {
        ClientManager h = playerHandlers.get(player);
        if (h == null) return null;
        try { return h.readLine(); } catch (Exception e) { return null; }
    }

    // ------------------------------------------------------------------ //
    //  Accessors
    // ------------------------------------------------------------------ //
    public String getRoomName()  { return roomName; }
    public Config getConfig(){ return config; }

    public Set<Player> getPlayers() { return playerHandlers.keySet(); }

    public void setCategory(String c)      { this.category      = c; }
    public void setDifficulty(String d)    { this.difficulty    = d; }
    public void setQuestionCount(int n)    { this.questionCount = n; }

    public String getCategory()   { return category; }
    public String getDifficulty() { return difficulty; }

    // ------------------------------------------------------------------ //
    //  Internal helpers
    // ------------------------------------------------------------------ //
    private List<Question> filterQuestions(List<Question> bank) {
        List<Question> filtered = new ArrayList<>();
        for (Question q : bank) {
            boolean catMatch  = category.equals("ALL")   || q.getCategory().equalsIgnoreCase(category);
            boolean diffMatch = difficulty.equals("ALL") || q.getDifficulty().equalsIgnoreCase(difficulty);
            if (catMatch && diffMatch) filtered.add(q);
        }
        Collections.shuffle(filtered);
        return filtered;
    }
}