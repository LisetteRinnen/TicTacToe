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

    private static long sessionIdCounter = 0;
    private static long gameIdCounter = 0;

    private static Map<InetAddress, String> sessionIds = new HashMap<>(); // maps InetAddress to sessionId
    private static Map<String, String> clientIds = new HashMap<>(); // maps sessionId to clientId
    private static Map<String, List<String>> clientGames = new HashMap<>(); // maps clientIds to a list of gameIds
    private static Map<String, String> gameStates = new HashMap<>(); // maps gameIds to game state {"OPEN","FULL","DONE"}

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

                System.out.println("UDP CLIENT CONNECTED");

                String request = new String(packet.getData(), 0, packet.getLength());
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();

                exec.execute(() -> handleClientRequest(null, clientAddress, clientPort, request));
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
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        String request = reader.readLine();
                        System.out.println("TCP CLIENT REQUEST: " + request);
                        handleClientRequest(clientSocket, clientSocket.getInetAddress(), clientSocket.getPort(), request);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleClientRequest(Socket clientSocket, InetAddress clientAddress, int clientPort, String request) {
        String[] requestParts = request.split(" ");
        String requestType = requestParts[0];
        String[] parameters = new String[requestParts.length - 1];
        System.arraycopy(requestParts, 1, parameters, 0, parameters.length);

        String response;

        int compatibleProtocolVersion = protocolVersionSupported(parameters);
        if (compatibleProtocolVersion == -1) {
            response = "PROT_VER_ERR"; // adjust to correct error response
        } else {
            response = handleRequestType(requestType, parameters, compatibleProtocolVersion, clientAddress);
        }

        sendResponse(clientSocket, response, clientAddress, clientPort);
    }

    private static void sendResponse(Socket clientSocket, String response, InetAddress clientAddress, int clientPort) {
        // Sending the response back to the client
        try {
            byte[] responseData = response.getBytes();
            if (clientSocket == null) {
                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                udpSocket.send(responsePacket);
            } else {
                clientSocket.getOutputStream().write(responseData);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int protocolVersionSupported(String[] parameters) {
        if (parameters.length > 0) {
            int clientProtocolVersion = Integer.parseInt(parameters[0]);
            if (clientProtocolVersion <= PROTOCOL_VERSION) {
                return Math.min(clientProtocolVersion, PROTOCOL_VERSION);
            }
        }
        return -1;
    }

    private static String handleRequestType(String requestType, String[] parameters, int compatibleProtocolVersion, InetAddress clientAddress) {
        // Determine the message type and call the appropriate handler method
        if (requestType.equals("HELO")) {
            return handleHELORequest(parameters, compatibleProtocolVersion, clientAddress);
        } else if (requestType.equals("LIST")) {
            return handleLISTRequest(parameters, compatibleProtocolVersion);
        } else if (requestType.equals("CREA")) {
            return handleCREARequest(parameters, compatibleProtocolVersion, clientAddress);
        } else if (requestType.equals("JOIN")) {
            return handleJOINRequest(parameters, compatibleProtocolVersion, clientAddress);
        } else {
            return "ERR";
        }
    }

    private static String handleHELORequest(String[] parameters, int compatibleProtocolVersion, InetAddress clientAddress) {
        if (parameters.length == 2) {
            String sessionId = createID();
            String clientId = parameters[1];
            sessionIds.put(clientAddress, sessionId);
            clientIds.put(sessionId, clientId);
            return "SESS " + compatibleProtocolVersion + " " + sessionId;
        } else {
            return "SESS_ERR";
        }
    }

    public static synchronized String createSessionID(){
        return "SID" + String.valueOf(sessionIdCounter++);
    }  

    private static String handleLISTRequest(String[] parameters, int compatibleProtocolVersion) {
        if (parameters.length == 0) {
            // return list of games with less than 2 players
            return "GAMS" + stringConcatenateGamesByType("OPEN");
        } else if (parameters.length == 1) {
            if (parameters[0].equals("CURR")){
                return "GAMS" + stringConcatenateGamesByType("OPEN") + stringConcatenateGamesByType("FULL");
            } else if (parameters[0].equals("ALL")){
                return "GAMS" + stringConcatenateGamesByType("OPEN") + stringConcatenateGamesByType("FULL") + stringConcatenateGamesByType("DONE");
            }
        }
        return "GAMS_ERR";
    }

    private static String stringConcatenateGamesByType(String gameType){
        String games = "";
        for (String gameId : gameStates.keySet()) {
            if (gameStates.get(gameId).equals(gameType)) {
                games += " " + gameId;
            }
        }
        return games;
    }

    private static String handleCREARequest(String[] parameters, int compatibleProtocolVersion, InetAddress clientAddress) {
        if (parameters.length == 1) {
            String clientId = parameters[0]
            String gameId = createGameID();
            gameStates.put(gameId, "OPEN");
            clientGames.computeIfAbsent(clientId, x -> new ArrayList<>()).add(gameId);
            return "JOND " + clientId + " " + gameId;
        } else {
            return "JOND_ERR";
        }
    }

    public static synchronized String createGameID(){
        return "GID" + String.valueOf(gameIdCounter++);
    }  

    private static String handleJOINRequest(String[] parameters, int compatibleProtocolVersion, InetAddress clientAddress) {
        if (parameters.length == 1) {
            // join game
            String clientId = clientIds.get(sessionIds.get(clientAddress));
            String gameId = parameters[0];
            gameStates.put(gameId, "FULL");
            clientGames.computeIfAbsent(clientId, x -> new ArrayList<>()).add(gameId);
            // randomize first player
            // send jond message
            // if full also send yrmv message
            
        } else {
            return "JOND_ERR";
        }
    }
}
