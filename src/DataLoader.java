import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

public class DataLoader {

    // ------------------------------------------------------------------ //
    //  Shared Gson instance  (pretty-printing for readable saved files)
    // ------------------------------------------------------------------ //
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ================================================================== //
    //  CONFIG
    // ================================================================== //
    public static Config loadConfig(String filePath) throws IOException {
        File file = new File(filePath);

        if (!file.exists()) {
            System.out.println("[WARN] " + filePath + " not found – using default config.");
            return new Config();        // all defaults
        }

        try (Reader reader = new FileReader(file)) {
            GameConfigDTO dto = GSON.fromJson(reader, GameConfigDTO.class);
            return dtoToConfig(dto);
        } catch (JsonSyntaxException e) {
            throw new IOException("Malformed config.json: " + e.getMessage(), e);
        }
    }

    // ================================================================== //
    //  USERS
    // ================================================================== //
    public static void loadUsers(String filePath, Map<String, Player> players) throws IOException {
        File file = new File(filePath);

        if (!file.exists()) {
            System.out.println("[WARN] " + filePath + " not found – starting with no users.");
            return;
        }

        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<PlayerDTO>>() {}.getType();
            List<PlayerDTO> dtos = GSON.fromJson(reader, listType);

            if (dtos == null) return;

            for (PlayerDTO dto : dtos) {
                Player p = dtoToPlayer(dto);
                players.put(p.getUsername(), p);
            }
        } catch (JsonSyntaxException e) {
            throw new IOException("Malformed users.json: " + e.getMessage(), e);
        }
    }


    public static void saveUsers(String filePath, Map<String, Player> players) throws IOException {
        List<PlayerDTO> dtos = new ArrayList<>();
        for (Player p : players.values()) {
            dtos.add(playerToDTO(p));
        }

        // Write to a temp file first, then rename – avoids corrupt file on crash
        File target  = new File(filePath);
        File tempFile = new File(filePath + ".tmp");

        try (Writer writer = new FileWriter(tempFile)) {
            GSON.toJson(dtos, writer);
        }

        // Atomic replace
        Files.move(tempFile.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    // ================================================================== //
    //  QUESTIONS
    // ================================================================== //


    public static void loadQuestions(String filePath, List<Question> questions) throws IOException {
        File file = new File(filePath);

        if (!file.exists()) {
            System.out.println("[WARN] " + filePath + " not found – no questions loaded.");
            return;
        }

        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<QuestionDTO>>() {}.getType();
            List<QuestionDTO> dtos = GSON.fromJson(reader, listType);

            if (dtos == null) return;

            for (QuestionDTO dto : dtos) {
                validateQuestionDTO(dto);      // throws IOException on bad data
                questions.add(dtoToQuestion(dto));
            }
        } catch (JsonSyntaxException e) {
            throw new IOException("Malformed questions.json: " + e.getMessage(), e);
        }
    }

    // ================================================================== //
    //  DTO  <->  Model conversions
    // ================================================================== //

    // ---------- Config ----------

    private static Config dtoToConfig(GameConfigDTO dto) {
        Config cfg = new Config();
        if (dto.questionDurationSeconds > 0) cfg.setQuestionDurationSeconds(dto.questionDurationSeconds);
        if (dto.minPlayersPerTeam      > 0) cfg.setMinPlayersPerTeam(dto.minPlayersPerTeam);
        if (dto.maxPlayersPerTeam      > 0) cfg.setMaxPlayersPerTeam(dto.maxPlayersPerTeam);
        if (dto.pointsEasy             > 0) cfg.setPointsEasy(dto.pointsEasy);
        if (dto.pointsMedium           > 0) cfg.setPointsMedium(dto.pointsMedium);
        if (dto.pointsHard             > 0) cfg.setPointsHard(dto.pointsHard);
        return cfg;
    }

    // ---------- Player ----------

    private static Player dtoToPlayer(PlayerDTO dto) {
        Player p = new Player(
                nullSafe(dto.name,     "Unknown"),
                nullSafe(dto.username, "user_" + System.nanoTime()),
                nullSafe(dto.password, "")
        );
        if (dto.scoreHistory != null) {
            for (String entry : dto.scoreHistory) p.addScoreHistory(entry);
        }
        return p;
    }

    private static PlayerDTO playerToDTO(Player p) {
        PlayerDTO dto    = new PlayerDTO();
        dto.name         = p.getName();
        dto.username     = p.getUsername();
        dto.password     = p.getPassword();
        dto.scoreHistory = new ArrayList<>(p.getScoreHistory());
        return dto;
    }

    // ---------- Question ----------

    private static Question dtoToQuestion(QuestionDTO dto) {
        return new Question(dto.text, dto.category, dto.difficulty,
                dto.choices, dto.correctAnswer);
    }

    private static void validateQuestionDTO(QuestionDTO dto) throws IOException {
        if (dto.text          == null || dto.text.isBlank())          throw new IOException("Question missing 'text'.");
        if (dto.category      == null || dto.category.isBlank())      throw new IOException("Question missing 'category'.");
        if (dto.difficulty    == null || dto.difficulty.isBlank())    throw new IOException("Question missing 'difficulty'.");
        if (dto.choices       == null || dto.choices.isEmpty())       throw new IOException("Question missing 'choices'.");
        if (dto.correctAnswer == null || dto.correctAnswer.isBlank()) throw new IOException("Question missing 'correctAnswer'.");
    }

    // ================================================================== //
    //  Utility
    // ================================================================== //

    private static String nullSafe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    // ================================================================== //
    //  Inner DTO classes  (Gson maps JSON directly to these)
    // ================================================================== //

    /** Internal DTO for config.json */
    private static class GameConfigDTO {
        int questionDurationSeconds;
        int minPlayersPerTeam;
        int maxPlayersPerTeam;
        int pointsEasy;
        int pointsMedium;
        int pointsHard;
    }

    private static class PlayerDTO {
        String       name;
        String       username;
        String       password;
        List<String> scoreHistory;
    }

    private static class QuestionDTO {
        String       text;
        String       category;
        String       difficulty;
        List<String> choices;
        String       correctAnswer;
    }
}