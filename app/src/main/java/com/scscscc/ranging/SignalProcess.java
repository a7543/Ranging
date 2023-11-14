package com.scscscc.ranging;

import android.util.Log;

import org.jtransforms.fft.DoubleFFT_1D;

public class SignalProcess {
    public static class SignalInfo {
        public double maxFreq;
        public double proportion;

        public SignalInfo(double maxFreq, double proportion) {
            this.maxFreq = maxFreq;
            this.proportion = proportion;
        }
    }

    public static SignalInfo signalAnalyze(double[] a, int samplingRate) {
        int n = a.length;

        DoubleFFT_1D fft = new DoubleFFT_1D(n);
        double[] transformed = a.clone(); // 创建一个a的副本，因为FFT会修改输入数组
        fft.realForward(transformed); // 对输入信号执行实数FFT

        int maxIndex = 0;
        double maxEnergy = 0.0;
        double energySum = 0.0;

        for (int i = 0; i < n / 2; i++) {
            double real = transformed[2 * i];
            double imag = transformed[2 * i + 1];
            double energy = real * real + imag * imag;
            energySum += energy;

            if (energy > maxEnergy) {
                maxEnergy = energy;
                maxIndex = i;
            }
        }
        double freq = (double) maxIndex * samplingRate / n;
        double proportion = maxEnergy / energySum;
        SignalInfo info = new SignalInfo(freq, proportion);
        return info;
    }

    public static int beepType(double[] a, int samplingRate) {
        SignalInfo info = signalAnalyze(a, samplingRate);
        int result = 0;
        if (info.maxFreq > 2800 && info.maxFreq < 3200 && info.proportion > 0.3)
            result = result | (1);
        if (info.maxFreq > 3800 && info.maxFreq < 4200 && info.proportion > 0.3)
            result = result | (1 << 1);
        return result;
    }

}
