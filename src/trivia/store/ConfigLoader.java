package trivia.store;

import trivia.model.GameConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ConfigLoader {
    private final Path configPath;

    public ConfigLoader(Path configPath) {
        this.configPath = configPath;
    }

    public GameConfig load() throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(configPath)) {
            p.load(in);
        }

        int serverPort = Integer.parseInt(p.getProperty("server.port", "5050"));
        int questionTime = Integer.parseInt(p.getProperty("question.time.seconds", "15"));
        int minPlayers = Integer.parseInt(p.getProperty("multiplayer.min.players.per.team", "1"));
        int maxPlayers = Integer.parseInt(p.getProperty("multiplayer.max.players.per.team", "4"));
        int pointsCorrect = Integer.parseInt(p.getProperty("points.correct", "10"));
        int pointsWrong = Integer.parseInt(p.getProperty("points.wrong", "-2"));

        String raw = p.getProperty("time.update.thresholds", "15,10,5,3,2,1");
        List<Integer> thresholds = new ArrayList<>();
        for (String token : raw.split(",")) {
            String t = token.trim();
            if (!t.isEmpty()) {
                thresholds.add(Integer.parseInt(t));
            }
        }

        return new GameConfig(serverPort, questionTime, minPlayers, maxPlayers, thresholds, pointsCorrect, pointsWrong);
    }
}
