package org.delightofcomposition.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * shadcn/ui-inspired theme for Swing.
 * Color palette with rounded corners, subtle borders, and clean typography.
 * Fields are mutable to support runtime theme switching via {@link ThemePreset}.
 */
public class Theme {

    // ── Zinc palette ──
    public static Color ZINC_50  = new Color(250, 250, 250);
    public static Color ZINC_100 = new Color(244, 244, 245);
    public static Color ZINC_200 = new Color(228, 228, 231);
    public static Color ZINC_300 = new Color(212, 212, 216);
    public static Color ZINC_400 = new Color(161, 161, 170);
    public static Color ZINC_500 = new Color(113, 113, 122);
    public static Color ZINC_600 = new Color(82, 82, 91);
    public static Color ZINC_700 = new Color(63, 63, 70);
    public static Color ZINC_800 = new Color(39, 39, 42);
    public static Color ZINC_900 = new Color(24, 24, 27);
    public static Color ZINC_950 = new Color(9, 9, 11);

    // ── Semantic tokens ──
    public static Color BG            = ZINC_950;
    public static Color BG_CARD       = ZINC_900;
    public static Color BG_MUTED      = ZINC_800;
    public static Color BG_INPUT      = new Color(30, 30, 34);
    public static Color BORDER        = ZINC_800;
    public static Color BORDER_SUBTLE = new Color(45, 45, 50);
    public static Color FG            = ZINC_50;
    public static Color FG_MUTED      = ZINC_400;
    public static Color FG_DIM        = ZINC_500;
    public static Color ACCENT        = new Color(99, 102, 241);
    public static Color ACCENT_HOVER  = new Color(129, 140, 248);
    public static Color ACCENT_MUTED  = new Color(99, 102, 241, 30);
    public static Color DESTRUCTIVE   = new Color(239, 68, 68);
    public static Color SUCCESS       = new Color(34, 197, 94);
    public static Color RING          = new Color(99, 102, 241, 80);
    public static Color ACCENT_FILL   = new Color(99, 102, 241, 30);
    public static Color SUCCESS_FILL  = new Color(34, 197, 94, 30);
    public static Color AMBER         = new Color(245, 158, 11);
    public static Color AMBER_FILL    = new Color(245, 158, 11, 30);

    // ── Derived tokens (not in ThemePreset — computed from palette) ──
    /** Thumb/node fill color for sliders, toggles, and envelope nodes. */
    public static Color THUMB  = ZINC_50;
    /** Subtle drop shadow for thumbs and elevated elements. */
    public static Color SHADOW = new Color(0, 0, 0, 40);

    // ── Typography ──
    public static Font FONT_BASE    = new Font("Segoe UI", Font.PLAIN, 13);
    public static Font FONT_SMALL   = new Font("Segoe UI", Font.PLAIN, 11);
    public static Font FONT_LABEL   = new Font("Segoe UI", Font.PLAIN, 12);
    public static Font FONT_HEADING = new Font("Segoe UI", Font.BOLD, 13);
    public static Font FONT_TITLE   = new Font("Segoe UI", Font.BOLD, 11);
    public static Font FONT_MONO    = new Font("Consolas", Font.PLAIN, 12);

    // ── Dimensions ──
    public static int RADIUS = 8;
    public static int RADIUS_SM = 6;
    public static int RADIUS_LG = 12;

    // ── Phone-style spacing ──
    public static final int TOUCH_TARGET = 44;
    public static final int SECTION_GAP = 20;
    public static final int CONTROL_GAP = 14;
    public static final int LABEL_GAP = 6;

    // ── Additional fonts ──
    public static Font FONT_VALUE   = new Font("Consolas", Font.PLAIN, 14);
    public static Font FONT_SECTION = new Font("Segoe UI", Font.BOLD, 10);

    // ── Default font/radius values (for presets that use null/-1) ──
    private static final Font DEF_FONT_BASE    = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font DEF_FONT_SMALL   = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font DEF_FONT_LABEL   = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font DEF_FONT_HEADING = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font DEF_FONT_TITLE   = new Font("Segoe UI", Font.BOLD, 11);
    private static final Font DEF_FONT_VALUE   = new Font("Consolas", Font.PLAIN, 14);
    private static final Font DEF_FONT_SECTION = new Font("Segoe UI", Font.BOLD, 10);
    private static final int DEF_RADIUS    = 8;
    private static final int DEF_RADIUS_SM = 6;
    private static final int DEF_RADIUS_LG = 12;

