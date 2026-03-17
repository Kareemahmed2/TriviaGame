package trivia.model;

import java.util.ArrayList;
import java.util.List;

public class GameConfig {
    private final int serverPort;
    private final int questionTimeSeconds;
    private final int minPlayersPerTeam;
    private final int maxPlayersPerTeam;
    private final List<Integer> timeThresholds;
    private final int pointsCorrect;
    private final int pointsWrong;

    public GameConfig(
            int serverPort,
            int questionTimeSeconds,
            int minPlayersPerTeam,
            int maxPlayersPerTeam,
            List<Integer> timeThresholds,
            int pointsCorrect,
            int pointsWrong) 
    {
        this.serverPort = serverPort;
        this.questionTimeSeconds = questionTimeSeconds;
        this.minPlayersPerTeam = minPlayersPerTeam;
        this.maxPlayersPerTeam = maxPlayersPerTeam;
        this.timeThresholds = new ArrayList<>(timeThresholds);
        this.pointsCorrect = pointsCorrect;
        this.pointsWrong = pointsWrong;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getQuestionTimeSeconds() {
        return questionTimeSeconds;
    }

    public int getMinPlayersPerTeam() {
        return minPlayersPerTeam;
    }

    public int getMaxPlayersPerTeam() {
        return maxPlayersPerTeam;
    }

    public List<Integer> getTimeThresholds() {
        return timeThresholds;
    }

    public int getPointsCorrect() {
        return pointsCorrect;
    }

    public int getPointsWrong() {
        return pointsWrong;
    }

}
