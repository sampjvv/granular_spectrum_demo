package org.delightofcomposition.midi;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;

import org.delightofcomposition.realtime.ControlState;
import org.delightofcomposition.realtime.Voice;

public class MidiInputHandler implements Receiver {
    private final Voice[] voices;
    private final ControlState controls;

    // Incremental soft pickup: absorbs first touch, then tracks deltas
    private final int[] lastHw = new int[128]; // previous hardware value (-1 = unseen)

    public MidiInputHandler(Voice[] voices, ControlState controls) {
        this.voices = voices;
        this.controls = controls;
        java.util.Arrays.fill(lastHw, -1);
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {
        if (!(message instanceof ShortMessage)) return;

        ShortMessage sm = (ShortMessage) message;
        int command = sm.getCommand();
        int channel = sm.getChannel();
        int data1 = sm.getData1();
        int data2 = sm.getData2();

        switch (command) {
            case ShortMessage.NOTE_ON:
                if (data2 > 0) {
                    handleNoteOn(data1, data2);
                } else {
                    // Note on with velocity 0 = note off
                    handleNoteOff(data1);
                }
                break;

            case ShortMessage.NOTE_OFF:
                handleNoteOff(data1);
                break;

            case ShortMessage.CONTROL_CHANGE:
                handleCC(data1, data2);
                break;
        }
    }

    private void handleNoteOn(int note, int velocity) {
        // 1. Try free voice
        for (Voice voice : voices) {
            if (!voice.isActive()) {
                voice.noteOn(note, velocity);
                System.out.println("Note ON: " + note + " vel=" + velocity);
                return;
            }
        }
        // 2. Steal oldest releasing voice
        Voice steal = null;
        for (Voice voice : voices) {
            if (voice.isReleasing() && (steal == null || voice.getAge() > steal.getAge()))
                steal = voice;
        }
        // 3. Steal oldest active voice as last resort
        if (steal == null) {
            for (Voice voice : voices)
                if (steal == null || voice.getAge() > steal.getAge()) steal = voice;
        }
        if (steal != null) {
            steal.noteOn(note, velocity);
            System.out.println("Note ON (stolen): " + note + " vel=" + velocity);
        }
    }

    private void handleNoteOff(int note) {
        for (Voice voice : voices) {
            if (voice.isActive() && voice.getMidiNote() == note) {
                voice.noteOff();
                System.out.println("Note OFF: " + note);
                return;
            }
        }
    }

    private double getSoftwareValue(int cc) {
        switch (cc) {
            case 74: return controls.getVolume();
            case 71: return controls.getDensity();
            case 76: return controls.getPan();
            case 77: return controls.getReverseAmount();
            default: return 0.0;
        }
    }

    private void setSoftwareValue(int cc, double val) {
        val = Math.max(0.0, Math.min(1.0, val));
        switch (cc) {
            case 74: controls.setVolume(val); break;
            case 71: controls.setDensity(val); break;
            case 76: controls.setPan(val); break;
            case 77: controls.setReverseAmount(val); break;
        }
    }

    private synchronized void handleCC(int cc, int value) {
        // Mod wheel: spring-loaded, always apply directly
        if (cc == 1) {
            controls.setMix(value / 127.0);
            return;
        }

        // Only process mapped CCs
        if (cc != 74 && cc != 71 && cc != 76 && cc != 77) return;

        // Incremental soft pickup
        if (lastHw[cc] == -1) {
            // First touch: record position, don't apply (prevents jump)
            lastHw[cc] = value;
            return;
        }

        // Apply delta between consecutive messages
        double delta = (value - lastHw[cc]) / 127.0;
        lastHw[cc] = value;
        setSoftwareValue(cc, getSoftwareValue(cc) + delta);
    }

    @Override
    public void close() {
        // Nothing to clean up
    }

    /** Returns all available MIDI input devices (devices with transmitters). */
    public static java.util.List<MidiDevice.Info> listInputDevices() {
        java.util.List<MidiDevice.Info> inputs = new java.util.ArrayList<>();
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        System.out.println("Available MIDI devices:");

        for (MidiDevice.Info info : infos) {
            try {
                MidiDevice device = MidiSystem.getMidiDevice(info);
                boolean hasTransmitters = device.getMaxTransmitters() != 0;
                String marker = hasTransmitters ? " [INPUT]" : "";
                System.out.println("  - " + info.getName() + " (" + info.getDescription() + ")" + marker);
                if (hasTransmitters) {
                    inputs.add(info);
                }
            } catch (MidiUnavailableException e) {
                System.out.println("  - " + info.getName() + " (UNAVAILABLE)");
            }
        }

        if (inputs.isEmpty()) {
            System.out.println("No MIDI input devices found.");
        }
        return inputs;
    }

    /** Opens a specific MIDI device. Returns null on failure. */
    public static MidiDevice openDevice(MidiDevice.Info info) {
        try {
            MidiDevice device = MidiSystem.getMidiDevice(info);
            device.open();
            System.out.println("Opened MIDI input: " + info.getName());
            return device;
        } catch (MidiUnavailableException e) {
            System.err.println("Failed to open MIDI device " + info.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /** Auto-detect: lists devices and opens the first one found. */
    public static MidiDevice findAndOpenDevice() {
        java.util.List<MidiDevice.Info> inputs = listInputDevices();
        if (!inputs.isEmpty()) {
            return openDevice(inputs.get(0));
        }
        return null;
    }
}
