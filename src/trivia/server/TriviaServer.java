package trivia.server;

import trivia.model.GameConfig;
import trivia.store.ConfigLoader;
import trivia.store.QuestionBank;
import trivia.store.ScoreStore;
import trivia.store.UserStore;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TriviaServer {
    private final GameConfig config;
    private final UserStore userStore;
    private final ScoreStore scoreStore;
    private final QuestionBank questionBank;
    private final RoomManager roomManager;
    private final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

    public TriviaServer(GameConfig config, UserStore userStore, ScoreStore scoreStore, QuestionBank questionBank) {
        this.config = config;
        this.userStore = userStore;
        this.scoreStore = scoreStore;
        this.questionBank = questionBank;
        this.roomManager = new RoomManager(config, questionBank, scoreStore);
    }

    public static void main(String[] args) throws Exception {
        Path root = resolveProjectRoot();
        ConfigLoader configLoader = new ConfigLoader(root.resolve("config").resolve("game.properties"));
        GameConfig config = configLoader.load();

        UserStore userStore = new UserStore(root.resolve("data").resolve("users.csv"));
        ScoreStore scoreStore = new ScoreStore(root.resolve("data").resolve("scores.csv"));
        QuestionBank questionBank = new QuestionBank(root.resolve("data").resolve("questions.csv"));

        userStore.load();
        scoreStore.load();
        questionBank.load();

        TriviaServer server = new TriviaServer(config, userStore, scoreStore, questionBank);
        server.start();
    }

    private static Path resolveProjectRoot() {
        Path start = Path.of(".").toAbsolutePath().normalize();
        Path cursor = start;
        for (int i = 0; i < 6 && cursor != null; i++) {
            if (looksLikeProjectRoot(cursor)) {
                return cursor;
            }
            // Common case in this workspace: run from parent and project is nested one level deeper.
            Path nested = cursor.resolve("TriviaGame");
            if (looksLikeProjectRoot(nested)) {
                return nested;
            }
            cursor = cursor.getParent();
        }
        return start;
    }

    private static boolean looksLikeProjectRoot(Path candidate) {
        if (candidate == null) {
            return false;
        }
        Path configProps = candidate.resolve("config").resolve("game.properties");
        Path questionsCsv = candidate.resolve("data").resolve("questions.csv");
        Path teammateJson = candidate.resolve("src").resolve("config.json");
        return Files.exists(configProps) || Files.exists(questionsCsv) || Files.exists(teammateJson);
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(config.getServerPort())) {
            System.out.println("Trivia server running on port " + config.getServerPort());
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                Thread t = new Thread(handler, "client-" + socket.getPort());
                t.start();
            }
        }
    }

    public GameConfig getConfig() {
        return config;
    }

    public UserStore getUserStore() {
        return userStore;
    }

    public ScoreStore getScoreStore() {
        return scoreStore;
    }

    public QuestionBank getQuestionBank() {
        return questionBank;
    }

    public RoomManager getRoomManager() {
        return roomManager;
    }

    public boolean isUserOnline(String username) {
        return onlineUsers.containsKey(username);
    }

    public boolean registerOnline(ClientHandler handler) {
        return onlineUsers.putIfAbsent(handler.getUsername(), handler) == null;
    }

    public void unregisterOnline(ClientHandler handler) {
        if (handler.getUsername() != null) {
            onlineUsers.remove(handler.getUsername(), handler);
        }
        roomManager.removeFromAllRooms(handler);
    }
}
