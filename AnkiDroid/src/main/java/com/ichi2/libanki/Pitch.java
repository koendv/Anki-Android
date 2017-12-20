/****************************************************************************************
 * Copyright (c) 2017 Koen De Vleeschauwer <koen@mcvax.org>                             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.DetermineDurationProcessor;
import be.tarsos.dsp.StopAudioProcessor;
import be.tarsos.dsp.io.android.AndroidAudioPlayer;
import be.tarsos.dsp.io.android.AndroidFFMPEGLocator;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;
import be.tarsos.dsp.writer.WriterProcessor;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.libanki.PitchScore;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;

import timber.log.Timber;

/**
 * Created by koen on 12/7/17.
 * XXX Todo: add Karaoke mode.
 */

public class Pitch {

    private AudioDispatcher mAudioDispatcher;
    private AndroidAudioPlayer mAndroidAudioPlayer;
    private PitchProcessor mPitchProcessor;
    private String recordPath;
    private String playPath;

    private static WeakReference<Context> mReviewer;

    public Pitch() {
        new AndroidFFMPEGLocator(AnkiDroidApp.getInstance().getApplicationContext());

        File recordDir = AnkiDroidApp.getInstance().getApplicationContext().getCacheDir();

        File recordFile = null;
        try {
            recordFile = File.createTempFile("record", ".wav", recordDir);
        } catch (IOException e) {
            Timber.e("Failed to create temp .wav file");
            e.printStackTrace();
        }
        recordPath = recordFile.getPath();
    }

    public static void initialize(Context context) {
        // Store weak reference to Activity to prevent memory leak
        mReviewer = new WeakReference<>(context);
    }

    private double soundDuration(String soundPath) {
        AudioDispatcher audioDispatcher = AudioDispatcherFactory.fromPipe(soundPath, 22050, 1024, 0);
        DetermineDurationProcessor ddp = new DetermineDurationProcessor();
        audioDispatcher.addAudioProcessor(ddp);
        audioDispatcher.run();
        return ddp.getDurationInSeconds();
    }

    public void playSound(String soundPath) {

        Timber.i("recordPath" + recordPath); // XXX

        double duration_sec = 3; // XXX Default for recorded speech

        if (soundPath.equals("@rec@")) {
            /* rec.mp3: record from microphone to file "record.wav" */
            mAudioDispatcher.stop();
            mAudioDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0);
            /* mAudioDispatcher.addAudioProcessor(new SilenceDetector()); */
            try {
                mAudioDispatcher.addAudioProcessor(new WriterProcessor(mAudioDispatcher.getFormat(), new RandomAccessFile(recordPath, "rw")));
            } catch (FileNotFoundException e) {
                Timber.e("Can't record to temporary .wav file");
                e.printStackTrace();
            }
            mAudioDispatcher.addAudioProcessor(new StopAudioProcessor(duration_sec));
        } else {
            if (soundPath.equals("@play@")) {
                /* play.mp3: playback microphone recording from file "record.wav" */
                mAudioDispatcher.stop();
                duration_sec = soundDuration(recordPath);
                mAudioDispatcher = AudioDispatcherFactory.fromPipe(recordPath, 22050, 1024, 0);
                mAudioDispatcher.addAudioProcessor(new AndroidAudioPlayer(mAudioDispatcher.getFormat(), AudioTrack.getMinBufferSize(22050, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), AudioManager.STREAM_MUSIC));
            } else {
                playPath = new String(soundPath);
                /* default: play audio file soundPath */
                duration_sec = soundDuration(soundPath); /* duration of mp3 determines horizontal axis */
                mAudioDispatcher = AudioDispatcherFactory.fromPipe(soundPath, 22050, 1024, 0);
                mAudioDispatcher.addAudioProcessor(new AndroidAudioPlayer(mAudioDispatcher.getFormat(), AudioTrack.getMinBufferSize(22050, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), AudioManager.STREAM_MUSIC));
            }
        }

        final boolean isAnswer = (soundPath.equals("@rec@") || soundPath.equals("@play@"));
        int graph_number;

        if (isAnswer) {
            graph_number = 1;
        } else {
            graph_number = 0;
        }

        ((AbstractFlashcardViewer) mReviewer.get()).runJavaScript("graph_start(" + graph_number + ", " + duration_sec + ")");

        mAudioDispatcher.addAudioProcessor(new PitchProcessor(PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, new PitchDetectionHandler() {
                @Override
                public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
                    final float pitchInHz = pitchDetectionResult.getPitch();
                    final float secondsProcessed = mAudioDispatcher.secondsProcessed();

                    /* Timber.i("pitch: " + pitchInHz); */
                    ((AbstractFlashcardViewer) mReviewer.get()).runJavaScript("graph_add(" + secondsProcessed + ", " + pitchInHz + ")");
                }
            }));
        new Thread(mAudioDispatcher, "Audio Dispatcher").start();

        return;
    }

    public void stopSound () {
        if (mAudioDispatcher != null) {
            mAudioDispatcher.stop();
        }
        ((AbstractFlashcardViewer) mReviewer.get()).runJavaScript("graph_stop()");
    }

    public void pitchScore () {

    }
}

// not truncated
