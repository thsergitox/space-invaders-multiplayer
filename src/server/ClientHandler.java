package server;

import common.GameStateUpdate;
import common.PlayerAction;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

// Handles communication with a single client
public class ClientHandler implements Runnable {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private GameServer server;
    private int playerId;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, GameServer server, int playerId) {
        this.socket = socket;
        this.server = server;
        this.playerId = playerId;
        try {
            // ORDER MATTERS: Create output stream first, then input stream
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.in = new ObjectInputStream(socket.getInputStream());
            System.out.println("[ClientHandler " + playerId + "] Streams opened.");

             // Send the newly assigned player ID back to the client immediately
             // Client needs this to tag its future actions.
             // Use a simple Integer object for this initial message.
            out.writeObject(Integer.valueOf(playerId));
            out.flush();
            System.out.println("[ClientHandler " + playerId + "] Sent player ID back to client.");


        } catch (IOException e) {
            System.err.println("[ClientHandler " + playerId + "] Error creating streams: " + e.getMessage());
            closeConnection();
        }
    }

    @Override
    public void run() {
        try {
            while (running) {
                // Read actions from the client
                PlayerAction action = (PlayerAction) in.readObject();
                if (action != null) {
                    // Important: Ensure the action is tagged with the correct playerId
                    // (Client should ideally send it, but server enforces)
                    action.playerId = this.playerId;
                     System.out.println("[ClientHandler " + playerId + "] Received action: " + action);
                    server.handlePlayerAction(action);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            if (running) {
                 System.err.println("[ClientHandler " + playerId + "] Error reading from client: " + e.getMessage());
                 // Assume client disconnected
            }
        } finally {
             System.out.println("[ClientHandler " + playerId + "] Disconnected.");
             server.removeClient(this);
             closeConnection();
        }
    }

    // Method called by GameServer to send state updates to this client
    public void sendGameState(GameStateUpdate gameState) {
         if (!running || out == null) return;
        try {
            out.writeObject(gameState);
            out.reset(); // Crucial for mutable objects like lists/maps
            out.flush();
           // System.out.println("[ClientHandler " + playerId + "] Sent game state.");
        } catch (IOException e) {
             System.err.println("[ClientHandler " + playerId + "] Error sending game state: " + e.getMessage());
             // Assume client disconnected, trigger removal
             stopRunning();
             server.removeClient(this);
             closeConnection();
        }
    }

     public int getPlayerId() {
        return playerId;
    }

     public void stopRunning() {
        this.running = false;
    }

    // Close streams and socket safely
    private void closeConnection() {
         running = false;
         try {
             if (in != null) in.close();
             if (out != null) out.close();
             if (socket != null && !socket.isClosed()) socket.close();
         } catch (IOException e) {
             System.err.println("[ClientHandler " + playerId + "] Error closing connection: " + e.getMessage());
         }
         System.out.println("[ClientHandler " + playerId + "] Connection resources closed.");
    }
}