    // ── Synthwave-specific colors (null when not synthwave) ──
    public static Color SW_CYAN      = null;
    public static Color SW_CYAN_DIM  = null;
    public static Color SW_GREEN     = null;
    public static Color SW_YELLOW    = null;
    public static Color SW_RED       = null;
    public static Color SW_PURPLE    = null;
    public static Color SW_LAVENDER  = null;
    public static Color SW_HOT_PINK  = null;
    public static Color SW_BG_DEEP   = null;
    public static Color SW_BG_SURFACE = null;
    public static Color SW_BG_RAISED = null;

    // ── Current preset tracking ──
    private static ThemePreset currentPreset = ThemePreset.DEFAULT_DARK;

    /** Returns true when the active theme is the Synthwave (NEON_OUTRUN) preset. */
    public static boolean isSynthwave() {
        return currentPreset == ThemePreset.NEON_OUTRUN;
    }

    /**
     * Apply a theme preset: overwrites all mutable static fields, then calls
     * {@link #install()} to refresh UIManager defaults.
     */
    public static void applyTheme(ThemePreset preset) {
        currentPreset = preset;

        // Zinc scale
        ZINC_50  = preset.zinc50;  ZINC_100 = preset.zinc100; ZINC_200 = preset.zinc200;
        ZINC_300 = preset.zinc300; ZINC_400 = preset.zinc400; ZINC_500 = preset.zinc500;
        ZINC_600 = preset.zinc600; ZINC_700 = preset.zinc700; ZINC_800 = preset.zinc800;
        ZINC_900 = preset.zinc900; ZINC_950 = preset.zinc950;

        // Backgrounds
        BG = preset.bg; BG_CARD = preset.bgCard;
        BG_MUTED = preset.bgMuted; BG_INPUT = preset.bgInput;

        // Borders
        BORDER = preset.border; BORDER_SUBTLE = preset.borderSubtle;

        // Foregrounds
        FG = preset.fg; FG_MUTED = preset.fgMuted; FG_DIM = preset.fgDim;

        // Accent
        ACCENT = preset.accent; ACCENT_HOVER = preset.accentHover;
        ACCENT_MUTED = preset.accentMuted; ACCENT_FILL = preset.accentFill;
        RING = preset.ring;

        // Semantic
        DESTRUCTIVE = preset.destructive;
        SUCCESS = preset.success; SUCCESS_FILL = preset.successFill;
        AMBER = preset.amber; AMBER_FILL = preset.amberFill;

        // Derived tokens
        THUMB = preset.zinc50;
        // Shadow opacity adapts: darker BG = lighter shadow, lighter BG = darker shadow
        int bgLuma = (preset.bg.getRed() + preset.bg.getGreen() + preset.bg.getBlue()) / 3;
        int shadowAlpha = bgLuma < 40 ? 40 : 60;
        SHADOW = new Color(0, 0, 0, shadowAlpha);

        // Fonts (null = use defaults)
        FONT_BASE    = preset.fontBase    != null ? preset.fontBase    : DEF_FONT_BASE;
        FONT_SMALL   = preset.fontSmall   != null ? preset.fontSmall   : DEF_FONT_SMALL;
        FONT_LABEL   = preset.fontLabel   != null ? preset.fontLabel   : DEF_FONT_LABEL;
        FONT_HEADING = preset.fontHeading != null ? preset.fontHeading : DEF_FONT_HEADING;
        FONT_TITLE   = preset.fontTitle   != null ? preset.fontTitle   : DEF_FONT_TITLE;
        FONT_VALUE   = preset.fontValue   != null ? preset.fontValue   : DEF_FONT_VALUE;
        FONT_SECTION = preset.fontSection != null ? preset.fontSection : DEF_FONT_SECTION;

        // Radii (-1 = use defaults)
        RADIUS    = preset.radius    >= 0 ? preset.radius    : DEF_RADIUS;
        RADIUS_SM = preset.radiusSm  >= 0 ? preset.radiusSm  : DEF_RADIUS_SM;
        RADIUS_LG = preset.radiusLg  >= 0 ? preset.radiusLg  : DEF_RADIUS_LG;

        // Synthwave-specific overrides
        if (isSynthwave()) {
            SynthwaveFonts.ensureLoaded();
            FONT_BASE    = SynthwaveFonts.BODY;
            FONT_SMALL   = SynthwaveFonts.BODY_SMALL;
            FONT_LABEL   = SynthwaveFonts.UI_SMALL;
            FONT_HEADING = SynthwaveFonts.DISPLAY;
            FONT_TITLE   = SynthwaveFonts.DISPLAY_SMALL;
            FONT_VALUE   = SynthwaveFonts.UI;
            FONT_SECTION = SynthwaveFonts.DISPLAY_SMALL;
            FONT_MONO    = SynthwaveFonts.UI;

            SW_CYAN      = new Color(34, 211, 238);
            SW_CYAN_DIM  = new Color(14, 116, 144);
            SW_GREEN     = new Color(74, 222, 128);
            SW_YELLOW    = new Color(251, 191, 36);
            SW_RED       = new Color(239, 68, 68);
            SW_PURPLE    = new Color(123, 94, 167);
            SW_LAVENDER  = new Color(183, 148, 246);
            SW_HOT_PINK  = new Color(255, 45, 149);
            SW_BG_DEEP   = new Color(26, 10, 46);
            SW_BG_SURFACE = new Color(59, 42, 122);
            SW_BG_RAISED = new Color(74, 54, 153);
        } else {
            SW_CYAN = SW_CYAN_DIM = SW_GREEN = SW_YELLOW = SW_RED = null;
            SW_PURPLE = SW_LAVENDER = SW_HOT_PINK = null;
            SW_BG_DEEP = SW_BG_SURFACE = SW_BG_RAISED = null;
        }

        install();
    }

