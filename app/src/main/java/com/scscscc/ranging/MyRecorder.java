package com.scscscc.ranging;


import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

public class MyRecorder {
    static {
        System.loadLibrary("native-lib");
    }

    private static int bufferSize;
    private static int bufferSampleNum;
    private static boolean isRecording;
    private static AudioRecord recorder;
    private static Thread recordingThread;
    private static Handler handler;

    private static void feedback(int channel, String txt) {
        Message msg = new Message();
        msg.what = channel;
        msg.obj = txt;
        handler.sendMessage(msg);
    }

    public static void init(Handler p_handler) {
        handler = p_handler;
        bufferSize = AudioRecord.getMinBufferSize(
                TheBrain.sampleRate,
                TheBrain.MYCONF_CHANNEL_IN_CONFIG,
                TheBrain.MYCONF_AUDIO_ENCODING);
        bufferSampleNum = bufferSize / 2;
    }

    public static void startRecording(Context context) {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            feedback(1, "no microphone permission");
            return;
        }
//        soundtest();
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                TheBrain.sampleRate, TheBrain.MYCONF_CHANNEL_IN_CONFIG, TheBrain.MYCONF_AUDIO_ENCODING, bufferSize);
        recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(() -> {
//                writeAudioDataToFile();
//                soundtest();
            detectSound();
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    public static void stopRecording() {

        if (recorder != null) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }


    }


//    private static void soundtest() {
//
//        byte[] buffer = new byte[bufferSize];
//        double[] x = new double[bufferSize];
//        int pos = bufferSize - TheBrain.W0;
//
//        int read;
//        String filename = context.getDataDir() + "/test.dat";
//
//        Log.d("scsc", "开始录音地址===== " + filename);
//        FileInputStream is = null;
//        try {
//            is = new FileInputStream(filename);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//        while (true) {
//            try {
//                read = is.read(buffer, 0, bufferSize);
//            } catch (IOException e) {
//                break;
//
//            }
//            if (read <= 0)
//                break;
//            if (sampleCount > 88000)
//                break;
//            short[] shorts = new short[buffer.length / 2];
//
//            ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
//
//            System.arraycopy(x, bufferSize / 2, x, 0, bufferSize / 2);
//            pos -= bufferSize / 2;
//            sampleCount += bufferSize / 2;
//            for (int i = 0; i < shorts.length; i++) {
//                x[i + shorts.length] = 1.0 * shorts[i] / Short.MAX_VALUE;
//            }
//
//            while (pos <= bufferSize / 2) {
//                SignalDetector.SignalInfo si = SignalDetector.detectSignal(Arrays.copyOfRange(x, pos, pos + bufferSize / 2), TheBrain.playBuffer);
//                feedback(5, "wowowow " + (sampleCount + pos - bufferSize + si.position) + " " + si.confidence + " " + si.similarity);
//                pos += bufferSize / 2 - TheBrain.W0;
//            }
//        }
//    }

    private static int count = 0;

    private static void detectSound() {
        byte[] buffer = new byte[bufferSize];
        double[] x = new double[TheBrain.refSamples.length + bufferSampleNum + TheBrain.W0];
        Arrays.fill(x, 7);
        int totalPos = TheBrain.W0;
//        FakeRecorder fr = new FakeRecorder();
        int read;
        SignalDetector.SignalInfo lastsi = new SignalDetector.SignalInfo(0, 0, new int[]{-1, -1, -1}, new double[]{0, 0, 0});
        while (isRecording) {
            read = recorder.read(buffer, 0, bufferSize);
            if (read != AudioRecord.ERROR_INVALID_OPERATION) {
                if (read != bufferSize) {
                    feedback(1, String.format(Locale.CHINA, "read error %d %d", bufferSize, read));
                    break;
                }
                //test
                short[] shorts = new short[bufferSampleNum];
                // to turn bytes to shorts as either big endian or little endian.
                ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);

                double[] x_tmp = new double[TheBrain.refSamples.length];
                System.arraycopy(x, bufferSampleNum, x_tmp, 0, TheBrain.refSamples.length);
                System.arraycopy(x_tmp, 0, x, 0, TheBrain.refSamples.length);

                for (int i = 0; i < shorts.length; i++) {
                    x[i + TheBrain.refSamples.length] = 1.0 * shorts[i] / Short.MAX_VALUE;
                }
                totalPos += bufferSampleNum;

                SignalDetector.SignalInfo si = SignalDetector.detectSignal(x, TheBrain.refSamples);
//                SignalDetector.SignalInfo si = cDetectSignal(x, TheBrain.refSamples, TheBrain.W0, TheBrain.simThreshold);

                if (si.status == 0) {
                    if (!(bufferSampleNum - si.position[0] < TheBrain.W1)) {
                        TheBrain.report(TheBrain.DATA_LISTEN, totalPos + si.position[0]);
                        count += 1;
                        Log.d("beepinfo", "detectSound: " + bufferSampleNum + " " + count + " |" + si.position[0] + " " + si.position[1] + " " + si.position[2] + "|" + si.similarity[0] + " " + si.similarity[1] + " " + si.similarity[2]);
                    }
                }
                feedback(1, String.format(Locale.CHINA, "%d: %d %.2f,suim\n %.2f %.2f %.2f\ncount = %d\n", si.status, totalPos + si.position[0], si.confidence, si.similarity[0], si.similarity[1], si.similarity[2], count));

                if (si.similarity[0] == lastsi.similarity[0] && si.similarity[1] == lastsi.similarity[1] && lastsi.similarity[2] == si.similarity[2])
                    feedback(2, "omg " + si.position[0] + " " + lastsi.position[0] + " " + (si.position[0] - lastsi.position[0]));
                lastsi = si;

            }
        }
    }


    private static native void cDetectSignalRaw(double[] data, double[] reference, int TB_W0, double TB_simThreshold, int[] ret1, double[] ret2);

    private static SignalDetector.SignalInfo cDetectSignal(double[] data, double[] reference, int TB_W0, double TB_simThreshold) {
        int[] ret1 = new int[4];
        double[] ret2 = new double[4];
        cDetectSignalRaw(data, reference, TB_W0, TB_simThreshold, ret1, ret2);
        return new SignalDetector.SignalInfo(ret1[0], ret2[0], Arrays.copyOfRange(ret1, 1, 4), Arrays.copyOfRange(ret2, 1, 4));
    }

}

