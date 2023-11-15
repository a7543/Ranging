package com.scscscc.ranging;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MyPlayer {
    private static final int MYCONF_CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    private static final int MYCONF_CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int MYCONF_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static int minBufferSize;
    private static int bufferSize;
    private boolean isPlaying;
    private AudioTrack player;
    private Thread playingThread;

    private final Context context;
    private final TheBrain theBrain;

    public MyPlayer(TheBrain theBrain, Context context) {
        this.theBrain = theBrain;
        this.context = context;
        minBufferSize = AudioTrack.getMinBufferSize(
                TheBrain.MYCONF_SAMPLERATE,
                MYCONF_CHANNEL_OUT_CONFIG,
                MYCONF_AUDIO_ENCODING);
        bufferSize = minBufferSize;
    }

    public void startPlaying() {
        if (isPlaying)
            return;
        player = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                TheBrain.MYCONF_SAMPLERATE,
                MYCONF_CHANNEL_OUT_CONFIG,
                MYCONF_AUDIO_ENCODING,
                bufferSize,
                AudioTrack.MODE_STREAM);

        player.play();
        isPlaying = true;
        playingThread = new Thread(() -> {
            playChirp();

            isPlaying = false;
            player.stop();
            player.release();
            player = null;
            playingThread = null;
        }, "AudioRecorder Thread");

        playingThread.start();
    }

    public void stopPlaying() {
        isPlaying = false;
    }

    public void beep(boolean isA) {
        if (isA)
            theBrain.report(TheBrain.DATA_A0, System.nanoTime());
        startPlaying();
    }

    private void playChirp() {
        byte[] buffer = new byte[bufferSize];
        int bufferSamples = bufferSize / 2;
        int sampleIdx = 0;
        while (isPlaying) {
            try {
                int playSamples = 0;
                for (int i = 0; i < bufferSamples; i++) {
                    if (sampleIdx == TheBrain.playBuffer.length) {
                        buffer[i * 2] = 0;
                        buffer[i * 2 + 1] = 0;
                    } else {
                        playSamples += 1;
                        double d = TheBrain.playBuffer[sampleIdx];
                        short val = (short) (d * Short.MAX_VALUE);
                        buffer[i * 2] = (byte) (val & 0x00ff);
                        buffer[i * 2 + 1] = (byte) ((val & 0xff00) >> 8);

                        sampleIdx += 1;
                    }
                }
                player.write(buffer, 0, playSamples * 2);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (sampleIdx == TheBrain.playBuffer.length)
                break;
        }
    }

    private void readAudioDataFromFile() {
        byte[] data = new byte[bufferSize];
        String filename = context.getDataDir() + "/test.dat";

        Log.d("scsc", "开始录音地址===== " + filename);
        FileInputStream is = null;
        try {
            is = new FileInputStream(filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        int write;

        if (is != null) {
            while (isPlaying) {
                try {
                    write = is.read(data);
                    if (write == -1) {
                        isPlaying = false;
                        break;
                    }
                    player.write(data, 0, bufferSize);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
