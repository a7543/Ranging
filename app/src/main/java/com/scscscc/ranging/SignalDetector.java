package com.scscscc.ranging;

import java.util.Arrays;

public class SignalDetector {
    private static TheBrain theBrain;

    private static class NoiseWindowInfo {
        public int maxIndex;
        public double crossCorrelation;

        public NoiseWindowInfo(int maxIndex, double crossCorrelation) {
            this.maxIndex = maxIndex;
            this.crossCorrelation = crossCorrelation;
        }
    }
    public SignalDetector(TheBrain theBrain) {
        this.theBrain = theBrain;
    }
    public static class SignalInfo {
        public int status;
        public double confidence;
        public int position;
        public double crossCorrelation;

        public SignalInfo(int status, double confidence, int position, double crossCorrelation) {
            this.status = status;
            this.confidence = confidence;
            this.position = position;
            this.crossCorrelation = crossCorrelation;
        }
    }

    public static SignalInfo detectSignal(double[] data, double[] reference) {
        NoiseWindowInfo nwi = getNoiseWindow(data, reference);
        int N = nwi.maxIndex;
        if (N == -1) {
            //System.out.println("Detection failed: signal energy is too weak or the noise level is high");
            return new SignalInfo(-1, 0, 0, 0);
        }

        double L2_S = calculateL2Norm(data, N, N + theBrain.W0);
        double L2_N = calculateL2Norm(data, N - theBrain.W0, N);

        if (L2_S / L2_N > 2 && nwi.crossCorrelation > 100)
            return new SignalInfo(0, L2_S / L2_N, N, nwi.crossCorrelation);
        else
            return new SignalInfo(-2, L2_S / L2_N, N, nwi.crossCorrelation);
    }

    // This method is used to calculate the L2-norm
    private static double calculateL2Norm(double[] data, int start, int end) {
        double sum = 0;
        for (int i = start; i < end; i++) {
            sum += data[i] * data[i];
        }
        return Math.sqrt(sum);
    }

    // This method is used to get the noise window.
    // In this code, it is assumed that the noise window is found when the cross-correlation value is maximum.
    // Actual check length = data.length - reference.length - W0 + 1
    private static NoiseWindowInfo getNoiseWindow(double[] original_data, double[] reference) {
        double[] data = Arrays.copyOf(original_data, original_data.length);
        double max = Double.NEGATIVE_INFINITY;
        for (double datum : data) max = Math.max(max, Math.abs(datum));
        for (int i = 0; i < data.length; i++) data[i] /= max;

        double maxCrossCorrelation = Double.NEGATIVE_INFINITY;
        int maxIndex = -1;

        for (int i = theBrain.W0; i <= data.length - reference.length; i++) {
            double crossCorrelation = 0;
            for (int j = 0; j < reference.length; j++) {
                crossCorrelation += data[i + j] * reference[j];
            }

            if (crossCorrelation > maxCrossCorrelation) {
                maxCrossCorrelation = crossCorrelation;
                maxIndex = i;
            }
        }
        return new NoiseWindowInfo(maxIndex, maxCrossCorrelation);
    }
}
