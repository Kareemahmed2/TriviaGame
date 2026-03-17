package trivia.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameRoom {
    private final String roomName;
    private final String creatorUsername;
    private final String category;
    private final String difficulty;
    private final int questionCount;

    private final Map<String, Set<ClientHandler>> teams = new HashMap<>();
    private boolean gameStarted;

    public GameRoom(String roomName, String creatorUsername, String category, String difficulty, int questionCount) {
        this.roomName = roomName;
        this.creatorUsername = creatorUsername;
        this.category = category;
        this.difficulty = difficulty;
        this.questionCount = questionCount;
    }

    public synchronized String getRoomName() {
        return roomName;
    }

    public synchronized String getCreatorUsername() {
        return creatorUsername;
    }

    public synchronized String getCategory() {
        return category;
    }

    public synchronized String getDifficulty() {
        return difficulty;
    }

    public synchronized int getQuestionCount() {
        return questionCount;
    }

    public synchronized boolean isGameStarted() {
        return gameStarted;
    }

    public synchronized void setGameStarted(boolean gameStarted) {
        this.gameStarted = gameStarted;
    }

    public synchronized int teamCount() {
        return teams.size();
    }

    public synchronized boolean teamExists(String teamName) {
        return teams.containsKey(teamName);
    }

    public synchronized int teamSize(String teamName) {
        return teams.getOrDefault(teamName, Set.of()).size();
    }

    public synchronized Set<String> teamNames() {
        return new HashSet<>(teams.keySet());
    }

    public synchronized String teamOf(String username) {
        for (Map.Entry<String, Set<ClientHandler>> e : teams.entrySet()) {
            for (ClientHandler p : e.getValue()) {
                if (p.getUsername().equalsIgnoreCase(username)) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    public synchronized void addPlayer(String teamName, ClientHandler player) {
        teams.computeIfAbsent(teamName, k -> new HashSet<>()).add(player);
    }

    public synchronized void removePlayer(ClientHandler player) {
        for (Set<ClientHandler> set : teams.values()) {
            set.remove(player);
        }
        teams.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public synchronized List<ClientHandler> allPlayers() {
        List<ClientHandler> players = new ArrayList<>();
        for (Set<ClientHandler> set : teams.values()) {
            players.addAll(set);
        }
        return players;
    }

    public synchronized Map<String, Set<ClientHandler>> snapshotTeams() {
        Map<String, Set<ClientHandler>> copy = new HashMap<>();
        for (Map.Entry<String, Set<ClientHandler>> e : teams.entrySet()) {
            copy.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        return copy;
    }
}
