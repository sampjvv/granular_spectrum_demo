package org.delightofcomposition.gui;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;

/**
 * Loads and exposes the synthwave theme's custom fonts:
 *   - Press Start 2P (pixel arcade display font)
 *   - Cascadia Code (UI and body monospace)
 *
 * Fonts are loaded lazily on first use and registered with the
 * graphics environment so they can be referenced by name.
 */
public class SynthwaveFonts {

    // Display font — arcade pixel style, used for headings & labels
    public static Font DISPLAY;        // Press Start 2P, 14pt
    public static Font DISPLAY_SMALL;  // Press Start 2P, 11pt

    // UI font — clean monospace, used for values & controls
    public static Font UI;             // Cascadia Code, 13pt
    public static Font UI_SMALL;       // Cascadia Code, 11pt

    // Body font — readable monospace
    public static Font BODY;           // Cascadia Code, 13pt
    public static Font BODY_BOLD;      // Cascadia Code Bold, 13pt
    public static Font BODY_SMALL;     // Cascadia Code, 11pt

    private static boolean loaded = false;

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String basePath = "resources/fonts/";

        // Press Start 2P
        Font pressStart = loadFont(ge, basePath + "PressStart2P-Regular.ttf");
        if (pressStart != null) {
            DISPLAY = pressStart.deriveFont(Font.PLAIN, 14f);
            DISPLAY_SMALL = pressStart.deriveFont(Font.PLAIN, 11f);
        } else {
            DISPLAY = new Font("Consolas", Font.BOLD, 16);
            DISPLAY_SMALL = new Font("Consolas", Font.BOLD, 13);
        }

        // Cascadia Code — system font, reference by name
        UI = new Font("Cascadia Code", Font.PLAIN, 13);
        UI_SMALL = new Font("Cascadia Code", Font.PLAIN, 11);

        BODY = new Font("Cascadia Code", Font.PLAIN, 13);
        BODY_BOLD = new Font("Cascadia Code", Font.BOLD, 13);
        BODY_SMALL = new Font("Cascadia Code", Font.PLAIN, 11);
    }

    private static Font loadFont(GraphicsEnvironment ge, String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                System.err.println("SynthwaveFonts: font not found: " + path);
                return null;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, file);
            ge.registerFont(font);
            return font;
        } catch (FontFormatException | IOException e) {
            System.err.println("SynthwaveFonts: failed to load " + path + ": " + e.getMessage());
            return null;
        }
    }
}
