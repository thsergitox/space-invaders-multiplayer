package server;

import common.Constants; // Use constants for port, etc.
import common.GameStateUpdate;
import common.PlayerAction;
import common.PlayerState;

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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.awt.Rectangle; // Use AWT Rectangle for collisions
import java.util.concurrent.TimeUnit;

// Main server class
public class GameServer {

    private int port;
    private ServerSocket serverSocket;
    private boolean running = false;
    private final ExecutorService clientExecutor; // To handle client threads
    private final List<ClientHandler> clients; // Thread-safe list of connected clients
    private final AtomicInteger nextPlayerId = new AtomicInteger(0);

    // Server State Enum Refined
    private enum ServerState {
        WAITING,    // Waiting for first player
        LOBBY,      // Players connected, waiting for START signal
        RUNNING,    // Game logic active
        PAUSED,     // Game logic paused by a player
        GAME_OVER   // Game finished, waiting for START signal to restart
    }
    private volatile ServerState serverState = ServerState.WAITING;

    // --- Game State (Server authoritative) ---
    // This needs to replicate the core state managed by GamePanel previously,
    // but adapted for multiple players and network updates.
    // We'll use ConcurrentHashMap for players for thread safety.
    private final Map<Integer, ServerPlayer> players = new ConcurrentHashMap<>();
    private final List<ServerInvader> invaders = new CopyOnWriteArrayList<>();
    private final List<ServerProjectile> playerProjectiles = new CopyOnWriteArrayList<>();
    private final List<ServerProjectile> invaderProjectiles = new CopyOnWriteArrayList<>();
    private final List<ServerBarrier> barriers = new CopyOnWriteArrayList<>();
    // TODO: Add UFO state

    private volatile int currentLevel = 1;
    private volatile boolean gameRunning = false; // Is the game logic loop active?
    private volatile boolean gameOver = false;
    private float invaderSpeedX = Constants.INVADER_INITIAL_SPEED_PX_PER_SEC;
    private int invaderDirection = 1; // 1 for right, -1 for left
    private boolean invadersNeedToDrop = false;
    private Random random = new Random();

    // Server-side representations of game objects
    private static class ServerPlayer {
        PlayerState state; // The serializable state
        boolean movingLeft = false;
        boolean movingRight = false;
        // Add other non-serializable server-side state if needed
        ServerPlayer(PlayerState state) { this.state = state; }
        Rectangle getBounds() {
             // Approx bounds - adjust charWidth/Height if needed
             return new Rectangle(state.x, state.y, Constants.PLAYER_ASCII_WIDTH * 6, Constants.PLAYER_ASCII_HEIGHT * 12);
        }
    }
    private static class ServerInvader {
        int id; // Unique ID for invaders might be useful
        int x, y, points, row;
        boolean alive = true;
        static AtomicInteger nextInvaderId = new AtomicInteger(0);
        ServerInvader(int x, int y, int p, int r){ this.id = nextInvaderId.getAndIncrement(); this.x=x;this.y=y;this.points=p;this.row=r;}
         Rectangle getBounds(){
             int cw = 6; int ch = 12; // Approx char size
             int aw = Constants.INVADER_ASCII_WIDTH*cw/2; int ah=Constants.INVADER_ASCII_HEIGHT*ch;
             return new Rectangle(x+aw/4, y, aw, ah);
         }
    }
     private static class ServerProjectile {
         int id; // Unique ID
         int x, y;
         int ownerPlayerId = -1; // For player projectiles
         boolean playerOwned; // True if shot by player, false if by invader
         boolean active = true;
         static AtomicInteger nextProjectileId = new AtomicInteger(0);
         ServerProjectile(int x, int y, boolean playerOwned, int ownerId) {
            this.id = nextProjectileId.getAndIncrement();
            this.x = x;
            this.y = y;
            this.playerOwned = playerOwned;
            this.ownerPlayerId = playerOwned ? ownerId : -1;
         }
         Rectangle getBounds() {
            int cw = 6; int ch = 12; // Approx char size
            return new Rectangle(x, y - ch, cw, ch);
         }
     }
    private static class ServerBarrier {
         int x, y, health;
         ServerBarrier(int x, int y){ this.x=x; this.y=y; this.health=Constants.BARRIER_INITIAL_HEALTH;}
         void hit(){ if(health>0){ health--; } }
         boolean isAlive(){ return health>0; }
         Rectangle getBounds(){
             if (!isAlive()) return new Rectangle(0,0,0,0);
             int cw = 6; int ch = 12; // Approx char size
             int aw=Constants.BARRIER_ASCII_WIDTH*cw; int ah=Constants.BARRIER_ASCII_HEIGHT*ch;
             return new Rectangle(x, y, aw, ah);
         }
     }

