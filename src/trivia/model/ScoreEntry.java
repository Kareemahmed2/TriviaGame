package trivia.model;

public class ScoreEntry {
    private final String username;
    private final String timestamp;
    private final String mode;
    private final String room;
    private final String team;
    private final int points;
    private final int correct;
    private final int incorrect;

    public ScoreEntry(String username, String timestamp, String mode, String room, String team, int points, int correct, int incorrect) {
        this.username = username;
        this.timestamp = timestamp;
        this.mode = mode;
        this.room = room;
        this.team = team;
        this.points = points;
        this.correct = correct;
        this.incorrect = incorrect;
    }

    public String getUsername() {
        return username;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getMode() {
        return mode;
    }

    public String getRoom() {
        return room;
    }

    public String getTeam() {
        return team;
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
}
