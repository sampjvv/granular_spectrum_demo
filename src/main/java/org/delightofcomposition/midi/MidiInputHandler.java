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
    private final double sourceFundamental;

    public MidiInputHandler(Voice[] voices, ControlState controls, double sourceFundamental) {
        this.voices = voices;
        this.controls = controls;
        this.sourceFundamental = sourceFundamental;
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
        // Find a free voice
        for (Voice voice : voices) {
            if (!voice.isActive()) {
                voice.noteOn(note, velocity, sourceFundamental);
                System.out.println("Note ON: " + note + " vel=" + velocity);
                return;
            }
        }
        System.out.println("No free voices for note " + note);
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

    private void handleCC(int cc, int value) {
        switch (cc) {
            case 1: // Mod wheel -> mix
                controls.setMix(value / 127.0);
                break;
            case 74: // Brightness -> density
                controls.setDensity(value / 127.0);
                break;
        }
    }

    @Override
    public void close() {
        // Nothing to clean up
    }

    /**
     * Lists available MIDI input devices and opens the first one found.
     * Returns null if no MIDI input devices are available.
     */
    public static MidiDevice findAndOpenDevice() {
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        System.out.println("Available MIDI devices:");

        for (MidiDevice.Info info : infos) {
            System.out.println("  - " + info.getName() + " (" + info.getDescription() + ")");
            try {
                MidiDevice device = MidiSystem.getMidiDevice(info);
                // We need a device that has transmitters (i.e., can send MIDI to us)
                if (device.getMaxTransmitters() != 0) {
                    device.open();
                    System.out.println("  -> Opened as MIDI input: " + info.getName());
                    return device;
                }
            } catch (MidiUnavailableException e) {
                // Skip this device
            }
        }

        System.out.println("No MIDI input devices found.");
        return null;
    }
}
