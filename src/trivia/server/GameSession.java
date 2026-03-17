package trivia.server;

import trivia.model.GameConfig;
import trivia.model.PlayerGameStats;
import trivia.model.Question;
import trivia.model.QuestionOutcome;
import trivia.store.QuestionBank;
import trivia.store.ScoreStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameSession implements Runnable, ActiveGame {
    private static class Participant {
        final ClientHandler client;
        final String team;
        final PlayerGameStats stats;

        Participant(ClientHandler client, String team) {
            this.client = client;
            this.team = team;
            this.stats = new PlayerGameStats(client.getUsername());
        }
    }

    private final String mode;
    private final String roomName;
    private final String category;
    private final String difficulty;
    private final int questionCount;
    private final GameConfig config;
    private final QuestionBank questionBank;
    private final ScoreStore scoreStore;
    private final Runnable onFinish;

    private final Map<String, Participant> participants = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    private volatile boolean acceptingAnswers = false;
    private volatile Question currentQuestion;
    private final Map<String, String> firstAnswers = new ConcurrentHashMap<>();

    private GameSession(
            String mode,
            String roomName,
            String category,
            String difficulty,
            int questionCount,
            GameConfig config,
            QuestionBank questionBank,
            ScoreStore scoreStore,
            Runnable onFinish) {
        this.mode = mode;
        this.roomName = roomName;
        this.category = category;
        this.difficulty = difficulty;
        this.questionCount = questionCount;
        this.config = config;
        this.questionBank = questionBank;
        this.scoreStore = scoreStore;
        this.onFinish = onFinish;
    }

    public static GameSession singlePlayer(
            ClientHandler player,
            String category,
            String difficulty,
            int questionCount,
            GameConfig config,
            QuestionBank questionBank,
            ScoreStore scoreStore,
            Runnable onFinish) {
        GameSession session = new GameSession("SINGLE", "-", category, difficulty, questionCount, config, questionBank, scoreStore, onFinish);
        session.participants.put(player.getUsername(), new Participant(player, "SOLO"));
        player.setActiveGame(session);
        return session;
    }

    public static GameSession multiplayer(
            String roomName,
            String category,
            String difficulty,
            int questionCount,
            Map<String, Set<ClientHandler>> teams,
            GameConfig config,
            QuestionBank questionBank,
            ScoreStore scoreStore,
            Runnable onFinish) {
        GameSession session = new GameSession("MULTI", roomName, category, difficulty, questionCount, config, questionBank, scoreStore, onFinish);
        for (Map.Entry<String, Set<ClientHandler>> e : teams.entrySet()) {
            String team = e.getKey();
            for (ClientHandler p : e.getValue()) {
                session.participants.put(p.getUsername(), new Participant(p, team));
                p.setActiveGame(session);
            }
        }
        return session;
    }

    @Override
    public void run() {
        try {
            List<Question> questions = questionBank.pickQuestions(category, difficulty, questionCount);
            if (questions.isEmpty()) {
                broadcast("No questions found for requested filters. Game cancelled.");
                return;
            }

            broadcast("==== GAME STARTED ==== mode=" + mode + " room=" + roomName + " category=" + category + " difficulty=" + difficulty);
            broadcast("Players: " + new ArrayList<>(participants.keySet()));

            int qNum = 1;
            for (Question q : questions) {
                if (!running.get() || participants.isEmpty()) {
                    break;
                }
                askQuestion(qNum++, q);
            }

            endGameReport();
        } finally {
            running.set(false);
            acceptingAnswers = false;
            for (Participant p : participants.values()) {
                p.client.setActiveGame(null);
                if ("MULTI".equalsIgnoreCase(mode)) {
                    p.client.setCurrentRoomName(null);
                }
            }
            if (onFinish != null) {
                onFinish.run();
            }
        }
    }

    private void askQuestion(int number, Question q) {
        currentQuestion = q;
        firstAnswers.clear();
        acceptingAnswers = true;

        broadcast("\nQUESTION " + number + ": " + q.getText());
        List<String> c = q.getChoices();
        broadcast("A) " + c.get(0));
        broadcast("B) " + c.get(1));
        broadcast("C) " + c.get(2));
        broadcast("D) " + c.get(3));
        broadcast("You have " + config.getQuestionTimeSeconds() + " seconds. Submit A/B/C/D. Only first answer counts.");

        Set<Integer> thresholds = new HashSet<>(config.getTimeThresholds());
        for (int remaining = config.getQuestionTimeSeconds(); remaining >= 1; remaining--) {
            if (!running.get()) {
                break;
            }
            if (thresholds.contains(remaining)) {
                broadcast("TIME UPDATE: " + remaining + " seconds left.");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        acceptingAnswers = false;
        evaluateCurrentQuestion();
    }

    private void evaluateCurrentQuestion() {
        if (currentQuestion == null) {
            return;
        }

        broadcast("Question closed. Evaluating...");
        for (Participant p : participants.values()) {
            String ans = firstAnswers.get(p.client.getUsername());
            if (ans == null) {
                p.stats.addMissed(config.getPointsWrong(), currentQuestion.getId());
                p.client.send("No answer submitted. Correct answer: " + currentQuestion.getCorrectChoice());
            } else if (currentQuestion.isCorrect(ans)) {
                p.stats.addCorrect(config.getPointsCorrect(), currentQuestion.getId(), ans);
                p.client.send("Correct (+" + config.getPointsCorrect() + ")");
            } else {
                p.stats.addIncorrect(config.getPointsWrong(), currentQuestion.getId(), ans);
                p.client.send("Wrong (" + config.getPointsWrong() + "). Correct answer: " + currentQuestion.getCorrectChoice());
            }
        }

        broadcastScoreboard();
    }

    private void broadcastScoreboard() {
        List<Participant> ranking = new ArrayList<>(participants.values());
        ranking.sort(Comparator.comparingInt((Participant p) -> p.stats.getPoints()).reversed());

        broadcast("SCOREBOARD:");
        for (Participant p : ranking) {
            broadcast(String.format("- %s [%s] points=%d correct=%d incorrect=%d",
                    p.client.getUsername(), p.team, p.stats.getPoints(), p.stats.getCorrect(), p.stats.getIncorrect()));
        }

        if ("MULTI".equalsIgnoreCase(mode)) {
            Map<String, Integer> teamScore = new HashMap<>();
            for (Participant p : participants.values()) {
                teamScore.merge(p.team, p.stats.getPoints(), Integer::sum);
            }
            broadcast("TEAM SCOREBOARD: " + teamScore);
        }
    }

    private void endGameReport() {
        broadcast("\n==== GAME ENDED ====");
        broadcastScoreboard();

        for (Participant p : participants.values()) {
            p.client.send("DETAILS for " + p.client.getUsername() + ":");
            for (QuestionOutcome o : p.stats.getOutcomes()) {
                p.client.send(String.format("  Question %d | submitted=%s | %s",
                        o.getQuestionId(), o.getSubmitted(), o.isCorrect() ? "CORRECT" : "WRONG"));
            }
        }

        for (Participant p : participants.values()) {
            try {
                scoreStore.add(
                        p.client.getUsername(),
                        mode,
                        roomName,
                        p.team,
                        p.stats.getPoints(),
                        p.stats.getCorrect(),
                        p.stats.getIncorrect());
            } catch (IOException e) {
                p.client.send("WARNING: could not persist score history: " + e.getMessage());
            }
            p.client.send("Your game session finished. You are back to the main menu.");
        }
    }

    @Override
    public synchronized void submitAnswer(ClientHandler player, String rawAnswer) {
        if (!running.get()) {
            player.send("ERROR: Game is not running.");
            return;
        }
        if (!participants.containsKey(player.getUsername())) {
            player.send("ERROR: You are not part of this game.");
            return;
        }
        if (!acceptingAnswers || currentQuestion == null) {
            player.send("ERROR: No active question right now.");
            return;
        }
        if (rawAnswer == null || rawAnswer.isBlank()) {
            player.send("ERROR: Empty answer.");
            return;
        }

        String normalized = String.valueOf(Character.toUpperCase(rawAnswer.trim().charAt(0)));
        if (!List.of("A", "B", "C", "D").contains(normalized)) {
            player.send("ERROR: Invalid option. Use A/B/C/D.");
            return;
        }

        if (firstAnswers.putIfAbsent(player.getUsername(), normalized) == null) {
            player.send("Answer received: " + normalized);
        } else {
            player.send("Only your first answer counts for this question.");
        }
    }

    @Override
    public void leaveGame(ClientHandler player, String reason) {
        Participant removed = participants.remove(player.getUsername());
        if (removed != null) {
            removed.client.setActiveGame(null);
            removed.client.send("You left the game. Reason: " + reason);
            broadcast(player.getUsername() + " left game. Reason: " + reason);
        }
        if (participants.isEmpty()) {
            running.set(false);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void broadcast(String message) {
        for (Participant p : participants.values()) {
            p.client.send(message);
        }
    }
}
