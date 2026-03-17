import java.util.List;

/**
 * Question
 * --------
 * A single MCQ question from the question bank.
 */
public class Question {

    private final String       text;
    private final String       category;
    private final String       difficulty;   // EASY | MEDIUM | HARD
    private final List<String> choices;      // e.g. ["A. Paris", "B. London", ...]
    private final String       correctAnswer; // e.g. "A"

    public Question(String text, String category, String difficulty,
                    List<String> choices, String correctAnswer) {
        this.text          = text;
        this.category      = category;
        this.difficulty    = difficulty.toUpperCase();
        this.choices       = choices;
        this.correctAnswer = correctAnswer.toUpperCase();
    }

    public String       getText()          { return text; }
    public String       getCategory()      { return category; }
    public String       getDifficulty()    { return difficulty; }
    public List<String> getChoices()       { return choices; }
    public String       getCorrectAnswer() { return correctAnswer; }

    /** Case-insensitive answer check. */
    public boolean isCorrect(String answer) {
        return answer != null && answer.trim().equalsIgnoreCase(correctAnswer);
    }

    /** Pretty-print the question for sending to clients. */
    public String format(int number) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nQ").append(number).append(" [").append(category)
                .append(" | ").append(difficulty).append("]\n");
        sb.append(text).append("\n");
        for (String choice : choices) {
            sb.append("  ").append(choice).append("\n");
        }
        return sb.toString();
    }
}