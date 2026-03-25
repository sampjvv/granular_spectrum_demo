package org.delightofcomposition.gui;

import java.io.File;
import java.util.prefs.Preferences;

/**
 * Persists the user's last-used sample file paths across sessions.
 */
public class SamplePreferences {

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(SamplePreferences.class);

    private static final String KEY_SOURCE = "sourceFile";
    private static final String KEY_GRAIN = "grainFile";
    private static final String KEY_IR = "impulseResponseFile";

    public static File loadSourceFile(File fallback) {
        return loadFile(KEY_SOURCE, fallback);
    }

    public static File loadGrainFile(File fallback) {
        return loadFile(KEY_GRAIN, fallback);
    }

    public static File loadImpulseResponseFile(File fallback) {
        return loadFile(KEY_IR, fallback);
    }

    public static void saveSourceFile(File file) {
        PREFS.put(KEY_SOURCE, file.getAbsolutePath());
    }

    public static void saveGrainFile(File file) {
        PREFS.put(KEY_GRAIN, file.getAbsolutePath());
    }

    public static void saveImpulseResponseFile(File file) {
        PREFS.put(KEY_IR, file.getAbsolutePath());
    }

    private static File loadFile(String key, File fallback) {
        String path = PREFS.get(key, null);
        if (path != null) {
            File f = new File(path);
            if (f.exists()) return f;
        }
        return fallback;
    }
}
