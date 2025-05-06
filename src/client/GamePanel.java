package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map; // For gameState.players
import java.util.concurrent.ConcurrentHashMap; // To store local copy of players
import javax.swing.JOptionPane;

import common.Constants;
import common.*; // Import all common classes

public class GamePanel extends JPanel implements ActionListener {

    // --- Networking Fields ---
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean isConnected = false;
    private int myPlayerId = -1; // Assigned by server
    private ServerListener serverListener;
    private Thread serverListenerThread;
    private volatile GameStateUpdate latestGameState;

    // Local representation of players based on last GameStateUpdate
    private Map<Integer, PlayerState> currentPlayers = new ConcurrentHashMap<>();

    // --- Game State Variables ---
    private boolean inGame = true; // Now more accurately means "connected and server says game is on"
    private boolean paused = false;
    private Timer timer;
    private long lastFrameTime;
    private int currentLevel = 1; // Keep local copy for display
    private int lives = Constants.PLAYER_LIVES; // Keep local copy for display
    private int score = 0; // Keep local copy for display
    private int highScore = 0; // Local high score
    private boolean movingLeft = false; // Still used for sending actions
    private boolean movingRight = false;


    // Font for ASCII rendering
    private Font asciiFont;
    private int charWidth;
    private int charHeight;

    // --- UI Components ---
    private JTextField portField;
    private JTextField ipField;
    private JButton connectButton;
    private JButton leftButton;
    private JButton rightButton;
    private JButton shootButton;
    private JButton startGameButton;
    private JButton pauseButton;

    // --- Inner Classes ---
    // Projectile class (can be used for player and invaders)
    private static class Projectile {
        int x, y;
        boolean active = true;
        // Optional: type (PLAYER, INVADER)

        Projectile(int x, int y) {
            this.x = x;
            this.y = y;
        }

        Rectangle getBounds(int charWidth, int charHeight) {
            // Simple bounds for single character projectile
            return new Rectangle(x, y - charHeight, charWidth, charHeight); // y adjusted because drawString draws at baseline
        }
    }

    // Simple Invader class
    private static class Invader {
        int x, y;
        int points; // Based on initial row
        int row;    // Original row index
        boolean alive = true;

        Invader(int x, int y, int points, int row) {
            this.x = x;
            this.y = y;
            this.points = points;
            this.row = row;
        }

        Rectangle getBounds(int charWidth, int charHeight) {
            // Use estimated ASCII dimensions for collision
            // Adjust coords slightly for better feel with ASCII
            int approxWidth = Constants.INVADER_ASCII_WIDTH * charWidth / 2; // Rough pixel width
            int approxHeight = Constants.INVADER_ASCII_HEIGHT * charHeight;
            return new Rectangle(x + approxWidth / 4, y, approxWidth, approxHeight);
        }
    }

    // Simple Barrier class
    private static class Barrier {
        int x, y;
        int health;
        String[] currentSprite; // Changes with health

        Barrier(int x, int y) {
            this.x = x;
            this.y = y;
            this.health = Constants.BARRIER_INITIAL_HEALTH;
            updateSprite();
        }

        void hit() {
            if (health > 0) {
                health--;
                updateSprite();
            }
        }

        boolean isAlive() {
            return health > 0;
        }

        // TODO: Implement different sprites based on health level
        private void updateSprite() {
            if (health <= 0) {
                currentSprite = null; // Or an empty array
            } else {
                // For now, always use full health sprite
                currentSprite = Constants.BARRIER_SPRITE_LVL_0;
            }
        }

       
    }

    // --- New Inner Class for Server Communication ---
    private class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                Object initialMsg = in.readObject();
                if (initialMsg instanceof Integer) {
                    myPlayerId = (Integer) initialMsg;
                    System.out.println("[Client] Received Player ID: " + myPlayerId);
                    SwingUtilities.invokeLater(GamePanel.this::requestFocusInWindow);
                    isConnected = true;
                } else {
                    System.err.println("[Client] Expected Integer player ID but received: " + initialMsg);
                    disconnect();
                    return;
                }