    /** Returns the currently active preset. */
    public static ThemePreset getPreset() {
        return currentPreset;
    }

    /**
     * Apply the theme globally via UIManager defaults.
     */
    public static void install() {
        // Panel / general
        UIManager.put("Panel.background", BG);
        UIManager.put("Panel.foreground", FG);

        // Labels
        UIManager.put("Label.foreground", FG);
        UIManager.put("Label.font", FONT_LABEL);

        // Buttons
        UIManager.put("Button.background", BG_MUTED);
        UIManager.put("Button.foreground", FG);
        UIManager.put("Button.font", FONT_BASE);
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));
        UIManager.put("Button.select", ZINC_700);
        UIManager.put("Button.border", BorderFactory.createEmptyBorder(6, 16, 6, 16));

        // Text fields
        UIManager.put("TextField.background", BG_INPUT);
        UIManager.put("TextField.foreground", FG);
        UIManager.put("TextField.caretForeground", FG);
        UIManager.put("TextField.font", FONT_BASE);
        UIManager.put("TextField.border", new RoundedBorder(BORDER, RADIUS_SM, new Insets(6, 10, 6, 10)));

        // Spinners
        UIManager.put("Spinner.background", BG_INPUT);
        UIManager.put("Spinner.foreground", FG);
        UIManager.put("Spinner.font", FONT_BASE);
        UIManager.put("Spinner.border", new RoundedBorder(BORDER, RADIUS_SM, new Insets(2, 2, 2, 2)));
        UIManager.put("Spinner.arrowButtonBackground", BG_MUTED);
        UIManager.put("Spinner.arrowButtonBorder", BorderFactory.createEmptyBorder());
        UIManager.put("FormattedTextField.background", BG_INPUT);
        UIManager.put("FormattedTextField.foreground", FG);

        // ComboBox
        UIManager.put("ComboBox.background", BG_INPUT);
        UIManager.put("ComboBox.foreground", FG);
        UIManager.put("ComboBox.selectionBackground", ACCENT);
        UIManager.put("ComboBox.selectionForeground", FG);
        UIManager.put("ComboBox.font", FONT_BASE);
        UIManager.put("ComboBox.border", new RoundedBorder(BORDER, RADIUS_SM, new Insets(4, 8, 4, 8)));
        UIManager.put("ComboBox.buttonBackground", BG_INPUT);

        // CheckBox
        UIManager.put("CheckBox.background", BG);
        UIManager.put("CheckBox.foreground", FG);
        UIManager.put("CheckBox.font", FONT_BASE);
        UIManager.put("CheckBox.focus", new Color(0, 0, 0, 0));

        // Sliders
        UIManager.put("Slider.background", BG);
        UIManager.put("Slider.foreground", FG);
        UIManager.put("Slider.focus", new Color(0, 0, 0, 0));
        UIManager.put("Slider.trackWidth", 6);
        UIManager.put("Slider.thumbWidth", 16);

        // Progress bar
        UIManager.put("ProgressBar.background", BG_MUTED);
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ProgressBar.selectionBackground", FG);
        UIManager.put("ProgressBar.selectionForeground", BG);
        UIManager.put("ProgressBar.font", FONT_SMALL);
        UIManager.put("ProgressBar.border", BorderFactory.createEmptyBorder());

        // Scroll pane / bar
        UIManager.put("ScrollPane.background", BG);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollBar.background", BG);
        UIManager.put("ScrollBar.foreground", ZINC_600);
        UIManager.put("ScrollBar.thumbDarkShadow", BG);
        UIManager.put("ScrollBar.thumbHighlight", BG);
        UIManager.put("ScrollBar.thumbShadow", BG);
        UIManager.put("ScrollBar.track", BG);
        UIManager.put("ScrollBar.thumb", ZINC_700);
        UIManager.put("ScrollBar.width", 10);

        // SplitPane
        UIManager.put("SplitPane.background", BG);
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPane.dividerSize", 1);
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());

        // Menu
        UIManager.put("MenuBar.background", BG_CARD);
        UIManager.put("MenuBar.foreground", FG);
        UIManager.put("MenuBar.font", FONT_BASE);
        UIManager.put("MenuBar.border", BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        UIManager.put("Menu.background", BG_CARD);
        UIManager.put("Menu.foreground", FG);
        UIManager.put("Menu.selectionBackground", BG_MUTED);
        UIManager.put("Menu.selectionForeground", FG);
        UIManager.put("Menu.font", FONT_BASE);
        UIManager.put("MenuItem.background", BG_CARD);
        UIManager.put("MenuItem.foreground", FG);
        UIManager.put("MenuItem.selectionBackground", BG_MUTED);
        UIManager.put("MenuItem.selectionForeground", FG);
        UIManager.put("MenuItem.font", FONT_BASE);
        UIManager.put("MenuItem.acceleratorForeground", FG_DIM);
        UIManager.put("PopupMenu.background", BG_CARD);
        UIManager.put("PopupMenu.foreground", FG);
        UIManager.put("PopupMenu.border", new RoundedBorder(BORDER, RADIUS_SM, new Insets(4, 0, 4, 0)));

        // RadioButtonMenuItem
        UIManager.put("RadioButtonMenuItem.background", BG_CARD);
        UIManager.put("RadioButtonMenuItem.foreground", FG);
        UIManager.put("RadioButtonMenuItem.selectionBackground", BG_MUTED);
        UIManager.put("RadioButtonMenuItem.selectionForeground", FG);
        UIManager.put("RadioButtonMenuItem.font", FONT_BASE);
        UIManager.put("RadioButtonMenuItem.checkIcon", null);

        // Tooltip — Radix-style custom UI
        UIManager.put("ToolTipUI", "org.delightofcomposition.gui.RadixToolTipUI");
        UIManager.put("ToolTip.background", ZINC_950);
        UIManager.put("ToolTip.foreground", ZINC_100);
        UIManager.put("ToolTip.font", FONT_SMALL);
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder());

        // Option pane / dialog
        UIManager.put("OptionPane.background", BG_CARD);
        UIManager.put("OptionPane.foreground", FG);
        UIManager.put("OptionPane.messageForeground", FG);
        UIManager.put("OptionPane.font", FONT_BASE);

        // File chooser
        UIManager.put("FileChooser.background", BG_CARD);
        UIManager.put("FileChooser.foreground", FG);
        UIManager.put("FileChooser.listFont", FONT_BASE);

        // List (used in combo popups, file choosers)
        UIManager.put("List.background", BG_CARD);
        UIManager.put("List.foreground", FG);
        UIManager.put("List.selectionBackground", ACCENT);
        UIManager.put("List.selectionForeground", FG);

        // Table / tree (file chooser internals)
        UIManager.put("Table.background", BG_CARD);
        UIManager.put("Table.foreground", FG);
        UIManager.put("Table.selectionBackground", ACCENT);
        UIManager.put("Table.selectionForeground", FG);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("TableHeader.background", BG_MUTED);
        UIManager.put("TableHeader.foreground", FG);

        // Titled border
        UIManager.put("TitledBorder.titleColor", FG_MUTED);
        UIManager.put("TitledBorder.border", BorderFactory.createLineBorder(BORDER));
    }

    // ── Custom Borders ──

    /**
     * A rounded-corner border with configurable color, radius, and padding.
     */
    public static class RoundedBorder extends AbstractBorder {
        private final Color color;  // null = use Theme.BORDER dynamically
        private final int radius;   // -1 = use Theme.RADIUS_SM dynamically
        private final Insets insets;

        public RoundedBorder(Color color, int radius, Insets insets) {
            this.color = color;
            this.radius = radius;
            this.insets = insets;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Color borderColor = color != null ? color : BORDER;
            int r = radius >= 0 ? radius : RADIUS_SM;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSynthwave()) {
                SynthwavePainter.CornerSize sz = SynthwavePainter.chooseSizeForDimensions(w, h);
                java.awt.Polygon outer = SynthwavePainter.pixelCornerShape(x, y, w, h, sz);
                java.awt.Polygon inner = SynthwavePainter.pixelCornerShapeInset(x, y, w, h, sz,
                        SynthwavePainter.BORDER_WIDTH);
                java.awt.geom.Area borderRing = new java.awt.geom.Area(outer);
                borderRing.subtract(new java.awt.geom.Area(inner));
                g2.setColor(borderColor);
                g2.fill(borderRing);
            } else {
                g2.setColor(borderColor);
                g2.drawRoundRect(x, y, w - 1, h - 1, r, r);
            }
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return insets;
        }

        @Override
        public Insets getBorderInsets(Component c, Insets i) {
            i.left = insets.left; i.top = insets.top;
            i.right = insets.right; i.bottom = insets.bottom;
            return i;
        }
    }

    /**
     * Walk up the component hierarchy to find the first opaque ancestor's
     * background color. Falls back to Theme.BG if none found.
     */
    private static Color findOpaqueAncestorBg(Component c) {
        java.awt.Container parent = c.getParent();
        while (parent != null) {
            if (parent.isOpaque() && parent.getBackground() != null) {
                return parent.getBackground();
            }
            parent = parent.getParent();
        }
        return BG;
    }

    /**
     * Card-style border: rounded rect with filled background.
     */
    public static class CardBorder extends AbstractBorder {
        private final Color bg;
        private final Color border;
        private final int radius;
        private final Insets insets;

        public CardBorder(Color bg, Color border, int radius, Insets insets) {
            this.bg = bg;
            this.border = border;
            this.radius = radius;
            this.insets = insets;
        }

        public CardBorder() {
            this(BG_CARD, BORDER, RADIUS, new Insets(12, 14, 12, 14));
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSynthwave()) {
                SynthwavePainter.fillPanel(g2, x, y, w, h, bg, border);
                SynthwavePainter.paintBevel(g2, x, y, w, h, true);
            } else {
                g2.setColor(bg);
                g2.fillRoundRect(x, y, w - 1, h - 1, radius, radius);
                g2.setColor(border);
                g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
            }
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return insets;
        }

        @Override
        public Insets getBorderInsets(Component c, Insets i) {
            i.left = insets.left; i.top = insets.top;
            i.right = insets.right; i.bottom = insets.bottom;
            return i;
        }
    }

    // ── Theme property tagging ──
    // Components tagged with these client properties survive theme switches:
    // the recursive walker re-applies current theme values from the tag.

    /** Tag a component's font so it survives theme switches. */
    public static void tagFont(JComponent c, String tag) {
        c.putClientProperty("theme.font", tag);
        c.setFont(resolveFont(tag));
    }

    /** Tag a component's foreground color so it survives theme switches. */
    public static void tagFg(JComponent c, String tag) {
        c.putClientProperty("theme.fg", tag);
        c.setForeground(resolveFg(tag));
    }

    /** Tag a component's background color so it survives theme switches. */
    public static void tagBg(JComponent c, String tag) {
        c.putClientProperty("theme.bg", tag);
        c.setBackground(resolveBg(tag));
    }

    private static Font resolveFont(String tag) {
        switch (tag) {
            case "base":    return FONT_BASE;
            case "small":   return FONT_SMALL;
            case "label":   return FONT_LABEL;
            case "heading": return FONT_HEADING;
            case "title":   return FONT_TITLE;
            case "mono":    return FONT_MONO;
            case "value":   return FONT_VALUE;
            case "section": return FONT_SECTION;
            default:        return FONT_BASE;
        }
    }

    private static Color resolveFg(String tag) {
        switch (tag) {
            case "fg":          return FG;
            case "fgMuted":     return FG_MUTED;
            case "fgDim":       return FG_DIM;
            case "bg":          return BG;
            case "sectionFg":   return isSynthwave() ? FG : FG_MUTED;
            case "destructive": return DESTRUCTIVE;
            case "success":     return SUCCESS;
            case "amber":       return AMBER;
            case "accent":      return ACCENT;
            default:            return FG;
        }
    }

    private static Color resolveBg(String tag) {
        switch (tag) {
            case "bg":      return BG;
            case "bgCard":  return BG_CARD;
            case "bgMuted": return BG_MUTED;
            case "bgInput": return BG_INPUT;
            default:        return BG;
        }
    }

    /** Null out ALL explicit font/fg/bg so UIManager defaults take over. */
    public static void resetExplicitProperties(Container root) {
        // Process root itself
        if (root instanceof JComponent) {
            JComponent jc = (JComponent) root;
            jc.setFont(null);
            jc.setForeground(null);
            jc.setBackground(null);
        }
        // Process children recursively
        for (Component comp : root.getComponents()) {
            if (comp instanceof JComponent) {
                JComponent jc = (JComponent) comp;
                jc.setFont(null);
                jc.setForeground(null);
                jc.setBackground(null);
            }
            if (comp instanceof Container) {
                resetExplicitProperties((Container) comp);
            }
        }
    }

    /** Re-apply tagged properties from current theme values. */
    public static void refreshTaggedProperties(Container root) {
        // Process root itself
        if (root instanceof JComponent) {
            JComponent jc = (JComponent) root;
            String fontTag = (String) jc.getClientProperty("theme.font");
            if (fontTag != null) jc.setFont(resolveFont(fontTag));
            String fgTag = (String) jc.getClientProperty("theme.fg");
            if (fgTag != null) jc.setForeground(resolveFg(fgTag));
            String bgTag = (String) jc.getClientProperty("theme.bg");
            if (bgTag != null) jc.setBackground(resolveBg(bgTag));
        }
        // Process children recursively
        for (Component comp : root.getComponents()) {
            if (comp instanceof JComponent) {
                JComponent jc = (JComponent) comp;
                String fontTag = (String) jc.getClientProperty("theme.font");
                if (fontTag != null) jc.setFont(resolveFont(fontTag));
                String fgTag = (String) jc.getClientProperty("theme.fg");
                if (fgTag != null) jc.setForeground(resolveFg(fgTag));
                String bgTag = (String) jc.getClientProperty("theme.bg");
                if (bgTag != null) jc.setBackground(resolveBg(bgTag));
            }
            if (comp instanceof Container) {
                refreshTaggedProperties((Container) comp);
            }
        }
    }

    // ── Styled component factories ──

    /**
     * Create a primary (filled) button.
     */
    public static JButton primaryButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bg = getModel().isPressed() ? ACCENT : getModel().isRollover() ? ACCENT_HOVER : FG;
                Color fg = getModel().isPressed() ? FG : BG;

                if (!isEnabled()) {
                    bg = ZINC_700;
                    fg = ZINC_500;
                }

                if (isSynthwave()) {
                    SynthwavePainter.paintPrimaryButton(g2, 0, 0, getWidth(), getHeight(),
                            getModel().isPressed(), getModel().isRollover(), isEnabled());
                    fg = isEnabled() ? FG : FG_DIM;
                } else {
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), RADIUS, RADIUS);
                }
                g2.setColor(fg);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                if (isFocusOwner() && !isSynthwave()) {
                    g2.setColor(RING);
                    g2.setStroke(new BasicStroke(2f));
                    if (isSynthwave()) {
                        SynthwavePainter.strokeShape(g2, 1, 1, getWidth() - 2, getHeight() - 2, RING);
                    } else {
                        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, RADIUS, RADIUS);
                    }
                }
                g2.dispose();
            }
        };
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        tagFont(btn, "base");
        tagFg(btn, "bg");
        return btn;
    }

    /**
     * Create a secondary (outline) button.
     */
    public static JButton secondaryButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bgColor = getModel().isPressed() ? ZINC_700 : getModel().isRollover() ? BG_MUTED : BG_CARD;
                if (!isEnabled()) bgColor = ZINC_900;
                Color borderColor = isEnabled() ? BORDER : ZINC_800;

                if (isSynthwave()) {
                    SynthwavePainter.paintSecondaryButton(g2, 0, 0, getWidth(), getHeight(),
                            getModel().isPressed(), getModel().isRollover(), isEnabled());
                } else {
                    g2.setColor(bgColor);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
                    g2.setColor(borderColor);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
                }

                g2.setColor(isEnabled() ? FG : (isSynthwave() ? FG_DIM : ZINC_600));
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                if (isFocusOwner() && !isSynthwave()) {
                    g2.setColor(RING);
                    g2.setStroke(new BasicStroke(2f));
                    if (isSynthwave()) {
                        SynthwavePainter.strokeShape(g2, 1, 1, getWidth() - 2, getHeight() - 2, RING);
                    } else {
                        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, RADIUS, RADIUS);
                    }
                }
                g2.dispose();
            }
        };
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        tagFont(btn, "base");
        tagFg(btn, "fg");
        return btn;
    }

    /**
     * Create a ghost button (no border, subtle hover).
     */
    public static JButton ghostButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (isSynthwave()) {
                    SynthwavePainter.paintGhostButton(g2, 0, 0, getWidth(), getHeight(),
                            getModel().isPressed(), getModel().isRollover(), isEnabled());
                } else if (getModel().isRollover() || getModel().isPressed()) {
                    Color hoverBg = getModel().isPressed() ? ZINC_700 : BG_MUTED;
                    g2.setColor(hoverBg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), RADIUS, RADIUS);
                }

                g2.setColor(isEnabled() ? getForeground() : (isSynthwave() ? FG_DIM : ZINC_600));
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                if (isFocusOwner() && !isSynthwave()) {
                    g2.setColor(RING);
                    g2.setStroke(new BasicStroke(2f));
                    if (isSynthwave()) {
                        SynthwavePainter.strokeShape(g2, 1, 1, getWidth() - 2, getHeight() - 2, RING);
                    } else {
                        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, RADIUS, RADIUS);
                    }
                }
                g2.dispose();
            }
        };
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        tagFont(btn, "base");
        tagFg(btn, "fg");
        return btn;
    }

    /**
     * Create a styled progress bar with rounded track and fill.
     */
    public static JProgressBar styledProgressBar() {
        JProgressBar bar = new JProgressBar(0, 100) {
            @Override
            public void updateUI() {
                // Re-apply custom UI instead of letting L&F replace it
                setUI(createProgressBarUI(this));
            }
        };
        bar.setUI(createProgressBarUI(bar));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder());
        bar.setStringPainted(false);
        tagFont(bar, "small");
        return bar;
    }

    private static BasicProgressBarUI createProgressBarUI(JProgressBar bar) {
        return new BasicProgressBarUI() {
            @Override
            protected void paintDeterminate(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = c.getWidth();
                int h = c.getHeight();

                boolean done = bar.getPercentComplete() >= 1.0;
                Color fillColor = done ? SUCCESS : ACCENT;

                if (isSynthwave()) {
                    Color swFill = done && SW_GREEN != null ? SW_GREEN : fillColor;
                    int segments = 20;
                    int segW = (w - (segments - 1) * 2) / segments;
                    int filled = (int) (segments * bar.getPercentComplete());
                    for (int i = 0; i < segments; i++) {
                        int sx = i * (segW + 2);
                        g2.setColor(i < filled ? swFill : BG_MUTED);
                        g2.fillRect(sx, 0, segW, h);
                    }
                } else {
                    int r = h;
                    g2.setColor(BG_MUTED);
                    g2.fillRoundRect(0, 0, w, h, r, r);
                    int fillW = (int) (w * bar.getPercentComplete());
                    if (fillW > 0) {
                        g2.setColor(fillColor);
                        g2.fillRoundRect(0, 0, fillW, h, r, r);
                    }
                }

                if (bar.isStringPainted()) {
                    g2.setFont(FONT_SMALL);
                    g2.setColor(FG);
                    FontMetrics fm = g2.getFontMetrics();
                    String s = bar.getString();
                    int tx = (w - fm.stringWidth(s)) / 2;
                    int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                    g2.drawString(s, tx, ty);
                }

                g2.dispose();
            }

            @Override
            protected void paintIndeterminate(Graphics g, JComponent c) {
                paintDeterminate(g, c);
            }
        };
    }

    /**
     * Create a section header label (uppercase, muted, small).
     */
    public static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        tagFont(label, "title");
        tagFg(label, "sectionFg");
        label.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 0, 0, 0));
        return label;
    }

    /**
     * Create a parameter label.
     */
    public static JLabel paramLabel(String text) {
        JLabel label = new JLabel(text);
        tagFont(label, "label");
        tagFg(label, "fgMuted");
        return label;
    }

    /**
     * Create a value display label (monospace).
     */
    public static JLabel valueLabel(String text) {
        JLabel label = new JLabel(text);
        tagFont(label, "mono");
        tagFg(label, "fg");
        return label;
    }

    /**
     * Create a card panel with rounded border and dark fill.
     */
    public static JPanel card() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (isSynthwave()) {
                    SynthwavePainter.fillPanel(g2, 0, 0, getWidth(), getHeight(), BG_CARD, BORDER);
                    SynthwavePainter.paintBevel(g2, 0, 0, getWidth(), getHeight(), true);
                } else {
                    g2.setColor(BG_CARD);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS_LG, RADIUS_LG);
                    g2.setColor(BORDER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS_LG, RADIUS_LG);
                }
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        return panel;
    }

    /**
     * Style a slider to look like shadcn's.
     */
    public static void styleSlider(JSlider slider) {
        slider.setOpaque(false);
        slider.setUI(new BasicSliderUI(slider) {
            @Override
            public void paintTrack(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int trackH = 6;
                int y = trackRect.y + (trackRect.height - trackH) / 2;

                if (isSynthwave()) {
                    // Rectangular track with pixel corners
                    SynthwavePainter.fillPanel(g2, trackRect.x, y, trackRect.width, trackH,
                            BG_MUTED, SW_PURPLE);
                    // Filled portion with glow
                    int fillW = thumbRect.x + thumbRect.width / 2 - trackRect.x;
                    if (fillW > 2) {
                        g2.setColor(ACCENT);
                        g2.fillRect(trackRect.x + 1, y + 1, fillW - 1, trackH - 2);
                        SynthwavePainter.paintGlow(g2, trackRect.x, y, fillW, trackH, ACCENT, 2);
                    }
                } else {
                    g2.setColor(BG_MUTED);
                    g2.fillRoundRect(trackRect.x, y, trackRect.width, trackH, trackH, trackH);
                    int fillW = thumbRect.x + thumbRect.width / 2 - trackRect.x;
                    g2.setColor(ACCENT);
                    g2.fillRoundRect(trackRect.x, y, fillW, trackH, trackH, trackH);
                }

                g2.dispose();
            }

            @Override
            public void paintThumb(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int size = 16;
                int x = thumbRect.x + (thumbRect.width - size) / 2;
                int y = thumbRect.y + (thumbRect.height - size) / 2;

                if (isSynthwave()) {
                    // Square thumb with pixel corners
                    SynthwavePainter.fillPanel(g2, x, y, size, size, SW_LAVENDER, SW_PURPLE);
                    SynthwavePainter.paintBevel(g2, x, y, size, size, true);
                } else {
                    g2.setColor(SHADOW);
                    g2.fillOval(x + 1, y + 1, size, size);
                    g2.setColor(THUMB);
                    g2.fillOval(x, y, size, size);
                    g2.setColor(ACCENT);
                    g2.drawOval(x, y, size - 1, size - 1);
                }

                g2.dispose();
            }

            @Override
            public void paintFocus(Graphics g) {
                // no focus ring
            }
        });
    }

    /**
     * Style a scroll pane with thin dark scrollbars.
     */
    public static void styleScrollPane(JScrollPane sp) {
        sp.setBorder(BorderFactory.createEmptyBorder());
        JScrollBar vbar = sp.getVerticalScrollBar();
        JScrollBar hbar = sp.getHorizontalScrollBar();
        styleScrollBar(vbar);
        styleScrollBar(hbar);
    }

    /**
     * Section header: uppercase label + 1px divider beneath, ~24px total.
     */
    public static JPanel sectionHeader(String text) {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(0);

        JLabel label = new JLabel(text.toUpperCase());
        tagFont(label, "section");
        tagFg(label, "sectionFg");
        label.setAlignmentX(0);
        label.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 0, 0, 0));
        panel.add(label);
        panel.add(javax.swing.Box.createVerticalStrut(4));

        JComponent divider = new JComponent() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                g.setColor(BORDER_SUBTLE);
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        divider.setPreferredSize(new java.awt.Dimension(Integer.MAX_VALUE, 1));
        divider.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 1));
        divider.setAlignmentX(0);
        panel.add(divider);

        panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 34));
        return panel;
    }

    /**
     * Toggle row: label on the left, control on the right, 44px height.
     */
    public static JPanel toggleRow(String label, JComponent control) {
        JPanel row = new JPanel();
        row.setLayout(new java.awt.BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(0);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, TOUCH_TARGET));
        row.setPreferredSize(new java.awt.Dimension(200, TOUCH_TARGET));

        JLabel lbl = paramLabel(label);
        lbl.setVerticalAlignment(javax.swing.SwingConstants.CENTER);
        row.add(lbl, java.awt.BorderLayout.WEST);
        row.add(control, java.awt.BorderLayout.EAST);
        return row;
    }

    private static void styleScrollBar(JScrollBar bar) {
        bar.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = ZINC_700;
                trackColor = BG;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return zeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return zeroButton();
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle r) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ZINC_700);
                g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6);
                g2.dispose();
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
                g.setColor(BG);
                g.fillRect(r.x, r.y, r.width, r.height);
            }

            private JButton zeroButton() {
                JButton btn = new JButton();
                btn.setPreferredSize(new java.awt.Dimension(0, 0));
                btn.setMaximumSize(new java.awt.Dimension(0, 0));
                return btn;
            }
        });
        bar.setPreferredSize(new java.awt.Dimension(8, 8));
    }
}
