package com.scscscc.ranging;


import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

public class MyRecorder {
    private static final int MYCONF_CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int MYCONF_CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int MYCONF_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static int minBufferSize;
    private static int bufferSize;
    private boolean isRecording;
    private AudioRecord recorder;
    private Thread recordingThread;

    private TheBrain theBrain;
    private Context context;
    private Handler handler;

    private class FakeRecorder {
        private int pos = 0;
        private int goodpos = 10000;

        public void read(short[] buffer) {
            for (int i = 0; i < buffer.length; i++) {
                if (pos < goodpos || pos >= goodpos + TheBrain.playBuffer.length)
                    buffer[i] = 1;
                else
                    buffer[i] = (short) (TheBrain.playBuffer[pos - goodpos] * Short.MAX_VALUE);
                pos += 1;

            }
        }
    }

    private void feedback(int channel, String txt) {
        Message msg = new Message();
        msg.what = channel;
        msg.obj = txt;
        handler.sendMessage(msg);
    }

    public MyRecorder(TheBrain theBrain, Context context, Handler handler) {
        this.theBrain = theBrain;
        this.context = context;
        this.handler = handler;
        minBufferSize = AudioRecord.getMinBufferSize(
                TheBrain.MYCONF_SAMPLERATE,
                MYCONF_CHANNEL_IN_CONFIG,
                MYCONF_AUDIO_ENCODING);
        bufferSize = minBufferSize * 10;
    }

    public void startRecording() {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            feedback(1, "no microphone permission");
            return;
        }
//        soundtest();
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                TheBrain.MYCONF_SAMPLERATE, MYCONF_CHANNEL_IN_CONFIG, MYCONF_AUDIO_ENCODING, bufferSize);
        recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(() -> {
//                writeAudioDataToFile();
//                soundtest();
            detectSound();
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    public void stopRecording() {

        if (recorder != null) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }


    }

    int sampleCount = 0;

    private void soundtest() {

        byte[] buffer = new byte[bufferSize];
        double[] x = new double[bufferSize];
        int pos = bufferSize - TheBrain.W0;

        int read;
        String filename = context.getDataDir() + "/test.dat";

        Log.d("scsc", "开始录音地址===== " + filename);
        FileInputStream is = null;
        try {
            is = new FileInputStream(filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        while (true) {
            try {
                read = is.read(buffer, 0, bufferSize);
            } catch (IOException e) {
                break;

            }
            if (read <= 0)
                break;
            if (sampleCount > 88000)
                break;
            short[] shorts = new short[buffer.length / 2];

            ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);

            System.arraycopy(x, bufferSize / 2, x, 0, bufferSize / 2);
            pos -= bufferSize / 2;
            sampleCount += bufferSize / 2;
            for (int i = 0; i < shorts.length; i++) {
                x[i + shorts.length] = 1.0 * shorts[i] / Short.MAX_VALUE;
            }

            while (pos <= bufferSize / 2) {
                SignalDetector.SignalInfo si = SignalDetector.detectSignal(Arrays.copyOfRange(x, pos, pos + bufferSize / 2), TheBrain.playBuffer);
                feedback(5, "wowowow " + (sampleCount + pos - bufferSize + si.position) + " " + si.confidence + " " + si.similarity);
                pos += bufferSize / 2 - TheBrain.W0;
            }
        }
    }

    int count = 0;

    private void detectSound() {
        byte[] buffer = new byte[bufferSize];
        double[] x = new double[bufferSize];
        int pos = bufferSize - TheBrain.W0;
//        FakeRecorder fr = new FakeRecorder();
        int read;
        while (isRecording) {
            read = recorder.read(buffer, 0, bufferSize);
            if (read != AudioRecord.ERROR_INVALID_OPERATION) {
                //test
                short[] shorts = new short[buffer.length / 2];
                // to turn bytes to shorts as either big endian or little endian.
                ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);

//                fr.read(shorts);
                System.arraycopy(x, bufferSize / 2, x, 0, bufferSize / 2);
                pos -= bufferSize / 2;
                sampleCount += bufferSize / 2;
                for (int i = 0; i < shorts.length; i++) {
                    x[i + shorts.length] = 1.0 * shorts[i] / Short.MAX_VALUE;
                }

                while (pos <= bufferSize / 2) {
                    SignalDetector.SignalInfo si = SignalDetector.detectSignal(Arrays.copyOfRange(x, pos, pos + bufferSize / 2), TheBrain.refBuffer);
                    feedback(1, String.format(Locale.CHINA, "%d: %d %.2f %.2f\n", si.status, (sampleCount + pos - bufferSize + si.position), si.confidence, si.similarity));
                    if (si.status == 0)
                        theBrain.report(TheBrain.DATA_LISTEN, sampleCount + pos - bufferSize + si.position);
                    if (si.status == 0)
                        count += 1;
                    feedback(2, "count: " + count);
                    pos += bufferSize / 2 - TheBrain.W0 - TheBrain.playBuffer.length + 1;
                }
            }
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

    private void writeAudioDataToFile() {
        byte[] data = new byte[bufferSize];
        String filename = context.getDataDir() + "/test.dat";

        Log.d("scsc", "开始录音地址===== " + filename);
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        int read;

        if (os != null) {
            while (isRecording) {
                read = recorder.read(data, 0, bufferSize);
                if (read != AudioRecord.ERROR_INVALID_OPERATION) {
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
