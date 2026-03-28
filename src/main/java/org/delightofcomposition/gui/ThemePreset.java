package org.delightofcomposition.gui;

import java.awt.Color;
import java.awt.Font;

/**
 * Defines all available color themes. Each preset provides the full set of
 * colors, fonts, and corner-radii consumed by {@link Theme}.
 */
public enum ThemePreset {

    DEFAULT_DARK("Midnight Indigo",
        /* ZINC 50-950 */
        c(250,250,250), c(244,244,245), c(228,228,231), c(212,212,216),
        c(161,161,170), c(113,113,122), c(82,82,91), c(63,63,70),
        c(39,39,42), c(24,24,27), c(9,9,11),
        /* BG, BG_CARD, BG_MUTED, BG_INPUT */
        c(9,9,11), c(24,24,27), c(39,39,42), c(30,30,34),
        /* BORDER, BORDER_SUBTLE */
        c(39,39,42), c(45,45,50),
        /* FG, FG_MUTED, FG_DIM */
        c(250,250,250), c(161,161,170), c(113,113,122),
        /* ACCENT, ACCENT_HOVER, ACCENT_MUTED, ACCENT_FILL, RING */
        c(99,102,241), c(129,140,248), c(99,102,241,30), c(99,102,241,30), c(99,102,241,80),
        /* DESTRUCTIVE, SUCCESS, SUCCESS_FILL, AMBER, AMBER_FILL */
        c(239,68,68), c(34,197,94), c(34,197,94,30), c(245,158,11), c(245,158,11,30),
        /* fonts (null = keep defaults) */
        null, null, null, null, null, null, null,
        /* RADIUS, RADIUS_SM, RADIUS_LG (-1 = keep defaults) */
        -1, -1, -1),

    TAPE_MACHINE("Warm Analog Studio",
        c(232,220,200), c(210,198,178), c(185,172,152), c(160,146,126),
        c(130,118,100), c(100,90,76), c(76,68,56), c(56,48,38),
        c(38,32,24), c(28,23,17), c(20,16,12),
        c(20,16,12), c(28,23,17), c(38,32,24), c(32,26,19),
        c(56,48,38), c(46,40,30),
        c(232,220,200), c(130,118,100), c(100,90,76),
        c(212,118,58), c(232,148,88), c(212,118,58,30), c(212,118,58,30), c(212,118,58,80),
        c(220,60,50), c(160,190,80), c(160,190,80,30), c(218,180,50), c(218,180,50,30),
        f("Consolas",Font.PLAIN,13), f("Consolas",Font.PLAIN,11), f("Consolas",Font.PLAIN,12),
        f("Consolas",Font.BOLD,13), f("Consolas",Font.BOLD,11), f("Consolas",Font.PLAIN,14),
        f("Consolas",Font.BOLD,10),
        6, 4, 10),

    NEON_OUTRUN("Synthwave",
        c(240,230,255), c(220,210,240), c(180,170,210), c(140,130,175),
        c(110,100,150), c(80,70,120), c(55,45,90), c(40,30,70),
        c(25,18,50), c(16,10,38), c(10,4,26),
        c(10,4,26), c(16,10,38), c(25,18,50), c(14,8,34),
        c(50,30,90), c(35,22,65),
        c(240,230,255), c(140,130,175), c(100,90,140),
        c(255,41,117), c(255,80,150), c(255,41,117,35), c(255,41,117,35), c(255,41,117,80),
        c(255,50,50), c(0,255,245), c(0,255,245,30), c(255,200,40), c(255,200,40,30),
        null, null, null, null, null, null, null,
        -1, -1, -1),

    CYAN("Cyan Glow",
        c(230,250,252), c(210,238,242), c(180,218,225), c(150,198,208),
        c(110,165,178), c(80,130,145), c(58,98,110), c(42,72,82),
        c(28,48,56), c(18,32,40), c(8,16,22),
        c(8,16,22), c(18,32,40), c(28,48,56), c(14,26,34),
        c(35,60,72), c(24,44,54),
        c(230,250,252), c(110,165,178), c(80,130,145),
        c(0,210,220), c(50,235,245), c(0,210,220,30), c(0,210,220,30), c(0,210,220,80),
        c(239,68,68), c(34,220,160), c(34,220,160,30), c(245,190,50), c(245,190,50,30),
        null, null, null, null, null, null, null,
        -1, -1, -1),

