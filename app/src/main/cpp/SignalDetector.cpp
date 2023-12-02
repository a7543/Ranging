#include <vector>
#include <cmath>
#include <limits>

namespace SignalDetector {
    struct NoiseWindowInfo {
        std::vector<int> maxIndex;
        std::vector<double> similarity;
    };

    struct SignalInfo {
        int status;
        double confidence;
        std::vector<int> position;
        std::vector<double> similarity;
    };

    // This method is used to calculate the L2-norm


    static double calculateL2Norm(const double *data, int start, int end) {
        double sum = 0;
        for (int i = start; i < end; i++) {
            sum += data[i] * data[i];
        }
        return std::sqrt(sum);
    }

    static NoiseWindowInfo
    getNoiseWindow(const double *data, const double *reference, int data_size,
                   int reference_size, int TB_W0) {
        std::vector<double> topSimilarity{-std::numeric_limits<double>::infinity(), 0, 0};
        std::vector<int> maxIndex{-1, -1, -1};

        for (int i = TB_W0; i <= data_size - reference_size; i++) {
            double crossCorrelation = 0;
            double vectorSqrLen = 0;
            for (int j = 0; j < reference_size; j++) {
                crossCorrelation += data[i + j] * reference[j];
                vectorSqrLen += data[i + j] * data[i + j];
            }
            double similarity = crossCorrelation / std::sqrt(vectorSqrLen);
            if (similarity > topSimilarity[0]) {
                topSimilarity[2] = topSimilarity[1];
                topSimilarity[1] = topSimilarity[0];
                topSimilarity[0] = similarity;
                maxIndex[2] = maxIndex[1];
                maxIndex[1] = maxIndex[0];
                maxIndex[0] = i;
            }
        }
        return NoiseWindowInfo{maxIndex, topSimilarity};
    }

    static SignalInfo
    detectSignal(double *data, int data_size, double *reference, int reference_size, int TB_W0,
                 double TB_simThreshold) {
        NoiseWindowInfo nwi = getNoiseWindow(data, reference, data_size, reference_size, TB_W0);
        int N = nwi.maxIndex[0];
        if (N == -1) {
            //System.out.println("Detection failed: signal energy is too weak or the noise level is high");
            return SignalInfo{-1, 0, std::vector<int>{-1, -1, -1},
                              std::vector<double>{0, 0, 0}};
        }

        double L2_S = calculateL2Norm(data, N, N + TB_W0);
        double L2_N = calculateL2Norm(data, N - TB_W0, N);

        if (nwi.similarity[0] > TB_simThreshold)
            return SignalInfo{0, L2_S / L2_N, nwi.maxIndex, nwi.similarity};
        else
            return SignalInfo{-2, L2_S / L2_N, nwi.maxIndex, nwi.similarity};
    }

    // This method is used to get the noise window.
    // In this code, it is assumed that the noise window is found when the cross-correlation value is maximum.
    // Actual check length = data.length - reference.length - W0 + 1

}