    public GameServer(int port) {
        this.port = port;
        this.clients = new CopyOnWriteArrayList<>();
        // Use a cached thread pool for client handlers
        this.clientExecutor = Executors.newCachedThreadPool();
    }

    public void start() {
        if (running) return;
        running = true;
        serverState = ServerState.WAITING;
        gameOver = false;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Game Server started on port: " + port);
            System.out.println("Server state: " + serverState);

            // Start game logic loop immediately
            new Thread(this::gameLoop).start();
            System.out.println("Game logic loop thread started.");

            // Accept client connections loop
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    int playerId = nextPlayerId.getAndIncrement();
                    System.out.println("Client connected: " + clientSocket.getInetAddress() + " assigned ID: " + playerId);
                    PlayerState newPlayerState = new PlayerState(playerId, Constants.PLAYER_START_X, Constants.PLAYER_START_Y, 0, Constants.PLAYER_LIVES);
                    ServerPlayer newServerPlayer = new ServerPlayer(newPlayerState);
                    players.put(playerId, newServerPlayer);

                    ClientHandler clientHandler = new ClientHandler(clientSocket, this, playerId);
                    clients.add(clientHandler);
                    clientExecutor.submit(clientHandler);
                    System.out.println("Client handler submitted for player " + playerId);

                    // Transition WAITING -> LOBBY
                    synchronized(this) {
                        if (serverState == ServerState.WAITING) {
                             System.out.println("First player connected, changing state to LOBBY.");
                             serverState = ServerState.LOBBY;
                             // Don't setup level yet, wait for START_GAME
                        }
                    }
                } catch (IOException e) {
                    if (!running || serverSocket.isClosed()) {
                        System.out.println("Server socket closed, accepting connections stopped.");
                        break; // Exit loop if server stopped
                    } else {
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

    // Setup or Reset game state for Level 1
    private synchronized void setupOrResetLevel(int level) {
        System.out.println("[Server] Setting up Level " + level);
        currentLevel = level;
        gameOver = false; // Logical game over reset

        // Clear transient objects
        invaders.clear();
        playerProjectiles.clear();
        invaderProjectiles.clear();
        barriers.clear();
        ServerInvader.nextInvaderId.set(0); // Reset IDs if needed
        ServerProjectile.nextProjectileId.set(0);

        // Reset invader movement params
        float levelSpeedMultiplier = 1.0f + (level - 1) * 0.1f; // Same logic as client had
        invaderSpeedX = Constants.INVADER_INITIAL_SPEED_PX_PER_SEC * levelSpeedMultiplier;
        invaderDirection = 1;
        invadersNeedToDrop = false;

        // Calculate grid start position (using approx char sizes)
        int approxCharWidth = 6;
        int approxCharHeight = 12;
        int gridWidth = Constants.INVADER_COLS * (Constants.INVADER_ASCII_WIDTH * approxCharWidth + Constants.INVADER_H_SPACING) - Constants.INVADER_H_SPACING;
        int startX = Constants.GAME_AREA_LEFT_X + (Constants.GAME_AREA_WIDTH - gridWidth) / 2;

        // Populate invaders
        for (int r = 0; r < Constants.INVADER_ROWS; r++) {
            for (int c = 0; c < Constants.INVADER_COLS; c++) {
                 int invX = startX + c * (Constants.INVADER_ASCII_WIDTH * approxCharWidth + Constants.INVADER_H_SPACING);
                 int invY = Constants.INVADER_GRID_START_Y + r * (Constants.INVADER_ASCII_HEIGHT * approxCharHeight + Constants.INVADER_V_SPACING);
                 int points = (r == 0) ? Constants.INVADER_SMALL_POINTS : (r <= 2) ? Constants.INVADER_MEDIUM_POINTS : Constants.INVADER_LARGE_POINTS;
                 invaders.add(new ServerInvader(invX, invY, points, r));
            }
        }
        System.out.println("[Server] Created " + invaders.size() + " invaders.");

        // Populate barriers
        barriers.add(new ServerBarrier(Constants.BARRIER_1_X, Constants.BARRIER_Y));
        barriers.add(new ServerBarrier(Constants.BARRIER_2_X, Constants.BARRIER_Y));
        barriers.add(new ServerBarrier(Constants.BARRIER_3_X, Constants.BARRIER_Y));
        barriers.add(new ServerBarrier(Constants.BARRIER_4_X, Constants.BARRIER_Y));
         System.out.println("[Server] Created barriers.");

        // Reset ALL players for the new game/level start
        for (ServerPlayer sp : players.values()) {
            sp.state.x = Constants.PLAYER_START_X;
            sp.state.y = Constants.PLAYER_START_Y;
            sp.state.alive = true;
            sp.state.lives = Constants.PLAYER_LIVES;
            sp.state.score = 0; // Reset score on new game start
            sp.movingLeft = false;
            sp.movingRight = false;
        }
        System.out.println("[Server] Level setup complete. Player states reset.");
    }

    // Placeholder for the main game loop on the server
    private void gameLoop() {
        long lastUpdateTime = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            double deltaTime = (now - lastUpdateTime) / 1_000_000_000.0;
            lastUpdateTime = now;

            GameStateUpdate currentState = null;

            // Lock state during update/check phases
            synchronized (this) {
                switch (serverState) {
                    case RUNNING:
                        updateServerGameState(deltaTime);
                        checkServerCollisions();
                        checkGameConditions(); // Might change state to GAME_OVER
                        currentState = createGameStateUpdate();
                        break;

                    case WAITING:
                    case LOBBY:
                    case GAME_OVER:
                        // Send state but don't update game logic
                        currentState = createGameStateUpdate(); // Send current state (lobby/game over info)
                        break;

                    case PAUSED:
                        // Send state but don't update game logic
                        currentState = createGameStateUpdate(); // Send paused state
                        break;
                }
            } // end synchronized block

            if (currentState != null) {
                broadcastGameState(currentState);
            }

            // Sleep
            try {
                long cycleTime = System.nanoTime() - now;
                long sleepTimeMs = Math.max(15, (Constants.OPTIMAL_TIME - cycleTime) / 1_000_000); // Sleep longer if waiting/paused
                Thread.sleep(sleepTimeMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false; // Stop server if loop is interrupted
            }
        }
        System.out.println("[Server] Game loop thread finished.");
    }

    // Creates the state object to send to clients
    private GameStateUpdate createGameStateUpdate() {
        GameStateUpdate update = new GameStateUpdate();

        // Players (send copies of PlayerState)
        Map<Integer, PlayerState> playerStates = new ConcurrentHashMap<>();
        for(Map.Entry<Integer, ServerPlayer> entry : players.entrySet()){
             playerStates.put(entry.getKey(), entry.getValue().state); // Add the serializable state
        }
        update.players = playerStates;

        // Invaders
        List<GameStateUpdate.SimplePosition> invaderPositions = new ArrayList<>();
        for (ServerInvader inv : invaders) {
            invaderPositions.add(new GameStateUpdate.SimplePosition(inv.x, inv.y));
        }
        update.invaders = invaderPositions;

        // Player Projectiles
         List<GameStateUpdate.SimplePosition> playerProjPositions = new ArrayList<>();
        for (ServerProjectile p : playerProjectiles) {
            playerProjPositions.add(new GameStateUpdate.SimplePosition(p.x, p.y));
        }
        update.playerProjectiles = playerProjPositions;

        // Invader Projectiles
         List<GameStateUpdate.SimplePosition> invaderProjPositions = new ArrayList<>();
        for (ServerProjectile p : invaderProjectiles) {
            invaderProjPositions.add(new GameStateUpdate.SimplePosition(p.x, p.y));
        }
        update.invaderProjectiles = invaderProjPositions;

        // Barriers
         List<GameStateUpdate.SimpleBarrierState> barrierStates = new ArrayList<>();
         for (ServerBarrier b : barriers) {
              barrierStates.add(new GameStateUpdate.SimpleBarrierState(b.x, b.y, b.health));
         }
         update.barriers = barrierStates;

        // Other state
        update.currentLevel = this.currentLevel;
        update.isGameOver = (serverState == ServerState.GAME_OVER); // Reflect game over state
        update.isPaused = (serverState == ServerState.PAUSED);

        return update;
    }

    // Sends the current game state to all connected clients
    private void broadcastGameState(GameStateUpdate gameState) {
        if(clients.isEmpty()) return; // No one to send to
        // System.out.println("Broadcasting state to " + clients.size() + " clients.");
        for (ClientHandler client : clients) {
            client.sendGameState(gameState);
        }
    }

    // Method called by ClientHandler when an action is received
    public synchronized void handlePlayerAction(PlayerAction action) {
        // Handle START_GAME and TOGGLE_PAUSE regardless of player state (but server state matters)
        if (action.type == PlayerAction.ActionType.START_GAME) {
             if (!players.isEmpty() && (serverState == ServerState.LOBBY || serverState == ServerState.GAME_OVER)) {
                 System.out.println("[Server] START_GAME received from player " + action.playerId + ". Starting/Restarting game.");
                 setupOrResetLevel(1); // Always restart from level 1
                 serverState = ServerState.RUNNING;
             } else {
                  System.out.println("[Server] Ignoring START_GAME (State: "+serverState+", Players: "+players.size()+")");
             }
             return;
        }
        if (action.type == PlayerAction.ActionType.TOGGLE_PAUSE) {
             if (serverState == ServerState.RUNNING) {
                  System.out.println("[Server] PAUSE received from player " + action.playerId + ". Pausing game.");
                  serverState = ServerState.PAUSED;
             } else if (serverState == ServerState.PAUSED) {
                  System.out.println("[Server] RESUME received from player " + action.playerId + ". Resuming game.");
                  serverState = ServerState.RUNNING;
                  // Maybe reset lastUpdateTime here?
                   // lastUpdateTime = System.nanoTime(); // Avoid large deltaTime jump after pause
             } else {
                   System.out.println("[Server] Ignoring TOGGLE_PAUSE (State: "+serverState+")");
             }
             return;
        }
        if (action.type == PlayerAction.ActionType.DISCONNECT) {
             System.out.println("Player " + action.playerId + " disconnect action.");
             ClientHandler handler = findClientHandler(action.playerId);
             if (handler != null) removeClient(handler);
             return;
        }

        // For other actions, server must be RUNNING and player valid
        if (serverState != ServerState.RUNNING) {
             // System.out.println("Ignoring action "+action.type+" from player "+action.playerId+" as server state is "+serverState);
             return;
        }
        ServerPlayer player = players.get(action.playerId);
        if (player == null || !player.state.alive) {
            return;
        }

        switch (action.type) {
            case MOVE_LEFT_START: player.movingLeft = true; player.movingRight = false; break;
            case MOVE_LEFT_STOP: player.movingLeft = false; break;
            case MOVE_RIGHT_START: player.movingRight = true; player.movingLeft = false; break;
            case MOVE_RIGHT_STOP: player.movingRight = false; break;
            case SHOOT:
                 int startX = player.state.x + (Constants.PLAYER_ASCII_WIDTH * 6) / 2 - 3;
                 int startY = player.state.y - 12;
                 playerProjectiles.add(new ServerProjectile(startX, startY, true, player.state.id));
                 break;
            // CONNECT/DISCONNECT/START/PAUSE handled above or ignored
        }
    }

    // Removes a client handler from the list (e.g., on disconnect)
    public synchronized void removeClient(ClientHandler clientHandler) {
        if (clientHandler == null) return;
        System.out.println("Removing client for player ID: " + clientHandler.getPlayerId());
        clientHandler.stopRunning(); // Signal handler to stop
        clients.remove(clientHandler);
        players.remove(clientHandler.getPlayerId());
        System.out.println("Removed player " + clientHandler.getPlayerId() + ". Remaining clients: " + clients.size());
        // If last player leaves, go back to WAITING state
         if (players.isEmpty() && serverState != ServerState.WAITING) {
              System.out.println("[Server] Last player disconnected. Returning to WAITING state.");
              serverState = ServerState.WAITING;
              // Optionally clear game elements immediately?
              // invaders.clear(); barriers.clear(); etc.
              gameOver = false; // Reset game over logical flag
         }
         // Check if game over needs to be triggered now (e.g., last alive player leaves)
          checkGameConditions();
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
        if (!running) return;
         running = false;
         // serverState = ServerState.WAITING; // Or some SHUTDOWN state?
         System.out.println("Stopping server...");
         try { if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close(); } catch (IOException e) { /* ignore */ }
         clientExecutor.shutdown();
          try {
              if (!clientExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                  clientExecutor.shutdownNow();
              }
          } catch (InterruptedException e) {
              clientExecutor.shutdownNow();
              Thread.currentThread().interrupt();
          }
         System.out.println("Client executor shutdown.");
         for (ClientHandler client : new ArrayList<>(clients)) { // Iterate copy
             removeClient(client);
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

    // --- Update Logic (Placeholder) ---
    private synchronized void updateServerGameState(double deltaTime) {
         if (serverState != ServerState.RUNNING) return;
         updatePlayers(deltaTime);
         updateInvaders(deltaTime);
         updateProjectiles(deltaTime);
         // updateUFO(deltaTime);
    }

     private void updatePlayers(double deltaTime) {
         for (ServerPlayer player : players.values()) {
            if (!player.state.alive) continue;

            int dx = 0;
            if (player.movingLeft) dx -= (int) (Constants.PLAYER_SPEED_PX_PER_SEC * deltaTime);
            if (player.movingRight) dx += (int) (Constants.PLAYER_SPEED_PX_PER_SEC * deltaTime);

            player.state.x += dx;

            // Clamp position
             if (player.state.x < Constants.GAME_AREA_LEFT_X) player.state.x = Constants.GAME_AREA_LEFT_X;
             int playerPixelWidth = Constants.PLAYER_ASCII_WIDTH * 6; // Approx
             if (player.state.x + playerPixelWidth > Constants.GAME_AREA_RIGHT_X) {
                 player.state.x = Constants.GAME_AREA_RIGHT_X - playerPixelWidth;
             }
        }
    }

     private void updateInvaders(double deltaTime) {
         if (serverState != ServerState.RUNNING) return;
          // Simplified float-based logic from client panel
          float dx = 0.0f;
          boolean boundaryHit = false;
          ServerInvader rightmost = null, leftmost = null;
          if (!invaders.isEmpty()) {
              rightmost = invaders.get(0); leftmost = invaders.get(0);
              for(ServerInvader inv : invaders) {
                  if(inv.x > rightmost.x) rightmost = inv;
                  if(inv.x < leftmost.x) leftmost = inv;
              }
          }

          if (invadersNeedToDrop) {
              for (ServerInvader invader : invaders) {
                  invader.y += Constants.INVADER_DROP_DISTANCE;
                  if (invader.y + (Constants.INVADER_ASCII_HEIGHT * 12) >= Constants.PLAYER_START_Y - 20) {
                      System.out.println("[Server] Game Over Condition: Invaders reached bottom.");
                       handleGameOver();
                      return;
                  }
              }
              invaderDirection *= -1;
              invaderSpeedX += Constants.INVADER_SPEED_INCREMENT;
              invadersNeedToDrop = false;
              dx = 0.0f;
          } else {
              dx = invaderSpeedX * invaderDirection * (float)deltaTime;
              ServerInvader checkRight = rightmost; ServerInvader checkLeft = leftmost;
              if(checkRight != null && invaderDirection == 1) {
                 float invW = Constants.INVADER_ASCII_WIDTH * 6f; float nextX = checkRight.x + dx;
                  if (nextX + invW > Constants.GAME_AREA_RIGHT_X) { boundaryHit = true; dx -= (nextX + invW - Constants.GAME_AREA_RIGHT_X); }
              } else if (checkLeft != null && invaderDirection == -1) {
                  float nextX = checkLeft.x + dx; if (nextX < Constants.GAME_AREA_LEFT_X) { boundaryHit = true; dx += (Constants.GAME_AREA_LEFT_X - nextX); }
              }
          }

          int intDx = Math.round(dx);
          for (ServerInvader invader : invaders) {
              float rowFactor = 1.0f - (float)invader.row * 0.03f;
              int rowDx = Math.round(dx * rowFactor);
              invader.x += rowDx;
          }

          if (boundaryHit) invadersNeedToDrop = true;

          // Invader Shooting
          double shootChance = Constants.INVADER_SHOOT_PROBABILITY_PER_SEC * deltaTime;
          for (ServerInvader invader : invaders) {
              if (random.nextDouble() < shootChance) {
                  // TODO: Check if invader is at bottom of its column?
                  int startX = invader.x + (Constants.INVADER_ASCII_WIDTH * 6) / 2 - 3;
                  int startY = invader.y + (Constants.INVADER_ASCII_HEIGHT * 12);
                  invaderProjectiles.add(new ServerProjectile(startX, startY, false, -1));
              }
          }
     }

     private void updateProjectiles(double deltaTime) {
        // Player Projectiles
         List<ServerProjectile> pToRemove = new ArrayList<>();
         for (ServerProjectile p : playerProjectiles) {
            p.y -= (int)(Constants.PLAYER_PROJECTILE_SPEED_PX_PER_SEC * deltaTime);
            if (p.y < Constants.GAME_AREA_TOP_Y) pToRemove.add(p);
         }
         playerProjectiles.removeAll(pToRemove);

         // Invader Projectiles
         List<ServerProjectile> iToRemove = new ArrayList<>();
         for (ServerProjectile p : invaderProjectiles) {
             p.y += (int)(Constants.INVADER_PROJECTILE_SPEED_PX_PER_SEC * deltaTime);
             if (p.y > Constants.WINDOW_HEIGHT) iToRemove.add(p);
         }
         invaderProjectiles.removeAll(iToRemove);
     }

    // --- Collision Detection --- (Implementing logic)
     private synchronized void checkServerCollisions() {
          if (serverState != ServerState.RUNNING) return;

          List<ServerProjectile> playerProjectilesToRemove = new ArrayList<>();
          List<ServerInvader> invadersToRemove = new ArrayList<>();
          List<ServerProjectile> invaderProjectilesToRemove = new ArrayList<>();

          // 1. Player Projectiles vs Invaders & Barriers
          for (ServerProjectile proj : playerProjectiles) {
                if (!proj.active) continue; // Skip already hit projectiles
                Rectangle projBounds = proj.getBounds();
                boolean hitSomething = false;

                // Check vs Invaders
                for (ServerInvader invader : invaders) {
                     if (invader.alive && invader.getBounds().intersects(projBounds)) {
                         invader.alive = false;
                         invadersToRemove.add(invader);
                         playerProjectilesToRemove.add(proj);
                         proj.active = false; // Mark as inactive
                         hitSomething = true;

                         // Award score to the player who shot
                         ServerPlayer shooter = players.get(proj.ownerPlayerId);
                         if (shooter != null) {
                             shooter.state.score += invader.points;
                             // TODO: Check high score (needs central tracking)
                         }
                         System.out.println("Invader hit by player " + proj.ownerPlayerId);
                         break; // Projectile hits only one thing
                     }
                }

                // Check vs Barriers (if didn't hit invader)
                if (!hitSomething) {
                     for (ServerBarrier barrier : barriers) {
                          if (barrier.isAlive() && barrier.getBounds().intersects(projBounds)) {
                              barrier.hit();
                              playerProjectilesToRemove.add(proj);
                              proj.active = false;
                              hitSomething = true;
                              System.out.println("Barrier hit by player projectile");
                              break;
                          }
                      }
                }
          }

          // 2. Invader Projectiles vs Players & Barriers
           for (ServerProjectile proj : invaderProjectiles) {
                if (!proj.active) continue;
                Rectangle projBounds = proj.getBounds();
                boolean hitSomething = false;

                 // Check vs Players
                 for (ServerPlayer player : players.values()) {
                     if (player.state.alive && player.getBounds().intersects(projBounds)) {
                          player.state.lives--;
                          invaderProjectilesToRemove.add(proj);
                          proj.active = false;
                          hitSomething = true;
                          System.out.println("Player " + player.state.id + " hit by invader projectile. Lives: " + player.state.lives);
                          if (player.state.lives <= 0) {
                              player.state.alive = false; // Player dies
                              // TODO: Handle player death logic (e.g., brief respawn delay?)
                              System.out.println("Player " + player.state.id + " DIED.");
                          }
                          // Invader proj hits only one player
                          break;
                     }
                 }

                 // Check vs Barriers (if didn't hit player)
                 if (!hitSomething) {
                     for (ServerBarrier barrier : barriers) {
                          if (barrier.isAlive() && barrier.getBounds().intersects(projBounds)) {
                              barrier.hit();
                              invaderProjectilesToRemove.add(proj);
                              proj.active = false;
                              hitSomething = true;
                              System.out.println("Barrier hit by invader projectile");
                              break;
                          }
                      }
                 }
           }

          // 3. Invaders vs Barriers
           for (ServerInvader invader : invaders) {
               if (!invader.alive) continue;
               Rectangle invaderBounds = invader.getBounds();
               for (ServerBarrier barrier : barriers) {
                    if (barrier.isAlive() && barrier.getBounds().intersects(invaderBounds)) {
                        // Invaders instantly destroy barriers they touch
                         while(barrier.isAlive()) barrier.hit();
                         System.out.println("Barrier destroyed by invader contact");
                    }
                }
           }

          // Perform removals (handle concurrent modification safely)
          playerProjectiles.removeAll(playerProjectilesToRemove);
          invaderProjectiles.removeAll(invaderProjectilesToRemove);
          invaders.removeAll(invadersToRemove); // Remove killed invaders

          // Clean up invaders list fully (in case removeAll missed due to concurrent modification on iterator? Less likely with COWList but safer)
          // invaders.removeIf(inv -> !inv.alive);
     }

     // --- Check Game Conditions ---
      private synchronized void checkGameConditions() {
          if (serverState != ServerState.RUNNING) return;

          // Level Clear
          if (invaders.isEmpty()) {
               System.out.println("[Server] Level " + currentLevel + " cleared!");
               setupOrResetLevel(currentLevel + 1); // Use combined setup method
               // State remains RUNNING
               return;
          }

          // Check if any player is still alive
          boolean anyPlayerAlive = false;
          for(ServerPlayer sp : players.values()) {
              if (sp.state.alive) { anyPlayerAlive = true; break; }
          }
          if (!anyPlayerAlive && !players.isEmpty()) {
              System.out.println("[Server] Game Over Condition: All players defeated.");
              handleGameOver();
          }
      }

      // Helper to transition to Game Over state
      private void handleGameOver() {
          if (serverState == ServerState.RUNNING) {
              System.out.println("[Server] Transitioning to GAME_OVER state.");
              serverState = ServerState.GAME_OVER;
              gameOver = true; // Set logical flag for state object
              // No timer needed, wait for START_GAME action
          }
      }

    // --- State Creation & Broadcast ---
     private GameStateUpdate createWaitingStateUpdate() {
         GameStateUpdate update = createGameStateUpdate();
         // Add minimal info, or maybe specific flags indicating waiting?
         update.invaders = new ArrayList<>();
         update.playerProjectiles = new ArrayList<>();
         update.invaderProjectiles = new ArrayList<>();
         update.barriers = new ArrayList<>();
         return update;
    }

    // ... broadcastGameState ...
} 