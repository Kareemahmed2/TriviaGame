# Multiplayer Trivia Game (Assignment 1)

This project implements a command-line multiplayer trivia game using Java sockets and multithreading.

## Implemented Features

- Client-server architecture with concurrent clients (tested with multiple simultaneous connections).
- User authentication:
  - `LOGIN <username> <password>`
  - `REGISTER <name> <username> <password>`
  - `401` for wrong password, `404` for username not found, custom reserved-username error for duplicate registration.
- Data loading at server startup from files:
  - `data/users.csv`
  - `data/scores.csv`
  - `config/game.properties`
  - `data/questions.csv`
- Teammate JSON compatibility layer:
  - If `config/game.properties` is missing, the server falls back to `src/config.json`.
  - `src/users.json` is merged as seed users (without overwriting CSV users).
  - `src/questions.json` is appended to the question bank (duplicates skipped).
- Single-player mode.
- Multiplayer team mode with room/team management:
  - Team names are unique per room.
  - Two teams must exist.
  - Teams must have equal size before starting.
  - Configurable min/max players per team.
- Timed question rounds:
  - Question timeout enforced.
  - Near real-time time-left broadcasts based on config thresholds.
  - Late answers are ignored.
  - Only first answer per player is accepted.
  - Case-insensitive answer checking.
- End-of-game report:
  - Player scores and per-question correctness details.
  - Team scoreboard in multiplayer.
- Score history persistence for each user.
- Robust handling for malformed inputs and disconnects.

## Project Structure

- `src/trivia/server`: server, client session handling, game engine, room/team manager.
- `src/trivia/client`: command-line client.
- `src/trivia/model`: shared data models.
- `src/trivia/store`: file-based persistence and loaders.
- `config/game.properties`: game/server configuration.
- `data/*.csv`: users, scores, and question bank.

## Build

From the project root:

```powershell
mvn -DskipTests compile
```

Or, if you prefer direct `javac` for the packaged implementation only:

```powershell
New-Item -ItemType Directory -Force -Path out | Out-Null
$files = Get-ChildItem -Recurse -Path src\trivia -Filter *.java | ForEach-Object { $_.FullName }
& javac -d out $files
```

## Run Server

```powershell
java -cp out trivia.server.TriviaServer
```

## Run Client

Open one terminal per player:

```powershell
java -cp out trivia.client.TriviaClient 127.0.0.1 5050
```

## Commands (after login)

- `SINGLE <category|ANY> <difficulty|ANY> <questionCount>`
- `MP HELP`
- `MP ROOMS`
- `MP CREATE <roomName> <teamName> <category|ANY> <difficulty|ANY> <questionCount>`
- `MP JOIN <roomName> <teamName>`
- `MP START <roomName>`
- `MP LEAVE <roomName>`
- `HISTORY`
- `MENU`
- `QUIT`

During a game:

- Submit `A`, `B`, `C`, or `D`.
- Use `-` to quit the current game at any time.
