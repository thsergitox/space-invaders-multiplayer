package common;

import java.io.Serializable;

// Represents the state of a single player for network transfer
public class PlayerState implements Serializable {
    private static final long serialVersionUID = 1L; // For serialization versioning

    public int id;          // Unique ID assigned by server
    public int x, y;
    public int score;
    public int lives;
    public boolean alive = true;
    // Add other relevant per-player state if needed (e.g., isShooting flag?)

    public PlayerState(int id, int x, int y, int score, int lives) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.score = score;
        this.lives = lives;
    }

    @Override
    public String toString() {
        return "PlayerState{" +
                "id=" + id +
                ", x=" + x +
                ", y=" + y +
                ", score=" + score +
                ", lives=" + lives +
                ", alive=" + alive +
                '}';
    }
} 