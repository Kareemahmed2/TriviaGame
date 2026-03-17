package trivia.store;

import trivia.model.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserStore {
    private final Path usersPath;
    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();

    public UserStore(Path usersPath) {
        this.usersPath = usersPath;
    }

    public synchronized void load() throws IOException {
        usersByUsername.clear();
        if (!Files.exists(usersPath)) {
            Files.createDirectories(usersPath.getParent());
            Files.writeString(usersPath, "name,username,password\n");
            return;
        }

        List<String> lines = Files.readAllLines(usersPath);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",", -1);
            if (parts.length < 3) {
                continue;
            }
            User user = new User(parts[0].trim(), parts[1].trim(), parts[2].trim());
            usersByUsername.put(user.getUsername(), user);
        }
    }

    public User find(String username) {
        return usersByUsername.get(username);
    }

    public synchronized boolean register(String name, String username, String password) throws IOException {
        if (usersByUsername.containsKey(username)) {
            return false;
        }
        User user = new User(name, username, password);
        usersByUsername.put(username, user);
        String row = String.format("%s,%s,%s%n", sanitize(name), sanitize(username), sanitize(password));
        Files.writeString(usersPath, row, StandardOpenOption.APPEND);
        return true;
    }

    public boolean validatePassword(String username, String password) {
        User user = usersByUsername.get(username);
        return user != null && user.getPassword().equals(password);
    }

    public List<User> listUsers() {
        return new ArrayList<>(usersByUsername.values());
    }

    private String sanitize(String input) {
        return input.replace(",", " ").trim();
    }
}
