package com.bame.client.module;

public class CustomCrosshairModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;
    public static int color = 0xFFFFFFFF;

    public static final int GRID_SIZE = 15;
    public static final int CENTER = 7;
    public static boolean[][] grid = new boolean[GRID_SIZE][GRID_SIZE];

    static {
        applyPreset(1); // Default to clean Plus
    }

    public static void clearGrid() {
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                grid[r][c] = false;
            }
        }
    }

    public static void resetToDefault() {
        color = 0xFFFFFFFF;
        applyPreset(1);
    }

    public static void applyPreset(int index) {
        clearGrid();
        switch (index) {
            case 0 -> { // 0: Boxed Cross (Corners + Cross)
                // Cross
                for (int i = 4; i <= 10; i++) {
                    grid[7][i] = true;
                    grid[i][7] = true;
                }
                // Corners
                grid[3][3] = grid[3][4] = grid[4][3] = true;
                grid[3][11] = grid[3][10] = grid[4][11] = true;
                grid[11][3] = grid[11][4] = grid[10][3] = true;
                grid[11][11] = grid[11][10] = grid[10][11] = true;
            }
            case 1 -> { // 1: Plus (+)
                for (int i = 2; i <= 12; i++) {
                    grid[7][i] = true;
                    grid[i][7] = true;
                }
            }
            case 2 -> { // 2: Circle
                drawCircleTemplate(false);
            }
            case 3 -> { // 3: Dot (.)
                grid[7][7] = true;
            }
            case 4 -> { // 4: Corners with Center Dot
                grid[7][7] = true;
                grid[3][3] = grid[3][4] = grid[4][3] = true;
                grid[3][11] = grid[3][10] = grid[4][11] = true;
                grid[11][3] = grid[11][4] = grid[10][3] = true;
                grid[11][11] = grid[11][10] = grid[10][11] = true;
            }
            case 5 -> { // 5: Target (Circle + Cross arms)
                drawCircleTemplate(false);
                // Cross arms extending out
                for (int i = 1; i <= 3; i++) {
                    grid[7][i] = true;
                    grid[7][14 - i] = true;
                    grid[i][7] = true;
                    grid[14 - i][7] = true;
                }
            }
            case 6 -> { // 6: Target + Center Dot
                drawCircleTemplate(false);
                for (int i = 1; i <= 3; i++) {
                    grid[7][i] = true;
                    grid[7][14 - i] = true;
                    grid[i][7] = true;
                    grid[14 - i][7] = true;
                }
                grid[7][7] = true;
            }
            case 7 -> { // 7: Gap Cross (Cross with empty center)
                for (int i = 2; i <= 5; i++) {
                    grid[7][i] = true;
                    grid[7][14 - i] = true;
                    grid[i][7] = true;
                    grid[14 - i][7] = true;
                }
            }
            case 8 -> { // 8: Bullseye (Circle + Center Dot)
                drawCircleTemplate(false);
                grid[7][7] = true;
            }
            default -> applyPreset(1);
        }
    }

    private static void drawCircleTemplate(boolean filled) {
        // Hollow circle radius ~4 around center (7, 7)
        grid[3][6] = grid[3][7] = grid[3][8] = true;
        grid[11][6] = grid[11][7] = grid[11][8] = true;
        grid[6][3] = grid[7][3] = grid[8][3] = true;
        grid[6][11] = grid[7][11] = grid[8][11] = true;

        grid[4][4] = grid[4][5] = grid[5][4] = true;
        grid[4][10] = grid[4][9] = grid[5][10] = true;
        grid[10][4] = grid[10][5] = grid[9][4] = true;
        grid[10][10] = grid[10][9] = grid[9][10] = true;
    }

    public static boolean[][] getPresetTemplate(int index) {
        boolean[][] t = new boolean[GRID_SIZE][GRID_SIZE];
        switch (index) {
            case 0 -> {
                for (int i = 4; i <= 10; i++) { t[7][i] = t[i][7] = true; }
                t[3][3] = t[3][4] = t[4][3] = true;
                t[3][11] = t[3][10] = t[4][11] = true;
                t[11][3] = t[11][4] = t[10][3] = true;
                t[11][11] = t[11][10] = t[10][11] = true;
            }
            case 1 -> {
                for (int i = 2; i <= 12; i++) { t[7][i] = t[i][7] = true; }
            }
            case 2 -> fillCircleTo(t);
            case 3 -> t[7][7] = true;
            case 4 -> {
                t[7][7] = true;
                t[3][3] = t[3][4] = t[4][3] = true;
                t[3][11] = t[3][10] = t[4][11] = true;
                t[11][3] = t[11][4] = t[10][3] = true;
                t[11][11] = t[11][10] = t[10][11] = true;
            }
            case 5 -> {
                fillCircleTo(t);
                for (int i = 1; i <= 3; i++) {
                    t[7][i] = t[7][14 - i] = t[i][7] = t[14 - i][7] = true;
                }
            }
            case 6 -> {
                fillCircleTo(t);
                for (int i = 1; i <= 3; i++) {
                    t[7][i] = t[7][14 - i] = t[i][7] = t[14 - i][7] = true;
                }
                t[7][7] = true;
            }
            case 7 -> {
                for (int i = 2; i <= 5; i++) {
                    t[7][i] = t[7][14 - i] = t[i][7] = t[14 - i][7] = true;
                }
            }
            case 8 -> {
                fillCircleTo(t);
                t[7][7] = true;
            }
        }
        return t;
    }

    private static void fillCircleTo(boolean[][] t) {
        t[3][6] = t[3][7] = t[3][8] = true;
        t[11][6] = t[11][7] = t[11][8] = true;
        t[6][3] = t[7][3] = t[8][3] = true;
        t[6][11] = t[7][11] = t[8][11] = true;
        t[4][4] = t[4][5] = t[5][4] = true;
        t[4][10] = t[4][9] = t[5][10] = true;
        t[10][4] = t[10][5] = t[9][4] = true;
        t[10][10] = t[10][9] = t[9][10] = true;
    }

    public static String getEncodedGrid() {
        StringBuilder sb = new StringBuilder(GRID_SIZE * GRID_SIZE);
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                sb.append(grid[r][c] ? '1' : '0');
            }
        }
        return sb.toString();
    }

    public static void setEncodedGrid(String s) {
        if (s == null) return;
        int idx = 0;
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (idx < s.length()) {
                    grid[r][c] = (s.charAt(idx) == '1');
                    idx++;
                }
            }
        }
    }
}
