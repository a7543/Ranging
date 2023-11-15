package com.scscscc.ranging;


import android.media.AudioRecord;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Switch;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

public class TheBrain {

    public static int DATA_A0 = 0;
    public static int DATA_DELTA_A = 1;
    public static int DATA_DELTA_B = 2;
    public static int DATA_A1 = 3;
    public static int DATA_A3 = 4;
    public static int DATA_B1 = 5;
    public static int DATA_B3 = 6;
    public static int DATA_LISTEN_1 = 7;
    public static int DATA_LISTEN_2 = 8;
    public static int DATA_DELTA = 9;
    public static int DATA_LISTEN = 10;

    public static final int MYCONF_SAMPLERATE = 44100;
    public static final int MYCONF_CHIPFREQ1 = 2000;
    public static final int MYCONF_CHIPFREQ2 = 6000;
    public static final int W0 = 100;
    public static double[] playBuffer;
    public static double[] refBuffer;
    private static long[] data = new long[7];
    private Handler handler;
    private double soundSpeed = 340;
    public MyPlayer myPlayer;
    public BluetoothService bluetoothService;

    public boolean enable = false;

    private void genChirp(int warmTimeInMillis, float warmFreq, int waitTimeInMillis, int chirpTimeInMillis, float freq1, float freq2) {
        int warmSamples = warmTimeInMillis * MYCONF_SAMPLERATE / 1000;
        int chirpSamples = chirpTimeInMillis * MYCONF_SAMPLERATE / 1000;
        int waitSamples = waitTimeInMillis * MYCONF_SAMPLERATE / 1000;

        double[] warmBuffer = new double[warmSamples];
        for (int sampleIdx = 0; sampleIdx < warmSamples; sampleIdx++) {
            double t = 1.0 * sampleIdx / MYCONF_SAMPLERATE;
            double d = Math.sin(2 * Math.PI * warmFreq * t);
            warmBuffer[sampleIdx] = d;
        }

        double[] chirpBuffer = new double[chirpSamples];
        float freqPerSample = (freq2 - freq1) / chirpSamples;
        for (int sampleIdx = 0; sampleIdx < chirpSamples; sampleIdx++) {
            double t = 1.0 * sampleIdx / MYCONF_SAMPLERATE;
            double d = Math.sin(2 * Math.PI * (freq1 + freq1 + sampleIdx * freqPerSample) * t / 2);
            chirpBuffer[sampleIdx] = d;
        }

        refBuffer = new double[chirpSamples];
        System.arraycopy(chirpBuffer, 0, refBuffer, 0, chirpSamples);

        playBuffer = new double[warmSamples + W0 + waitSamples + chirpSamples];
        System.arraycopy(warmBuffer, 0, playBuffer, 0, warmSamples);
        System.arraycopy(chirpBuffer, 0, playBuffer, warmSamples + W0 + waitSamples, chirpSamples);
    }


    private void feedback(int channel, String txt, boolean append) {
        Message msg = new Message();
        msg.what = channel;
        msg.obj = txt;
        if (append)
            msg.arg2 = 1;
        handler.sendMessage(msg);
    }

    public void reset() {
        clear();
        feedback(5, "reset brain", false);
    }

    private void clear() {
        Arrays.fill(data, -1);
    }

    public void report(int type, long value) {
        if (!enable)
            return;
        if (type == DATA_A0) {
            clear();
            data[DATA_A0] = value;
            feedback(5, "A0: " + value, true);
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
                    bluetoothService.sendMessage(delta);
                }
            } else { //B
                if (data[DATA_B1] == -1) {
                    data[DATA_B1] = value;
                    myPlayer.beep(false);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        feedback(5, "sleep interrupted", true);
                    }
                    feedback(5, "B1: " + value, true);
                } else if (data[DATA_B3] == -1) {
                    data[DATA_B3] = value;
                    long delta = data[DATA_B3] - data[DATA_B1];
                    feedback(5, "B3: " + value, true);
                    feedback(5, "send deltab: " + delta, true);
                    bluetoothService.sendMessage(delta);
                }
            }
            return;
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
            double c = soundSpeed;
            double ta3 = 1.0 * data[DATA_A3] / MYCONF_SAMPLERATE;
            double ta1 = 1.0 * data[DATA_A1] / MYCONF_SAMPLERATE;
            double tb3_tb1 = 1.0 * data[DATA_DELTA_B] / MYCONF_SAMPLERATE;
            double dist = c / 2 * ((ta3 - ta1) - (tb3_tb1));
            feedback(4, String.format(Locale.CHINA, "dist: %.2f", dist), false);
            feedback(5, "update dist = " + dist, true);
            clear();
            //myPlayer.beep(true);
        } else if (data[DATA_B1] != -1 && data[DATA_B3] != -1 && data[DATA_DELTA_A] != -1) {
            double c = soundSpeed;
            double tb3 = 1.0 * data[DATA_B3] / MYCONF_SAMPLERATE;
            double tb1 = 1.0 * data[DATA_B1] / MYCONF_SAMPLERATE;
            double ta3_ta1 = 1.0 * data[DATA_DELTA_A] / MYCONF_SAMPLERATE;
            double dist = c / 2 * ((ta3_ta1) - (tb3 - tb1));
            feedback(4, String.format(Locale.CHINA, "dist: %.2f", dist), false);
            feedback(5, "update dist = " + dist, true);
            clear();
        }
    }

    public TheBrain(Handler handler) {
        genChirp(50, 11000, 5, 50, MYCONF_CHIPFREQ1, MYCONF_CHIPFREQ2);
        this.handler = handler;
        clear();
    }
}