                while (isConnected && !Thread.currentThread().isInterrupted()) {
                    Object receivedObject = in.readObject();
                    if (receivedObject instanceof GameStateUpdate) {
                        latestGameState = (GameStateUpdate) receivedObject;
                        // Update local state copies
                        if(latestGameState.players != null) {
                            currentPlayers.clear();
                            currentPlayers.putAll(latestGameState.players);
                            PlayerState myState = currentPlayers.get(myPlayerId);
                            if(myState != null) { score = myState.score; lives = myState.lives; }
                        }
                        if (latestGameState != null) {
                            currentLevel = latestGameState.currentLevel;
                            inGame = !latestGameState.isGameOver;
                        }
                    } else {
                         System.err.println("[Client] Received unexpected object type: " + receivedObject.getClass().getName());
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                 if (isConnected) {
                    System.err.println("[Client] Disconnected from server: " + e.getMessage());
                    disconnect();
                 }
            } finally {
                 System.out.println("[Client] Server listener thread finished.");
                 disconnect();
            }
        }
    }

    // --- Constructor & Initialization ---
    public GamePanel() {
        // Init lists if they were still used locally (they aren't needed for server-driven state)

        // Setup panel and initial game state
        initPanel();
        initUIComponents();
        initGame();
    }

    private void initPanel() {
        setPreferredSize(Constants.WINDOW_SIZE);
        setBackground(Constants.BACKGROUND_COLOR);
        setFocusable(true);
        setLayout(null); // Still using null layout for the top/side UI

        // Setup font for ASCII rendering
        asciiFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        // Estimate character size (might need adjustment)
        FontMetrics fm = getFontMetrics(asciiFont);
        charWidth = fm.charWidth('M'); // Average width
        charHeight = fm.getHeight();

        addKeyListener(new TAdapter());
    }

    // Keep UI components definitions but they don't control game area now
    private void initUIComponents() {
        // Top Bar (Connection)
        connectButton = new JButton("Conexión");
        connectButton.setBounds(10, 10, 100, 30);
        connectButton.addActionListener(_ -> connectToServer());
        add(connectButton);

        portField = new JTextField("5123");
        portField.setBounds(120, 10, 50, 30);
        add(portField);

        ipField = new JTextField("127.0.0.1");
        ipField.setBounds(180, 10, 100, 30);
        add(ipField);

        // Start Game Button
        startGameButton = new JButton("Start Game");
        startGameButton.setBounds(300, 10, 120, 30);
        startGameButton.addActionListener(_ -> sendAction(new PlayerAction(PlayerAction.ActionType.START_GAME, myPlayerId)));
        startGameButton.setEnabled(false);
        add(startGameButton);

        // Pause Button
        pauseButton = new JButton("Pause");
        pauseButton.setBounds(430, 10, 100, 30);
        pauseButton.addActionListener(_ -> sendAction(new PlayerAction(PlayerAction.ActionType.TOGGLE_PAUSE, myPlayerId)));
        pauseButton.setEnabled(false);

        // Game Area is now the panel itself, remove JTextArea setup

        resetConnectionUI(); // Call helper to set initial state
    }

    private void initGame() {
        this.myPlayerId = -1;
        this.isConnected = false;
        this.latestGameState = null;
        this.currentPlayers.clear();
        this.movingLeft = false;
        this.movingRight = false;
        resetConnectionUI(); // Set initial UI state

        // Start the rendering timer
        if (timer == null) {
            timer = new Timer(1000 / Constants.TARGET_FPS, this);
            timer.start();
            lastFrameTime = System.nanoTime();
        } else if (!timer.isRunning()) {
            timer.start();
            lastFrameTime = System.nanoTime();
        }
        System.out.println("GamePanel initialized for connection.");
    }

    // --- Networking Logic ---
    private void connectToServer() {
        if (isConnected) return;

        String host = ipField.getText().trim();
        String portStr = portField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portStr);
             if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid port number (1-65535).", "Connection Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Server IP cannot be empty.", "Connection Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        System.out.println("Attempting to connect to " + host + ":" + port);
        connectButton.setEnabled(false);
        ipField.setEnabled(false);
        portField.setEnabled(false);

        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                System.out.println("Socket and streams created. Starting listener...");

                serverListener = new ServerListener();
                serverListenerThread = new Thread(serverListener);
                serverListenerThread.start();
                // ServerListener enables game buttons upon receiving player ID

            } catch (UnknownHostException e) {
                handleConnectionError("Could not find server: " + host, e);
            } catch (IOException e) {
                 // Check for specific connection refused error
                 String msg = (e.getMessage().contains("Connection refused"))
                           ? "Connection refused. Is server running?"
                           : "Could not connect: " + e.getMessage();
                handleConnectionError(msg, e);
            }
        }).start();
    }

    private void handleConnectionError(String message, Exception e) {
         System.err.println("Connection failed: " + e.getMessage());
         SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(GamePanel.this, message, "Connection Error", JOptionPane.ERROR_MESSAGE);
            resetConnectionUI(); // Re-enable connection fields
         });
         disconnect(); // Ensure cleanup happens
    }

    // Send action to server
    private synchronized void sendAction(PlayerAction action) {
        if (!isConnected || out == null) return;
        try {
            out.writeObject(action);
            out.flush();
        } catch (IOException e) {
            System.err.println("Error sending action ("+action.type+"): " + e.getMessage());
            disconnect();
        }
    }

    // Disconnect logic
    private void disconnect() {
        if (!isConnected && socket == null) return;
        boolean wasConnected = isConnected;
        isConnected = false;
        System.out.println("Disconnecting client (ID: "+myPlayerId+")...");

        int idToDisconnect = myPlayerId;
        myPlayerId = -1;

        if (wasConnected && out != null && idToDisconnect != -1) {
            try {
                PlayerAction disconnectAction = new PlayerAction(PlayerAction.ActionType.DISCONNECT, idToDisconnect);
                out.writeObject(disconnectAction);
                out.flush();
                 System.out.println("Sent DISCONNECT action to server.");
            } catch (Exception e) {
                 System.err.println("Error sending disconnect message: " + e.getMessage());
            }
        }

        if (serverListenerThread != null) {
            serverListenerThread.interrupt();
             try { serverListenerThread.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // Wait briefly
        }

        // Close resources quietly
        try { if (in != null) in.close(); } catch (IOException e) {} finally { in = null; }
        try { if (out != null) out.close(); } catch (IOException e) {} finally { out = null; }
        try { if (socket != null) socket.close(); } catch (IOException e) {} finally { socket = null; }

        serverListenerThread = null;
        serverListener = null;
        latestGameState = null;
        currentPlayers.clear();

        System.out.println("Client disconnected. Resetting UI.");
        SwingUtilities.invokeLater(this::resetConnectionUI);
    }

    // Helper to reset UI
    private void resetConnectionUI() {
        if (ipField != null) ipField.setEnabled(true);
        if (portField != null) portField.setEnabled(true);
        if (connectButton != null) connectButton.setEnabled(true);
        if (leftButton != null) leftButton.setEnabled(false);
        if (rightButton != null) rightButton.setEnabled(false);
        if (shootButton != null) shootButton.setEnabled(false);
        if (startGameButton != null) startGameButton.setEnabled(false);
        if (pauseButton != null) pauseButton.setEnabled(false);
        System.out.println("Connection UI Reset.");
    }

     // Called from GameWindow when closing
     public void notifyClosing() {
          disconnect();
     }

    // --- Game Loop & Updates (Simplified) ---
    @Override
    public void actionPerformed(ActionEvent e) {
        // Check if the action came from a button OR the timer
        String command = e.getActionCommand();
        if (command != null) {
            // Button press - handled by handleButtonAction
            handleButtonAction(command);
        } else {
            // Timer tick - just repaint if not paused locally
             if (!paused) {
                 // Server drives state updates, local updateGame is minimal
             }
            repaint();
        }
    }

    // --- Rendering (Uses Server State) ---
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        setupRenderingHints(g2d);

        // Update button states based on the very latest info
        updateButtonStates();

        // Draw based on connection status and latest server state
        if (isConnected && latestGameState != null) {
            if (!latestGameState.isGameOver) {
                drawGameObjectsFromServer(g2d);
                if (latestGameState.isPaused) {
                     drawPausedOverlay(g2d);
                }
            } else {
                drawGameOver(g2d);
            }
        } else {
            // Draw screen indicating disconnected status or waiting for connection/state
            drawWaitingScreen(g2d);
        }

        // Always draw UI overlay (Score, Lives, Level etc.)
        drawUI(g2d);

        Toolkit.getDefaultToolkit().sync();
    }

    private void setupRenderingHints(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    }

    private void drawWaitingScreen(Graphics2D g) {
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.setColor(Color.WHITE);
        String msg = isConnected ? "Connected. Waiting for server state..." : "Disconnected";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2 - 20);
        if (!isConnected) {
             String help = "Enter Server IP/Port and click Conexión";
             g.setFont(new Font("Arial", Font.PLAIN, 14));
             fm = g.getFontMetrics();
            g.drawString(help, (getWidth() - fm.stringWidth(help)) / 2, getHeight() / 2 + 20);
        }
    }

    // Draw game elements based on the latest GameStateUpdate from the server
    private void drawGameObjectsFromServer(Graphics2D g) {
        if (latestGameState == null) return; // Safety check

        g.setFont(asciiFont);
        g.setColor(Constants.DEFAULT_TEXT_COLOR);

        // Draw Players
        if (latestGameState.players != null) {
            for (PlayerState player : latestGameState.players.values()) {
                if (player.alive) {
                    g.setColor((player.id == myPlayerId) ? Color.CYAN : Constants.DEFAULT_TEXT_COLOR);
                    drawAsciiArt(g, Constants.PLAYER_SPRITE, player.x, player.y);
                }
            }
        }

        // Draw Invaders
        g.setColor(Constants.DEFAULT_TEXT_COLOR);
        if (latestGameState.invaders != null) {
            for (GameStateUpdate.SimplePosition invaderPos : latestGameState.invaders) {
                drawAsciiArt(g, Constants.INVADER_SPRITE, invaderPos.x, invaderPos.y);
            }
        }

        // Draw Barriers
        g.setColor(Constants.BARRIER_COLOR);
        if (latestGameState.barriers != null) {
            for (GameStateUpdate.SimpleBarrierState barrierState : latestGameState.barriers) {
                if (barrierState.health > 0) {
                    // TODO: Select barrier sprite based on health
                    drawAsciiArt(g, Constants.BARRIER_SPRITE_LVL_0, barrierState.x, barrierState.y);
                }
            }
        }

        // Draw Player Projectiles
        g.setColor(Constants.PLAYER_PROJECTILE_COLOR);
        if (latestGameState.playerProjectiles != null) {
            for (GameStateUpdate.SimplePosition projPos : latestGameState.playerProjectiles) {
                g.drawString(Constants.PLAYER_PROJECTILE_SPRITE, projPos.x, projPos.y);
            }
        }

        // Draw Invader Projectiles
        g.setColor(Constants.INVADER_PROJECTILE_COLOR);
        if (latestGameState.invaderProjectiles != null) {
            for (GameStateUpdate.SimplePosition projPos : latestGameState.invaderProjectiles) {
                g.drawString(Constants.INVADER_PROJECTILE_SPRITE, projPos.x, projPos.y);
            }
        }

        // TODO: Draw UFO from server state if/when implemented
    }

     private void drawAsciiArt(Graphics g, String[] art, int x, int y) {
        if (art == null) return; // Avoid NPE if sprite is null (e.g., destroyed barrier)
        for (int i = 0; i < art.length; i++) {
            g.drawString(art[i], x, y + i * charHeight);
        }
    }

    private void drawUI(Graphics2D g) {
        // Use local copies updated by listener for smoother display
        int displayScore = score;
        int displayLives = lives;
        int displayLevel = currentLevel;
        int displayHiScore = highScore;

        g.setColor(Constants.DEFAULT_TEXT_COLOR);
        Font uiFont = new Font("Arial", Font.BOLD, 18);
        g.setFont(uiFont);
        FontMetrics fm = g.getFontMetrics(uiFont);
        int textY = 65;

        g.drawString("Score: " + displayScore, Constants.SIDE_MARGIN, textY);
        int scoreWidth = fm.stringWidth("Score: " + displayScore);
        int livesX = Constants.SIDE_MARGIN + scoreWidth + 50;
        g.drawString("Lives: " + displayLives, livesX, textY);
        String levelText = "Level: " + displayLevel;
        int levelTextWidth = fm.stringWidth(levelText);
        g.drawString(levelText, (Constants.WINDOW_WIDTH - levelTextWidth) / 2, textY);
        String hiScoreText = "Hi-Score: " + displayHiScore;
        int hiScoreWidth = fm.stringWidth(hiScoreText);
        g.drawString(hiScoreText, Constants.WINDOW_WIDTH - Constants.SIDE_MARGIN - hiScoreWidth, textY);
    }

     private void drawGameOver(Graphics2D g) {
         int finalScore = score; // Use local score copy
         String msg = "Game Over";
         Font font = new Font("Arial", Font.BOLD, 48);
         FontMetrics fm = getFontMetrics(font);
         g.setColor(Color.RED);
         g.setFont(font);
         g.drawString(msg, (Constants.WINDOW_WIDTH - fm.stringWidth(msg)) / 2, Constants.WINDOW_HEIGHT / 2 - 40);

         String scoreMsg = "Final Score: " + finalScore;
         Font scoreFont = new Font("Arial", Font.PLAIN, 24);
         fm = getFontMetrics(scoreFont);
         g.setColor(Color.WHITE);
         g.setFont(scoreFont);
         g.drawString(scoreMsg, (Constants.WINDOW_WIDTH - fm.stringWidth(scoreMsg)) / 2, Constants.WINDOW_HEIGHT / 2 + 10);
         // No restart message needed, server controls game state
    }

    // --- Input Handling (Sends Actions) ---
    private void handleButtonAction(String command) {
        if (!isConnected || myPlayerId == -1) return; // Need to be connected and have an ID

        switch (command) {
            case "left":
                if (!movingLeft) { // Send START only once per continuous press
                    sendAction(new PlayerAction(PlayerAction.ActionType.MOVE_LEFT_START, myPlayerId));
                    movingLeft = true;
                    movingRight = false; // Stop moving right if we press left
                    if(movingRight) sendAction(new PlayerAction(PlayerAction.ActionType.MOVE_RIGHT_STOP, myPlayerId));
                }
                break;
            case "right":
                if (!movingRight) { // Send START only once
                    sendAction(new PlayerAction(PlayerAction.ActionType.MOVE_RIGHT_START, myPlayerId));
                    movingRight = true;
                    movingLeft = false; // Stop moving left
                    if(movingLeft) sendAction(new PlayerAction(PlayerAction.ActionType.MOVE_LEFT_STOP, myPlayerId));
                }
                break;
            case "shoot":
                 sendAction(new PlayerAction(PlayerAction.ActionType.SHOOT, myPlayerId));
                 break;
         }
         requestFocusInWindow(); // Keep focus for keyboard input
    }

    // Renamed from tryShoot to make purpose clear
    private void sendShootAction() {
        if (!isConnected || myPlayerId == -1) return;
        sendAction(new PlayerAction(PlayerAction.ActionType.SHOOT, myPlayerId));
    }

    private class TAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            // Allow Esc even if not fully connected (to allow disconnect attempt)
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                 System.out.println("Escape pressed, disconnecting...");
                 disconnect();
                 return;
            }

            if (!isConnected || myPlayerId == -1) return; // Ignore other input if not ready

            // Game Over / Paused logic (client only pauses rendering)
            if (!inGame) return; // Ignore game input if server says game over

            int key = e.getKeyCode();

            if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                 if (!movingLeft) { // Send START only on first press
                     sendAction(new PlayerAction(PlayerAction.ActionType.MOVE_LEFT_START, myPlayerId));
                     movingLeft = true;
                     // Stop opposite direction if needed
                     if(movingRight) sendAction(new PlayerAction(PlayerAction.ActionType.MOVE_RIGHT_STOP, myPlayerId));
                     movingRight = false;
                 }
            }
            else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                 if (!movingRight) { // Send START only on first press
                     sendAction(new PlayerAction(PlayerAction.ActionType.MOVE_RIGHT_START, myPlayerId));
                     movingRight = true;
                      // Stop opposite direction if needed
                     if(movingLeft) sendAction(new PlayerAction(PlayerAction.ActionType.MOVE_LEFT_STOP, myPlayerId));
                     movingLeft = false;
                 }
            }
            else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                 sendShootAction();
            }
            else if (key == KeyEvent.VK_P) {
                 paused = !paused;
                 System.out.println(paused ? "Local render paused" : "Local render resumed");
                 // Note: This pause is purely client-side rendering pause
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
             if (!isConnected || myPlayerId == -1) return;

            int key = e.getKeyCode();

            if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                 if (movingLeft) { // Send STOP only if we were moving left
                     sendAction(new PlayerAction(PlayerAction.ActionType.MOVE_LEFT_STOP, myPlayerId));
                     movingLeft = false;
                 }
            }
            else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                 if (movingRight) { // Send STOP only if we were moving right
                     sendAction(new PlayerAction(PlayerAction.ActionType.MOVE_RIGHT_STOP, myPlayerId));
                     movingRight = false;
                 }
            }
        }
    }

    // Refined button state logic
     private void updateButtonStates() {
         // Determine server status from latest GameStateUpdate
         boolean serverSentState = isConnected && latestGameState != null;
         boolean serverSaysPaused = false;
         boolean serverSaysGameOver = false;
         boolean gameElementsPresent = false; // Are invaders/projectiles active?

         if (serverSentState) {
             serverSaysPaused = latestGameState.isPaused;
             serverSaysGameOver = latestGameState.isGameOver;
             // Check if core game elements exist (implies game is running or paused, not just lobby)
             gameElementsPresent = (latestGameState.invaders != null && !latestGameState.invaders.isEmpty()) ||
                                   (latestGameState.playerProjectiles != null && !latestGameState.playerProjectiles.isEmpty()) ||
                                   (latestGameState.invaderProjectiles != null && !latestGameState.invaderProjectiles.isEmpty());
         }

        // --- Derived States --- 
         boolean serverIsRunning = serverSentState && !serverSaysPaused && !serverSaysGameOver && gameElementsPresent;
         boolean serverIsInLobby = serverSentState && !serverSaysPaused && !serverSaysGameOver && !gameElementsPresent;
         // serverIsPaused = serverSaysPaused (defined above)
         // serverIsGameOver = serverSaysGameOver (defined above)

        // --- Enable/Disable Logic --- 

         // Game control buttons only active when server is RUNNING
         if (leftButton != null) leftButton.setEnabled(serverIsRunning);
         if (rightButton != null) rightButton.setEnabled(serverIsRunning);
         if (shootButton != null) shootButton.setEnabled(serverIsRunning);

         // Start/Restart button enabled if connected AND (in lobby OR game is over)
         // Also handle the brief moment before first state arrives
         boolean enableStartButton = isConnected && (!serverSentState || serverIsInLobby || serverSaysGameOver);
         if (startGameButton != null) {
              startGameButton.setEnabled(enableStartButton);
              startGameButton.setText(serverSaysGameOver ? "Restart Game" : "Start Game");
         }

         // Pause/Resume button enabled only if game is RUNNING or PAUSED
         boolean enablePauseButton = serverSentState && (serverIsRunning || serverSaysPaused);
         if (pauseButton != null) {
              pauseButton.setEnabled(enablePauseButton);
              pauseButton.setText(serverSaysPaused ? "Resume" : "Pause");
         }

         // Connection controls disabled when connected
         if (connectButton != null) connectButton.setEnabled(!isConnected);
         if (ipField != null) ipField.setEnabled(!isConnected);
         if (portField != null) portField.setEnabled(!isConnected);

         // --- Logging for Debugging --- 
         /* // Uncomment for debugging button states
          System.out.printf("UpdateButtons: isConnected=%b, serverSentState=%b, isRunning=%b, isPaused=%b, isGameOver=%b, inLobby=%b -> enableStart=%b, enablePause=%b, enableControls=%b\n",
                  isConnected, serverSentState, serverIsRunning, serverSaysPaused, serverSaysGameOver, serverIsInLobby,
                  startGameButton != null && startGameButton.isEnabled(),
                  pauseButton != null && pauseButton.isEnabled(),
                  leftButton != null && leftButton.isEnabled());
         */
     }

    private void drawPausedOverlay(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 150)); // Semi-transparent black overlay
        g.fillRect(0, Constants.TOP_BAR_HEIGHT, getWidth(), getHeight() - Constants.TOP_BAR_HEIGHT);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        g.setColor(Color.YELLOW);
        String msg = "PAUSED";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
    }
}
