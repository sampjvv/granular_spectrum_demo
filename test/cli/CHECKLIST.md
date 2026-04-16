# CLI Test Checklist

Manual test checklist for the granular spectrum CLI. Tick through in order — smoke failures block everything below them. Assumes you're running from the repo root with the project built (`mvn clean package`). Commands use the Windows wrapper (`granular.bat`); substitute `./granular` on Unix.

Each item has an exact command, the expected result, and (where helpful) what the failure mode usually means.

## 0. Setup (do this once per test session)

- [ ] Clean build: `mvn clean package` exits 0 and ends with `BUILD SUCCESS`.
- [ ] Fat jar exists at `target/granular-spectrum-demo-1.0.0-jar-with-dependencies.jar`.
- [ ] Create a scratch directory for render outputs: `mkdir -p test/cli/out`. All test renders below land in here; delete the directory between runs if you want a clean slate.

---

## 1. Smoke

Fast checks that the artifact is well-formed. If any of these fail, stop and fix before going deeper.

- [ ] `granular.bat --help` prints root usage, lists both `render` and `analyze` as subcommands, exits 0.
- [ ] `granular.bat --version` prints `granular 1.0.0`, exits 0.
- [ ] `granular.bat render --help` lists all 22 render flags, including `--[no-]reverb`, `--[no-]palindrome`, `--[no-]dynamics`.
- [ ] `granular.bat analyze --help` lists `--source`, `--window-exp`, `--control-rate`, `--format`, `-o/--output`.
- [ ] `granular.bat bogus` exits non-zero, prints a usage hint on stderr.
- [ ] From a subdirectory: `cd docs && ..\granular.bat --help` still finds the jar and prints usage. Proves the wrapper's `%~dp0` self-location works. Return to repo root afterwards.
- [ ] Delete the jar temporarily (`mv target/granular-spectrum-demo-1.0.0-jar-with-dependencies.jar /tmp/stash.jar`), run `granular.bat --help`. Expect: `Jar not built. Run: mvn package` on stderr, exit 1, **no stack trace**. Restore the jar afterwards.

---

## 2. Goldens & determinism

These establish reference renders we'll compare other tests against. Use `bell.wav` as the primary test source because it's short (~3 s) so each render takes under 30 seconds.

- [ ] **Golden A (dry)**: `granular.bat render --source ../samples/bell.wav --grain ../samples/bell.wav --ir ../samples/cathedral.wav --source-end 0.66 --no-reverb -o test/cli/out/golden_dry.wav`. Exits 0. Produces a playable WAV.
- [ ] **Golden B (reverb on)**: same command without `--no-reverb`, output `golden_reverb.wav`. Exits 0. Audibly wetter than A.
- [ ] **Golden C (palindrome)**: `granular.bat render --source ../samples/bell.wav --grain ../samples/bell.wav --ir ../samples/cathedral.wav --source-end 0.66 --no-reverb --palindrome -o test/cli/out/golden_pal.wav`. Exits 0. Roughly double the length of A.
- [ ] **Determinism**: re-run the Golden A command into `test/cli/out/golden_dry2.wav`, then `cmp test/cli/out/golden_dry.wav test/cli/out/golden_dry2.wav` — must exit 0 (bit-identical). Failure here means `Random(idx)` isn't giving deterministic renders and layers 3+ can't trust `cmp` comparisons.
- [ ] **WAV sanity**: for each golden, run
  ```
  python -c "import wave; w=wave.open('test/cli/out/golden_dry.wav'); print(w.getframerate(), w.getsampwidth(), w.getnchannels(), w.getnframes())"
  ```
  Expect `48000 2 2 <nonzero>`. Repeat for `golden_reverb.wav` and `golden_pal.wav`.
- [ ] **Ear check**: listen to all three. A should be dry bells, B reverberant, C should have a clear forward→reverse crossfade. Subjective but catches renders that are "valid WAVs of silence".
- [ ] **CLI ↔ GUI parity**: in the GUI, configure a render with the same flags as Golden A (source `bell.wav`, region 0..0.66, reverb off, everything else default), render it to `test/cli/out/gui_dry.wav`, then `cmp test/cli/out/golden_dry.wav test/cli/out/gui_dry.wav`. Must be bit-identical. If not, the `OfflineRenderer` extraction isn't semantically transparent and needs investigation before trusting the CLI.