    MODULAR("Eurorack Patching",
        c(220,220,220), c(200,200,200), c(175,175,175), c(150,150,150),
        c(120,120,120), c(95,95,95), c(72,72,72), c(55,55,55),
        c(40,40,40), c(30,30,30), c(22,22,22),
        c(22,22,22), c(30,30,30), c(40,40,40), c(26,26,26),
        c(100,100,100), c(65,65,65),
        c(220,220,220), c(150,150,150), c(100,100,100),
        c(255,50,50), c(255,90,90), c(255,50,50,35), c(255,50,50,35), c(255,50,50,80),
        c(255,30,30), c(50,255,50), c(50,255,50,30), c(255,204,0), c(255,204,0,30),
        null, null, null, null, null, null, null,
        4, 2, 8);

    // ── Instance fields ──

    public final String displayName;

    // Zinc scale (11 tones)
    public final Color zinc50, zinc100, zinc200, zinc300, zinc400;
    public final Color zinc500, zinc600, zinc700, zinc800, zinc900, zinc950;

    // Backgrounds
    public final Color bg, bgCard, bgMuted, bgInput;

    // Borders
    public final Color border, borderSubtle;

    // Foregrounds
    public final Color fg, fgMuted, fgDim;

    // Accent
    public final Color accent, accentHover, accentMuted, accentFill, ring;

    // Semantic
    public final Color destructive, success, successFill, amber, amberFill;

    // Fonts (null = keep Segoe UI defaults)
    public final Font fontBase, fontSmall, fontLabel, fontHeading, fontTitle, fontValue, fontSection;

    // Radii (-1 = keep defaults 8/6/12)
    public final int radius, radiusSm, radiusLg;

    ThemePreset(String displayName,
                Color zinc50, Color zinc100, Color zinc200, Color zinc300,
                Color zinc400, Color zinc500, Color zinc600, Color zinc700,
                Color zinc800, Color zinc900, Color zinc950,
                Color bg, Color bgCard, Color bgMuted, Color bgInput,
                Color border, Color borderSubtle,
                Color fg, Color fgMuted, Color fgDim,
                Color accent, Color accentHover, Color accentMuted, Color accentFill, Color ring,
                Color destructive, Color success, Color successFill, Color amber, Color amberFill,
                Font fontBase, Font fontSmall, Font fontLabel,
                Font fontHeading, Font fontTitle, Font fontValue, Font fontSection,
                int radius, int radiusSm, int radiusLg) {
        this.displayName = displayName;
        this.zinc50 = zinc50; this.zinc100 = zinc100; this.zinc200 = zinc200;
        this.zinc300 = zinc300; this.zinc400 = zinc400; this.zinc500 = zinc500;
        this.zinc600 = zinc600; this.zinc700 = zinc700; this.zinc800 = zinc800;
        this.zinc900 = zinc900; this.zinc950 = zinc950;
        this.bg = bg; this.bgCard = bgCard; this.bgMuted = bgMuted; this.bgInput = bgInput;
        this.border = border; this.borderSubtle = borderSubtle;
        this.fg = fg; this.fgMuted = fgMuted; this.fgDim = fgDim;
        this.accent = accent; this.accentHover = accentHover;
        this.accentMuted = accentMuted; this.accentFill = accentFill; this.ring = ring;
        this.destructive = destructive; this.success = success; this.successFill = successFill;
        this.amber = amber; this.amberFill = amberFill;
        this.fontBase = fontBase; this.fontSmall = fontSmall; this.fontLabel = fontLabel;
        this.fontHeading = fontHeading; this.fontTitle = fontTitle;
        this.fontValue = fontValue; this.fontSection = fontSection;
        this.radius = radius; this.radiusSm = radiusSm; this.radiusLg = radiusLg;
    }

    private static Color c(int r, int g, int b) {
        return new Color(r, g, b);
    }

    private static Color c(int r, int g, int b, int a) {
        return new Color(r, g, b, a);
    }

    private static Font f(String name, int style, int size) {
        return new Font(name, style, size);
    }
}
