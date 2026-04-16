# Command-Line Interface

The Granular Spectrum Synthesizer has a headless command-line interface for running offline renders and inspecting spectral content without opening the GUI. This document is the narrative reference; `./granular --help` is the authoritative flag listing.

## Overview

Two subcommands are available in v1:

- **`render`** &mdash; run the full offline pipeline (FFT → grain placement → palindrome → dynamics → stereo output) and write a `.wav` to disk.
- **`analyze`** &mdash; dump per-window spectral peaks as text or CSV, useful for inspecting a sample before deciding how to render it.

Live MIDI is **not** available through the CLI in v1. Use the GUI for real-time playback.

The mental model is: **preset files hold the slow-moving parameters; CLI flags are one-shot overrides.** Envelopes (density, mix, dynamics, pitch) are time-varying curves that can only be drawn in the GUI. Save a preset from the GUI once, then use the CLI to batch-render variations. This is covered in detail under [Presets](#presets).

## Install / Build

Requires JDK 21 and Maven.

```sh
mvn clean package
./granular --help            # macOS / Linux / Git Bash
granular.bat --help          # Windows cmd / PowerShell
```

`mvn package` builds the CLI fat jar at `target/granular-spectrum-demo-1.0.0-jar-with-dependencies.jar`. The `granular.sh` and `granular.bat` wrappers at the repo root auto-locate that jar and put the local `lib/` contents on the classpath. Run them from the repo root — relative paths you pass to `--source`, `--grain`, and `--ir` are resolved against your current working directory.

## Quickstart

Three copy-pasteable recipes:

```sh
# 1. Render using only defaults, no preset
./granular render --source ../samples/Cello/bowedCello1.wav --grain ../samples/bell.wav \
                  --ir ../samples/cathedral.wav -o cello.wav

# 2. Render from a preset you saved in the GUI
./granular render --preset presets/my_cello.properties -o out.wav

# 3. Render a preset but override reverb and output path
./granular render --preset presets/my_cello.properties --no-reverb -o dry.wav
```

## `render` &mdash; flag reference

Flags you do not set keep the preset value (or the built-in default if no preset is loaded). Boolean flags are negatable: `--reverb` / `--no-reverb`, `--palindrome` / `--no-palindrome`, `--dynamics` / `--no-dynamics`.

### Files

Tells the synth which samples to use and where to write the output.

| Flag | Type | Notes |
|---|---|---|
| `--preset <file>` | path | `.properties` preset saved by the GUI. Seeds every parameter, including envelopes. |
| `--source <file>` | path | Source `.wav` whose spectrum drives grain placement. Required if no preset. |
| `--grain <file>` | path | Grain `.wav`, e.g. `../samples/bell.wav`. Required if no preset. |
| `--ir <file>` | path | Impulse response `.wav` for convolution reverb. Required if reverb is on. |
| `-o`, `--output <file>` | path | Output `.wav`. Default `out.wav`. Overwrites existing files. |

### Source region

Trims the source before analysis. Output length scales with the region.

| Flag | Type | Range |
|---|---|---|
| `--source-start <frac>` | double | 0.0 – 1.0 |
| `--source-end <frac>`   | double | 0.0 – 1.0 |

### Grain synth

How the grain sample is tuned and placed.

| Flag | Type | Notes |
|---|---|---|
| `--grain-freq <Hz>` | double | Reference pitch of the grain sample. The GUI normally auto-detects this on file load. |

### FFT

Controls the windowed spectral analysis driving grain placement. Bigger window = more frequency resolution but less time resolution.

| Flag | Type | Notes |
|---|---|---|
| `--window-exp <int>` | int | Window size = 2^N samples. Default `14` (16384). Useful range 12–16. |
| `--control-rate <sec>` | double | Seconds between FFT windows. Default `0.1`. Smaller = denser analysis, more work. |
| `--grains-per-peak <int>` | int | Grains spawned per spectral peak per window. |
| `--amp-threshold <0..1>` | double | Minimum peak amplitude for grain spawn. |

### Reverb

Convolution reverb with separate wet/dry for the source and the granular layers.

| Flag | Type | Range |
|---|---|---|
| `--reverb` / `--no-reverb` | boolean | |
| `--source-reverb-mix <frac>` | double | 0.0 – 1.0 |
| `--synth-reverb-mix <frac>` | double | 0.0 – 1.0 |

### Palindrome

Plays the render forward, then crossfades into a reversed copy. Fun for ambient loops.

| Flag | Type | Notes |
|---|---|---|
| `--palindrome` / `--no-palindrome` | boolean | |
| `--crossfade-duration <sec>` | double | Length of the fwd↔rev crossfade. |
| `--crossfade-curve <-1..1>` | double | Shape: `-1` concave, `0` linear, `+1` convex. |
| `--crossfade-overlap <0..1>` | double | `0` = standard crossfade, `1` = full overlap plateau. |

### Dynamics

Amplitude envelope applied to the rendered mix.

| Flag | Type | Notes |
|---|---|---|
| `--dynamics` / `--no-dynamics` | boolean | |
| `--dramatic-factor <double>` | double | Controls exponential dynamics shape (0 = linear). |

The envelope curve itself comes from the preset &mdash; there is no CLI flag for it.

### Output shaping

| Flag | Type | Notes |
|---|---|---|
| `--pan-smoothing <0..1>` | double | Stereo pan ramp smoothing. |

### CLI behavior

| Flag | Notes |
|---|---|
| `-q`, `--quiet` | Suppress progress bar output. |
| `-h`, `--help` | Show the help for this subcommand. |

### Exit codes

| Code | Meaning |
|---|---|
| `0` | Render succeeded. |
| `1` | User error (missing file, bad flag, invalid preset). |
| `2` | Render failure (internal error during synthesis or output write). |

## `analyze` &mdash; flag reference

### What is this for?

`analyze` dumps the per-window FFT peak list that `render` would consume internally. Use it to inspect a sample's spectral content *before* you pick render parameters &mdash; for example, to choose a `--window-exp` that catches the partials you care about, or to decide whether `--amp-threshold` should be raised to ignore noise.

### Flags

| Flag | Type | Default |
|---|---|---|
| `--source <file>` (required) | path | — |
| `--window-exp <int>` | int | `14` (2^14 = 16384 samples) |
| `--control-rate <sec>` | double | `0.1` |
| `--format <text\|csv>` | enum | `text` |
| `-o`, `--output <file>` | path | stdout |

### Output formats

**Text** (human-readable, default):

```
# fundamental=219.82 Hz  windows=430  windowDur=0.100s
window=0 time=0.100s peaks=4
      220.50  0.4120
      441.20  0.2870
      661.80  0.1410
      882.40  0.0650
window=1 time=0.200s peaks=5
  ...
```

**CSV** (for pipelines into Python, R, spreadsheets):

```
window,time_sec,freq_hz,amplitude
0,0.100,220.500,0.412000
0,0.100,441.200,0.287000
0,0.100,661.800,0.141000
...
```

### Worked example

```sh
./granular analyze --source ../samples/Cello/bowedCello1.wav --format csv -o peaks.csv
```

Then in Python:

```python
import pandas as pd
df = pd.read_csv("peaks.csv")
df.groupby("window").size().plot(title="peak count per window")
```

If you don't set `-o`, CSV goes to stdout and all the progress chatter is redirected to stderr so the stdout stream stays clean for piping:

```sh
./granular analyze --source ../samples/Cello/bowedCello1.wav --format csv > peaks.csv
```

## Presets

Presets are `.properties` files written by the GUI. They contain every parameter in `SynthParameters`, including envelope curves encoded as comma-separated arrays. The CLI reads them via the same `PresetManager` the GUI uses, so any preset the GUI can save, the CLI can load.

### Recommended workflow

1. Open the GUI, dial in a sound you like, click Save Preset.
2. Use the CLI to batch-render variations with flag overrides (e.g. different sources, reverb on/off, different source regions).

### Preset file format

Presets are plain text. A short excerpt:

```properties
sourceFile=../samples/Cello/bowedCello1.wav
grainFile=../samples/bell.wav
impulseResponseFile=../samples/cathedral.wav
sourceStartFraction=0.0
sourceEndFraction=1.0
grainReferenceFreq=1287.0
windowSizeExponent=14
controlRate=0.1
grainsPerPeak=15
amplitudeThreshold=0.05
useReverb=true
sourceReverbMix=0.2
synthReverbMix=0.3
probEnv.times=0.0,0.6,0.8,0.9,1.0
probEnv.values=0.0,0.1,1.0,1.0,0.0
mixEnv.times=0.0,0.7,1.0
mixEnv.values=0.0,0.0,1.0
```

Sample paths inside a preset are resolved relative to your current working directory at render time. Running the CLI from the repo root is the safest bet &mdash; otherwise edit the preset to use absolute paths.

### Envelopes

The four envelope parameters (`probEnv`, `mixEnv`, `dynamicsEnv`, `pitchEnv`) plus `dramaticEnvShape` are time-varying curves. **There are no CLI flags for them.** If you want different curves, either edit the envelope in the GUI and re-save the preset, or hand-edit the `.properties` file (the format is `name.times=` / `name.values=` comma-separated floats).

## Batch rendering

The main reason to use the CLI: rendering a directory of samples through the same preset without clicking.

**Bash / Git Bash:**

```sh
mkdir -p renders
for src in samples/*.wav; do
  name=$(basename "$src" .wav)
  ./granular render --preset presets/cello.properties --source "$src" \
                    -o "renders/${name}_rendered.wav"
done
```

**Windows cmd:**

```bat
mkdir renders
for %%f in (samples\*.wav) do (
  granular.bat render --preset presets\cello.properties --source "%%f" ^
                      -o "renders\%%~nf_rendered.wav"
)
```

## Headless environments

The CLI sets `java.awt.headless=true` at startup, so a display server is not required. You can run renders on a headless Linux box, in CI, inside a container, or over SSH without issue. Audio output (live MIDI playback) is *not* supported headless &mdash; only `render` and `analyze` are.

## Troubleshooting

- **`Jar not built. Run: mvn package`** &mdash; You ran `./granular` before building. `mvn clean package` once, then try again.
- **`Source file not found`** &mdash; The path you gave (or the path inside a preset) is resolved relative to your current directory. Run from the repo root, or use an absolute path.
- **`Impulse response file not found`** &mdash; Either provide `--ir path/to/ir.wav` or disable reverb with `--no-reverb`.
- **`OutOfMemoryError` during render** &mdash; Long samples plus a fine `--control-rate` can be memory-hungry. Raise the heap: `JAVA_TOOL_OPTIONS=-Xmx4g ./granular render …`.
- **Rendered output sounds different from the GUI** &mdash; It shouldn't; GUI and CLI call the same `OfflineRenderer` code path. If it really does, file a bug and attach the preset.
- **`UnsupportedAudioFileException`** &mdash; Source must be a PCM WAV (16 or 24-bit). Convert other formats with `sox` or `ffmpeg` first.

## Appendix: CLI flags ↔ GUI controls

For anyone who knows the GUI and wants to learn the CLI by analogy. Obvious mappings like `--source` are omitted.

| CLI flag | GUI control |
|---|---|
| `--window-exp` | "Window Size" dropdown (the exponent; 14 = 16384) |
| `--control-rate` | "Control Rate" field (seconds between FFT windows) |
| `--grains-per-peak` | "Grains/Peak" field in the parameter panel |
| `--amp-threshold` | "Amp Threshold" slider |
| `--grain-freq` | "Grain Ref. Freq." field (auto-detected on load) |
| `--source-reverb-mix` | "Source Reverb" slider |
| `--synth-reverb-mix` | "Synth Reverb" slider |
| `--pan-smoothing` | "Pan Smoothing" slider |
| `--palindrome` | "Palindrome" toggle |
| `--crossfade-duration` | Palindrome editor: crossfade length |
| `--crossfade-curve` | Palindrome editor: curve shape slider |
| `--crossfade-overlap` | Palindrome editor: overlap plateau |
| `--dramatic-factor` | "Dramatic" slider in the dynamics group |
| `--dynamics` | "Use Dynamics Env" checkbox |
