package client;

import javax.swing.*;
import common.Constants;

public class GameWindow extends JFrame {

    private GamePanel gamePanel;

    public GameWindow() {
        setTitle(Constants.WINDOW_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        gamePanel = new GamePanel();
        add(gamePanel);

        pack(); // Sizes the window based on the preferred sizes of its subcomponents
        setLocationRelativeTo(null); // Center the window on the screen
        setVisible(true);

        // Ensure the panel can receive focus for key events
        gamePanel.requestFocusInWindow();
    }

    // Main method to launch the game
    public static void main(String[] args) {
        // Run the GUI creation on the Event Dispatch Thread
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new GameWindow();
            }
        });
    }
} 