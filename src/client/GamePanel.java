package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import common.Constants;

public class GamePanel extends JPanel implements ActionListener {

    // --- Game State Variables ---
    private boolean inGame = true; // Flag to check if the game is running
    private boolean paused = false; // TODO: Implement pausing
    private Timer timer;          // Timer for game loop
    private long lastFrameTime;   // For delta time calculation

    // Player
    private int playerX;
    private int playerY;
    private boolean movingLeft = false;
    private boolean movingRight = false;
    private int lives = Constants.PLAYER_LIVES;
    private int score = 0;
    private int highScore = 0; // TODO: Load/Save High Score

    // Player Projectile
    private int playerProjectileX;
    private int playerProjectileY;
    private boolean playerProjectileActive = false;

    // Invaders
    private List<Invader> invaders;
    private float invaderSpeedX = Constants.INVADER_INITIAL_SPEED_PX_PER_SEC;
    private int invaderDirection = 1; // 1 for right, -1 for left
    private boolean invadersNeedToDrop = false;
    private long lastInvaderMoveTime = 0;

    // Barriers (Simplified: Using positions, health TBD)
    // private List<Point> barriers; // Or a Barrier class

    // Font for ASCII rendering
    private Font asciiFont;
    private int charWidth;
    private int charHeight;

    // --- UI Components --- (Keep for connection later)
    private JTextField portField;
    private JTextField ipField;
    private JButton connectButton;
    private JButton leftButton;
    private JButton rightButton;
    private JButton shootButton;
    // private JTextArea gameArea; // REMOVED

    // --- Inner Classes ---
    // Simple Invader class for position and type (points)
    private static class Invader {
        int x, y;
        int points; // Based on initial row
        boolean alive = true;

        Invader(int x, int y, int points) {
            this.x = x;
            this.y = y;
            this.points = points;
        }

        Rectangle getBounds() {
             // Use estimated ASCII dimensions for collision
            return new Rectangle(x, y, Constants.INVADER_ASCII_WIDTH * 6, Constants.INVADER_ASCII_HEIGHT * 12); // Approx pixel size
        }
    }

    // --- Constructor & Initialization ---
    public GamePanel() {
        initPanel();
        initUIComponents(); // Keep UI for now, but rendering is separate
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
        portField = new JTextField("5123");
        portField.setBounds(10, 10, 60, 30);
        add(portField);

        ipField = new JTextField("192.168.0.125");
        ipField.setBounds(80, 10, 120, 30);
        add(ipField);

        connectButton = new JButton("Conexión");
        connectButton.setBounds(210, 10, 100, 30);
        connectButton.setEnabled(false);
        add(connectButton);

        // Game Area is now the panel itself, remove JTextArea setup

        // Right Side Buttons
        int buttonPanelX = Constants.GAME_AREA_RIGHT_X + 5; // Move slightly outside game area
        int buttonYStart = Constants.TOP_BAR_HEIGHT + 200;

        leftButton = new JButton("<");
        leftButton.setBounds(buttonPanelX, buttonYStart, 50, 30);
        leftButton.addActionListener(this);
        leftButton.setActionCommand("left");
        leftButton.setFocusable(false);
        add(leftButton);

        rightButton = new JButton(">");
        rightButton.setBounds(buttonPanelX + 60, buttonYStart, 50, 30);
        rightButton.addActionListener(this);
        rightButton.setActionCommand("right");
        rightButton.setFocusable(false);
        add(rightButton);

        shootButton = new JButton("disparar");
        shootButton.setBounds(buttonPanelX, buttonYStart + 100, 110, 30);
        shootButton.addActionListener(this);
        shootButton.setActionCommand("shoot");
        shootButton.setFocusable(false);
        add(shootButton);
    }

    private void initGame() {
        invaders = new ArrayList<>();
        // Calculate starting X for the grid to be centered
        int gridWidth = Constants.INVADER_COLS * (Constants.INVADER_ASCII_WIDTH * charWidth + Constants.INVADER_H_SPACING) - Constants.INVADER_H_SPACING;
        int startX = Constants.GAME_AREA_LEFT_X + (Constants.GAME_AREA_WIDTH - gridWidth) / 2;

        for (int row = 0; row < Constants.INVADER_ROWS; row++) {
            for (int col = 0; col < Constants.INVADER_COLS; col++) {
                int invaderX = startX + col * (Constants.INVADER_ASCII_WIDTH * charWidth + Constants.INVADER_H_SPACING);
                int invaderY = Constants.INVADER_GRID_START_Y + row * (Constants.INVADER_ASCII_HEIGHT * charHeight + Constants.INVADER_V_SPACING);
                int points = 0;
                // Assign points based on row (as per requirements)
                 if (row == 0) points = Constants.INVADER_SMALL_POINTS;
                 else if (row <= 2) points = Constants.INVADER_MEDIUM_POINTS;
                 else points = Constants.INVADER_LARGE_POINTS;

                invaders.add(new Invader(invaderX, invaderY, points));
            }
        }

        // Player setup
        playerX = Constants.PLAYER_START_X;
        playerY = Constants.PLAYER_START_Y;

        // Start timer
        timer = new Timer(1000 / Constants.TARGET_FPS, this);
        lastFrameTime = System.nanoTime();
        timer.start();
    }

