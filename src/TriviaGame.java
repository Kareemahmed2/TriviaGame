import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * TriviaGame
 * ----------
 * Runs the full question loop for one game session (single or multiplayer).
 *
 * Flow per question:
 *   1. Broadcast question text + choices to all players.
 *   2. Start a countdown timer on a separate thread.
 *      - Timer broadcasts warnings at configurable intervals.
 *      - Timer signals "question closed" after the full duration.
 *   3. Collect answers from all players concurrently.
 *      - Each player gets ONE attempt.
 *      - Late answers (after timer fires) are ignored.
 *      - Answer evaluation is case-insensitive.
 *   4. After the question closes, broadcast results + updated scores.
 *   5. Repeat for every question.
 *   6. After all questions, broadcast the final leaderboard and
 *      persist score history for every player.
 */
public class TriviaGame {

    // ------------------------------------------------------------------ //
    //  Constants
    // ------------------------------------------------------------------ //
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Countdown warning thresholds (seconds remaining). */
    private static final int[] WARNING_THRESHOLDS = {10, 5, 3};

    // ------------------------------------------------------------------ //
    //  Fields
    // ------------------------------------------------------------------ //
    private final GameRoom        room;
    private final List<Question>  questions;
    private final Config      config;

    /**
     * Per-question tracking:
     *   player -> answer they submitted  (null = no answer yet)
     */
    private Map<Player, String> answers;

    /**
     * Whether the current question is still accepting answers.
     * Volatile so the timer thread and reader threads see the same value.
     */
    private volatile boolean questionOpen;

    /** Per-player record: list of booleans (true = correct) per question. */
    private final Map<Player, List<Boolean>> questionRecord = new LinkedHashMap<>();

    // ------------------------------------------------------------------ //
    //  Constructor
    // ------------------------------------------------------------------ //
    public TriviaGame(GameRoom room, List<Question> questions, Config config) {
        this.room      = room;
        this.questions = questions;
        this.config    = config;

        // Initialise the question record for each player
        for (Player p : room.getPlayers()) {
            questionRecord.put(p, new ArrayList<>());
        }
    }

    // ================================================================== //
    //  Main game loop
    // ================================================================== //
    public void run() {
        int total = questions.size();

        for (int i = 0; i < total; i++) {
            Question q = questions.get(i);
            runQuestion(q, i + 1, total);

            // Brief pause between questions
            sleep(2000);
        }

        showFinalLeaderboard();
        persistScoreHistory();
    }

