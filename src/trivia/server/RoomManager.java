package trivia.server;

import trivia.model.GameConfig;
import trivia.store.QuestionBank;
import trivia.store.ScoreStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final GameConfig config;
    private final QuestionBank questionBank;
    private final ScoreStore scoreStore;

    public RoomManager(GameConfig config, QuestionBank questionBank, ScoreStore scoreStore) {
        this.config = config;
        this.questionBank = questionBank;
        this.scoreStore = scoreStore;
    }

    public String createRoom(String roomName, String teamName, String category, String difficulty, int questionCount, ClientHandler creator) {
        if (rooms.containsKey(roomName)) {
            return "ERROR: Room already exists.";
        }
        GameRoom room = new GameRoom(roomName, creator.getUsername(), category, difficulty, questionCount);
        room.addPlayer(teamName, creator);
        rooms.put(roomName, room);
        creator.setCurrentRoomName(roomName);
        return "Room created. Others can now join using: MP JOIN " + roomName + " <teamName>";
    }

    public String joinRoom(String roomName, String teamName, ClientHandler player) {
        GameRoom room = rooms.get(roomName);
        if (room == null) {
            return "ERROR: Room not found.";
        }
        synchronized (room) {
            if (room.isGameStarted()) {
                return "ERROR: Game already started in this room.";
            }
            if (room.teamExists(teamName)) {
                if (room.teamSize(teamName) >= config.getMaxPlayersPerTeam()) {
                    return "ERROR: Team is full.";
                }
            } else {
                if (room.teamCount() >= 2) {
                    return "ERROR: Only two teams are allowed.";
                }
            }

            String alreadyInTeam = room.teamOf(player.getUsername());
            if (alreadyInTeam != null) {
                return "ERROR: You are already in this room under team " + alreadyInTeam + ".";
            }

            room.addPlayer(teamName, player);
            player.setCurrentRoomName(roomName);
            broadcastRoom(room, "[ROOM " + roomName + "] " + player.getUsername() + " joined team " + teamName);
            return "Joined room " + roomName + " under team " + teamName;
        }
    }

    public String leaveRoom(String roomName, ClientHandler player) {
        GameRoom room = rooms.get(roomName);
        if (room == null) {
            player.setCurrentRoomName(null);
            return "Left room.";
        }
        synchronized (room) {
            room.removePlayer(player);
            player.setCurrentRoomName(null);
            broadcastRoom(room, "[ROOM " + roomName + "] " + player.getUsername() + " left the room.");
            if (!room.isGameStarted() && room.allPlayers().isEmpty()) {
                rooms.remove(roomName);
            }
            return "Left room " + roomName;
        }
    }

    public void removeFromAllRooms(ClientHandler player) {
        for (GameRoom room : rooms.values()) {
            synchronized (room) {
                room.removePlayer(player);
                if (!room.isGameStarted() && room.allPlayers().isEmpty()) {
                    rooms.remove(room.getRoomName());
                }
            }
        }
    }

    public List<String> listRooms() {
        List<GameRoom> roomList = new ArrayList<>(rooms.values());
        roomList.sort(Comparator.comparing(GameRoom::getRoomName));
        List<String> out = new ArrayList<>();
        for (GameRoom room : roomList) {
            synchronized (room) {
                out.add(String.format("%s | creator=%s | category=%s | difficulty=%s | questions=%d | teams=%s",
                        room.getRoomName(), room.getCreatorUsername(), room.getCategory(), room.getDifficulty(), room.getQuestionCount(), room.teamNames()));
            }
        }
        if (out.isEmpty()) {
            out.add("No active rooms.");
        }
        return out;
    }

    public String startRoomGame(String roomName, ClientHandler requester) {
        GameRoom room = rooms.get(roomName);
        if (room == null) {
            return "ERROR: Room not found.";
        }

        synchronized (room) {
            if (!room.getCreatorUsername().equalsIgnoreCase(requester.getUsername())) {
                return "ERROR: Only the room creator can start the game.";
            }
            if (room.isGameStarted()) {
                return "ERROR: Room game already started.";
            }
            if (room.teamCount() != 2) {
                return "ERROR: Exactly two teams are required to start.";
            }

            List<String> teamNames = new ArrayList<>(room.teamNames());
            int sizeA = room.teamSize(teamNames.get(0));
            int sizeB = room.teamSize(teamNames.get(1));
            if (sizeA != sizeB) {
                return "ERROR: Both teams must have equal number of players. Current: " + sizeA + " vs " + sizeB;
            }
            if (sizeA < config.getMinPlayersPerTeam()) {
                return "ERROR: Each team must have at least " + config.getMinPlayersPerTeam() + " players.";
            }

            room.setGameStarted(true);
            Map<String, Set<ClientHandler>> teamsSnapshot = room.snapshotTeams();
            GameSession session = GameSession.multiplayer(room.getRoomName(), room.getCategory(), room.getDifficulty(), room.getQuestionCount(),
                    teamsSnapshot, config, questionBank, scoreStore,
                    () -> {
                        rooms.remove(room.getRoomName());
                    });

            Thread t = new Thread(session, "game-room-" + room.getRoomName());
            t.start();
            return "Game started in room " + roomName;
        }
    }

    private void broadcastRoom(GameRoom room, String msg) {
        for (ClientHandler c : room.allPlayers()) {
            c.send(msg);
        }
    }
}
