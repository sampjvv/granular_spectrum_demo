# Granular Spectrum Synthesizer

Spectral analysis-driven granular synthesizer that transforms source audio into evolving textural compositions. Detects spectral peaks via FFT, then resynthesizes them as grain clouds with configurable density, mix, dynamics, and pitch envelopes.

Originally developed by [Tim McDunn](https://github.com/twmcdunn/granular_spectrum_demo). Extended with a full GUI, real-time MIDI mode, library system, and region-based rendering.

## Quick Start

```bash
mvn clean package
mvn exec:java
# or: java -cp "target/classes;lib/*" org.delightofcomposition.AppMain
```

Requires **Java 11+**. Depends on [Apache Commons Math 3.6.1](https://commons.apache.org/proper/commons-math/) (included in `lib/`).

## Modes

### WAV Render (Offline)
Renders the full granular spectrum synthesis to a stereo buffer for playback and export. Parallel FFT processing across all CPU cores.

### Live MIDI (Real-Time)
16-voice polyphonic playback driven by MIDI controller or on-screen piano keyboard. Pre-renders 5 granular layers at varying densities, then pitch-shifts and blends them per-voice in real time. Live controls for mix, density, and pan.

### Headless CLI
Offline `render` and `analyze` subcommands for driving the synth from a terminal, shell script, or CI &mdash; no GUI required. After `mvn package`:

```sh
./granular render --preset presets/my_cello.properties -o out.wav
./granular analyze --source ../samples/Cello/bowedCello1.wav --format csv -o peaks.csv
```

See [`docs/CLI.md`](docs/CLI.md) for the full reference, batch examples, and how CLI flags map to GUI controls.

## Core Features

| Feature | Description |
|---------|-------------|
| **4 Envelope Editors** | Density (grain probability), Mix (source vs granular), Dynamics (amplitude), Pitch (frequency ratio). Draggable nodes, undo, zoom, waveform backgrounds. |
| **Source Region Selector** | Waveform display with draggable start/end markers. Output duration adjusts for pitch: 10s region at half-speed pitch = 20s output. |
| **Chord Mode** | Just intonation chords from a single source. Configurable ratios, per-voice attack offsets, forward/reverse crossfade. |
| **Library** | Save renders (WAV + params) and live presets. Browse, play, load, update, rename, delete. Shape-aware auto-naming from synthesis parameters (e.g. `building-cavernous-bowedCello1-bell`). |
| **Auto Pitch Detection** | FFT-based fundamental detection on grain samples. Parabolic interpolation for sub-bin accuracy. Runs on file selection and startup. |
| **Reverb** | Convolution reverb with separate wet/dry mix for source and granular components. |
| **Themes** | Runtime-switchable color themes with full UI restyling. |

## How It Works

1. **Spectral Analysis** &mdash; Sliding FFT windows extract spectral peaks (frequency + amplitude) from the source sample
2. **Grain Placement** &mdash; For each peak, grains are probabilistically placed based on the density envelope. Each grain is a pitch-shifted copy of the grain sample, tuned to the detected peak frequency.
3. **Source Mix** &mdash; The original source audio (optionally pitch-shifted and reverbed) is blended with the granular texture via the mix envelope
4. **Output Scaling** &mdash; Output length = selected region / average pitch ratio. All envelopes stretch across the full adjusted duration.

## Project Structure

```
src/main/java/org/delightofcomposition/
  Demo.java                  Core synthesis algorithm
  RenderController.java      Offline render orchestration
  SynthParameters.java       Central parameter model
  gui/
    MainWindow.java          Main application frame
    EnvelopeEditorPanel.java 4-envelope editor grid
    ParameterPanel.java      Synth parameter controls
    SourceRegionSelector.java  Waveform region picker
    LibraryPanel.java        Save/load sidebar
    SoundLibrary.java        Library file management + naming
    PianoKeyboard.java       On-screen MIDI keyboard
    WaveformDisplay.java     Rendered output visualization
    Theme.java               UI theming system
  realtime/
    AudioEngine.java         48kHz stereo output thread
    Voice.java               Per-voice state + render
    LiveMidiController.java  Layer pre-render + MIDI routing
  sound/
    FFT2.java                FFT with pitch detection
    WaveWriter.java          Stereo WAV export (48kHz/16-bit)
    Reverb.java              Convolution reverb
    AudioPlayer.java         Real-time playback
  synth/
    SimpleSynth.java         Grain synthesis engine
resources/
  fonts/                     UI fonts
../samples/                  Shared sample library (outside this repo)
```

## Samples

Default samples (cello, bells, bass, tuba, plucked strings, piano, cathedral IR) live in the shared `ComputerMusic/samples/` directory one level above this repo. The code references them via `../samples/`. Drop any `.wav` file onto the sample panels in the GUI to use your own.

## Library Naming

Saved entries are auto-named by analyzing envelope shapes and synthesis parameters:

- **Envelope trajectory** (rising, falling, peaked, etc.) maps to adjectives like *building*, *dissolving*, *blooming*
- **Two most distinctive axes** are selected (texture, character, space, intensity, pitch)
- Collisions swap the second adjective to the next most different axis, then fall back to numeric suffixes

Example: `blooming-cavernous-bowedCello1-bell`
