package org.delightofcomposition.sound;

import java.util.ArrayList;
import org.apache.commons.math3.transform.*;
import org.apache.commons.math3.complex.Complex;

import java.util.Collections;
import java.util.Comparator;

/**
 * Write a description of class FFT here.
 *
 * @author (your name)
 * @version (a version number or a date)
 */
public class FFT
{
    private static final FastFourierTransformer TRANSFORMER =
            new FastFourierTransformer(DftNormalization.STANDARD);

    public static ArrayList<double[]> getSpectrumWPhase(double[] data, boolean normalize){
        double[][] trans = getTransformationWPhase(data);

        ArrayList<double[]> bins = new ArrayList<double[]>();
        double max = 0;
        for(int i = 0; i < trans[0].length * 0.5 + 1; i++){
            bins.add(new double[]{(i) / (double)trans[0].length, trans[0][i],trans[1][i]});
            max = Math.max(max, trans[0][i]);
        }

        if(normalize)
            for(double[] bin: bins)
                bin[1] /= max;

        return bins;

    }
    
    public static ArrayList<double[]> getSortedSpect(double[] data){
        ArrayList<double[]> bins = getSpectrumWPhase(data, false);
        class AmpSort implements Comparator<double[]>{
            public int compare(double[] bin1, double[] bin2){
                return (int)(1000000 *(bin2[1] - bin1[1])); // 
            }
        }
        try{
            Collections.sort(bins, new AmpSort());
        }catch(java.lang.IllegalArgumentException e){
            System.out.println(e);
        }
        return bins;
    }

    public static ArrayList<ArrayList<double[]>> getSpectWidows(double[] mySoundSample){
        int windowSize = 2048;//1024;//2048;
        ArrayList<ArrayList<double[]>> spects = new ArrayList<ArrayList<double[]>>();
        for(int i = 0; i < mySoundSample.length - windowSize; i+= windowSize / 2){

            double[] window = new double[windowSize];
            for(int n = 0; n < windowSize; n++){
                double hammingFunction = 0.54 - 0.46 * Math.cos(Math.PI * 2 *  n / (double)(windowSize - 1));
                double windowFunction = (2 - (Math.cos(Math.PI * 2 *  n / (double)(windowSize - 1)) + 1)) / 2.0;

                window[n] = mySoundSample[n + i] * windowFunction;
            }
            ArrayList<double[]> spect = getSpectrumWPhase(window, true);
            /*
            variation = Math.pow(10, -4) * 44100 * i / (double)mySoundSample.length;
            double fund = getLoudestFreq(spect);
            for(int j = 0; j < spect.size(); j++){
            double[] bin = spect.get(j);

            for(int n = 0; n < windowSize; n++){

            synth[i + n] += bin[1] * Math.sin(bin[2] + 2 * Math.PI * bin[0] * n);

            }
            }
             */
            spects.add(spect);
        }
        return spects;
    }
    
    public static double[][] getTransformationWPhase(double[] data){
        double peek = 0;
        for(int i = 0; i < data.length; i++){
            peek = Math.max(peek, Math.abs((double)data[i]));
        }
        //System.out.println("PEEK = " + peek);
        int po2 = 0;
        while(Math.pow(2, po2) < data.length){
            po2++;
        }
        double[] dataToTransform = new double[(int)Math.pow(2, po2)];
        for(int i = 0; i < dataToTransform.length; i++){
            if(i < data.length){
                //DO NOT NORMALIZE YET!
                dataToTransform[i] = ((double)data[i]);// / peek;
            }
            else
                dataToTransform[i] = 0;
        }

        double[][] output = transformWPhase(dataToTransform);
        return output;
    }

    public static double[][] getTransformRaw(double[] data) {
        double[][] trans = getTransformationWPhase(data);
        int halfLen = trans[0].length / 2 + 1;
        double max = 0;
        for (int i = 0; i < halfLen; i++) max = Math.max(max, trans[0][i]);
        if (max > 0) {
            for (int i = 0; i < halfLen; i++) trans[0][i] /= max;
        }
        return trans;
    }

    public static double[][] transformWPhase(double[] input) 
    {   

        double[][] tempConversion = new double[2][input.length];
        try {
            Complex[] complx = TRANSFORMER.transform(input, TransformType.FORWARD);

            for (int i = 0; i < complx.length; i++) {               
                double rr = (complx[i].getReal());
                double ri = (complx[i].getImaginary());

                tempConversion[0][i] = Math.sqrt((rr * rr) + (ri * ri));
                tempConversion[1][i] = Math.atan2(ri, rr);
            }

        } catch (IllegalArgumentException e) {
            System.out.println(e);
        }

        return tempConversion;
    }
}