---

## 3. Flag sensitivity

Each flag must actually affect output. For each pair below, render with each variant and `cmp` them — they should **differ** (exit 1 from `cmp` is the pass condition here). Reuse short renders on `bell.wav`.

- [ ] `--no-reverb` vs default: renders differ.
- [ ] `--palindrome` vs default: renders differ; palindrome output is longer.
- [ ] `--source-start 0.5 --source-end 1.0` vs default: renders differ; output roughly half length.
- [ ] `--window-exp 12` vs `--window-exp 16`: renders differ.
- [ ] `--grains-per-peak 1` vs `--grains-per-peak 30`: renders differ.
- [ ] `--amp-threshold 0.5` vs default: renders differ and the high-threshold version is audibly sparser.
- [ ] `--control-rate 0.5` vs default: renders differ.
- [ ] `--source-reverb-mix 0.0` vs `--source-reverb-mix 1.0` (both with reverb on): renders differ.
- [ ] `--synth-reverb-mix 0.0` vs `--synth-reverb-mix 1.0` (both with reverb on): renders differ.
- [ ] `--pan-smoothing 0.0` vs `--pan-smoothing 1.0`: renders differ (different L/R balance over time).
- [ ] `--crossfade-duration 0.05` vs `--crossfade-duration 1.0` (both with `--palindrome`): renders differ.
- [ ] `--dramatic-factor 5.0 --dynamics` vs `--no-dynamics`: renders differ.

---

## 4. Preset handling

Presets are the most error-prone integration point.

- [ ] **Get a preset**: save one from the GUI (or hand-write a `test/cli/presets/basic.properties` with non-default values for several parameters). Commit its path below.
- [ ] **Preset drives render**: `granular.bat render --preset test/cli/presets/basic.properties -o test/cli/out/preset_render.wav` exits 0 and differs from the default golden.
- [ ] **Flag overrides win**: using a preset with `useReverb=true`, run with `--no-reverb` and verify the output matches a direct `--no-reverb` render (same other params). `cmp` the two — must be bit-identical.
- [ ] **Unset flags don't clobber**: with a preset that has `grainsPerPeak=5`, run without passing `--grains-per-peak`. Separately, run without preset but with `--grains-per-peak 5` and matching other flags. `cmp` the two — must be bit-identical. Catches the "null wipes out preset value" bug.
- [ ] **Preset with relative source path**: load a preset whose `sourceFile=../samples/bell.wav`; run from repo root — succeeds. Run from `docs/` with `..\granular.bat render --preset ..\test\cli\presets\basic.properties …` — expect a "source file not found" error (this confirms CWD-relative resolution is documented correctly in `docs/CLI.md`).
- [ ] **Malformed preset**: create `test/cli/presets/garbage.properties` containing random text. Run `granular.bat render --preset test/cli/presets/garbage.properties --source ../samples/bell.wav --grain ../samples/bell.wav --ir ../samples/cathedral.wav --no-reverb -o test/cli/out/junk.wav`. Expected: either exit non-zero with a clear error, or succeed using defaults for all unreadable keys (`PresetManager.load` tolerates missing keys). **Must not** stack-trace into the user's face.

---

## 5. Error handling

Each item asserts exit code, that stderr contains a recognizable substring, and (where noted) that stdout is empty.

- [ ] No `--source` and no preset: `granular.bat render -o test/cli/out/x.wav 2>err.txt`. Exit 1. `err.txt` mentions "source". Stdout empty.
- [ ] Non-existent source: `granular.bat render --source nope.wav --grain ../samples/bell.wav --no-reverb -o test/cli/out/x.wav 2>err.txt`. Exit 1. `err.txt` names `nope.wav`.
- [ ] Non-existent grain: same shape, with a bogus `--grain`. Exit 1.
- [ ] Non-existent preset: `granular.bat render --preset nonexistent.properties -o test/cli/out/x.wav 2>err.txt`. Exit 1. `err.txt` mentions "preset".
- [ ] Reverb on, no IR, no preset: `granular.bat render --source ../samples/bell.wav --grain ../samples/bell.wav --reverb -o test/cli/out/x.wav 2>err.txt`. Exit 1. `err.txt` suggests `--no-reverb` or `--ir`.
- [ ] Unwritable output path: `granular.bat render --source ../samples/bell.wav --grain ../samples/bell.wav --no-reverb -o Z:\nosuchdrive\out.wav 2>err.txt`. Exit 2. `err.txt` mentions "write output" or "failed".
- [ ] Required `--source` omitted from analyze: `granular.bat analyze 2>err.txt`. Exit non-zero. picocli's usage error printed to stderr.
- [ ] Invalid `--format` value: `granular.bat analyze --source ../samples/bell.wav --format xyz 2>err.txt`. Exit non-zero. `err.txt` lists valid values.