    // --- Game Loop & Updates ---

    @Override
    public void actionPerformed(ActionEvent e) {
        long currentTime = System.nanoTime();
        double deltaTime = (currentTime - lastFrameTime) / 1_000_000_000.0; // Delta time in seconds
        lastFrameTime = currentTime;

        if (inGame && !paused) {
            updateGame(deltaTime);
            checkCollisions();
            repaint(); // Request redraw
        }

        // Handle button clicks (separate from game loop updates)
        String command = e.getActionCommand();
        if (command != null) {
            handleButtonAction(command);
        }
    }

    private void updateGame(double deltaTime) {
        updatePlayer(deltaTime);
        updatePlayerProjectile(deltaTime);
        updateInvaders(deltaTime);
        // TODO: updateInvaderProjectiles(deltaTime);
        // TODO: updateUFO(deltaTime);
    }

    private void updatePlayer(double deltaTime) {
        int dx = 0;
        if (movingLeft) {
            dx -= (int) (Constants.PLAYER_SPEED_PX_PER_SEC * deltaTime);
        }
        if (movingRight) {
            dx += (int) (Constants.PLAYER_SPEED_PX_PER_SEC * deltaTime);
        }

        playerX += dx;

        // Clamp player position to screen bounds
        if (playerX < Constants.GAME_AREA_LEFT_X) {
            playerX = Constants.GAME_AREA_LEFT_X;
        }
        int playerPixelWidth = Constants.PLAYER_ASCII_WIDTH * charWidth;
        if (playerX + playerPixelWidth > Constants.GAME_AREA_RIGHT_X) {
            playerX = Constants.GAME_AREA_RIGHT_X - playerPixelWidth;
        }
    }

    private void updatePlayerProjectile(double deltaTime) {
        if (playerProjectileActive) {
            int dy = (int) (Constants.PLAYER_PROJECTILE_SPEED_PX_PER_SEC * deltaTime);
            playerProjectileY -= dy;

            // Check if projectile went off-screen
            if (playerProjectileY < Constants.GAME_AREA_TOP_Y) {
                playerProjectileActive = false;
            }
        }
    }

     private void updateInvaders(double deltaTime) {
        // Calculate how much time has passed since last move for speed scaling
        long now = System.nanoTime();
        double timeSinceLastMove = (now - lastInvaderMoveTime) / 1_000_000_000.0;

        // Only move invaders periodically based on their speed
        // This creates the step-like movement
         double moveInterval = 1.0 / (invaderSpeedX / (Constants.INVADER_ASCII_WIDTH * charWidth)); // Time to move one invader width
         // Simplified: move every frame for now, refine later if jerky
         // if (timeSinceLastMove >= moveInterval) {

            int dx = (int) (invaderSpeedX * invaderDirection * deltaTime);
            boolean boundaryHit = false;

            if (invadersNeedToDrop) {
                 // Move all invaders down
                 for (Invader invader : invaders) {
                     invader.y += Constants.INVADER_DROP_DISTANCE;
                     // Check if invaders reached player level (Game Over condition)
                     if (invader.y + (Constants.INVADER_ASCII_HEIGHT * charHeight) >= playerY - 20) {
                         inGame = false; // Trigger Game Over
                     }
                 }
                 invaderDirection *= -1; // Change direction
                 invaderSpeedX += Constants.INVADER_SPEED_INCREMENT; // Increase speed
                 invadersNeedToDrop = false;
                 dx = 0; // Don't move horizontally on the same frame as dropping
            } else {
                // Check boundaries before moving horizontally
                for (Invader invader : invaders) {
                    int invaderPixelWidth = Constants.INVADER_ASCII_WIDTH * charWidth;
                    if (invaderDirection == 1 && invader.x + invaderPixelWidth + dx > Constants.GAME_AREA_RIGHT_X) {
                        boundaryHit = true;
                        dx = Constants.GAME_AREA_RIGHT_X - (invader.x + invaderPixelWidth); // Adjust dx to touch boundary
                        break;
                    } else if (invaderDirection == -1 && invader.x + dx < Constants.GAME_AREA_LEFT_X) {
                        boundaryHit = true;
                        dx = Constants.GAME_AREA_LEFT_X - invader.x; // Adjust dx to touch boundary
                        break;
                    }
                }
            }

            // Move all invaders horizontally
            for (Invader invader : invaders) {
                invader.x += dx;
            }

             if (boundaryHit) {
                 invadersNeedToDrop = true;
             }

           // lastInvaderMoveTime = now;
        // }
        // TODO: Invader shooting logic
    }