    // ================================================================== //
    //  Single-question flow
    // ================================================================== //
    private void runQuestion(Question question, int number, int total) {
        // ---- 1. Reset per-question state ----
        answers      = new ConcurrentHashMap<>();
        questionOpen = true;

        // ---- 2. Broadcast question ----
        room.broadcast("─────────────────────────────────");
        room.broadcast("Question " + number + " of " + total
                + question.format(number));
        room.broadcast("You have " + config.getQuestionDurationSeconds()
                + " seconds. Enter your answer (A/B/C/D):");

        // ---- 3. Start timer thread ----
        int durationSec   = config.getQuestionDurationSeconds();
        Thread timerThread = new Thread(() -> runTimer(durationSec));
        timerThread.setDaemon(true);
        timerThread.start();

        // ---- 4. Collect answers from all players concurrently ----
        collectAnswers();

        // ---- 5. Wait for timer to finish (it may already be done) ----
        try { timerThread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // ---- 6. Evaluate and broadcast results ----
        evaluateAnswers(question, number);
    }

    // ================================================================== //
    //  Timer
    // ================================================================== //

    /**
     * Runs on its own thread. Broadcasts countdown warnings, then closes
     * the question when time runs out.
     */
    private void runTimer(int durationSec) {
        long endTime = System.currentTimeMillis() + (durationSec * 1000L);

        // Build a set of thresholds we still need to announce
        Set<Integer> pendingWarnings = new LinkedHashSet<>();
        for (int t : WARNING_THRESHOLDS) {
            if (t < durationSec) pendingWarnings.add(t);
        }

        while (System.currentTimeMillis() < endTime && questionOpen) {
            long remainingMs  = endTime - System.currentTimeMillis();
            int  remainingSec = (int) (remainingMs / 1000);

            // Fire any due warnings
            Iterator<Integer> it = pendingWarnings.iterator();
            while (it.hasNext()) {
                int threshold = it.next();
                if (remainingSec <= threshold) {
                    room.broadcast("[TIMER] " + threshold + " seconds remaining!");
                    it.remove();
                }
            }

            // Check if all players have already answered → end early
            if (allPlayersAnswered()) break;

            sleep(300);   // poll every 300 ms to stay responsive
        }

        // Close the question
        questionOpen = false;
        room.broadcast("[TIMER] Time's up!");
    }

    // ================================================================== //
    //  Answer collection
    // ================================================================== //

    /**
     * Spawns one reader thread per player. Each thread blocks on readLine()
     * and records the first valid answer it receives while the question is open.
     */
    private void collectAnswers() {
        List<Thread> readers = new ArrayList<>();

        for (Player player : room.getPlayers()) {
            Thread t = new Thread(() -> {
                while (questionOpen) {
                    String line = room.readFrom(player);
                    if (line == null) break;           // client disconnected

                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;

                    // Only accept A / B / C / D
                    if (!trimmed.matches("(?i)[A-D]")) {
                        room.sendTo(player, "[ERROR] Invalid answer. Enter A, B, C, or D.");
                        continue;
                    }

                    if (!questionOpen) {
                        room.sendTo(player, "[INFO] Too late – question already closed.");
                        break;
                    }

                    // Record first answer only
                    answers.putIfAbsent(player, trimmed.toUpperCase());
                    room.sendTo(player, "[OK] Answer received: " + trimmed.toUpperCase());
                    break;   // one attempt per player
                }
            });
            t.setDaemon(true);
            t.start();
            readers.add(t);
        }

        // Wait for all reader threads (they exit when question closes OR player answers)
        for (Thread t : readers) {
            try { t.join(config.getQuestionDurationSeconds() * 1000L + 500L); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    // ================================================================== //
    //  Evaluation & scoring
    // ================================================================== //

    private void evaluateAnswers(Question question, int questionNumber) {
        room.broadcast("\n--- Results for Question " + questionNumber + " ---");
        room.broadcast("Correct answer: " + question.getCorrectAnswer());

        for (Player player : room.getPlayers()) {
            String given   = answers.get(player);
            boolean correct = question.isCorrect(given);

            // Track per-question record
            questionRecord.get(player).add(correct);

            if (given == null) {
                room.broadcast(player.getName() + ": No answer submitted.");
            } else if (correct) {
                int points = config.getPointsFor(question.getDifficulty());
                player.addScore(points);
                room.broadcast(player.getName() + ": ✓ Correct! +" + points
                        + " pts  (total: " + player.getCurrentScore() + ")");
            } else {
                room.broadcast(player.getName() + ": ✗ Wrong (answered " + given + ")."
                        + "  (total: " + player.getCurrentScore() + ")");
            }
        }
    }

    // ================================================================== //
    //  End-of-game leaderboard
    // ================================================================== //

    private void showFinalLeaderboard() {
        room.broadcast("\n══════════════════════════════════");
        room.broadcast("         FINAL RESULTS");
        room.broadcast("══════════════════════════════════");

        // Sort players by score descending
        List<Player> sorted = new ArrayList<>(room.getPlayers());
        sorted.sort((a, b) -> b.getCurrentScore() - a.getCurrentScore());

        int rank = 1;
        for (Player p : sorted) {
            List<Boolean> record = questionRecord.get(p);
            long correct = record.stream().filter(Boolean::booleanValue).count();
            room.broadcast(rank + ". " + p.getName()
                    + " — Score: " + p.getCurrentScore()
                    + "  (" + correct + "/" + record.size() + " correct)");
            rank++;
        }

        room.broadcast("══════════════════════════════════");

        // Per-player question breakdown
        room.broadcast("\n--- Question Breakdown ---");
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            StringBuilder line = new StringBuilder("Q" + (i + 1) + ": ");
            for (Player p : room.getPlayers()) {
                Boolean correct = questionRecord.get(p).get(i);
                String given    = "?";   // stored in answers per question – abbreviated here
                line.append(p.getName())
                        .append(correct ? " ✓" : " ✗")
                        .append("  ");
            }
            line.append("| Answer: ").append(q.getCorrectAnswer());
            room.broadcast(line.toString());
        }
    }

    // ================================================================== //
    //  Score history persistence
    // ================================================================== //

    private void persistScoreHistory() {
        String mode = (room.getPlayers().size() == 1) ? "SinglePlayer" : "Multiplayer";
        String date = LocalDateTime.now().format(DATE_FMT);

        for (Player p : room.getPlayers()) {
            List<Boolean> record = questionRecord.get(p);
            long correct = record.stream().filter(Boolean::booleanValue).count();

            String entry = String.format("%s | Score: %d (%d/%d correct) | %s | %s | %s",
                    mode,
                    p.getCurrentScore(),
                    correct,
                    record.size(),
                    room.getDifficulty(),
                    room.getCategory(),
                    date);

            p.addScoreHistory(entry);
        }

        // Persist to disk
        GameServer.saveUsers();
    }

    // ================================================================== //
    //  Helpers
    // ================================================================== //

    private boolean allPlayersAnswered() {
        for (Player p : room.getPlayers()) {
            if (!answers.containsKey(p)) return false;
        }
        return true;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}