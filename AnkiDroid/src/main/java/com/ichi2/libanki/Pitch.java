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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;

import timber.log.Timber;

/**
 * Created by koen on 12/7/17.
 * XXX Todo: add Karaoke mode.
 * XXX Fix sample rate. On some phones the 'universal' sample rate of 22050 does not work.
 */

public class Pitch {

    private AudioDispatcher mAudioDispatcher = null;
    private AndroidAudioPlayer mAndroidAudioPlayer = null;
    private PitchProcessor mPitchProcessor = null;
    private String recordPath;
    private boolean recorded = false;

    private double mLastPitchTimeStamp = 0; // time of last detected pitch, in seconds. -1 if no pitch detected yet.

    private static WeakReference<Context> mReviewer;

    private MinMaxPitch minMaxPitch = new MinMaxPitch(); /* lowest pitch (frequency) to be expected */

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
                recorded = true;
            } else {
                if (soundPath.equals("@play@")) {
                    /* playback microphone recording from file "record.wav" */
                    if (recorded) play(recordPath, 1); /* if not recorded do not playback */
                } else {
                    /* default: play audio file soundPath */
                    play(soundPath, 0); 
                }
            }
        }

        return;
    }

    /* 'erase' recording */
    public void erase () {
        recorded = false;
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
        drawPitch(graphNumber, false);
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
        drawPitch(1, true);
        return;
    }

    /* draw pitch graph */
    private void drawPitch(int graphNumber, final boolean recordingFromMicrophone) {

        minMaxPitch.newseries(graphNumber); /* adjust graph baseline */
        mLastPitchTimeStamp = -1;

        /* the graph is drawn by sending javascript to the webview. See pitch.js */
        ((AbstractFlashcardViewer) mReviewer.get()).runJavaScript("graph_start(" + graphNumber + "," + minMaxPitch.min(graphNumber) + ")");

        mAudioDispatcher.addAudioProcessor(new PitchProcessor(PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
                final float pitchInHz = pitchDetectionResult.getPitch();
                final float secondsProcessed = mAudioDispatcher.secondsProcessed();

                /* running minimum */
                minMaxPitch.data(pitchInHz);

                /* add data point (secondsProcessed, pitchInHz) to graph */
                ((AbstractFlashcardViewer) mReviewer.get()).runJavaScript("graph_add(" + secondsProcessed + ", " + pitchInHz + ")");

                /* end recording if 0.5 seconds of "silence" */
                if (pitchInHz != -1) {
                    /* pitch detected, update timestamp */
                    mLastPitchTimeStamp = secondsProcessed;
                }
                else {
                    /* end recording if 0.5 seconds without detecting pitch */
                    if (recordingFromMicrophone && (mLastPitchTimeStamp > 0) && ((secondsProcessed - mLastPitchTimeStamp) > 0.5)) mAudioDispatcher.stop();
                }
            }
        }));
        new Thread(mAudioDispatcher, "Audio Dispatcher").start();
        return;
    }

    /*
     * Calculates the lowest expected frequency of a graph. Used to position the '0' of the y axis.
     * Keep two different 'lowest expected frequencies", one for question and one for answer.
     * "Put the origin of the y-axis at the lowest point of the fourth tone."
     */

    class MinMaxPitch {
        private int current_graph = 0;
        private double[] y_min = {-1.0, -1.0}; /* all-time minimum + low-pass */
        private double[] y_max= {-1.0, -1.0}; /* all-time maximum + low-pass */
        private double running_min = -1.0; /* minimum of current graph */
        private double running_max = -1.0; /* maximum of current graph */

        public void data(double y) {
            if (y == -1.0) return;
            if ((running_min == -1.0) || (running_min > y)) running_min = y;
            if ((running_max == -1.0) || (running_max < y)) running_max = y;
            return;
        }

        public void newseries(int new_graph) {
            final double tau = 0.1;

            if (running_min != -1.0) {
                if (y_min[current_graph] == -1.0) y_min[current_graph] = running_min; /* first data point */
                else y_min[current_graph] = tau * running_min + (1.0 - tau) * y_min[current_graph]; /* low-pass filter */
            }

            if (running_max != -1.0) {
                if (y_max[current_graph] == -1.0) y_max[current_graph] = running_max; /* first data point */
                else y_max[current_graph] = tau * running_max + (1.0 - tau) * y_max[current_graph]; /* low-pass filter */
            }

            Timber.d(String.format("y_min[0]: %6.1f y_max[0]: %6.1f y_min[1]: %6.1f y_max[1]: %6.1f run_max: %6.1f run_min: %6.1f", y_min[0], y_max[0], y_min[1], y_max[1], running_max, running_min));
            running_min = -1.0; /* reset running minimum */
            running_max = -1.0; /* reset running maximum */
            current_graph = new_graph;
        }

        public double min (int graph) {
            return y_min[graph];
        }

        public double max (int graph) {
            return y_max[graph];
        }
    }


}

// not truncated
