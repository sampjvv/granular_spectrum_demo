package org.delightofcomposition.gui;

import java.util.prefs.Preferences;

/**
 * Persists the user's selected theme via {@link java.util.prefs.Preferences}.
 */
public class ThemePreferences {

    private static final String KEY = "theme";
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(ThemePreferences.class);

    public static ThemePreset load() {
        String name = PREFS.get(KEY, ThemePreset.DEFAULT_DARK.name());
        try {
            return ThemePreset.valueOf(name);
        } catch (IllegalArgumentException e) {
            return ThemePreset.DEFAULT_DARK;
        }
    }

    public static void save(ThemePreset preset) {
        PREFS.put(KEY, preset.name());
    }
}
