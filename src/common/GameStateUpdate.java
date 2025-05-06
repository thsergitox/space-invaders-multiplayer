package common;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

// Represents the full game state broadcast from server to clients
public class GameStateUpdate implements Serializable {
    private static final long serialVersionUID = 2L;

    // Using simpler structures for serialization - adapt GamePanel classes if needed
    public List<SimplePosition> invaders;       // List of invader positions
    public List<SimplePosition> playerProjectiles;
    public List<SimplePosition> invaderProjectiles;
    public Map<Integer, PlayerState> players;    // Map of player ID to PlayerState
    public List<SimpleBarrierState> barriers; // List of barrier states (pos + health)
    // Add UFO state later
    public int currentLevel;
    public boolean isGameOver = false; // Global game over state
    public boolean isPaused = false; // Added

    // Simple position class for generic objects
    public static class SimplePosition implements Serializable {
        private static final long serialVersionUID = 3L;
        public int x, y;
        public SimplePosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
         @Override
        public String toString() {
            return "(" + x + "," + y + ')';
        }
    }

     // Simple barrier state
    public static class SimpleBarrierState implements Serializable {
        private static final long serialVersionUID = 4L;
        public int x, y, health;
        public SimpleBarrierState(int x, int y, int health) {
             this.x = x;
             this.y = y;
             this.health = health;
        }
    }

    // Constructor could be added if needed
} 