package org.delightofcomposition.gui;

import java.awt.Color;
import java.awt.Font;

/**
 * Defines all available color themes. Each preset provides the full set of
 * colors, fonts, and corner-radii consumed by {@link Theme}.
 */
public enum ThemePreset {

    PAPER("Paper",
        /* ZINC 50-950: darks for text, rest near-white */
        c(30,30,30), c(50,50,50), c(90,90,90), c(140,140,140),
        c(200,200,200), c(210,210,210), c(222,222,222), c(234,234,234),
        c(242,242,242), c(250,250,250), c(255,255,255),
        /* BG, BG_CARD, BG_MUTED, BG_INPUT */
        c(252,252,252), c(255,255,255), c(240,240,240), c(248,248,248),
        /* BORDER, BORDER_SUBTLE */
        c(225,225,225), c(238,238,238),
        /* FG, FG_MUTED, FG_DIM */
        c(30,30,30), c(140,140,140), c(195,195,195),
        /* ACCENT (blue), ACCENT_HOVER, ACCENT_MUTED, ACCENT_FILL, RING */
        c(0xd0,0xd7,0xf0), c(0xd0,0xd7,0xf0), c(0xd0,0xd7,0xf0,12), c(0xd0,0xd7,0xf0,12), c(0xd0,0xd7,0xf0,40),
        /* DESTRUCTIVE (pink for pitch env), SUCCESS (green), SUCCESS_FILL, AMBER (yellow for dynamics env), AMBER_FILL */
        c(0xe8,0x90,0xb0), c(0xc2,0xe8,0xc2), c(0x9c,0xe0,0x9c,20), c(0xe0,0xd0,0x70), c(0xe0,0xd0,0x70,20),
        /* fonts */
        null, null, null, null, null, null, null,
        /* radii */
        6, 4, 10),

    PAPER_MINIMALIST("Paper Minimalist",
        /* ZINC 50-950: pure grayscale, warm-tinted (dark→light) */
        c(0x44,0x44,0x44), c(0x55,0x55,0x55), c(0x77,0x77,0x77), c(0xAA,0xAA,0xAA),
        c(0xD5,0xD5,0xD5), c(0xDD,0xDD,0xDD), c(0xE6,0xE6,0xE6), c(0xEE,0xEE,0xEE),
        c(0xF4,0xF4,0xF4), c(0xFA,0xFA,0xFA), c(0xFF,0xFE,0xFD),
        /* BG (cooled-down page), BG_CARD/INPUT (light warm), BG_MUTED */
        c(0xFB,0xF9,0xF6), c(0xFA,0xF8,0xF5), c(0xF0,0xEE,0xEB), c(0xFA,0xF8,0xF5),
        /* BORDER, BORDER_SUBTLE */
        c(0xDD,0xDD,0xDD), c(0xEE,0xEE,0xEE),
        /* FG, FG_MUTED, FG_DIM — grayscale charcoal */
        c(0x44,0x44,0x44), c(0x77,0x77,0x77), c(0xAA,0xAA,0xAA),
        /* ACCENT — grayscale (TEMPORARY) */
        c(0x66,0x66,0x66), c(0x55,0x55,0x55), c(0x66,0x66,0x66,10), c(0x66,0x66,0x66,10), c(0x66,0x66,0x66,30),
        /* DESTRUCTIVE, SUCCESS, SUCCESS_FILL, AMBER, AMBER_FILL — all grayscale (TEMPORARY) */
        c(0x88,0x88,0x88), c(0x88,0x88,0x88), c(0x88,0x88,0x88,15), c(0x88,0x88,0x88), c(0x88,0x88,0x88,15),
        /* fonts — Space Mono for section headers, rest default */
        null, null, null, null,
        new java.awt.Font("Space Mono", java.awt.Font.BOLD, 14),
        null,
        new java.awt.Font("Space Mono", java.awt.Font.BOLD, 14),
        /* radii */
        8, 4, 12),

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
        /* DESTRUCTIVE (coral for pitch env), SUCCESS, SUCCESS_FILL, AMBER, AMBER_FILL */
        c(240,128,88), c(34,197,94), c(34,197,94,30), c(245,158,11), c(245,158,11,30),
        /* fonts (null = keep defaults) */
        null, null, null, null, null, null, null,
        /* RADIUS, RADIUS_SM, RADIUS_LG (-1 = keep defaults) */
        -1, -1, -1),

    NEON_OUTRUN("Synthwave",
        /* ZINC 50-950: purple-tinted grayscale */
        c(232,224,240), c(220,210,240), c(180,170,210), c(157,142,194),
        c(123,94,167), c(93,74,137), c(59,42,122), c(45,27,105),
        c(59,42,122), c(45,27,105), c(26,10,46),
        /* BG, BG_CARD, BG_MUTED, BG_INPUT */
        c(26,10,46), c(45,27,105), c(59,42,122), c(35,18,75),
        /* BORDER, BORDER_SUBTLE */
        c(123,94,167), c(93,74,137),
        /* FG, FG_MUTED, FG_DIM */
        c(232,224,240), c(157,142,194), c(123,94,167),
        /* ACCENT, ACCENT_HOVER, ACCENT_MUTED, ACCENT_FILL, RING */
        c(255,45,149), c(255,80,170), c(255,45,149,35), c(255,45,149,35), c(255,45,149,80),
        /* DESTRUCTIVE, SUCCESS (cyan), SUCCESS_FILL, AMBER, AMBER_FILL */
        c(239,68,68), c(34,211,238), c(34,211,238,30), c(251,191,36), c(251,191,36,30),
        /* fonts: null = overridden in Theme.applyTheme() with SynthwaveFonts */
        null, null, null, null, null, null, null,
        /* radii: -1 = keep defaults (pixel corners are handled by SynthwavePainter) */
        -1, -1, -1);

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
