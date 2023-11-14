package com.scscscc.ranging;


import android.media.AudioRecord;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

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
    public static double[] chirpBuffer;
    private static long[] data = new long[7];
    private Handler handler;
    private double soundSpeed = 340;
    public MyPlayer myPlayer;
    public BluetoothService bluetoothService;


    private void genChirp(int timeInMillis, float freq1, float freq2) {

        int samples = timeInMillis * MYCONF_SAMPLERATE / 1000;
        chirpBuffer = new double[samples];
        float freqPerSample = (freq2 - freq1) / samples;
        for (int sampleIdx = 0; sampleIdx < samples; sampleIdx++) {
            double t = 1.0 * sampleIdx / MYCONF_SAMPLERATE;
            double d = Math.cos(2 * Math.PI * (freq1 + freq1 + sampleIdx * freqPerSample) * t / 2);
            chirpBuffer[sampleIdx] = d;
        }
    }


    private void feedback(int channel, String txt) {
        Message msg = new Message();
        msg.what = channel;
        msg.obj = txt;
        handler.sendMessage(msg);
    }

    private void clear() {
        Arrays.fill(data, -1);
    }

    public void report(int type, long value) {
        if (type == DATA_A0) {
            clear();
            data[DATA_A0] = value;
            feedback(5, "A0: " + value);
            return;
        } else if (type == DATA_LISTEN) {
            if (data[DATA_A0] != -1) { // A
                if (data[DATA_A1] == -1) {
                    data[DATA_A1] = value;
                    feedback(5, "A1: " + value);
                } else if (data[DATA_A3] == -1) {
                    data[DATA_A3] = value;
                    long delta = data[DATA_A3] - data[DATA_A1];
                    feedback(5, "A3: " + value);
                    feedback(5, "send deltaa: " + delta);
                    bluetoothService.sendMessage(delta);
                }
            } else { //B
                if (data[DATA_B1] == -1) {
                    data[DATA_B1] = value;
                    myPlayer.beep(false);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        feedback(5, "sleep interrupted");
                    }
                    feedback(5, "B1: " + value);
                } else if (data[DATA_B3] == -1) {
                    data[DATA_B3] = value;
                    long delta = data[DATA_B3] - data[DATA_B1];
                    feedback(5, "B3: " + value);
                    feedback(5, "send deltab: " + delta);
                    bluetoothService.sendMessage(delta);
                }
            }
            return;
        } else if (type == DATA_DELTA) {
            if (data[DATA_A0] != -1) {
                data[DATA_DELTA_B] = value;
                feedback(5, "recv deltab: " + value);
            } else {
                data[DATA_DELTA_A] = value;
                feedback(5, "recv deltaa: " + value);
            }
        }

        if (data[DATA_A1] != -1 && data[DATA_A3] != -1 && data[DATA_DELTA_B] != -1) {
            double c = soundSpeed;
            double ta3 = 1.0 * data[DATA_A3] / MYCONF_SAMPLERATE;
            double ta1 = 1.0 * data[DATA_A1] / MYCONF_SAMPLERATE;
            double tb3_tb1 = 1.0 * data[DATA_DELTA_B] / MYCONF_SAMPLERATE;
            double dist = c / 2 * ((ta3 - ta1) - (tb3_tb1));
            feedback(4, String.format(Locale.CHINA, "dist: %.2f", dist));
            feedback(5, "update dist = " + dist);
            clear();
            //myPlayer.beep(true);
        } else if (data[DATA_B1] != -1 && data[DATA_B3] != -1 && data[DATA_DELTA_A] != -1) {
            double c = soundSpeed;
            double tb3 = 1.0 * data[DATA_B3] / MYCONF_SAMPLERATE;
            double tb1 = 1.0 * data[DATA_B1] / MYCONF_SAMPLERATE;
            double ta3_ta1 = 1.0 * data[DATA_DELTA_A] / MYCONF_SAMPLERATE;
            double dist = c / 2 * ((ta3_ta1) - (tb3 - tb1));
            feedback(4, String.format(Locale.CHINA, "dist: %.2f", dist));
            feedback(5, "update dist = " + dist);
            clear();
        }
    }

    public TheBrain(Handler handler) {
        genChirp(50, MYCONF_CHIPFREQ1, MYCONF_CHIPFREQ2);
        this.handler = handler;
        clear();
    }
}
