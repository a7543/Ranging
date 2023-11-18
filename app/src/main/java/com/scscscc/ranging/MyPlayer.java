package com.scscscc.ranging;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class MyPlayer {
    private static final int MYCONF_CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int MYCONF_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static int bufferSize;
    private static boolean isPlaying;
    private static AudioTrack player;
    private static Thread playingThread;


    public static void init() {
        int minBufferSize = AudioTrack.getMinBufferSize(
                TheBrain.MYCONF_SAMPLERATE,
                MYCONF_CHANNEL_OUT_CONFIG,
                MYCONF_AUDIO_ENCODING);
        bufferSize = minBufferSize * 2;
    }

    public static void startPlaying() {
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

    public static void stopPlaying() {
        isPlaying = false;
    }

    public static void beep(boolean isA) {
        if (isA)
            TheBrain.report(TheBrain.DATA_A0, System.nanoTime());
        startPlaying();
    }

    private static void playChirp() {
        byte[] buffer = new byte[bufferSize];
        int bufferPos = 0;
        while (isPlaying && bufferPos < TheBrain.playBuffer.length) {
            try {
                int playLength = Math.min(bufferSize, TheBrain.playBuffer.length - bufferPos);
                System.arraycopy(TheBrain.playBuffer, bufferPos, buffer, 0, playLength);
                player.write(buffer, 0, playLength);
                bufferPos += playLength;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
//
//    private static void readAudioDataFromFile() {
//        byte[] data = new byte[bufferSize];
//        String filename = context.getDataDir() + "/test.dat";
//
//        Log.d("scsc", "开始录音地址===== " + filename);
//        FileInputStream is = null;
//        try {
//            is = new FileInputStream(filename);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//
//        int write;
//
//        if (is != null) {
//            while (isPlaying) {
//                try {
//                    write = is.read(data);
//                    if (write == -1) {
//                        isPlaying = false;
//                        break;
//                    }
//                    player.write(data, 0, bufferSize);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//
//            try {
//                is.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }
}