---

## 6. Analyze subcommand

- [ ] **Text mode, stdout**: `granular.bat analyze --source ../samples/bell.wav` prints `# fundamental=…` as the first line, followed by `window=0 …` blocks. No ANSI escapes in the header, no progress bar bleeding in.
- [ ] **Text mode, file**: `granular.bat analyze --source ../samples/bell.wav -o test/cli/out/peaks.txt`. Stdout contains only `Wrote test/cli/out/peaks.txt (N windows)`. First line of the file is the `# fundamental=` header.
- [ ] **CSV mode, stdout, pipe-clean**: `granular.bat analyze --source ../samples/bell.wav --format csv > test/cli/out/peaks.csv 2>nul` (or `2>/dev/null` on Unix). First line of `peaks.csv` is exactly `window,time_sec,freq_hz,amplitude`. No stray text anywhere.
- [ ] **CSV parses as pandas DataFrame**:
  ```
  python -c "import pandas as pd; df=pd.read_csv('test/cli/out/peaks.csv'); assert set(df.columns)=={'window','time_sec','freq_hz','amplitude'}; print(len(df))"
  ```
  Prints a non-zero row count without errors.
- [ ] **Fundamental sanity on bell**: text-mode output on `../samples/bell.wav` shows `# fundamental=` in the 1280–1290 Hz range (matches the default `grainReferenceFreq=1287`).
- [ ] **Fundamental sanity on cello**: same on `../samples/Cello/bowedCello1.wav` — should land in the expected cello pitch range (roughly 100–300 Hz depending on the note).
- [ ] **`--control-rate` scales window count**: analyze once with `--control-rate 0.1`, once with `--control-rate 0.05`. The second output has roughly twice the window count. Check via `wc -l` on CSV outputs.

---

## 7. Stream hygiene

Separation of stdout (data) from stderr (chatter) is what makes the CLI usable in pipelines.

- [ ] `granular.bat render … 2>nul` (suppress stderr) still prints the final `Wrote …` line on stdout. Progress bar is gone.
- [ ] `granular.bat render … 1>nul` (suppress stdout) still prints the progress bar on stderr. No `Wrote …` line visible.
- [ ] `granular.bat render --quiet …` suppresses the progress bar entirely. `Wrote …` still lands on stdout.
- [ ] Render success → exit 0. User error (missing file, bad flag) → exit 1. Render/write failure → exit 2. All three are distinguishable and match what `docs/CLI.md` promises.

---

## 8. Edge cases

- [ ] Very short region (`--source-start 0.0 --source-end 0.01`): render succeeds, no `ArrayIndexOutOfBoundsException`. The `extractRegion` minimum-1000-samples clamp should kick in.
- [ ] Degenerate region (`--source-start 0.5 --source-end 0.5`): same — clamp applies, render succeeds.
- [ ] Inverted region (`--source-start 0.8 --source-end 0.2`): document the behavior (most likely clamps to a minimum window). If it crashes, add validation.
- [ ] `--window-exp 10` (tiny window 1024 samples): either succeeds or fails with a clear error.
- [ ] `--window-exp 17` (large window 131072 samples): either succeeds or fails with a clear OOM-style error, not a silent hang.
- [ ] Render the full unclipped `../samples/Cello/bowedCello1.wav` at `--control-rate 0.05`: completes without OOM on default heap. If it OOMs, confirm `JAVA_TOOL_OPTIONS=-Xmx4g granular.bat render …` works (the workaround documented in `docs/CLI.md`).

---

## 9. Wrappers & platform

