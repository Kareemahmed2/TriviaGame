package trivia.model;

import java.util.List;

public class Question {
    private final int id;
    private final String category;
    private final String difficulty;
    private final String text;
    private final List<String> choices;
    private final char correctChoice;

    public Question(int id, String category, String difficulty, String text, List<String> choices, char correctChoice) {
        this.id = id;
        this.category = category;
        this.difficulty = difficulty;
        this.text = text;
        this.choices = choices;
        this.correctChoice = Character.toUpperCase(correctChoice);
    }

    public int getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getText() {
        return text;
    }

    public List<String> getChoices() {
        return choices;
    }

    public char getCorrectChoice() {
        return correctChoice;
    }

    public boolean isCorrect(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        return Character.toUpperCase(answer.trim().charAt(0)) == correctChoice;
    }
}
