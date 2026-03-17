
public class Config {

    private int questionDurationSeconds = 15;

    private int minPlayersPerTeam = 1;

    private int maxPlayersPerTeam = 4;

    // Points awarded per correct answer by difficulty
    private int pointsEasy   = 1;
    private int pointsMedium = 2;
    private int pointsHard   = 3;

    // ------------------------------------------------------------------ //
    //  Getters / Setters
    // ------------------------------------------------------------------ //
    public int getQuestionDurationSeconds()              { return questionDurationSeconds; }
    public void setQuestionDurationSeconds(int v)        { questionDurationSeconds = v; }

    public int  getMinPlayersPerTeam()                   { return minPlayersPerTeam; }
    public void setMinPlayersPerTeam(int v)              { minPlayersPerTeam = v; }

    public int  getMaxPlayersPerTeam()                   { return maxPlayersPerTeam; }
    public void setMaxPlayersPerTeam(int v)              { maxPlayersPerTeam = v; }

    public int  getPointsEasy()                          { return pointsEasy; }
    public void setPointsEasy(int v)                     { pointsEasy = v; }

    public int  getPointsMedium()                        { return pointsMedium; }
    public void setPointsMedium(int v)                   { pointsMedium = v; }

    public int  getPointsHard()                          { return pointsHard; }
    public void setPointsHard(int v)                     { pointsHard = v; }

    public int getPointsFor(String difficulty) {
        switch (difficulty.toUpperCase()) {
            case "HARD":   return pointsHard;
            case "MEDIUM": return pointsMedium;
            default:       return pointsEasy;
        }
    }
}