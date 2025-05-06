package server;

import common.Constants; // Use constants for port, etc.
import common.GameStateUpdate;
import common.PlayerAction;
import common.PlayerState;
import common.GameStateUpdate.SimpleBarrierState;
import common.GameStateUpdate.SimplePosition;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList; // For temporary lists
import java.util.Random;
import java.util.stream.Collectors;
import java.awt.Rectangle; // Need this for collision checking

// Main server class
public class GameServer {

    private int port;
    private ServerSocket serverSocket;
    private boolean running = false;
    private final ExecutorService clientExecutor; // To handle client threads
    private final List<ClientHandler> clients; // Thread-safe list of connected clients
    private final AtomicInteger nextPlayerId = new AtomicInteger(0);

    // --- Game State (Server authoritative) ---
    // This needs to replicate the core state managed by GamePanel previously,
    // but adapted for multiple players and network updates.
    // We'll use ConcurrentHashMap for players for thread safety.
    private final Map<Integer, PlayerState> players = new ConcurrentHashMap<>();
    // Use concurrent lists for thread safety during iteration/modification if needed,
    // although most modifications happen within gameLoop's lock now.
    private final List<SimplePosition> invaders = new CopyOnWriteArrayList<>();
    private final List<SimplePosition> playerProjectiles = new CopyOnWriteArrayList<>();
    private final List<SimplePosition> invaderProjectiles = new CopyOnWriteArrayList<>();
    private final List<SimpleBarrierState> barriers = new CopyOnWriteArrayList<>();
    private volatile int currentLevel = 0; // Start at 0, setupLevel increments to 1
    private volatile float invaderSpeedX = Constants.INVADER_INITIAL_SPEED_PX_PER_SEC;
    private volatile int invaderDirection = 1;
    private volatile boolean invadersNeedToDrop = false;
    private volatile boolean gameOver = false;
    private Random random = new Random();
    private final Object gameLock = new Object(); // Lock for modifying game state

