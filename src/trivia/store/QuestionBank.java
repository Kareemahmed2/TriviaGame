package trivia.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import trivia.model.Question;

public class QuestionBank {
    private final Path questionsPath;
    private final List<Question> allQuestions = new CopyOnWriteArrayList<>();

    public QuestionBank(Path questionsPath) {
        this.questionsPath = questionsPath;
    }

    public void load() throws IOException {
        allQuestions.clear();
        List<String> lines = Files.readAllLines(questionsPath);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("id|")) {
                continue;
            }
            String[] p = trimmed.split("\\|", -1);
            if (p.length < 9) {
                continue;
            }
            int id = Integer.parseInt(p[0].trim());
            String category = p[1].trim();
            String difficulty = p[2].trim();
            String text = p[3].trim();
            List<String> choices = List.of(p[4].trim(), p[5].trim(), p[6].trim(), p[7].trim());
            char correct = p[8].trim().toUpperCase(Locale.ROOT).charAt(0);
            allQuestions.add(new Question(id, category, difficulty, text, choices, correct));
        }
    }

    public List<Question> pickQuestions(String category, String difficulty, int count) {
        List<Question> filtered = allQuestions.stream()
                .filter(q -> category.equalsIgnoreCase("ANY") || q.getCategory().equalsIgnoreCase(category))
                .filter(q -> difficulty.equalsIgnoreCase("ANY") || q.getDifficulty().equalsIgnoreCase(difficulty))
                .collect(Collectors.toCollection(ArrayList::new));

        Collections.shuffle(filtered);
        if (filtered.size() <= count) {
            return filtered;
        }
        return filtered.subList(0, count);
    }

    public List<String> allCategories() {
        return allQuestions.stream()
                .map(Question::getCategory)
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList());
    }
}
