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

    // Soft pickup: prevents CC knob jumps on absolute encoders
    private final int[] lastHwCc = new int[128];
    private final boolean[] ccPickedUp = new boolean[128];

    public MidiInputHandler(Voice[] voices, ControlState controls) {
        this.voices = voices;
        this.controls = controls;
        java.util.Arrays.fill(lastHwCc, -1);
        java.util.Arrays.fill(ccPickedUp, false);
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

    /**
     * Soft pickup: only apply a CC value when the physical knob has "caught"
     * the current software value. Prevents jumps with absolute-position encoders.
     */
    private boolean shouldPickUp(int cc, int value) {
        double softwareValue;
        switch (cc) {
            case 1: softwareValue = controls.getMix(); break;
            case 76: softwareValue = controls.getPan(); break;
            case 71: softwareValue = controls.getDensity(); break;
            case 74: softwareValue = controls.getVolume(); break;
            case 77: softwareValue = controls.getReverseAmount(); break;
            default: return true; // unmapped CCs don't need pickup
        }
        int swVal = (int) (softwareValue * 127);
        int prev = lastHwCc[cc];
        lastHwCc[cc] = value;

        if (ccPickedUp[cc]) return true;

        if (prev < 0) {
            // First message for this CC — pick up if close enough
            if (Math.abs(value - swVal) <= 5) {
                ccPickedUp[cc] = true;
                return true;
            }
            return false;
        }

        // Check if knob swept past the software value
        if ((prev <= swVal && value >= swVal) || (prev >= swVal && value <= swVal)) {
            ccPickedUp[cc] = true;
            return true;
        }
        return false;
    }

    private void handleCC(int cc, int value) {
        if (!shouldPickUp(cc, value)) return;

        switch (cc) {
            case 1: // Mod wheel -> mix
                controls.setMix(value / 127.0);
                break;
            case 76: // Pan
                controls.setPan(value / 127.0);
                break;
            case 71: // Density
                controls.setDensity(value / 127.0);
                break;
            case 74: // Volume
                controls.setVolume(value / 127.0);
                break;
            case 77: // Reverse
                controls.setReverseAmount(value / 127.0);
                break;
        }
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