    // Font metrics needed for calculations
    private static final java.awt.FontMetrics METRICS;
    private static final int CHAR_WIDTH;
    private static final int CHAR_HEIGHT;
    static { // Static initializer to get FontMetrics
        java.awt.Font font = new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12);
        javax.swing.JLabel dummyLabel = new javax.swing.JLabel();
        METRICS = dummyLabel.getFontMetrics(font);
        CHAR_WIDTH = METRICS.charWidth('M');
        CHAR_HEIGHT = METRICS.getHeight();
        System.out.println("[Server] Font Metrics Initialized: CharWidth=" + CHAR_WIDTH + ", CharHeight=" + CHAR_HEIGHT);
    }

    public GameServer(int port) {
        this.port = port;
        this.clients = new CopyOnWriteArrayList<>();
        // Use a cached thread pool for client handlers
        this.clientExecutor = Executors.newCachedThreadPool();
    }

    public void start() {
        if (running) {
            System.out.println("Server is already running.");
            return;
        }
        running = true;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Game Server started on port: " + port);

            // Initialize the game state (Level 1)
            setupLevel(1); // <<< Initialize game state here

            // Start game logic loop in a separate thread
            new Thread(this::gameLoop).start(); // <<< Start the loop

            // Accept client connections loop
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    int playerId = nextPlayerId.getAndIncrement();
                    System.out.println("Client connected: " + clientSocket.getInetAddress() + " assigned ID: " + playerId);

                    // Initialize player state synchronized with game logic
                    synchronized (gameLock) {
                        PlayerState newPlayer = new PlayerState(playerId, Constants.PLAYER_START_X, Constants.PLAYER_START_Y, 0, Constants.PLAYER_LIVES);
                        players.put(playerId, newPlayer);
                        System.out.println("Initialized state for player " + playerId);
                    }

                    ClientHandler clientHandler = new ClientHandler(clientSocket, this, playerId);
                    clients.add(clientHandler);
                    clientExecutor.submit(clientHandler);
                    System.out.println("Client handler started for player " + playerId);

                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server on port " + port + ": " + e.getMessage());
        } finally {
            stop();
        }
    }

    // --- Game Setup ---
    private void setupLevel(int level) {
         // Ensure exclusive access to game state during setup
         synchronized (gameLock) {
             this.currentLevel = level;
             this.gameOver = false;
             System.out.println("[Server] Setting up Level " + level);

             // Clear objects from previous level
             invaders.clear();
             playerProjectiles.clear();
             invaderProjectiles.clear();
             barriers.clear();

             // Reset movement flags for any connected players
             // Also reset player positions to start for the new level
             for(PlayerState p : players.values()) {
                 p.movingLeft = false;
                 p.movingRight = false;
                 p.x = Constants.PLAYER_START_X; // Reset position
                 p.y = Constants.PLAYER_START_Y;
                 // Keep score, reset lives?
                 // p.lives = Constants.PLAYER_LIVES;
                 p.alive = true; // Make sure they are alive for the new level if they were dead before
             }

             // Calculate invader start grid
             int gridWidth = Constants.INVADER_COLS * (Constants.INVADER_ASCII_WIDTH * CHAR_WIDTH + Constants.INVADER_H_SPACING) - Constants.INVADER_H_SPACING;
             int startX = Constants.GAME_AREA_LEFT_X + (Constants.GAME_AREA_WIDTH - gridWidth) / 2;

             // Setup level parameters (speed)
             float levelSpeedMultiplier = 1.0f + (level - 1) * 0.1f; // Example speed increase
             invaderSpeedX = Constants.INVADER_INITIAL_SPEED_PX_PER_SEC * levelSpeedMultiplier;
             invaderDirection = 1; // Start moving right
             invadersNeedToDrop = false;

             // Populate invaders list
             for (int r = 0; r < Constants.INVADER_ROWS; r++) {
                 for (int col = 0; col < Constants.INVADER_COLS; col++) {
                     int invaderX = startX + col * (Constants.INVADER_ASCII_WIDTH * CHAR_WIDTH + Constants.INVADER_H_SPACING);
                     int invaderY = Constants.INVADER_GRID_START_Y + r * (Constants.INVADER_ASCII_HEIGHT * CHAR_HEIGHT + Constants.INVADER_V_SPACING);
                     invaders.add(new SimplePosition(invaderX, invaderY));
                 }
             }

             // Initialize Barriers with full health
             barriers.add(new SimpleBarrierState(Constants.BARRIER_1_X, Constants.BARRIER_Y, Constants.BARRIER_INITIAL_HEALTH));
             barriers.add(new SimpleBarrierState(Constants.BARRIER_2_X, Constants.BARRIER_Y, Constants.BARRIER_INITIAL_HEALTH));
             barriers.add(new SimpleBarrierState(Constants.BARRIER_3_X, Constants.BARRIER_Y, Constants.BARRIER_INITIAL_HEALTH));
             barriers.add(new SimpleBarrierState(Constants.BARRIER_4_X, Constants.BARRIER_Y, Constants.BARRIER_INITIAL_HEALTH));

             System.out.println("[Server] Level " + level + " setup complete. Invaders: " + invaders.size() + ", Barriers: " + barriers.size());
         }
    }

    // --- Main Game Loop ---
    private void gameLoop() {
        long lastUpdateTime = System.nanoTime();
        System.out.println("[Server] Game loop started.");
        while (running) {
            long now = System.nanoTime();
            // Prevent spiral of death if updates take too long, cap delta time
            double deltaTime = Math.min((now - lastUpdateTime) / 1_000_000_000.0, 0.1); // Max 0.1s update
            lastUpdateTime = now;

            if (!gameOver) { // Only update if game is running
                 synchronized (gameLock) {
                    updateServerGameState(deltaTime); // Call main update method
                    checkServerCollisions(); // Call collision check

                    // Check for level clear / game over conditions after updates/collisions
                    if (invaders.isEmpty()) {
                        System.out.println("[Server] Level " + currentLevel + " cleared!");
                        setupLevel(currentLevel + 1);
                    } else {
                         // Check game over: Any player alive?
                         boolean anyPlayerAlive = players.values().stream().anyMatch(p -> p.alive);
                         // Game also over if invaders reach bottom (handled in updateInvaders)
                         if (!anyPlayerAlive && !players.isEmpty()) {
                            System.out.println("[Server] Game Over - All players defeated.");
                            gameOver = true;
                         }
                    }
                 }
            }

            // Create and broadcast state regardless of game over status
            GameStateUpdate currentState = createGameStateUpdate();
            broadcastGameState(currentState);

            // Sleep/Wait to maintain target FPS
            try {
                 long cycleEndTime = System.nanoTime();
                 long timeTakenNs = cycleEndTime - now;
                 long sleepTimeNs = Constants.OPTIMAL_TIME - timeTakenNs;
                 if (sleepTimeNs > 0) {
                     Thread.sleep(sleepTimeNs / 1_000_000, (int) (sleepTimeNs % 1_000_000));
                 } else {
                     // System.out.println("[WARN] Server loop took longer than optimal time: " + timeTakenNs / 1_000_000 + "ms");
                      Thread.yield();
                 }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
                System.out.println("[Server] Game loop interrupted.");
            }
        }
         System.out.println("[Server] Game loop finished.");
    }

    // --- Game Logic Updates (Called from gameLoop) ---
    private void updateServerGameState(double deltaTime) {
        // Order matters!
        updatePlayers(deltaTime);
        updateProjectiles(playerProjectiles, deltaTime, -1); // Player shots move up
        updateInvaders(deltaTime); // Invaders move and shoot
        updateProjectiles(invaderProjectiles, deltaTime, 1); // Invader shots move down
        // updateUFO(deltaTime); // TODO
    }

    // --- Placeholder Update Methods (To be implemented next) ---
     private void updatePlayers(double deltaTime) {
         // TODO: Implement player movement based on flags
     }
     private void updateProjectiles(List<SimplePosition> projectiles, double deltaTime, int direction) {
          // TODO: Implement projectile movement & off-screen removal
     }
      private void updateInvaders(double deltaTime) {
          // TODO: Implement invader group movement, drop, speed increase, shooting call
          // TODO: Set gameOver = true if invaders reach bottom
      }
       private void handleInvaderShooting(double deltaTime) {
          // TODO: Find eligible shooters and create projectiles
       }
     private void createInvaderProjectile(SimplePosition shooter) {
         // TODO: Add new projectile to invaderProjectiles
     }
      private void createPlayerProjectile(PlayerState shooter) {
         // TODO: Add new projectile to playerProjectiles (with limits?)
      }

    // --- Placeholder Collision Check (To be implemented next) ---
     private void checkServerCollisions() {
        // TODO: Implement collision checks and state updates
     }

    // --- State Creation & Broadcasting ---
    private GameStateUpdate createGameStateUpdate() {
         synchronized (gameLock) {
             GameStateUpdate update = new GameStateUpdate();
             // Create defensive copies of mutable state
             update.players = players.entrySet().stream()
                 .collect(Collectors.toConcurrentMap(Map.Entry::getKey, e -> new PlayerState(e.getValue().id, e.getValue().x, e.getValue().y, e.getValue().score, e.getValue().lives)));
             update.invaders = new ArrayList<>(this.invaders);
             update.playerProjectiles = new ArrayList<>(this.playerProjectiles);
             update.invaderProjectiles = new ArrayList<>(this.invaderProjectiles);
             // Copy barrier states
             update.barriers = this.barriers.stream()
                                 .map(b -> new SimpleBarrierState(b.x, b.y, b.health))
                                 .collect(Collectors.toList());
             update.currentLevel = this.currentLevel;
             update.isGameOver = this.gameOver;
             return update;
         }
    }

    // Sends the current game state to all connected clients
    private void broadcastGameState(GameStateUpdate gameState) {
        if (clients.isEmpty()) return;
        // System.out.println("[Server] Broadcasting state to " + clients.size() + " clients.");
        for (ClientHandler client : clients) {
            client.sendGameState(gameState);
        }
    }

    // --- Action Handling ---
    public synchronized void handlePlayerAction(PlayerAction action) {
         synchronized (gameLock) {
             PlayerState player = players.get(action.playerId);
             if (player == null || !player.alive || gameOver) return;

             // System.out.println("[Server] Handling action: " + action);
             switch (action.type) {
                 case MOVE_LEFT_START: player.movingLeft = true; player.movingRight = false; break;
                 case MOVE_LEFT_STOP: player.movingLeft = false; break;
                 case MOVE_RIGHT_START: player.movingRight = true; player.movingLeft = false; break;
                 case MOVE_RIGHT_STOP: player.movingRight = false; break;
                 case SHOOT:
                     createPlayerProjectile(player);
                     break;
                 case DISCONNECT:
                      System.out.println("[Server] Handling DISCONNECT action for player " + action.playerId);
                      if (player != null) {
                          player.alive = false; // Mark as inactive
                          // Optionally remove immediately or let game over check handle it
                          // players.remove(action.playerId);
                      }
                      ClientHandler handler = findClientHandler(action.playerId);
                      if (handler != null) {
                           handler.stopRunning();
                           removeClient(handler); // Ensure removal from active handlers
                      }
                     break;
                 case CONNECT: break; // Handled on initial connection
             }
         }
    }

    // Removes a client handler from the list (e.g., on disconnect)
    public synchronized void removeClient(ClientHandler clientHandler) {
        if (clientHandler == null) return;
        System.out.println("Removing client for player ID: " + clientHandler.getPlayerId());
        clients.remove(clientHandler);
        // Also remove player state when client disconnects
         synchronized (gameLock) {
             players.remove(clientHandler.getPlayerId());
         }
        System.out.println("Removed player " + clientHandler.getPlayerId() + ". Remaining clients: " + clients.size());
    }

    private ClientHandler findClientHandler(int playerId) {
         for (ClientHandler handler : clients) {
            if (handler.getPlayerId() == playerId) {
                return handler;
            }
        }
        return null;
    }

    public void stop() {
        running = false;
        System.out.println("Stopping server...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
        clientExecutor.shutdown();
        System.out.println("Client executor shutdown.");
        for (ClientHandler client : clients) {
            client.stopRunning();
        }
        clients.clear();
         synchronized(gameLock) { // Clear game state synchronized
            players.clear();
            invaders.clear();
            playerProjectiles.clear();
            invaderProjectiles.clear();
            barriers.clear();
         }
        System.out.println("Server stopped.");
    }

    // Main method to run the server
    public static void main(String[] args) {
        // TODO: Make port configurable (e.g., command line arg)
        int port = 5123; // Default port
        GameServer server = new GameServer(port);

        // Add shutdown hook for graceful exit
         Runtime.getRuntime().addShutdownHook(new Thread(() -> {
             System.out.println("Shutdown hook triggered...");
             server.stop();
         }));

        server.start(); // This blocks until server stops or crashes
    }
} 