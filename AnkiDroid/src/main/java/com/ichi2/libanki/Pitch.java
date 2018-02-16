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

	/*
	 * Data about Chinese speech:
	 *
	 * * The average length of a syllable is between 180 and 215 ms.
	 *
	 * This means that to get 5 to 10 pitch samples per syllable
	 * we need 24 to 48 pitch samples per second.
	 * (FFTsize code)
	 *
	 * * The pitch range for Chinese speech is less than an octave.
	 *
	 * This allows us to filter outliers from the pitch values.
	 * (FilterPitch pass1 code)
	 *
	 * Also, when drawing the graph of pitch values,
	 * draw at least an octave from highest pitch down.
	 * (pitch.js code)
	 *
	 * * Rise and fall time for pitch change,
	 * *  t_rise = 89.6 + 8.7 * d
	 * * where t_rise = rise time in milliseconds
	 * *  t_fall = 100.4 + 5.8 * d
	 * * where t_fall = fall time in milliseconds
	 * * with d in semitones (12 semitones to an octave):
	 * *  d = 12 * ln(f/f0) / ln(2)
	 * * and f > f0.
	 *
	 * This allows us to filter glitches in the pitch values.
	 * (FilterPitch pass2, t_rise, t_fall code)
	 *
	 * * Source: The Oxford Handbook of Chinese Linguistics,
	 * * Ch. 36, "Intonation in Chinese", pp. 490-491,
	 *
	 */

	/*
	 * XXX Todo: to run smoother on systems with slower cpu, take logarithm of
	 * pitch frequency once, store logarithms in array, and do slew rate calculations
	 * and draw pitch graph with stored logaritms only.
	 * Todo: add karaoke mode?
	 */


package com.ichi2.libanki;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.GainProcessor;
import be.tarsos.dsp.StopAudioProcessor;
import be.tarsos.dsp.io.android.AndroidAudioPlayer;
import be.tarsos.dsp.io.android.AndroidFFMPEGLocator;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;
import be.tarsos.dsp.rubberband.RubberBandAudioProcessor;
import be.tarsos.dsp.writer.WriterProcessor;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiDroidApp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;

import org.json.JSONArray;

import timber.log.Timber;

public class Pitch {

    private AudioDispatcher mAudioDispatcher = null;
    private AudioDispatcher mKaraokeDispatcher = null;
    private AndroidAudioPlayer mAndroidAudioPlayer = null;
    private PitchProcessor mPitchProcessor = null;
    private String recordPath; // filename of recording
    private boolean recorded = false; // true if file with name recordPath exists
    private String timeStretchPath; // filename of timestretchec speedh
    private boolean timeStretched = false; // true if file with name timeStretchPath exists
    private SampleRates sampleRates = new SampleRates();
    private double mLastPitchTimeStamp = 0; // time of last detected pitch, in seconds. -1 if no pitch detected yet.
    private List<double[]> pitchData = new ArrayList<double[]>(); /* arraylist of (time, frequency) pairs. */
    private class TimeValue {
        public final double t;
        public final double y;
        public TimeValue(double t_val, double y_val) {
            this.t = t_val;
            this.y = y_val;
        }
    }
    private List<TimeValue> TimeSeries = new ArrayList<TimeValue>(); /* timeseries of (time, value) pairs */

    private static WeakReference<Context> mReviewer;

    /* Preference Panel */
    boolean tonesEnabled = false;
    boolean karaokeEnabled = false;
    int karaokeSpeed = 100;
    boolean contourEnabled = false;

    /*
     * safety factor.
     * Determines how hard pitch values are filtered for outliers and glitches.
     * safety factor value is higher than 1. Suggested value 1.66.
     * A value of 1 means hard filtering; a value of 4 in practice does not remove anything.
     */
    final private double safety_factor = 1.66; /* value between 1 (hard) and 4 (no) removing outliers/glitches */

