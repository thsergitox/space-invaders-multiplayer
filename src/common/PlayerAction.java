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
        CONNECT, // Initial message from client
        DISCONNECT // Optional: Sent before closing
    }

    public ActionType type;
    public int playerId; // ID assigned by server after connection

    public PlayerAction(ActionType type, int playerId) {
        this.type = type;
        this.playerId = playerId;
    }

     public PlayerAction(ActionType type) { // For CONNECT message before ID assigned
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