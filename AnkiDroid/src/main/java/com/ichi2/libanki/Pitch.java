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
 * Check permissions
 * Fix sample rate
 */

public class Pitch {

    private AudioDispatcher mAudioDispatcher = null;
    private AndroidAudioPlayer mAndroidAudioPlayer = null;
    private PitchProcessor mPitchProcessor = null;
    private String recordPath;
    private String playPath;

    private static WeakReference<Context> mReviewer;

    public Pitch() {
        /* find ffmpeg binaries */
        new AndroidFFMPEGLocator(AnkiDroidApp.getInstance().getApplicationContext());

        /* build temp filename for recordings */
        File recordDir = AnkiDroidApp.getInstance().getApplicationContext().getCacheDir();
        File recordFile = null;
        try {
            recordFile = File.createTempFile("record", ".wav", recordDir);
            recordFile.deleteOnExit();
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

    public void playSound(String soundPath) {

        if  (soundPath.equals("@stop@")) {
            stop();
        } else {
            if (soundPath.equals("@rec@")) {
                /* record microphone to file 'record.wav' */
                record();
            } else {
                if (soundPath.equals("@play@")) {
                    /* playback microphone recording from file "record.wav" */
                    play(recordPath, 1); 
                } else {
                    /* default: play audio file soundPath */
                    play(soundPath, 0); 
                }
            }
        }

        return;
    }

    /* stop recording and playback */
    public void stop () {
        if ((mAudioDispatcher != null) && !mAudioDispatcher.isStopped())
            mAudioDispatcher.stop();
        return;
    }

    /* play mp3/wav file 'soundPath' and draw pitch in graph 'graphNumber' */
    public void play (String soundPath, int graphNumber) {
        stop();
        mAudioDispatcher = AudioDispatcherFactory.fromPipe(soundPath, 22050, 1024, 0);
        mAudioDispatcher.addAudioProcessor(new AndroidAudioPlayer(mAudioDispatcher.getFormat(), AudioTrack.getMinBufferSize(22050, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), AudioManager.STREAM_MUSIC));
        drawPitch(graphNumber);
        return;
    }

    /* record from microphone to file 'record.wav' and draw pitch in graph no. 1 */
    public void record () {
        double duration_sec = 3; // XXX Default length for recorded speech

        stop();
        mAudioDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0);
        try {
            mAudioDispatcher.addAudioProcessor(new WriterProcessor(mAudioDispatcher.getFormat(), new RandomAccessFile(recordPath, "rw")));
        } catch (FileNotFoundException e) {
            Timber.e("Can't record to temporary .wav file");
            e.printStackTrace();
        }
        mAudioDispatcher.addAudioProcessor(new StopAudioProcessor(duration_sec));
        drawPitch(1);
        return;
    }

    /* draw pitch graph */
    private void drawPitch(int graphNumber) {

        /* the graph is drawn by sending javascript to the webview. See pitch.js */
        ((AbstractFlashcardViewer) mReviewer.get()).runJavaScript("graph_start(" + graphNumber + ")");

        mAudioDispatcher.addAudioProcessor(new PitchProcessor(PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
                final float pitchInHz = pitchDetectionResult.getPitch();
                final float secondsProcessed = mAudioDispatcher.secondsProcessed();

                /* add data point (secondsProcessed, pitchInHz) to graph */
                ((AbstractFlashcardViewer) mReviewer.get()).runJavaScript("graph_add(" + secondsProcessed + ", " + pitchInHz + ")");
                }
            }));
        new Thread(mAudioDispatcher, "Audio Dispatcher").start();
        return;
    }
        
}

// not truncated
