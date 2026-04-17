package org.delightofcomposition.sound;

import java.io.File;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.delightofcomposition.util.FindResourceFile;

import javax.sound.sampled.AudioFormat;

/**
 * Write a description of class ReadSound here.
 *
 * @author (your name)
 * @version (a version number or a date)
 */
public class ReadSound {

    public static double[] readSoundDoubles(String fileName) {
        float[] fSound = readSound(fileName);
        double[] dSound = new double[fSound.length];
        for (int i = 0; i < fSound.length; i++)
            dSound[i] = fSound[i];
        return dSound;
    }

    public static float[] readSound(String fileName) {
        File soundFile = FindResourceFile.findResourceFile(fileName);// new File(fileName);
        AudioInputStream audioInputStream = null;
        byte[] soundData = new byte[0];
        float[] normalizedData = new float[0];
        AudioFormat audioFormat = null;
        int numBytes = 0;
        boolean is24bit = false;
        try {
            audioInputStream = AudioSystem.getAudioInputStream(soundFile);
            audioFormat = audioInputStream.getFormat();
            int bytesPerFrame = audioInputStream.getFormat().getFrameSize();
            float frameRate = audioInputStream.getFormat().getFrameRate();
            float sampleRate = audioInputStream.getFormat().getSampleRate();
            float sampleSize = audioInputStream.getFormat().getSampleSizeInBits();
            // System.out.println("isBigEndian: " + audioInputStream.getFormat().isBigEndian());
            // System.out.println(audioInputStream.getFormat().getEncoding());
            // System.out.println(audioInputStream.getFormat());

            int channels = audioFormat.getChannels();
            int bytesPerSample = audioFormat.getSampleSizeInBits() / 8;
            if (bytesPerSample == 3) {
                System.out.println("Attempting to read 24-bit format");
                is24bit = true;
            } else if (bytesPerSample != 2) {
                System.out.println("FILE IS NOT 16-bit or 24-bit format");
                return null;
            }
            soundData = new byte[(int) (audioInputStream.getFrameLength() * bytesPerFrame)];
            normalizedData = new float[(int) audioInputStream.getFrameLength()];
            numBytes = audioInputStream.read(soundData);
        } catch (Exception ex) {
            System.out.println("*** Cannot process " + fileName + " ***");
            System.out.println(ex);
            System.exit(1);
        }
        if (is24bit) {
            normalizedData = decode24bit(soundData, normalizedData,
                    audioFormat.getChannels(), audioFormat.isBigEndian());
        } else {
             // 16-bit audio decoding with stereo-to-mono mixdown
            int channels = audioFormat.getChannels();
            int bytesPerFrame = channels * 2; // 2 bytes per sample * channels
            float max = 0;
            int frameCount = soundData.length / bytesPerFrame;
            normalizedData = new float[frameCount];

            for (int frame = 0; frame < frameCount; frame++) {
                float mixedSample = 0;
                for (int ch = 0; ch < channels; ch++) {
                    int offset = frame * bytesPerFrame + ch * 2;
                    int b1, b2;

                    if (audioFormat.isBigEndian()) {
                        b1 = soundData[offset]; // MSB
                        b2 = soundData[offset + 1]; // LSB
                    } else {
                        b1 = soundData[offset + 1]; // MSB
                        b2 = soundData[offset]; // LSB
                    }

                    int unsignedB1 = b1 & 0xFF;
                    int unsignedB2 = b2 & 0xFF;
                    int dataPoint = (unsignedB1 << 8) | unsignedB2;
                    if (dataPoint > 32767) {
                        dataPoint -= 65536;
                    }
                    mixedSample += dataPoint;
                }
                mixedSample /= channels;
                max = Math.max(max, Math.abs(mixedSample));
                normalizedData[frame] = mixedSample;
            }

            // Normalize to [-1, 1] range
            for (int i = 0; i < normalizedData.length; i++) {
                normalizedData[i] /= max;
            }
        }

        /*
         * SourceDataLine line = null;
         * try
         * {
         * DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
         * line = (SourceDataLine)AudioSystem.getLine(info);
         * line.open(audioFormat);
         * line.start();
         * line.write(sd2, 0, numBytes);
         * }
         * catch (Exception ex)
         * {
         * System.out.println("*** Audio line unavailable ***");
         * System.exit(1);
         * }
         */
        /*
         * for(int i = 0; i < sdata.length; i++){
         * System.out.println(sdata[i]);
         * }
         */

        return normalizedData;
    }

    public static float[] decode24bit(byte[] soundData, float[] normalizedData,
                                      int channels, boolean bigEndian) {
        int bytesPerFrame = 3 * channels;
        int frameCount = soundData.length / bytesPerFrame;
        float max = 0;

        for (int frame = 0; frame < frameCount; frame++) {
            float mixedSample = 0;
            for (int ch = 0; ch < channels; ch++) {
                int offset = frame * bytesPerFrame + ch * 3;
                int b1, b2, b3;
                if (bigEndian) {
                    b3 = soundData[offset];              // MSB, keep sign
                    b2 = soundData[offset + 1] & 0xFF;
                    b1 = soundData[offset + 2] & 0xFF;
                } else {
                    b1 = soundData[offset]     & 0xFF;
                    b2 = soundData[offset + 1] & 0xFF;
                    b3 = soundData[offset + 2];          // MSB, keep sign
                }
                int dataPoint = b1 | (b2 << 8) | (b3 << 16);
                mixedSample += dataPoint;
            }
            mixedSample /= channels;
            max = Math.max(max, Math.abs(mixedSample));
            normalizedData[frame] = mixedSample;
        }
        // byte[] sd2 = new byte[numBytes];
        for (int i = 0; i < normalizedData.length; i++) {
            /*
             * int c2 = data[i] / 256;
             * int c1 = data[i] - c2;
             * 
             * sd2[i*2] = (byte)c1;
             * sd2[i*2 + 1] = (byte)(c2);
             */
            normalizedData[i] /= max;
            if (normalizedData[i] > 1)
                System.out.println("ERROR: " + normalizedData[i]);
        }
        return normalizedData;
    }
}
