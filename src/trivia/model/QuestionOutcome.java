package trivia.model;

public class QuestionOutcome {
    private final int questionId;
    private final String submitted;
    private final boolean correct;

    public QuestionOutcome(int questionId, String submitted, boolean correct) {
        this.questionId = questionId;
        this.submitted = submitted;
        this.correct = correct;
    }

    public int getQuestionId() {
        return questionId;
    }

    public String getSubmitted() {
        return submitted;
    }

    public boolean isCorrect() {
        return correct;
    }
}