    public Pitch() {
        /* find ffmpeg binaries */
        new AndroidFFMPEGLocator(AnkiDroidApp.getInstance().getApplicationContext());

        /* build temp filename for recordings */
        File cacheDir = AnkiDroidApp.getInstance().getApplicationContext().getCacheDir();
        File recordFile = null;
        try {
            recordFile = File.createTempFile("record", ".wav", cacheDir);
            recordFile.deleteOnExit();
        } catch (IOException e) {
            Timber.e("Failed to create temp .wav file");
            e.printStackTrace();
        }
        recordPath = recordFile.getPath();

        /* build temp filename for time-stretching */
        File timeStretchFile = null;
        try {
            timeStretchFile = File.createTempFile("record", ".wav", cacheDir);
            timeStretchFile.deleteOnExit();
        } catch (IOException e) {
            Timber.e("Failed to create time-stretching .wav file");
            e.printStackTrace();
        }
        timeStretchPath = timeStretchFile.getPath();
    }

    public static void initialize(Context context) {
        // Store weak reference to Activity to prevent memory leak
        mReviewer = new WeakReference<>(context);
    }

    /*
     * Get preferences from Settings panel
     */
    private void getPreferences() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());

        int oldKaraokeSpeed = karaokeSpeed;
        tonesEnabled = preferences.getBoolean("tones_enabled", false);
        karaokeEnabled = preferences.getBoolean("tones_karaoke_enabled", false);
        karaokeSpeed = preferences.getInt("tones_karaoke_speed", 100);
        contourEnabled = preferences.getBoolean("tones_contour_enabled", false);
        if (oldKaraokeSpeed != karaokeSpeed)
            timeStretched = false; // time stretch ratio changed
        return;
    }

    /*
     * Called when a playsound: button with action=[stop|record|playback|replay] is pressed
     */

    public void playSound(String soundPath, String soundAction) {

        getPreferences();

        if (soundAction.equals("stop")) {
            /* stop all playback and recording */
            stop();
        } else if (soundAction.equals("record")) {
            /* record microphone to file 'record.wav' */
            record();
            recorded = true;
            if (karaokeEnabled) playKaraoke(soundPath);
        } else if (soundAction.equals("playback") && recorded) {
            /* playback microphone recording from file "record.wav" */
            play(recordPath, 1);
        } else if (soundAction.equals("replay")) {
            /* play audio file soundPath */
            play(soundPath, 0);
        }
        return;
     }

    /* 'erase' recording */
    public void erase () {
        recorded = false;
        timeStretched = false;
        return;
    }

    /* stop recording and playback */
    public void stop () {
        if ((mAudioDispatcher != null) && !mAudioDispatcher.isStopped())
            mAudioDispatcher.stop();
        if ((mKaraokeDispatcher != null) && !mKaraokeDispatcher.isStopped())
            mKaraokeDispatcher.stop();
        return;
    }

    /* play mp3/wav file 'soundPath' and draw pitch in graph 'graphNumber' */
    public void play (String soundPath, int graphNumber) {
        stop();
        mAudioDispatcher = AudioDispatcherFactory.fromPipe(soundPath, sampleRates.TrackSampleRate(), sampleRates.TrackBufferSizeInSamples(), 0);
        mAudioDispatcher.addAudioProcessor(new AndroidAudioPlayer(mAudioDispatcher.getFormat(), sampleRates.TrackBufferSizeInSamples(), AudioManager.STREAM_MUSIC));
        drawPitch(graphNumber, false);
        return;
    }

    /* record from microphone to file 'record.wav' and draw pitch in graph no. 1 */
    public void record () {
        double duration_sec = 10; // XXX Max length for recorded speech
        stop();
        mAudioDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRates.RecordSampleRate(), sampleRates.RecordBufferSizeInSamples(), 0);
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

    /* play mp3/wav file 'soundPath' with time-stretching */
    public void playKaraoke (String soundPath) {
        /*
         * Time-stretching is processor-intensive.
         * Do time-stretching only once, before playback, and write time-stretched audio to temp file.
         * Playback is then simply playing a .wav file
         */
        double timeRatio = karaokeSpeed / 100.0;
        if (!timeStretched) {
            // create time-stretched audio
            try {
                AudioDispatcher timeStretchDispatcher = AudioDispatcherFactory.fromPipe(soundPath, sampleRates.TrackSampleRate(), sampleRates.TrackBufferSizeInSamples(), 0);
                timeStretchDispatcher.addAudioProcessor(new RubberBandAudioProcessor(sampleRates.TrackSampleRate(), timeRatio, 1.0));
                timeStretchDispatcher.addAudioProcessor(new GainProcessor(1.0)); // buffer
                timeStretchDispatcher.addAudioProcessor(new WriterProcessor(timeStretchDispatcher.getFormat(), new RandomAccessFile(timeStretchPath, "rw")));
                timeStretchDispatcher.run();
                timeStretched = true;
            } catch (FileNotFoundException e) {
                Timber.d("RubberBand exception - could not write temp file");
            } catch (RuntimeException e){
            Timber.d("RubberBand exception");
            }
        }
        try {
            mKaraokeDispatcher = AudioDispatcherFactory.fromPipe(timeStretchPath, sampleRates.TrackSampleRate(), sampleRates.TrackBufferSizeInSamples(), 0);
            mKaraokeDispatcher.addAudioProcessor(new AndroidAudioPlayer(mKaraokeDispatcher.getFormat(), sampleRates.TrackBufferSizeInSamples(), AudioManager.STREAM_MUSIC));
            new Thread(mKaraokeDispatcher, "Karaoke Audio Dispatcher").start();
        } catch (RuntimeException e) {
            Timber.d("Karaoke exception");
        }




        return;
    }

    /* draw pitch graph */
    private void drawPitch(int graphNumber, final boolean recordingFromMicrophone) {

        mLastPitchTimeStamp = -1;

        int sampleRate;
        int bufferSize;
        if (recordingFromMicrophone) {
            sampleRate = sampleRates.RecordSampleRate();
            bufferSize = sampleRates.RecordBufferSizeInSamples();
        }
        else {
            sampleRate = sampleRates.TrackSampleRate();
            bufferSize = sampleRates.TrackBufferSizeInSamples();
        }

        /* calculate about 24 to 48 pitch values per second */
        int FFTSize = 512;
        while (24 * FFTSize < sampleRate) {
            FFTSize = FFTSize * 2;
        }
        Timber.d("FFT buffersize: " + FFTSize);
        if (FFTSize != bufferSize) {
            mAudioDispatcher.setStepSizeAndOverlap(FFTSize, 0);
        }

        /* the graph is drawn by sending javascript to the webview. See pitch.js */
        pitchData = new ArrayList<double[]>();

        final int graphNr = graphNumber;

        mAudioDispatcher.addAudioProcessor(new PitchProcessor(PitchEstimationAlgorithm.FFT_YIN, sampleRate, FFTSize, new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
                final float pitchInHz = pitchDetectionResult.getPitch();
                final float secondsProcessed = mAudioDispatcher.secondsProcessed();

                /* add data point (secondsProcessed, pitchInHz) to data */
                double[] dataPoint = {secondsProcessed, pitchInHz};
                pitchData.add(dataPoint);

                /* pass data on to javascript */
                JSONArray jscript_data = new JSONArray(FilterPitch(pitchData));

                /* draw graph */
                ((AbstractFlashcardViewer) mReviewer.get()).runJavaScript("graph_draw(" + graphNr + ", " + jscript_data.toString() + " )");

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
     * Filter glitches in measured pitch.
     *
     * pitchArray is an ArrayList of [t, f] values, where t is time in seconds, and f is frequency in Hz.
     * if f is -1 then no pitch was detected.
     */


    private  List<double[]> FilterPitch( List<double[]> pitchArray) {
        List<double[]> dta = pitchArray;
        ListIterator<double[]> litr = null;

        /* pass 1. check frequency range */
        /*
         * Chinese speech has a pitch range of less than an octave.
         * If pitch range is more than an octave,
         * remove max (or min) pitch sample if it's just a blip.
         */

        List<double[]> dta0 = new ArrayList<double[]>();

        /* create sorted list of pitch values */
        List<Double> freq = new ArrayList<Double>();
        litr = dta.listIterator();
        while (litr.hasNext()){
            double point[] = litr.next();
            double f = point[1];
            if (f != -1)
                freq.add(f);
        }

        Collections.sort(freq);

        /* determine cutoff points so maximum number of data points fits in an octave */
        double
                cutoff_low = -1,
                cutoff_high = -1,
                max_pitch_range = 2.0 * safety_factor; /* pitch range across the four tones in normal speech is  2 (an octave) */

        int count = -1;

        /* safe initial values */
        if (!freq.isEmpty()){
            cutoff_low = freq.get(0);
            cutoff_high = freq.get(freq.size()-1);
        }

        for (int i = 0; i < freq.size(); i++) {
            double f_low = freq.get(i);
            for (int j = freq.size()-1; j >= i; --j) {
                if (j - i + 1 < count)
                    break;
                double f_high = freq.get(j);
                if (f_high < max_pitch_range * f_low) {
                    if (j - i + 1 > count) {
                        cutoff_low = f_low;
                        cutoff_high = f_high;
                        count = j - i + 1;
                    }
                    break;
                }
            }
        }
        /* Timber.d("cutoff " + cutoff_low + " Hz to " + cutoff_high + " Hz"); */

        /* copy pitch values within range cutoff_low..cutoff_high (and silences) */
        litr = dta.listIterator();
        while (litr.hasNext()) {
            double[] point = litr.next();
            double pitch = point[1];
            if ((pitch == -1) || ((pitch >= cutoff_low) && (pitch <= cutoff_high)))
                dta0.add(point);
            else
                Timber.d("drop outlier " + point[0] + " s " + point[1] +" Hz");
        }

        /* pass 2. check pitch rise and fall time. */
        List<double[]> dta1 = new ArrayList<double[]>();
        litr = dta0.listIterator();
        double[] pt_curr = new double[] {-1, -1};
        double[] pt_next = litr.next();
        boolean check_curr = true;
        boolean check_next = true;

        while (litr.hasNext()) {
            pt_curr = pt_next;
            pt_next = litr.next();
            check_curr = check_next;
            check_next = checkSlewRate(pt_curr, pt_next);

            /* drop if transition to and from this freq was too fast */
            if (!check_curr && !check_next) {
                Timber.d("drop pitch rate " + pt_curr[0] + " s " + pt_curr[1] +" Hz");
                continue;
            }

            /* valid data point */
            dta1.add(pt_curr);
        }
        dta1.add(pt_next);

        /* pass 3. replace multiple frequency == -1 values (unknown freq.) by a single -1 value */
        List<double[]> dta2 = new ArrayList<double[]>();
        litr = dta1.listIterator();
        while (litr.hasNext()) {
            double[] point = litr.next();
            if ((point[1] == -1) && (dta2.isEmpty() || (dta2.get(dta2.size() - 1)[1] == -1)))
                continue;
            dta2.add(point);
        }

        /* if last element has frequency -1 then remove last element */
        if (!dta2.isEmpty() && (dta2.get(dta2.size() - 1)[1] == -1))
            dta2.remove(dta2.size() - 1);

        return dta2;
    }

    /*
     * check whether transition from p0 [t0, f0] to p1 [t1, f1] is within what a human voice can do.
     */

    private boolean checkSlewRate(double p0[], double p1[]){
        double t0, t1, f0, f1;

        /* make sure t0 < t1 */
        if (p0[0] < p1[0]) {
            t0 = p0[0];
            t1 = p1[0];
            f0 = p0[1];
            f1 = p1[1];
        }
        else {
            t0 = p1[0];
            t1 = p0[0];
            f0 = p1[1];
            f1 = p0[1];
        }

        final double delta_t = t1 - t0;

        /*
         * check frequency slew rate against t_rise and t_fall,
         */

        /* f = -1 indicates no pitch detected */
        if ((f0 <= 0) || (f1 <= 0))
            return true;
        else if (f1 > f0) {
            /* rising */
            double rise_time = 0.1506 * Math.log(f1/f0);
            return (delta_t * safety_factor > rise_time);
        } else if (f1 < f0) {
            /* falling */
            double fall_time = 0.1004 * Math.log(f0/f1);
            return (delta_t * safety_factor > fall_time);
        }

        return true;
    }

    /* Convert frequency in Hz to semitone. There are 12 semitones in an octave. */
    private double hertzToSemitone (double f) {
        final double scale = 12 / Math.log(2);
        return scale * Math.log(f);
    }


}

// not truncated
