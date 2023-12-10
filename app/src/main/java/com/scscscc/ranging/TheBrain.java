package com.scscscc.ranging;


import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;

import java.util.Arrays;
import java.util.Locale;

public class TheBrain {

    public static final int DATA_A0 = 0;
    public static final int DATA_DELTA_A = 1;
    public static final int DATA_DELTA_B = 2;
    public static final int DATA_A1 = 3;
    public static final int DATA_A3 = 4;
    public static final int DATA_B1 = 5;
    public static final int DATA_B3 = 6;
    public static final int DATA_DELTA = 9;
    public static final int DATA_LISTEN = 10;
    public static int sampleRate = 44100;
    public static final int MYCONF_CHIPFREQ1 = 19000;
    public static final int MYCONF_CHIPFREQ2 = 20000;
    public static final int W0 = 0;
    public static final int W1 = 100;
    public static double[] playSamples;
    public static byte[] playBuffer;
    public static double[] refSamples;
    private static final long[] data = new long[7];
    private static Handler handler;
    private static final double soundSpeed = 340;
    private static long startTime;

    public static boolean enable = false;
    public static double simThreshold = 5;
    public static final int MYCONF_CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_MONO;

    public static final int MYCONF_CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    public static final int MYCONF_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static void genChirp(int blankTimeInMillis, int warmTimeInMillis, float warmFreq, int waitTimeInMillis, int chirpTimeInMillis, float freq1, float freq2) {
        int blankSampleNum = blankTimeInMillis * sampleRate / 1000;
        int warmSampleNum = warmTimeInMillis * sampleRate / 1000;
        int chirpSampleNum = chirpTimeInMillis * sampleRate / 1000;
        int waitSampleNum = waitTimeInMillis * sampleRate / 1000;

        double[] blankBuffer = new double[blankSampleNum];
        Arrays.fill(blankBuffer, 0);

        double[] warmBuffer = new double[warmSampleNum];
        for (int sampleIdx = 0; sampleIdx < warmSampleNum; sampleIdx++) {
            double t = 1.0 * sampleIdx / sampleRate;
            double d = Math.sin(2 * Math.PI * warmFreq * t);
            warmBuffer[sampleIdx] = d;
        }

        double[] chirpBuffer = new double[chirpSampleNum];
        float freqPerSample = (freq2 - freq1) / chirpSampleNum;
        for (int sampleIdx = 0; sampleIdx < chirpSampleNum; sampleIdx++) {
            double t = 1.0 * sampleIdx / sampleRate;
            double d = Math.sin(2 * Math.PI * (freq1 + freq1 + sampleIdx * freqPerSample) * t / 2);
            chirpBuffer[sampleIdx] = d;
        }

        refSamples = new double[chirpSampleNum];
        System.arraycopy(chirpBuffer, 0, refSamples, 0, chirpSampleNum);

        playSamples = new double[blankSampleNum + warmSampleNum + W0 + waitSampleNum + chirpSampleNum];

        System.arraycopy(blankBuffer, 0, playSamples, 0, blankSampleNum);
        System.arraycopy(warmBuffer, 0, playSamples, blankSampleNum, warmSampleNum);
        System.arraycopy(chirpBuffer, 0, playSamples, blankSampleNum + warmSampleNum + W0 + waitSampleNum, chirpSampleNum);

        playBuffer = new byte[playSamples.length * 2];
        for (int i = 0; i < playSamples.length; i++) {
            short s = (short) (playSamples[i] * Short.MAX_VALUE);
            playBuffer[i * 2] = (byte) (s & 0xff);
            playBuffer[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
    }


    private static void feedback(int channel, String txt, boolean append) {
        Message msg = new Message();
        msg.what = channel;
        msg.obj = txt;
        if (append)
            msg.arg2 = 1;
        handler.sendMessage(msg);
    }

    public static void reset() {
        clear();
        feedback(5, "reset brain", false);
    }

    private static void clear() {
        Arrays.fill(data, -1);
    }

    public static void report(int type, long value) {
        if (!enable) {
            if (type == DATA_A0) {
                startTime = System.nanoTime();
            } else if (type == DATA_LISTEN) {
                long time = System.nanoTime() - startTime;
                double timeInMilli = 1.0 * time / 1000000;
                feedback(5, String.format(Locale.CHINA, "time: %.2fms", timeInMilli), false);
            }
            return;
        }
        if (type == DATA_A0) {
            clear();
            data[DATA_A0] = value;
            startTime = System.nanoTime();
            feedback(5, "A0: " + value, false);
            Thread watcher = new Thread(() -> {
                try {
                    long tag = data[DATA_A0];
                    Thread.sleep(1000);
                    if (data[DATA_A0] == tag) {
                        feedback(4, "dist: failed", false);
                        clear();
                    }
                } catch (InterruptedException e) {
                    feedback(5, "sleep interrupted", true);
                }
            });
            watcher.start();
            return;
        } else if (type == DATA_LISTEN) {
            if (data[DATA_A0] != -1) { // A
                if (data[DATA_A1] == -1) {
                    data[DATA_A1] = value;
                    feedback(5, "A1: " + value, true);
                } else if (data[DATA_A3] == -1) {
                    data[DATA_A3] = value;
                    long delta = data[DATA_A3] - data[DATA_A1];
                    feedback(5, "A3: " + value, true);
                    feedback(5, "send deltaa: " + delta, true);
                    BluetoothService.sendMessage(delta);
                }
            } else { //B
                if (data[DATA_B1] == -1) {
                    data[DATA_B1] = value;
                    Thread watcher = new Thread(() -> {
                        try {
                            long tag = data[DATA_B1];
                            Thread.sleep(1000);
                            if (data[DATA_B1] == tag) {
                                feedback(4, "dist: failed", false);
                                clear();
                            }
                        } catch (InterruptedException e) {
                            feedback(5, "sleep interrupted", true);
                        }
                    });
                    watcher.start();
                    MyPlayer.beep(false);
                    feedback(5, "B1: " + value, true);
                } else if (data[DATA_B3] == -1) {
                    data[DATA_B3] = value;
                    long delta = data[DATA_B3] - data[DATA_B1];
                    feedback(5, "B3: " + value, true);
                    feedback(5, "send deltab: " + delta, true);
                    BluetoothService.sendMessage(delta);
                }
            }
        } else if (type == DATA_DELTA) {
            if (data[DATA_A0] != -1) {
                data[DATA_DELTA_B] = value;
                feedback(5, "recv deltab: " + value, true);
            } else {
                data[DATA_DELTA_A] = value;
                feedback(5, "recv deltaa: " + value, true);
            }
        }

        if (data[DATA_A1] != -1 && data[DATA_A3] != -1 && data[DATA_DELTA_B] != -1) {
            double ta3 = 1.0 * data[DATA_A3] / sampleRate;
            double ta1 = 1.0 * data[DATA_A1] / sampleRate;
            double tb3_tb1 = 1.0 * data[DATA_DELTA_B] / sampleRate;
            double dist = soundSpeed / 2 * ((ta3 - ta1) - (tb3_tb1));
            long endTime = System.nanoTime();
            double time = 1.0 * (endTime - startTime) / 1000000;
            feedback(4, String.format(Locale.CHINA, "dist: %.2f\ntime: %.2fms", dist, time), false);
            feedback(5, "update dist = " + dist, true);
            clear();
            //myPlayer.beep(true);
        } else if (data[DATA_B1] != -1 && data[DATA_B3] != -1 && data[DATA_DELTA_A] != -1) {
            double tb3 = 1.0 * data[DATA_B3] / sampleRate;
            double tb1 = 1.0 * data[DATA_B1] / sampleRate;
            double ta3_ta1 = 1.0 * data[DATA_DELTA_A] / sampleRate;
            double dist = soundSpeed / 2 * ((ta3_ta1) - (tb3 - tb1));
            feedback(4, String.format(Locale.CHINA, "dist: %.2f", dist), false);
            feedback(5, "update dist = " + dist, true);
            clear();
        }
    }

    public static void init(Handler p_handler, Context context) {
        genChirp(50, 0, 19000, 0, 50, MYCONF_CHIPFREQ1, MYCONF_CHIPFREQ2);
        handler = p_handler;
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        String sampleRateStr = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        int bestSampleRate = Integer.parseInt(sampleRateStr);
        if (bestSampleRate != 0)
            sampleRate = bestSampleRate;
        clear();
    }
}