    private void checkCollisions() {
        if (!playerProjectileActive) {
            return;
        }

        Rectangle projectileBounds = new Rectangle(playerProjectileX, playerProjectileY, charWidth, charHeight);

        Iterator<Invader> iterator = invaders.iterator();
        while (iterator.hasNext()) {
            Invader invader = iterator.next();
            if (invader.getBounds().intersects(projectileBounds)) {
                playerProjectileActive = false; // Projectile disappears
                invader.alive = false; // Mark invader as not alive
                score += invader.points; // Add score
                if(score > highScore) highScore = score; // Update high score

                iterator.remove(); // Remove invader from list
                break; // Only one collision per projectile frame
            }
        }

        // TODO: Check invader projectiles vs player
        // TODO: Check player/invader projectiles vs barriers
        // TODO: Check invaders vs barriers
    }

    // --- Rendering --- (Directly on JPanel)

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Set rendering hints for potentially better quality
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // Draw Background (already done by setBackground)

        if (inGame) {
            drawGameObjects(g2d);
        } else {
            drawGameOver(g2d);
        }

        // Draw UI on top
        drawUI(g2d);

        Toolkit.getDefaultToolkit().sync();
    }

    private void drawGameObjects(Graphics2D g) {
        g.setFont(asciiFont);
        g.setColor(Constants.DEFAULT_TEXT_COLOR); // Default color for ASCII

        // Draw Player
        drawAsciiArt(g, Constants.PLAYER_SPRITE, playerX, playerY);

        // Draw Player Projectile
        if (playerProjectileActive) {
            g.setColor(Constants.PLAYER_PROJECTILE_COLOR);
            g.drawString(Constants.PLAYER_PROJECTILE_SPRITE, playerProjectileX, playerProjectileY);
        }

        // Draw Invaders
        g.setColor(Constants.DEFAULT_TEXT_COLOR);
        for (Invader invader : invaders) {
            drawAsciiArt(g, Constants.INVADER_SPRITE, invader.x, invader.y);
        }

        // TODO: Draw Barriers
        // TODO: Draw UFO
        // TODO: Draw Invader Projectiles
    }

    // Helper to draw multi-line ASCII strings
    private void drawAsciiArt(Graphics g, String[] art, int x, int y) {
        for (int i = 0; i < art.length; i++) {
            g.drawString(art[i], x, y + i * charHeight);
        }
    }

    private void drawUI(Graphics2D g) {
        // Draw the top bar background area (optional, if needed)
        // g.setColor(Color.DARK_GRAY); // Example
        // g.fillRect(0, 0, Constants.WINDOW_WIDTH, Constants.TOP_BAR_HEIGHT);

        g.setColor(Constants.DEFAULT_TEXT_COLOR);
        g.setFont(new Font("Arial", Font.BOLD, 18)); // Use a clearer font for UI text

        g.drawString("Score: " + score, 50, 35);
        g.drawString("Hi-Score: " + highScore, 600, 35);
        g.drawString("Lives: " + lives, 350, 35);
        // TODO: Draw life icons (small player sprites?)
        // TODO: Draw Level indicator
    }

    private void drawGameOver(Graphics2D g) {
        String msg = "Game Over";
        Font font = new Font("Arial", Font.BOLD, 48);
        FontMetrics fm = getFontMetrics(font);

        g.setColor(Color.RED);
        g.setFont(font);
        g.drawString(msg, (Constants.WINDOW_WIDTH - fm.stringWidth(msg)) / 2,
                     Constants.WINDOW_HEIGHT / 2 - 20);

        String scoreMsg = "Final Score: " + score;
        Font scoreFont = new Font("Arial", Font.PLAIN, 24);
        fm = getFontMetrics(scoreFont);
        g.setColor(Color.WHITE);
        g.setFont(scoreFont);
        g.drawString(scoreMsg, (Constants.WINDOW_WIDTH - fm.stringWidth(scoreMsg)) / 2,
                     Constants.WINDOW_HEIGHT / 2 + 30);
        // TODO: Add instruction to restart?
    }

    // --- Input Handling ---

    private void handleButtonAction(String command) {
        switch (command) {
            case "left":
                // Simulate key press for continuous movement if held
                movingLeft = true;
                movingRight = false;
                break;
            case "right":
                movingRight = true;
                movingLeft = false;
                break;
            case "shoot":
                tryShoot();
                break;
        }
        requestFocusInWindow(); // Keep focus on the panel
    }

    private void tryShoot() {
        if (inGame && !playerProjectileActive) {
            playerProjectileActive = true;
            // Calculate starting position (center top of player sprite)
            playerProjectileX = playerX + (Constants.PLAYER_ASCII_WIDTH * charWidth) / 2 - charWidth / 2;
            playerProjectileY = playerY - charHeight; // Start just above the player
            // TODO: Play shooting sound
        }
    }

    private class TAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int key = e.getKeyCode();

            if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                movingLeft = true;
            }

            if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                movingRight = true;
            }

            if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                tryShoot();
            }

             if (key == KeyEvent.VK_P) { // Example: Pause toggle
                 paused = !paused;
             }

             if (key == KeyEvent.VK_ESCAPE) { // Example: Exit
                  System.exit(0);
             }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            int key = e.getKeyCode();

            if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                movingLeft = false;
            }

            if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                movingRight = false;
            }
        }
    }
}
