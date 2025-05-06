package common;

import java.io.Serializable;

// Represents an action sent from client to server
public class PlayerAction implements Serializable {
    private static final long serialVersionUID = 5L;

    public enum ActionType {
        MOVE_LEFT_START,
        MOVE_LEFT_STOP,
        MOVE_RIGHT_START,
        MOVE_RIGHT_STOP,
        SHOOT,
        CONNECT,        // Initial message from client (might not be needed if ID sent first)
        DISCONNECT,     // Optional: Sent before closing
        START_GAME,     // <<< ADDED: Client requests game start/restart
        TOGGLE_PAUSE    // <<< ADDED: Client requests pause/resume
    }

    public ActionType type;
    public int playerId; // ID assigned by server after connection

    public PlayerAction(ActionType type, int playerId) {
        this.type = type;
        this.playerId = playerId;
    }

     // Constructor for actions before ID assigned (potentially just CONNECT)
     public PlayerAction(ActionType type) {
        if (type != ActionType.CONNECT) {
           // Consider throwing an exception if used improperly
           System.err.println("Warning: PlayerAction created without ID for type: " + type);
        }
        this.type = type;
        this.playerId = -1; // Indicate no ID yet
    }

    @Override
    public String toString() {
        return "PlayerAction{" +
                "type=" + type +
                ", playerId=" + playerId +
                '}';
    }
} 