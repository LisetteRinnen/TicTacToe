import java.util.*;
import java.util.Random;

class Game {
    private static final int[][] WINNING_CONDITIONS = {
      {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, // rows
      {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, // columns
      {0, 4, 8}, {2, 4, 6} // diagonals
    };

    private String gameId;
    private List<String> players;
    private boolean isDone;
    private int firstPlayer;
    private String currentPlayer;
    private List<String> board;
    private String winner;

    public Game(String gameId) {
        this.gameId = gameId;
        this.players = new ArrayList<>();
        this.board = new ArrayList<>(Arrays.asList("*", "*", "*", "*", "*", "*", "*", "*", "*"));
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
        firstPlayer = rand.nextInt(players.size());
        currentPlayer = players.get(firstPlayer);
    }

    public int getFirstPlayer() {
      return firstPlayer;
    }

    public boolean isFull() {
        return players.size() >= 2;
    }

    public boolean isDone() {
        return isDone;
    }

    public String getWinner() {
      return winner;
    }

    public void setWinner(String losingPlayer) {
      for (String player : players) {
        if (!losingPlayer.equals(player)) {
          winner = player;
        }
      }
    }

    public boolean isOpen() {
        return !isFull() && !isDone;
    }

    public void finishGame() {
        isDone = true;
    }

    public boolean makeMove(int index) {
      String element = "X";
      String firstPlayerId = players.get(firstPlayer);
      if (!currentPlayer.equals(firstPlayerId)) {
        element = "O";
      }

      int fixedIndex = 8 - index + 1;
      if (board.get(fixedIndex).equals("*")) {
        return false;
      }
      board.set(fixedIndex, element);
      checkForWinner();
      return true;
    }

    public boolean makeMove(int x, int y) {
      return makeMove(x + y);
    }

    public List<String> getBoard() {
      return board;
    }

    public void checkForWinner() {
      for (int[] slots : WINNING_CONDITIONS) {
        String slot1 = board.get(slots[0]);
        String slot2 = board.get(slots[1]);
        String slot3 = board.get(slots[2]);

        if (!slot1.equals("*") && slot1.equals(slot2) && slot2.equals(slot3)) {
          if (slot1.equals("X")) {
            winner = players.get(firstPlayer);
          } else {
            winner = players.get(1 - firstPlayer);
          }
          finishGame();
        }
      }
      checkForStalemate();
    }

    private void checkForStalemate() {
      boolean movesAvailable = false;
      for (int i = 0; i < board.size(); i++) {
        if (board.get(i).equals("*")) {
          movesAvailable = true;
        }
      }

      if (!movesAvailable) {
        winner = "";
        finishGame();
      }
    }

}