import java.io.*;
import java.io.BufferedReader;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;

public class TicTacToeServer {
    private static final int PORT = 3116;
    private static final int MAX_PACKET_SIZE = 1024;
    private static final int PROTOCOL_VERSION = 1;

    private static DatagramSocket udpSocket;
    private static ServerSocket tcpSocket;

    private static ExecutorService exec = Executors.newCachedThreadPool();

    private static Map<String, ClientConnection> clientConnections = new HashMap<>(); // maps clientIds to clientConnections
    private static Games games = new Games();

    public static void main(String[] args) {
        try {
            udpSocket = new DatagramSocket(PORT);
            tcpSocket = new ServerSocket(PORT);
            exec = Executors.newFixedThreadPool(10);
            exec.execute(TicTacToeServer::handleUdpRequests);
            exec.execute(TicTacToeServer::handleTcpRequests);
            System.out.println("Server is running on port " + PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleUdpRequests() {
        while (true) {
            try {
                byte[] buffer = new byte[MAX_PACKET_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                ClientConnection clientConnection = null;
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();
                for (ClientConnection c : clientConnections.values()) {
                    if (c.getClientAddress().equals(clientAddress) && c.getClientPort() == clientPort) {
                        clientConnection = c;
                        break;
                    }
                }
                if (clientConnection == null) { 
                    System.out.println("UDP CLIENT CONNECTED");
                    clientConnection = new ClientConnection(udpSocket, clientAddress, clientPort);
                }
                final ClientConnection finalClientConnection = clientConnection;
                String request = new String(packet.getData(), 0, packet.getLength());
                System.out.println("[UDP REQUEST] " + request);

                exec.execute(() -> {
                    handleClientRequest(finalClientConnection, request);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleTcpRequests() {
        while (true) {
            try {
                Socket clientSocket = tcpSocket.accept();
                System.out.println("TCP CLIENT CONNECTED");
                exec.execute(() -> {
                    try {
                        ClientConnection clientConnection = new ClientConnection(clientSocket);
                        while(true) {
                            String request = clientConnection.readRequest();
                            System.out.println("[TCP REQUEST] " + request);
                            handleClientRequest(clientConnection, request);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleClientRequest(ClientConnection clientConnection, String request) {
        String[] requestParts = request.split(" ");
        String requestType = requestParts[0];
        String[] parameters = new String[requestParts.length - 1];
        System.arraycopy(requestParts, 1, parameters, 0, parameters.length);
        handleRequestType(requestType, parameters, clientConnection);
    }

    private static void sendResponse(ClientConnection clientConnection, String response) {
        try {
            clientConnection.sendResponse(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleRequestType(String requestType, String[] parameters, ClientConnection clientConnection) {
        // Determine the message type and call the appropriate handler method
        if (requestType.equals("HELO")) {
            String heloResponse = handleHELORequest(parameters, clientConnection);
            sendResponse(clientConnection, heloResponse);
        } else if (requestType.equals("LIST")) {
            String listResponse = handleLISTRequest(parameters);
            sendResponse(clientConnection, listResponse);
        } else if (requestType.equals("CREA")) {
            String creaResponse = handleCREARequest(parameters, clientConnection);
            System.out.println("CreateResponse:  " + creaResponse);
            sendResponse(clientConnection, creaResponse);
        } else if (requestType.equals("JOIN")) {
            String joinResponse = handleJOINRequest(parameters, clientConnection);
            sendResponse(clientConnection, joinResponse);
            if (!joinResponse.equals("JOND_ERR")) {
                sendYRMV(parameters[0], clientConnection);
            }
        } else if (requestType.equals("STAT")) {
            String statResponse = handleSTATRequest(parameters, clientConnection);
            sendResponse(clientConnection, statResponse);
        } else if (requestType.equals("MOVE")) {
            String moveResponse = handleMOVERequest(parameters, clientConnection);
            sendResponse(clientConnection, moveResponse);

            boolean gameIsDone = checkIfDone(parameters[0]);

            if (!moveResponse.equals("MOVE_ERR") && !gameIsDone) {
                sendYRMV(parameters[0], clientConnection);
            } else if (gameIsDone) {
                buildTERMResponse(parameters[0], clientConnection);
            }
        } else if (requestType.equals("GDBY")) {
            String gdbyResponse = handleGDBYRequest(parameters, clientConnection);
            sendResponse(clientConnection, gdbyResponse);
            clientConnection.close();
        } else if (requestType.equals("QUIT")) {
            boolean quitSuccess = handleQUITRequest(parameters, clientConnection);
            if (!quitSuccess) {
              sendResponse(clientConnection, "QUIT_ERR");
            }
        } else {
            sendResponse(clientConnection, "METHOD NOT FOUND: " + requestType);
        }
    }

    private static String handleHELORequest(String[] parameters, ClientConnection clientConnection) {
        if (parameters.length == 2) {
            String version = parameters[0];
            String sessionId = clientConnection.setSessionId();
            String clientId = parameters[1];
            clientConnection.setClientId(clientId);

            int compatibleProtocolVersion = protocolVersionSupported(version);
            if (compatibleProtocolVersion == -1) {
                return "PROT_VER_ERR"; // adjust to correct error response
            } else {
                clientConnections.put(clientId, clientConnection);
                return "SESS " + compatibleProtocolVersion + " " + sessionId;
            }
        } else {
            return "SESS_ERR";
        }
    }

    private static int protocolVersionSupported(String version) {
        int clientProtocolVersion = Integer.parseInt(version);
        if (clientProtocolVersion <= PROTOCOL_VERSION) {
            return Math.min(clientProtocolVersion, PROTOCOL_VERSION);
        }
        return -1;
    }

    private static String handleLISTRequest(String[] parameters) {
        if (parameters.length == 0) {
            return "GAMS" + games.getGamesByType("OPEN");
        } else if (parameters.length == 1 && (parameters[0].equals("CURR") || parameters[0].equals("ALL"))) {
            return "GAMS" + games.getGamesByType(parameters[0]);
        }
        return "GAMS_ERR";
    }

    private static String handleCREARequest(String[] parameters, ClientConnection clientConnection) {
        if (parameters.length == 1) {
            String clientId = parameters[0];
            Game newGame = games.createGame(clientId);
            clientConnection.addToGameList(newGame);
            return "JOND " + clientId + " " + newGame.getGameId();
        } else {
            return "JOND_ERR";
        }
    }

    private static synchronized String handleJOINRequest(String[] parameters, ClientConnection clientConnection) {
        if (parameters.length == 1) {
            String clientId = clientConnection.getClientId();
            String gameId = parameters[0];
            if (games.addPlayerToGame(clientId, gameId)) {
                return "JOND " + clientId + " " + gameId;
            }
        }
        return "JOND_ERR";
    }

    private static String handleSTATRequest(String[] parameters, ClientConnection clientConnection) {
      if (parameters.length == 1) {
        String gameId = parameters[0];

        return buildBORDResponse(gameId);
      }
      return "STAT_ERR";
    }

    private static String handleMOVERequest(String[] parameters, ClientConnection clientConnection) {
      if (parameters.length == 2) {
        String gameId = parameters[0];
        Game game = games.getGame(gameId);

        if (parameters[1].contains(",")) {
          String[] coord = parameters[1].split(",");
          try {
            int x = Integer.parseInt(coord[0]);
            int y = Integer.parseInt(coord[1]);
            if (x > 3 || y > 3) {
              System.out.println("index out of bounds: " + x + " " + y);
              return buildBORDResponse(gameId);
            }

            boolean goodMove = game.makeMove(x, y);
            if (goodMove) {
              game.switchTurn();
            }
            return buildBORDResponse(gameId);

          } catch (Exception e){
            return buildBORDResponse(gameId);
          }
        } else {
          try {
            int index = Integer.parseInt(parameters[1]);
            if (index > 9) {
              System.out.println("index out of bounds: " + index);
              return buildBORDResponse(gameId);
            }

            boolean goodMove = game.makeMove(index);
            if (goodMove) {
              game.switchTurn();
            }
            return buildBORDResponse(gameId);

          } catch (Exception e) {
            return buildBORDResponse(gameId);
          }
        }
      }
      return "MOVE_ERR";
    }

    private static String buildBORDResponse(String gameId) {
      StringBuilder bordResponse = new StringBuilder();
      Game game = games.getGame(gameId);
      List<String> players = game.getPlayers();

      bordResponse.append("BORD " + gameId + " ");

      if (game.isOpen()) {
        bordResponse.append(players.get(0));
        return bordResponse.toString();
      }

      int firstPlayer = game.getFirstPlayer();

      bordResponse.append(players.get(firstPlayer) + " ");
      bordResponse.append(players.get(1 - firstPlayer) + " ");
      bordResponse.append(game.getCurrentPlayer() + " " );

      List<String> boardList = game.getBoard();
      String board = buildT3Board(boardList);
      bordResponse.append(board + " ");

      if (game.isDone()) {
        bordResponse.append(game.getWinner());
      }
      return bordResponse.toString();
    }

    private static String buildT3Board(List<String> board) {
      StringBuilder t3board = new StringBuilder();
      t3board.append("|");
      for (int i = 0; i < board.size(); i ++) {
        t3board.append(board.get(i));
        t3board.append("|");
      }
      return t3board.toString();
    }

    private static void sendYRMV(String gameId, ClientConnection clientConnection) {
        Game game = games.getGame(gameId);
        for (String clientId : game.getPlayers()) {
            ClientConnection playerConnection = clientConnections.get(clientId);
            sendResponse(playerConnection, "YRMV " + gameId + " " + game.getCurrentPlayer());
        }
    }

    private static boolean checkIfDone(String gameId) {
      Game game = games.getGame(gameId);
      return game.isDone();
    }

    private static void buildTERMResponse(String gameId, ClientConnection clientConnection) {
      Game game = games.getGame(gameId);
      for (String clientId : game.getPlayers()) {
        ClientConnection playerConnection = clientConnections.get(clientId);
        sendResponse(playerConnection, "TERM " + gameId + " " + game.getWinner() + " KTHXBYE");
      }
    }

    private static String handleGDBYRequest(String[] parameters, ClientConnection clientConnection) {
      if (parameters.length == 1) {
        String clientId = clientConnection.getClientId();
        ArrayList<Game> gameList = clientConnection.getGameList();
        for (Game game : gameList) {
          if (!game.isDone()) {
            game.finishGame();
            game.setWinner(clientId);
            buildTERMResponse(clientId, clientConnection);
          }
        }
        return "GDBY";
      }
      return "GDBY_ERR";
    }

    private static boolean handleQUITRequest(String[] parameters, ClientConnection clientConnection) {
      if (parameters.length == 1) {
        String gameId = parameters[0];
        String clientId = clientConnection.getClientId();
        Game game = games.getGame(gameId);
        game.finishGame();
        game.setWinner(clientId);

        buildTERMResponse(gameId, clientConnection);
        return true;
      }
      return false;
    }

}
