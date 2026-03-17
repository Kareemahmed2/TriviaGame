package trivia.store;

import trivia.model.ScoreEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ScoreStore {
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path scoresPath;
    private final List<ScoreEntry> allScores = new ArrayList<>();

    public ScoreStore(Path scoresPath) {
        this.scoresPath = scoresPath;
    }

    public synchronized void load() throws IOException {
        allScores.clear();
        if (!Files.exists(scoresPath)) {
            Files.createDirectories(scoresPath.getParent());
            Files.writeString(scoresPath, "username,timestamp,mode,room,team,points,correct,incorrect\n");
            return;
        }

        List<String> lines = Files.readAllLines(scoresPath);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split(",", -1);
            if (p.length < 8) {
                continue;
            }
            allScores.add(new ScoreEntry(
                    p[0].trim(), p[1].trim(), p[2].trim(), p[3].trim(), p[4].trim(),
                    Integer.parseInt(p[5].trim()), Integer.parseInt(p[6].trim()), Integer.parseInt(p[7].trim())
            ));
        }
    }

    public synchronized void add(String username, String mode, String room, String team, int points, int correct, int incorrect) throws IOException {
        
        String ts = TS_FORMAT.format(LocalDateTime.now());
        ScoreEntry entry = new ScoreEntry(username, ts, mode, room, team, points, correct, incorrect);
        allScores.add(entry);
        String row = String.format("%s,%s,%s,%s,%s,%d,%d,%d%n",
                sanitize(username), sanitize(ts), sanitize(mode), sanitize(room), sanitize(team), points, correct, incorrect);
        Files.writeString(scoresPath, row, StandardOpenOption.APPEND);
    }

    public synchronized List<ScoreEntry> latestForUser(String username, int limit) {
        return allScores.stream()
                .filter(e -> e.getUsername().equalsIgnoreCase(username))
                .sorted(Comparator.comparing(ScoreEntry::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String sanitize(String s) {
        return s == null ? "-" : s.replace(",", " ").trim();
    }
}