- [ ] `granular.bat` from repo root: works (already exercised above).
- [ ] `granular.bat` from a subdirectory: works (already covered in Smoke).
- [ ] `./granular` (the `.sh` wrapper) under Git Bash: runs and matches the `.bat` output. `./granular --help` / `./granular render --source …`.
- [ ] `./granular` under real Linux or macOS: build, run `--help`, run a render. **Manual, requires a Unix box.** Skip if unavailable; note as "not verified on Unix this cycle."
- [ ] Break the `lib/*` glob: temporarily rename `lib/commons-math3-3.6.1.jar` to `lib/_broken.jar` (keep the `.jar` extension so it still matches the glob) — render should still work. Rename it to `lib/_broken.txt` — render should fail with a clear `NoClassDefFoundError`. Put it back.

---

## 10. Headless verification

- [ ] Grep CLI sources for accidental Swing/AWT imports:
  ```
  grep -rn "javax.swing\|java.awt" src/main/java/org/delightofcomposition/cli/
  ```
  Must return nothing.
- [ ] `OfflineRenderer` is Swing-free:
  ```
  grep -n "javax.swing\|java.awt" src/main/java/org/delightofcomposition/OfflineRenderer.java
  ```
  Must return nothing.
- [ ] On Linux, unset `DISPLAY` and run `./granular render …`. Must succeed. **Manual, requires a Linux box.** Skip if unavailable.

---

## 11. GUI regression

The refactor touched `RenderController`. These confirm the GUI still works.

- [ ] Launch the GUI (`mvn exec:java`, or however you normally do it). Launcher window opens.
- [ ] From the launcher, pick **WAV Render**. Load `../samples/Cello/bowedCello1.wav` as source, `../samples/bell.wav` as grain, render with default parameters. Output plays correctly inside the GUI.
- [ ] Save the GUI's current state as a preset via `PresetManager`, then render that same preset via the CLI (`granular.bat render --preset <that file> -o test/cli/out/cli_gui_parity.wav`). Compare against the GUI's rendered output with `cmp`. Must be bit-identical. This is the definitive GUI↔CLI parity proof.
- [ ] Launch the GUI, start a long render, hit Cancel. The cancel path still aborts the render cleanly (no orphaned threads, GUI returns to idle).
- [ ] From the launcher, pick **Real-Time MIDI**. The live mode still launches and works. (The refactor shouldn't have touched realtime code, but confirm.)

---

## 12. Documentation validation

- [ ] Every command in the `docs/CLI.md` **Quickstart** section runs verbatim from the repo root and exits 0.
- [ ] Every flag mentioned in `docs/CLI.md` tables appears in `granular.bat render --help` or `granular.bat analyze --help` output. Spot-check 5 random flags from each.
- [ ] The **Batch rendering** bash snippet from `docs/CLI.md`: create a `samples/` directory with 2 small WAVs (copies of `../samples/bell.wav`), run the loop, confirm 2 output WAVs appear in `renders/`.
- [ ] The **Analyze worked example** Python snippet from `docs/CLI.md`: run it against a real CSV output from section 6. Must not throw.
- [ ] Read `docs/CLI.md` end-to-end as if you've never seen the CLI before. Every claim, flag, and example should still be accurate. Note anything stale.

---

## Results log template

Copy this to the bottom of the file (or a fresh file) each run and fill in as you go:

```
Test run: 2026-__-__  on <machine/OS>
Build SHA: <git rev-parse HEAD>

Section 0 Setup:                [ ] pass  [ ] fail — notes:
Section 1 Smoke:                [ ] pass  [ ] fail — notes:
Section 2 Goldens:              [ ] pass  [ ] fail — notes:
Section 3 Flag sensitivity:     [ ] pass  [ ] fail — notes:
Section 4 Preset handling:      [ ] pass  [ ] fail — notes:
Section 5 Error handling:       [ ] pass  [ ] fail — notes:
Section 6 Analyze:              [ ] pass  [ ] fail — notes:
Section 7 Stream hygiene:       [ ] pass  [ ] fail — notes:
Section 8 Edge cases:           [ ] pass  [ ] fail — notes:
Section 9 Wrappers & platform:  [ ] pass  [ ] fail — notes:
Section 10 Headless:            [ ] pass  [ ] fail — notes:
Section 11 GUI regression:      [ ] pass  [ ] fail — notes:
Section 12 Docs validation:     [ ] pass  [ ] fail — notes:

Items skipped (with reason): …
Items failed (with ticket/fix notes): …
```
