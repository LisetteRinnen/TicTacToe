import java.io.*;
import java.net.*;
import java.util.*;

public class ClientConnection {
    private Socket tcpSocket;
    private DatagramSocket udpSocket;
    private BufferedReader reader;
    private OutputStream outputStream;
    private InetAddress clientAddress;
    private int clientPort;
    private String sessionId;
    private String clientId;
    private ArrayList<Game> gameList;

    private static long sessionIdCounter = 0;

    public ClientConnection(Socket socket) throws IOException {
        this.tcpSocket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.outputStream = socket.getOutputStream();
        this.clientAddress = socket.getInetAddress();
        this.clientPort = socket.getPort();
        this.sessionId = null;
        this.clientId = null;
        this.gameList = new ArrayList<>();
    }

    public ClientConnection(DatagramSocket socket, InetAddress address, int port) {
        this.udpSocket = socket;
        this.clientAddress = address;
        this.clientPort = port;
        this.sessionId = null;
        this.clientId = null;
        this.gameList = new ArrayList<>();
    }

    public boolean isTcpConnection() {
        return tcpSocket != null;
    }

    public boolean isUdpConnection() {
        return udpSocket != null;
    }

    public Socket getTcpSocket() {
        return tcpSocket;
    }

    public DatagramSocket getUdpSocket() {
        return udpSocket;
    }

    public String getSessionId() {
        return sessionId;
    }

    public InetAddress getClientAddress() {
        return this.clientAddress;
    }

    public int getClientPort() {
        return this.clientPort;
    }

    public String getClientId() {
        return clientId;
    }

    public String setSessionId() {
        this.sessionId = createSessionID();
        return this.sessionId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void addToGameList(Game game) {
      this.gameList.add(game);
    }

    public ArrayList<Game> getGameList() {
      return gameList;
    }


    public static synchronized String createSessionID(){
        return "SID" + sessionIdCounter++;
    }

    public String readRequest() throws IOException {
        return reader.readLine();
    }

    public void sendResponse(String response) throws IOException {
        byte[] responseData = (response + "\r\n").getBytes();
        if (isTcpConnection()) {
            outputStream.write(responseData);
            outputStream.flush();
            System.out.println("[TCP response] " + response);
        } else if (isUdpConnection()) {
            DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
            udpSocket.send(responsePacket);
            System.out.println("[UDP response] " + response);
        }
    }

    public void close() {
        if (isTcpConnection()) {
            try {
                tcpSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (isUdpConnection()) {
            udpSocket.close();
        }
    }
}