//    private void detectSound() {
//        byte[] buffer = new byte[bufferSize];
//        int read = 0;
//        while (isRecording) {
//            read = recorder.read(buffer, 0, bufferSize);
//            if (read != AudioRecord.ERROR_INVALID_OPERATION) {
//                //test
//                short[] shorts = new short[buffer.length / 2];
//                // to turn bytes to shorts as either big endian or little endian.
//                ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
//                double[] x = new double[shorts.length];
//                for (int i = 0; i < shorts.length; i++) {
//                    x[i] = 1.0 * shorts[i] / Short.MAX_VALUE;
//                }
//                SignalProcess.SignalInfo sInfo = SignalProcess.signalAnalyze(x, TheBrain.MYCONF_SAMPLERATE);
//                int beepType = SignalProcess.beepType(x, TheBrain.MYCONF_SAMPLERATE);
//                feedback(1, String.format(Locale.CHINA, "freq: %.2f. proportion: %.2f", sInfo.maxFreq, sInfo.proportion));
//                feedback(2, "beepType: " + beepType);
//                if ((beepType & 1) > 0)
//                    theBrain.report(TheBrain.DATA_LISTEN_1, System.nanoTime());
//                if ((beepType & (1 << 1)) > 0)
//                    theBrain.report(TheBrain.DATA_LISTEN_2, System.nanoTime());
//            }
//        }
//    }

//    private void writeAudioDataToFile() {
//        byte[] data = new byte[bufferSize];
//        String filename = context.getDataDir() + "/test.dat";
//
//        Log.d("scsc", "开始录音地址===== " + filename);
//        FileOutputStream os = null;
//        try {
//            os = new FileOutputStream(filename);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//
//        int read;
//
//        if (os != null) {
//            while (isRecording) {
//                read = recorder.read(data, 0, bufferSize);
//                if (read != AudioRecord.ERROR_INVALID_OPERATION) {
//                    try {
//                        os.write(data);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//
//            try {
//                os.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }
