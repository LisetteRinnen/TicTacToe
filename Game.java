import java.util.*;
import java.util.Random;

class Game {
    private String gameId;
    private List<String> players;
    private boolean isDone;
    private String currentPlayer;

    public Game(String gameId) {
        this.gameId = gameId;
        this.players = new ArrayList<>();
        this.isDone = false;
    }

    public String getGameId() {
        return gameId;
    }

    public List<String> getPlayers() {
        return players;
    }

    public String getCurrentPlayer() {
        return currentPlayer;
    }

    public void switchTurn() {
        int currentPlayerIndex = players.indexOf(currentPlayer);
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        currentPlayer = players.get(currentPlayerIndex);
    }

    public void addPlayer(String clientId) {
        players.add(clientId);
    }

    public void chooseStartingPlayer() {
        Random rand = new Random();
        currentPlayer = players.get(rand.nextInt(players.size()));
    }

    public boolean isFull() {
        return players.size() >= 2;
    }

    public boolean isDone() {
        return isDone;
    }

    public boolean isOpen() {
        return !isFull() && !isDone;
    }

    public  void finishGame() {
        isDone = true;
    }

}