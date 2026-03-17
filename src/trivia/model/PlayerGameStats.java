package trivia.model;

import java.util.ArrayList;
import java.util.List;

public class PlayerGameStats {
    private final String username;
    private int points;
    private int correct;
    private int incorrect;
    private final List<QuestionOutcome> outcomes = new ArrayList<>();

    public PlayerGameStats(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public int getPoints() {
        return points;
    }

    public int getCorrect() {
        return correct;
    }

    public int getIncorrect() {
        return incorrect;
    }

    public List<QuestionOutcome> getOutcomes() {
        return outcomes;
    }

    public void addCorrect(int pointsPerCorrect, int questionId, String submitted) {
        points += pointsPerCorrect;
        correct++;
        outcomes.add(new QuestionOutcome(questionId, submitted, true));
    }

    public void addIncorrect(int pointsPerWrong, int questionId, String submitted) {
        points += pointsPerWrong;
        incorrect++;
        outcomes.add(new QuestionOutcome(questionId, submitted, false));
    }

    public void addMissed(int pointsPerWrong, int questionId) {
        points += pointsPerWrong;
        incorrect++;
        outcomes.add(new QuestionOutcome(questionId, "NO_ANSWER", false));
    }
}
