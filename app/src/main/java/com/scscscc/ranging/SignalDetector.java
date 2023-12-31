package com.scscscc.ranging;

public class SignalDetector {

    private static class NoiseWindowInfo {
        public int[] maxIndex;
        public double[] similarity;

        public NoiseWindowInfo(int[] maxIndex, double[] similarity) {
            this.maxIndex = maxIndex;
            this.similarity = similarity;
        }
    }


    public static class SignalInfo {
        public int status;
        public double confidence;
        public int[] position;
        public double[] similarity;

        public SignalInfo(int status, double confidence, int[] position, double[] similarity) {
            this.status = status;
            this.confidence = confidence;
            this.position = position;
            this.similarity = similarity;
        }
    }

    public static SignalInfo detectSignal(double[] data, double[] reference) {
        NoiseWindowInfo nwi = getNoiseWindow(data, reference);
        int N = nwi.maxIndex[0];
        if (N == -1) {
            //System.out.println("Detection failed: signal energy is too weak or the noise level is high");
            return new SignalInfo(-1, 0, new int[]{-1, -1, -1}, new double[]{0, 0, 0});
        }

        double L2_S = calculateL2Norm(data, N, N + TheBrain.W0);
        double L2_N = calculateL2Norm(data, N - TheBrain.W0, N);

        if (nwi.similarity[0] > TheBrain.simThreshold)
            return new SignalInfo(0, L2_S / L2_N, nwi.maxIndex, nwi.similarity);
        else
            return new SignalInfo(-2, L2_S / L2_N, nwi.maxIndex, nwi.similarity);
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
    private static NoiseWindowInfo getNoiseWindow(double[] data, double[] reference) {


        double[] topSimilarity = new double[]{Double.NEGATIVE_INFINITY, 0, 0};
        int[] maxIndex = new int[]{-1, -1, -1};

        for (int i = TheBrain.W0; i <= data.length - reference.length; i++) {
            double crossCorrelation = 0;
            double vectorSqrLen = 0;
            for (int j = 0; j < reference.length; j++) {
                crossCorrelation += data[i + j] * reference[j];
                vectorSqrLen += data[i + j] * data[i + j];
            }
            double similarity = crossCorrelation / Math.sqrt(vectorSqrLen);
            if (similarity > topSimilarity[0]) {
                topSimilarity[2] = topSimilarity[1];
                topSimilarity[1] = topSimilarity[0];
                topSimilarity[0] = similarity;
                maxIndex[2] = maxIndex[1];
                maxIndex[1] = maxIndex[0];
                maxIndex[0] = i;
            }
        }


        return new NoiseWindowInfo(maxIndex, topSimilarity);
    }
}
