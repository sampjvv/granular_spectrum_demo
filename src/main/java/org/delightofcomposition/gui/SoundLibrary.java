package org.delightofcomposition.gui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.delightofcomposition.SynthParameters;
import org.delightofcomposition.sound.WaveWriter;

/**
 * File system model for the sound library.
 * Manages ~/GranularLibrary/ with renders/ and live-presets/ subdirectories.
 */
public class SoundLibrary {

    private static final String LIB_DIR = "GranularLibrary";
    private static final String RENDERS_DIR = "renders";
    private static final String LIVE_PRESETS_DIR = "live-presets";
    private static final String NAME_KEY = "_libraryName";

    public static File getLibraryRoot() {
        File root = new File(System.getProperty("user.home"), LIB_DIR);
        root.mkdirs();
        return root;
    }

    public static File getRendersDir() {
        File dir = new File(getLibraryRoot(), RENDERS_DIR);
        dir.mkdirs();
        return dir;
    }

    public static File getLivePresetsDir() {
        File dir = new File(getLibraryRoot(), LIVE_PRESETS_DIR);
        dir.mkdirs();
        return dir;
    }

    private static String generateTimestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }

    // ── Shape-aware adjective naming ──

    private enum EnvShape { FLAT_LOW, FLAT_MID, FLAT_HIGH, RISING, FALLING, PEAKED, VALLEY, COMPLEX }

    // Texture (probEnv — grain density)
    private static final java.util.Map<EnvShape, String> TEXTURE_ADJ = new java.util.LinkedHashMap<>();
    static {
        TEXTURE_ADJ.put(EnvShape.FLAT_LOW, "wispy");
        TEXTURE_ADJ.put(EnvShape.FLAT_MID, "textured");
        TEXTURE_ADJ.put(EnvShape.FLAT_HIGH, "saturated");
        TEXTURE_ADJ.put(EnvShape.RISING, "building");
        TEXTURE_ADJ.put(EnvShape.FALLING, "dissolving");
        TEXTURE_ADJ.put(EnvShape.PEAKED, "blooming");
        TEXTURE_ADJ.put(EnvShape.VALLEY, "hollowed");
        TEXTURE_ADJ.put(EnvShape.COMPLEX, "shifting");
    }

    // Character (mixEnv — source vs granular)
    private static final java.util.Map<EnvShape, String> CHARACTER_ADJ = new java.util.LinkedHashMap<>();
    static {
        CHARACTER_ADJ.put(EnvShape.FLAT_LOW, "crystalline");
        CHARACTER_ADJ.put(EnvShape.FLAT_MID, "blended");
        CHARACTER_ADJ.put(EnvShape.FLAT_HIGH, "organic");
        CHARACTER_ADJ.put(EnvShape.RISING, "emerging");
        CHARACTER_ADJ.put(EnvShape.FALLING, "fragmenting");
        CHARACTER_ADJ.put(EnvShape.PEAKED, "pulsing");
        CHARACTER_ADJ.put(EnvShape.VALLEY, "ghostly");
        CHARACTER_ADJ.put(EnvShape.COMPLEX, "morphing");
    }

    // Space (reverb — single value)
    private static final String[] SPACE_ADJ = {"dry", "intimate", "spacious", "cavernous"};
    // Intensity (dramaticFactor)
    private static final String[] INTENSITY_ADJ = {"gentle", "subtle", "moderate", "dramatic", "explosive"};
    // Pitch deviation
    private static final String[] PITCH_ADJ = {"natural", "bent", "warped", "twisted"};

    /** Classify an envelope's shape by sampling it at 5 points. */
    private static EnvShape classifyEnvelope(org.delightofcomposition.envelopes.Envelope env) {
        if (env == null) return EnvShape.FLAT_MID;
        double[] s = new double[5];
        for (int i = 0; i < 5; i++) s[i] = env.getValue(i / 4.0);

        double start = s[0], q1 = s[1], mid = s[2], q3 = s[3], end = s[4];
        double avg = (start + q1 + mid + q3 + end) / 5.0;
        double range = 0;
        for (double v : s) range = Math.max(range, Math.abs(v - avg));

        // Flat: all values within a narrow band
        if (range < 0.15) {
            if (avg < 0.35) return EnvShape.FLAT_LOW;
            if (avg > 0.65) return EnvShape.FLAT_HIGH;
            return EnvShape.FLAT_MID;
        }

        // Rising: end significantly higher than start, mostly monotonic
        if (end - start > 0.25 && q1 <= mid && mid <= q3) return EnvShape.RISING;

        // Falling: start significantly higher than end
        if (start - end > 0.25 && q1 >= mid && mid >= q3) return EnvShape.FALLING;

        // Peaked: middle region higher than both ends
        double midRegion = (q1 + mid + q3) / 3.0;
        double endRegion = (start + end) / 2.0;
        if (midRegion - endRegion > 0.2) return EnvShape.PEAKED;

        // Valley: middle region lower than both ends
        if (endRegion - midRegion > 0.2) return EnvShape.VALLEY;

        return EnvShape.COMPLEX;
    }

    /** Score how "interesting" an envelope shape is (non-flat = more interesting). */
    private static double shapeInterest(EnvShape shape) {
        switch (shape) {
            case FLAT_MID: return 0.1;
            case FLAT_LOW: case FLAT_HIGH: return 0.3;
            case RISING: case FALLING: return 0.7;
            case PEAKED: case VALLEY: return 0.8;
            case COMPLEX: return 0.9;
            default: return 0.1;
        }
    }

    /** Generates a unique display name: two adjectives + source-grain, disambiguated against existing names. */
    private static String generateDisplayName(SynthParameters params) {
        String src = stripExtension(params.sourceFile != null ? params.sourceFile.getName() : "source");
        String grain = stripExtension(params.grainFile != null ? params.grainFile.getName() : "grain");
        String sampleSuffix = src + "-" + grain;

        // Compute all 5 adjectives
        String[] adjectives = computeAllAdjectives(params);

        // Rank axes by interestingness
        double[] scores = computeAxisScores(params);
        int[] ranked = rankIndices(scores);

        // Build name with top 2
        String name = adjectives[ranked[0]] + "-" + adjectives[ranked[1]] + "-" + sampleSuffix;

        // Check for collision against existing library entries
        java.util.Set<String> existingNames = new java.util.HashSet<>();
        for (LibraryEntry e : listRenders()) existingNames.add(e.displayName);
        for (LibraryEntry e : listLivePresets()) existingNames.add(e.displayName);

        if (existingNames.contains(name)) {
            // Find the axis (excluding the first-pick) whose adjective differs most
            // from what it would be with the colliding entry's params — try remaining axes in rank order
            for (int r = 2; r < ranked.length; r++) {
                String alt = adjectives[ranked[0]] + "-" + adjectives[ranked[r]] + "-" + sampleSuffix;
                if (!existingNames.contains(alt)) {
                    name = alt;
                    break;
                }
            }
            // If still colliding after trying all combos, append a number
            if (existingNames.contains(name)) {
                int n = 2;
                String base = name;
                while (existingNames.contains(name)) {
                    name = base + "-" + n;
                    n++;
                }
            }
        }

        return name;
    }

    private static String[] computeAllAdjectives(SynthParameters params) {
        EnvShape textureShape = classifyEnvelope(params.probEnv);
        EnvShape characterShape = classifyEnvelope(params.mixEnv);
        double spaceVal = params.useReverb ? 0.3 + 0.7 * params.sourceReverbMix : 0.0;
        double intensityVal = Math.min(1.0, params.dramaticFactor / 5.0);
        double pitchDev = computePitchDeviation(params);

        return new String[] {
            TEXTURE_ADJ.get(textureShape),
            CHARACTER_ADJ.get(characterShape),
            pickFromArray(SPACE_ADJ, spaceVal),
            pickFromArray(INTENSITY_ADJ, intensityVal),
            pickFromArray(PITCH_ADJ, pitchDev)
        };
    }

    private static double[] computeAxisScores(SynthParameters params) {
        return new double[] {
            shapeInterest(classifyEnvelope(params.probEnv)),
            shapeInterest(classifyEnvelope(params.mixEnv)),
            params.useReverb && params.sourceReverbMix > 0.05
                    ? 0.3 + 0.7 * params.sourceReverbMix : 0,
            Math.min(1.0, params.dramaticFactor / 5.0),
            computePitchDeviation(params)
        };
    }

    private static double computePitchDeviation(SynthParameters params) {
        if (!params.usePitchEnv || params.pitchEnv == null) return 0.0;
        double max = 0;
        for (int i = 0; i < 5; i++) {
            double v = params.pitchEnv.getValue(i / 4.0);
            max = Math.max(max, Math.abs(v - 1.0));
        }
        return Math.min(1.0, max * 2.0);
    }

    /** Returns indices sorted by descending score. */
    private static int[] rankIndices(double[] scores) {
        int n = scores.length;
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        // Simple insertion sort (only 5 elements)
        for (int i = 1; i < n; i++) {
            int key = idx[i];
            double keyScore = scores[key];
            int j = i - 1;
            while (j >= 0 && scores[idx[j]] < keyScore) {
                idx[j + 1] = idx[j];
                j--;
            }
            idx[j + 1] = key;
        }
        return idx;
    }

    private static String pickFromArray(String[] arr, double value) {
        value = Math.max(0, Math.min(1, value));
        int idx = (int) (value * (arr.length - 1));
        return arr[Math.max(0, Math.min(arr.length - 1, idx))];
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    /**
     * Saves a rendered buffer + params to the library.
     * Returns the WAV file path.
     */
    public static File saveRender(float[][] buffer, SynthParameters params) {
        String stem = "render_" + generateTimestamp();
        File dir = getRendersDir();
        File wavFile = new File(dir, stem + ".wav");
        File propsFile = new File(dir, stem + ".properties");

        writeWav(buffer, wavFile);
        PresetManager.save(params, propsFile.getAbsolutePath());

        // Set display name from source-grain
        renameEntry(new LibraryEntry(wavFile, propsFile, stem, 0), generateDisplayName(params));

        return wavFile;
    }

    /**
     * Updates an existing render entry in place (overwrites WAV + properties).
     */
    public static void updateRender(LibraryEntry entry, float[][] buffer, SynthParameters params) {
        if (entry.wavFile != null) writeWav(buffer, entry.wavFile);
        if (entry.propertiesFile != null) {
            // Preserve the custom name
            String existingName = readNameKey(entry.propertiesFile);
            PresetManager.save(params, entry.propertiesFile.getAbsolutePath());
            if (existingName != null) {
                renameEntry(new LibraryEntry(entry.wavFile, entry.propertiesFile, existingName, 0), existingName);
            }
        }
    }

    /**
     * Updates an existing live preset in place (overwrites properties).
     */
    public static void updateLivePreset(LibraryEntry entry, SynthParameters params) {
        if (entry.propertiesFile != null) {
            String existingName = readNameKey(entry.propertiesFile);
            PresetManager.save(params, entry.propertiesFile.getAbsolutePath());
            if (existingName != null) {
                renameEntry(new LibraryEntry(null, entry.propertiesFile, existingName, 0), existingName);
            }
        }
    }

    private static void writeWav(float[][] buffer, File wavFile) {
        WaveWriter ww = new WaveWriter("_lib", buffer[0].length);
        ww.df = new float[][] {
            Arrays.copyOf(buffer[0], buffer[0].length),
            Arrays.copyOf(buffer[1], buffer[1].length)
        };
        ww.renderToFile(wavFile.getAbsolutePath());
    }

    /**
     * Saves a live preset (no WAV).
     */
    public static File saveLivePreset(SynthParameters params) {
        String stem = "preset_" + generateTimestamp();
        File dir = getLivePresetsDir();
        File propsFile = new File(dir, stem + ".properties");
        PresetManager.save(params, propsFile.getAbsolutePath());

        // Set display name from source-grain
        renameEntry(new LibraryEntry(null, propsFile, stem, 0), generateDisplayName(params));

        return propsFile;
    }

    /**
     * Lists all renders, sorted by date descending.
     */
    public static List<LibraryEntry> listRenders() {
        List<LibraryEntry> entries = new ArrayList<>();
        File dir = getRendersDir();
        File[] wavFiles = dir.listFiles((d, name) -> name.endsWith(".wav"));
        if (wavFiles == null) return entries;

        for (File wav : wavFiles) {
            String stem = wav.getName().replace(".wav", "");
            File props = new File(dir, stem + ".properties");
            String displayName = stem;
            if (props.exists()) {
                String custom = readNameKey(props);
                if (custom != null) displayName = custom;
            }
            entries.add(new LibraryEntry(wav, props.exists() ? props : null,
                    displayName, wav.lastModified()));
        }

        entries.sort((a, b) -> Long.compare(b.dateMillis, a.dateMillis));
        return entries;
    }

    /**
     * Lists all live presets, sorted by date descending.
     */
    public static List<LibraryEntry> listLivePresets() {
        List<LibraryEntry> entries = new ArrayList<>();
        File dir = getLivePresetsDir();
        File[] propsFiles = dir.listFiles((d, name) -> name.endsWith(".properties"));
        if (propsFiles == null) return entries;

        for (File props : propsFiles) {
            String stem = props.getName().replace(".properties", "");
            String displayName = stem;
            String custom = readNameKey(props);
            if (custom != null) displayName = custom;
            entries.add(new LibraryEntry(null, props, displayName, props.lastModified()));
        }

        entries.sort((a, b) -> Long.compare(b.dateMillis, a.dateMillis));
        return entries;
    }

    public static void deleteEntry(LibraryEntry entry) {
        if (entry.wavFile != null) entry.wavFile.delete();
        if (entry.propertiesFile != null) entry.propertiesFile.delete();
    }

    public static void renameEntry(LibraryEntry entry, String newName) {
        File propsFile = entry.propertiesFile;
        if (propsFile == null || !propsFile.exists()) return;
        try {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(propsFile)) {
                props.load(fis);
            }
            props.setProperty(NAME_KEY, newName);
            try (FileOutputStream fos = new FileOutputStream(propsFile)) {
                props.store(fos, "Granular Spectrum Synthesizer Preset");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads a stereo WAV file back into float[2][n].
     */
    public static float[][] readStereoWav(File wavFile) {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile);
            AudioFormat fmt = ais.getFormat();
            int channels = fmt.getChannels();
            int bytesPerSample = fmt.getSampleSizeInBits() / 8;
            int frameCount = (int) ais.getFrameLength();
            byte[] data = new byte[(int) (ais.getFrameLength() * fmt.getFrameSize())];
            ais.read(data);
            ais.close();

            float[][] buf = new float[2][frameCount];
            boolean bigEndian = fmt.isBigEndian();

            for (int frame = 0; frame < frameCount; frame++) {
                for (int ch = 0; ch < Math.min(channels, 2); ch++) {
                    int offset = frame * channels * bytesPerSample + ch * bytesPerSample;
                    short sample;
                    if (bigEndian) {
                        sample = (short) (((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF));
                    } else {
                        sample = (short) (((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF));
                    }
                    buf[ch][frame] = sample / 32768f;
                }
                // If mono source, duplicate to right channel
                if (channels == 1) {
                    buf[1][frame] = buf[0][frame];
                }
            }
            return buf;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String readNameKey(File propsFile) {
        try {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(propsFile)) {
                props.load(fis);
            }
            return props.getProperty(NAME_KEY);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Represents a single library entry (render or live preset).
     */
    public static class LibraryEntry {
        public final File wavFile;         // null for live presets
        public final File propertiesFile;
        public final String displayName;
        public final long dateMillis;

        public LibraryEntry(File wavFile, File propertiesFile, String displayName, long dateMillis) {
            this.wavFile = wavFile;
            this.propertiesFile = propertiesFile;
            this.displayName = displayName;
            this.dateMillis = dateMillis;
        }
    }
}
