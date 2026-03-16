import java.util.ArrayList;
import java.util.List;

/**
 * Player
 * ------
 * Represents a registered user. Holds credentials, current session
 * score, and historical score records.
 */
public class Player {

    private final String name;
    private final String username;
    private final String password;

    /** Is this player currently connected? */
    private volatile boolean online = false;

    /** Score in the current active game session. */
    private int currentScore = 0;

    /**
     * Score history: each entry is a summary string like
     * "SinglePlayer | Score: 3/5 | 2026-03-10"
     */
    private final List<String> scoreHistory = new ArrayList<>();

    // ------------------------------------------------------------------ //
    //  Constructor
    // ------------------------------------------------------------------ //
    public Player(String name, String username, String password) {
        this.name     = name;
        this.username = username;
        this.password = password;
    }

    // ------------------------------------------------------------------ //
    //  Getters / Setters
    // ------------------------------------------------------------------ //
    public String getName()     { return name; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    public boolean isOnline()          { return online; }
    public void    setOnline(boolean v){ online = v; }

    public int  getCurrentScore()      { return currentScore; }
    public void setCurrentScore(int s) { currentScore = s; }
    public void addScore(int points)   { currentScore += points; }
    public void resetCurrentScore()    { currentScore = 0; }

    public List<String> getScoreHistory() { return scoreHistory; }
    public void addScoreHistory(String entry) { scoreHistory.add(entry); }

    @Override
    public String toString() {
        return username + " (" + name + ") score=" + currentScore;
    }
}