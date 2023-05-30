import java.util.*;

class Games {
    private Map<String, Game> games;
    private static long gameIdCounter = 0;

    public Games() {
        this.games = new HashMap<>();
    }

    public Map<String, Game> getGames() {
        return games;
    }

    public Game getGame(String gameId) {
        return games.get(gameId);
    }

    public Game createGame(String clientId) {
        String gameId = createGameID();
        Game newGame = new Game(gameId);
        newGame.addPlayer(clientId);
        games.put(gameId, newGame);
        return newGame;
    }

    public static synchronized String createGameID(){
        return "GID" + gameIdCounter++;
    }

    public boolean addPlayerToGame(String playerId, String gameId) {
        Game game = games.get(gameId);
        if (game != null && !game.isFull() && !game.isDone()) {
            game.addPlayer(playerId);
            if (game.isFull()) {
                game.chooseStartingPlayer();
            }
            return true;
        }
        return false;
    }

    public String getGamesByType(String type) {
        StringBuilder sb = new StringBuilder();
        for (Game game : games.values()) {
            if (type.equals("OPEN") && game.isOpen()) {
                sb.append(" ").append(game.getGameId());
            } else if (type.equals("CURR") && game.isFull() && !game.isDone()) {
                sb.append(" ").append(game.getGameId());
            } else if (type.equals("ALL")) {
                sb.append(" ").append(game.getGameId());
            }
        }
        return sb.toString();
    }
}