package com.scscscc.ranging;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

public class MyPlayer {

    private static int bufferSize;
    private static boolean isPlaying;
    private static AudioTrack player;
    private static Thread playingThread;


    public static void init() {
        bufferSize = AudioTrack.getMinBufferSize(
                TheBrain.sampleRate,
                TheBrain.MYCONF_CHANNEL_OUT_CONFIG,
                TheBrain.MYCONF_AUDIO_ENCODING);

        AudioTrack.Builder builder = new AudioTrack.Builder();
        builder.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(TheBrain.MYCONF_AUDIO_ENCODING)
                        .setSampleRate(TheBrain.sampleRate)
                        .setChannelMask(TheBrain.MYCONF_CHANNEL_OUT_CONFIG)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM);

        player = builder.build();
    }

    public static void startPlaying() {
        if (isPlaying)
            return;

        player.play();
        isPlaying = true;
        playingThread = new Thread(() -> {
            playChirp();

            isPlaying = false;
            player.stop();
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
        int bufferPos = 0;
        while (isPlaying && bufferPos < TheBrain.playBuffer.length) {
            try {
                int playLength = Math.min(bufferSize, TheBrain.playBuffer.length - bufferPos);
                player.write(TheBrain.playBuffer, bufferPos, playLength);
                bufferPos += playLength;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
