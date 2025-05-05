package common;

import java.awt.Color;
import java.awt.Dimension;

public class Constants {

    // Window Settings
    public static final String WINDOW_TITLE = "Space Invader";
    public static final int WINDOW_WIDTH = 800;
    public static final int WINDOW_HEIGHT = 600;
    public static final Dimension WINDOW_SIZE = new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT);
    public static final int TARGET_FPS = 60;
    public static final long OPTIMAL_TIME = 1000000000 / TARGET_FPS; // Nanoseconds per frame

    // UI Layout
    public static final int TOP_BAR_HEIGHT = 50;
    public static final int SIDE_MARGIN = 25;
    public static final int BOTTOM_MARGIN = 25;
    public static final int GAME_AREA_TOP_Y = TOP_BAR_HEIGHT;
    public static final int GAME_AREA_BOTTOM_Y = WINDOW_HEIGHT - BOTTOM_MARGIN;
    public static final int GAME_AREA_LEFT_X = SIDE_MARGIN;
    public static final int GAME_AREA_RIGHT_X = WINDOW_WIDTH - SIDE_MARGIN;
    public static final int GAME_AREA_WIDTH = GAME_AREA_RIGHT_X - GAME_AREA_LEFT_X;
    public static final int GAME_AREA_HEIGHT = GAME_AREA_BOTTOM_Y - GAME_AREA_TOP_Y;

    // Colors
    public static final Color BACKGROUND_COLOR = Color.BLACK;
    public static final Color DEFAULT_TEXT_COLOR = Color.WHITE;
    public static final Color BARRIER_COLOR = Color.GREEN; // Example
    public static final Color PLAYER_PROJECTILE_COLOR = Color.WHITE;
    public static final Color INVADER_PROJECTILE_COLOR = Color.RED; // Example

    // Player Settings
    public static final int PLAYER_WIDTH = 40;
    public static final int PLAYER_HEIGHT = 20;
    public static final int PLAYER_START_X = WINDOW_WIDTH / 2 - PLAYER_WIDTH / 2;
    public static final int PLAYER_START_Y = 550;
    public static final int PLAYER_SPEED_PX_PER_SEC = 200;
    public static final int PLAYER_LIVES = 3;

    // Player Projectile Settings
    public static final int PLAYER_PROJECTILE_WIDTH = 5;
    public static final int PLAYER_PROJECTILE_HEIGHT = 15;
    public static final int PLAYER_PROJECTILE_SPEED_PX_PER_SEC = 300;

    // Barrier Settings
    public static final int NUM_BARRIERS = 4;
    public static final int BARRIER_WIDTH = 80;
    public static final int BARRIER_HEIGHT = 40;
    public static final int BARRIER_Y = 450;
    public static final int BARRIER_INITIAL_HEALTH = 4;
    // Calculated positions for barriers (example distribution)
    public static final int BARRIER_1_X = 125;
    public static final int BARRIER_2_X = 275;
    public static final int BARRIER_3_X = 425;
    public static final int BARRIER_4_X = 575;

    // Invader Settings
    public static final int INVADER_ROWS = 6;
    public static final int INVADER_COLS = 5;
    public static final int INVADER_H_SPACING = 20;
    public static final int INVADER_V_SPACING = 20;
    public static final int INVADER_GRID_START_Y = 100;
    public static final int INVADER_DROP_DISTANCE = 15;
    public static final float INVADER_INITIAL_SPEED_PX_PER_SEC = 30.0f;
    public static final float INVADER_SPEED_INCREMENT = 2.0f;
    public static final double INVADER_SHOOT_PROBABILITY_PER_SEC = 0.01;

    // Invader Types (Sizes and Points)
    // Assuming fixed sizes for now, replace with actual ASCII dimensions if needed
    public static final int INVADER_SMALL_WIDTH = 30;
    public static final int INVADER_SMALL_HEIGHT = 20;
    public static final int INVADER_SMALL_POINTS = 30;
    public static final int INVADER_MEDIUM_WIDTH = 40;
    public static final int INVADER_MEDIUM_HEIGHT = 20;
    public static final int INVADER_MEDIUM_POINTS = 20;
    public static final int INVADER_LARGE_WIDTH = 50;
    public static final int INVADER_LARGE_HEIGHT = 20;
    public static final int INVADER_LARGE_POINTS = 10;

    // Invader Projectile Settings
    public static final int INVADER_PROJECTILE_WIDTH = 5;
    public static final int INVADER_PROJECTILE_HEIGHT = 15;
    public static final int INVADER_PROJECTILE_SPEED_PX_PER_SEC = 200;

    // UFO Settings
    public static final int UFO_WIDTH = 60;
    public static final int UFO_HEIGHT = 25;
    public static final int UFO_Y = GAME_AREA_TOP_Y + 10; // Appears near the top
    public static final int UFO_SPEED_PX_PER_SEC = 120;
    public static final int UFO_MIN_APPEAR_DELAY_SEC = 20;
    public static final int UFO_MAX_APPEAR_DELAY_SEC = 30;
    public static final int UFO_MIN_POINTS = 100;
    public static final int UFO_MAX_POINTS = 300;
    public static final int UFO_POINT_INCREMENT = 50;

    // ASCII Art
    public static final String[] PLAYER_SPRITE = {
        "  | ┌≡┐ |",
        " ╔▀─┘ └─▀╗",
        " ╚▄═════▄╝"
    };
    // Estimate dimensions based on longest line and number of lines
    public static final int PLAYER_ASCII_WIDTH = 15; // " ╔▀─┘ └─▀╗".length()
    public static final int PLAYER_ASCII_HEIGHT = 3;

    public static final String[] INVADER_SPRITE = {
        "    ╝╚",
        " ╔[°__°]╗ ",
        "    ╗╔  " 
    };
    // Estimate dimensions based on longest line and number of lines
    public static final int INVADER_ASCII_WIDTH = 11; // " ╔[°__°]╗ ".length()
    public static final int INVADER_ASCII_HEIGHT = 3;

    // Basic Projectile (can be refined)
    public static final String PLAYER_PROJECTILE_SPRITE = "|";
    public static final String INVADER_PROJECTILE_SPRITE = "*";

    // Placeholder for Barrier ASCII (can be multi-level based on damage)
    public static final String[] BARRIER_SPRITE_LVL_0 = {
        "████████",
        "████████",
        "████████",
        "██  ██"
    };
     // Estimate dimensions
    public static final int BARRIER_ASCII_WIDTH = 8;
    public static final int BARRIER_ASCII_HEIGHT = 4;

    // Placeholder for UFO
    public static final String[] UFO_SPRITE = {
        "  _.--\"\"--._  ",
        " /<>_____<>\\ ",
        " \\ \"\"\"\"\"\"\"\"\" / "
    };
    public static final int UFO_ASCII_WIDTH = 14;
    public static final int UFO_ASCII_HEIGHT = 3;

}
