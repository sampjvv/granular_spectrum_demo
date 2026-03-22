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

    OSCILLOSCOPE("CRT Terminal",
        c(200,255,210), c(170,230,180), c(130,200,140), c(90,170,100),
        c(50,140,65), c(30,110,45), c(20,80,30), c(12,55,20),
        c(6,35,12), c(4,20,6), c(0,0,0),
        c(0,0,0), c(4,12,4), c(8,24,8), c(2,8,2),
        c(0,70,20), c(0,45,12),
        c(0,255,65), c(0,160,40), c(0,100,25),
        c(0,230,60), c(40,255,100), c(0,230,60,30), c(0,230,60,30), c(0,230,60,80),
        c(255,50,50), c(0,255,65), c(0,255,65,30), c(200,220,0), c(200,220,0,30),
        f("Consolas",Font.PLAIN,13), f("Consolas",Font.PLAIN,11), f("Consolas",Font.PLAIN,12),
        f("Consolas",Font.BOLD,13), f("Consolas",Font.BOLD,11), f("Consolas",Font.PLAIN,14),
        f("Consolas",Font.BOLD,10),
        0, 0, 0),

    BRUTALIST("Raw Concrete",
        c(20,20,20), c(40,40,40), c(60,60,60), c(90,90,90),
        c(120,120,120), c(150,150,150), c(180,180,180), c(210,210,210),
        c(230,230,225), c(240,240,235), c(245,245,240),
        c(245,245,240), c(240,240,235), c(230,230,225), c(255,255,255),
        c(0,0,0), c(180,180,180),
        c(0,0,0), c(80,80,80), c(130,130,130),
        c(220,0,0), c(255,40,40), c(220,0,0,25), c(220,0,0,25), c(220,0,0,80),
        c(220,0,0), c(0,130,0), c(0,130,0,25), c(180,120,0), c(180,120,0,25),
        f("Consolas",Font.PLAIN,14), f("Consolas",Font.PLAIN,12), f("Consolas",Font.PLAIN,13),
        f("Consolas",Font.BOLD,14), f("Consolas",Font.BOLD,12), f("Consolas",Font.PLAIN,15),
        f("Consolas",Font.BOLD,11),
        0, 0, 0),

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

    BLUEPRINT("Technical Drawing",
        c(192,216,240), c(170,195,220), c(140,168,196), c(110,140,170),
        c(80,112,148), c(55,85,120), c(38,62,94), c(25,48,78),
        c(16,34,58), c(12,26,46), c(8,18,34),
        c(8,18,34), c(12,26,46), c(16,34,58), c(10,22,40),
        c(30,55,90), c(22,42,70),
        c(192,216,240), c(100,135,175), c(65,95,132),
        c(255,255,255), c(220,235,255), c(255,255,255,25), c(255,255,255,25), c(255,255,255,60),
        c(255,80,80), c(100,200,255), c(100,200,255,30), c(255,200,100), c(255,200,100,30),
        f("Consolas",Font.PLAIN,13), f("Consolas",Font.PLAIN,11), f("Consolas",Font.PLAIN,12),
        f("Consolas",Font.BOLD,13), f("Consolas",Font.BOLD,11), f("Consolas",Font.PLAIN,14),
        f("Consolas",Font.BOLD,10),
        4, 2, 6),

    WARM_MINIMAL("Cozy Dark",
        c(250,250,249), c(245,245,244), c(231,229,228), c(214,211,209),
        c(168,162,158), c(120,113,108), c(87,83,78), c(68,64,60),
        c(41,37,36), c(28,25,23), c(12,10,9),
        c(12,10,9), c(28,25,23), c(41,37,36), c(33,30,28),
        c(41,37,36), c(50,45,42),
        c(250,250,249), c(168,162,158), c(120,113,108),
        c(245,158,11), c(251,191,36), c(245,158,11,30), c(245,158,11,30), c(245,158,11,80),
        c(239,68,68), c(34,197,94), c(34,197,94,30), c(245,158,11), c(245,158,11,30),
        f("Segoe UI",Font.PLAIN,14), f("Segoe UI",Font.PLAIN,12), f("Segoe UI",Font.PLAIN,13),
        f("Segoe UI",Font.BOLD,14), f("Segoe UI",Font.BOLD,12), f("Consolas",Font.PLAIN,15),
        f("Segoe UI",Font.BOLD,11),
        10, 8, 14),

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
        4, 2, 8),

    PAPER_INK("Light Minimal",
        c(26,26,26), c(45,45,45), c(70,70,70), c(100,100,100),
        c(130,130,130), c(160,160,160), c(190,190,190), c(215,215,215),
        c(235,235,235), c(245,245,245), c(255,255,255),
        c(255,255,255), c(250,250,250), c(242,242,242), c(255,255,255),
        c(200,200,200), c(225,225,225),
        c(26,26,26), c(100,100,100), c(150,150,150),
        c(26,39,68), c(40,60,100), c(26,39,68,20), c(26,39,68,20), c(26,39,68,60),
        c(180,30,30), c(20,120,60), c(20,120,60,20), c(160,110,20), c(160,110,20,20),
        null, null, null,
        f("Georgia",Font.BOLD,13), f("Georgia",Font.BOLD,11), f("Consolas",Font.PLAIN,14),
        f("Georgia",Font.BOLD,10),
        4, 2, 6),

    GRAIN("Textural Organic",
        c(224,221,216), c(205,200,194), c(180,174,166), c(155,148,138),
        c(125,118,108), c(96,90,82), c(72,67,60), c(52,48,42),
        c(38,35,30), c(28,26,22), c(18,17,14),
        c(18,17,14), c(28,26,22), c(38,35,30), c(24,22,18),
        c(52,48,42), c(42,39,34),
        c(224,221,216), c(145,138,128), c(105,98,88),
        c(196,101,74), c(220,130,100), c(196,101,74,35), c(196,101,74,35), c(196,101,74,80),
        c(200,60,50), c(107,143,113), c(107,143,113,35), c(200,165,70), c(200,165,70,35),
        null, null, null, null, null, null, null,
        10, 8, 14),

    MISSION_CONTROL("Data Dashboard",
        c(220,240,255), c(195,215,235), c(160,185,210), c(125,150,180),
        c(90,118,152), c(60,85,115), c(40,60,85), c(25,42,65),
        c(15,28,48), c(12,20,35), c(8,12,22),
        c(8,12,22), c(12,20,35), c(15,28,48), c(10,16,28),
        c(30,70,110), c(20,45,75),
        c(220,240,255), c(100,140,180), c(60,90,125),
        c(0,212,255), c(60,230,255), c(0,212,255,30), c(0,212,255,30), c(0,212,255,80),
        c(255,50,50), c(50,220,100), c(50,220,100,30), c(255,165,0), c(255,165,0,30),
        f("Consolas",Font.PLAIN,13), f("Consolas",Font.PLAIN,11), f("Consolas",Font.PLAIN,12),
        f("Consolas",Font.BOLD,13), f("Consolas",Font.BOLD,11), f("Consolas",Font.PLAIN,14),
        f("Consolas",Font.BOLD,10),
        0, 0, 2);

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
