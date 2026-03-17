package trivia.server;

public interface ActiveGame {
    void submitAnswer(ClientHandler player, String rawAnswer);

    void leaveGame(ClientHandler player, String reason);

    boolean isRunning();
}
