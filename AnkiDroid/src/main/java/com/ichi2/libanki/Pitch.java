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
import java.lang.StringBuilder;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;

import timber.log.Timber;

public class Pitch {

    private AudioDispatcher mAudioDispatcher = null;
    private AudioDispatcher mKaraokeDispatcher = null;
    private String recordPath; // filename of recording
    private boolean recorded = false; // true if file with name recordPath exists
    private String timeStretchPath; // filename of timestretchec speedh
    private boolean timeStretched = false; // true if file with name timeStretchPath exists
    private SampleRates sampleRates = new SampleRates();
    private double mLastPitchTimeStamp = 0; // time of last detected pitch, in seconds. -1 if no pitch detected yet.
    private class PitchValue {
        public final float t;
        public final float y;
        public PitchValue(float t_val, float y_val) {
            this.t = t_val; /* time in seconds */
            this.y = y_val; /* frequency in semitones */
        }
    }
    private List<PitchValue> pitchSeries = new ArrayList<PitchValue>(); /* timeseries of (time, semiTones) pairs */

    private static WeakReference<Context> mReviewer;

    /* Preference Panel */
    boolean tonesEnabled = false;
    boolean karaokeEnabled = false;
    int karaokeSpeed = 100;
    boolean toneContourEnabled = false;

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
        toneContourEnabled = preferences.getBoolean("tones_contour_enabled", false);
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
            Timber.d("RubberBand runtime exception");
            }
        }
        // playback time-stretched audio
        try {
            mKaraokeDispatcher = AudioDispatcherFactory.fromPipe(timeStretchPath, sampleRates.TrackSampleRate(), sampleRates.TrackBufferSizeInSamples(), 0);
            mKaraokeDispatcher.addAudioProcessor(new AndroidAudioPlayer(mKaraokeDispatcher.getFormat(), sampleRates.TrackBufferSizeInSamples(), AudioManager.STREAM_MUSIC));
            new Thread(mKaraokeDispatcher, "Karaoke Audio Dispatcher").start();
        } catch (RuntimeException e) {
            Timber.d("Karaoke playback exception");
        }

        return;
    }

    /* draw pitch graph */
    private void drawPitch(final int graphNumber, final boolean recordingFromMicrophone) {

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
        pitchSeries = new ArrayList<PitchValue>(); /* clear all values */

        final int graphNr = graphNumber;

        mAudioDispatcher.addAudioProcessor(new PitchProcessor(PitchEstimationAlgorithm.FFT_YIN, sampleRate, FFTSize, new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
                float pitch = pitchDetectionResult.getPitch();
                float secondsProcessed = mAudioDispatcher.secondsProcessed();
                boolean lastPitch = false;

                /* end recording if 0.5 seconds of "silence" */
                if (pitch != -1) {
                    /* convert to semitones */
                    pitch = hertzToSemitone(pitch);
                    /* pitch detected, update timestamp */
                    mLastPitchTimeStamp = secondsProcessed;

                }
                else {
                    /* end recording if 0.5 seconds without detecting pitch */
                    if (recordingFromMicrophone && (mLastPitchTimeStamp > 0) && ((secondsProcessed - mLastPitchTimeStamp) > 0.5)) {
                        mAudioDispatcher.stop();
                        lastPitch = true;
                    }
                }

                /* add data point (secondsProcessed, pitchInHz) to data */
                pitchSeries.add(new PitchValue(secondsProcessed, pitch));
                /* clean up measured pitch */
                List<PitchValue> filteredPitch = FilterPitch(pitchSeries);

                if (pitchSeries.size() > 2) {
                    /* Build javascript command */
                    StringBuilder jscriptCmd = new StringBuilder(1024);
                    jscriptCmd.append("graph_draw(");
                    jscriptCmd.append(graphNr);
                    jscriptCmd.append(", [");
                    Iterator<PitchValue> pitchValueIterator = filteredPitch.iterator();
                    String bracket = "[";
                    while (pitchValueIterator.hasNext()) {
                        jscriptCmd.append(bracket);
                        PitchValue pitchValue = pitchValueIterator.next();
                        jscriptCmd.append(pitchValue.t);
                        jscriptCmd.append(',');
                        jscriptCmd.append(pitchValue.y);
                        jscriptCmd.append(']');
                        bracket = ",[";
                    }
                    jscriptCmd.append("], ");
                    String pinyin = null;
                    if (lastPitch && toneContourEnabled && (graphNr == 1) && ((pinyin = getPinyin()) != null)) {
                        jscriptCmd.append('"');
                        jscriptCmd.append(pinyin);
                        jscriptCmd.append('"');
                    }
                    else {
                        jscriptCmd.append("null");
                    }
                    jscriptCmd.append(')');

                    /* draw graph */
                    ((AbstractFlashcardViewer) mReviewer.get()).runJavaScript(jscriptCmd.toString());
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


    private  List<PitchValue> FilterPitch( List<PitchValue> pitchArray) {
        ListIterator<PitchValue> litr = null;

        /* pass 1. check frequency range */

        /*
         * Chinese speech has a pitch range of less than an octave.
         * If pitch range is more than an octave,
         * remove max (or min) pitch sample if it's just a blip.
         */

        List<PitchValue> out1 = new ArrayList<PitchValue>();
        /* create sorted list of pitch values */
        List<Float> semitones = new ArrayList<Float>();

        litr = pitchArray.listIterator();
        while (litr.hasNext()){
            PitchValue p = litr.next();
            if (p.y != -1)
                semitones.add(p.y);
        }

        Collections.sort(semitones);

        /* determine cutoff points so maximum number of data points fits in an octave */

        float
                cutoff_low = -1,
                cutoff_high = -1,
                /* pitch_range_in_semitones Determines how hard tones are filtered */
                pitch_range_in_semitones = 14; /* pitch range across the four tones in normal speech is 12 semitones (an octave) */

        int count = -1;

        /* safe initial values */
        if (!semitones.isEmpty()){
            cutoff_low = semitones.get(0);
            cutoff_high = semitones.get(semitones.size()-1);
        }

        for (int i = 0; i < semitones.size(); i++) {
            float semitone_low = semitones.get(i);
            for (int j = semitones.size()-1; j >= i; --j) {
                if (j - i + 1 < count)
                    break;
                float semitone_high = semitones.get(j);
                if (semitone_high - semitone_low > pitch_range_in_semitones) {
                    if (j - i + 1 > count) {
                        cutoff_low = semitone_low;
                        cutoff_high = semitone_high;
                        count = j - i + 1;
                    }
                    break;
                }
            }
        }

        /* copy pitch values within range cutoff_low..cutoff_high (and silences) */
        litr = pitchArray.listIterator();
        while (litr.hasNext()) {
            PitchValue p = litr.next();
            if ((p.y == -1) || ((p.y >= cutoff_low) && (p.y <= cutoff_high)))
                out1.add(p);
            else
                Timber.d("drop outlier " + p.t + " s " + p.y +" semitone");
        }

        /* pass 2. Bandpass filter. Limit rise and fall time. */

        List<PitchValue> out2 = new ArrayList<PitchValue>();

        if (out1.size() > 2) {
            litr = out1.listIterator();
            PitchValue pt_curr = new PitchValue(-1, -1);
            PitchValue pt_next = litr.next();
            boolean check_curr = true;
            boolean check_next = true;

            while (litr.hasNext()) {
                pt_curr = pt_next;
                pt_next = litr.next();
                check_curr = check_next;
                check_next = checkSlewRate(pt_curr, pt_next);

                /* drop pitch value if transition to and from this freq was too fast */
                if (!check_curr && !check_next) {
                    Timber.d("drop pitch rate limit " + pt_curr.t + " s " + pt_curr.y + " semitones");
                    check_next = true; /* avoid next pitch value being discarded */
                    continue;
                }

            /* valid data point */
                out2.add(pt_curr);
            }
            out2.add(pt_next);
        } else {
            /* too few pitch values for filtering */
            out2 = out1;
        }

        /* pass 3. replace multiple frequency == -1 values (unknown freq.) by a single -1 value */

        List<PitchValue> out3 = new ArrayList<PitchValue>();
        litr = out1.listIterator();
        while (litr.hasNext()) {
            PitchValue p = litr.next();
            if ((p.y == -1) && (out3.isEmpty() || (out3.get(out3.size() - 1).y == -1)))
                continue;
            out3.add(p);
        }

        /* if last element has frequency -1 then remove last element */
        if (!out3.isEmpty() && (out3.get(out3.size() - 1).y == -1))
            out3.remove(out3.size() - 1);

        /* pass 4. begin at time = 0 */
        List<PitchValue> out4 = new ArrayList<PitchValue>();
        float begin_time = 0;
        if (!out3.isEmpty()) {
            begin_time = out3.get(0).t;
        }
        litr = out3.listIterator();
        while (litr.hasNext()) {
            PitchValue p = litr.next();
            out4.add(new PitchValue(p.t - begin_time, p.y));
        }

        return out4;
    }

    /* Convert frequency in Hz to semitone. There are 12 semitones in an octave. */
    final float semitone_scale =  12 / (float)Math.log(2);

    private float hertzToSemitone (float f) {
        /* this can be done faster using Math.exponent() and some simple math.
         * See 'calculating Integer log base 2 of a float in Java' */
        if (f < 1.0) return 0; // avoid exceptions
        return semitone_scale * (float)Math.log(f);
    }

    private boolean checkSlewRate (PitchValue p0, PitchValue p1) {
        /*
         * rise time and fall time in seconds per semitone, according to
         * The Oxford Handbook of Chinese Linguistics,
         * Ch. 36, "Intonation in Chinese", pp. 490-491,
         */
        final float rise_time = (float) 0.0087; /* in semitones per second */
        final float fall_time = (float) 0.0058; /* in semitones per second */
        float t0, t1, d0, d1;

        /* make sure t0 < t1 */
        if (p0.t < p1.t) {
            t0 = p0.t;
            t1 = p1.t;
            d0 = p0.t;
            d1 = p1.t;
        } else {
            t0 = p1.t;
            t1 = p0.t;
            d0 = p1.t;
            d1 = p0.t;
        }
        final float delta_t = t1 - t0;

        /* check rise and fall times */
        if ((d0 < 0) || (d1 < 0))
            /* semitone -1 indicates no pitch */
            return true;
        else if (d1 > d0) {
            /* rising */
            float d_max = d0 + delta_t / rise_time;
            return d1 < d_max;
        } else {
            /* falling */
            float d_min = d0 - delta_t / fall_time;
            return d1 > d_min;
        }
    }

    private String getPinyin() {
        String pinyin = null;

        pinyin = ((AbstractFlashcardViewer) mReviewer.get()).getField("Pinyin");
        if (pinyin == null) {
            pinyin = ((AbstractFlashcardViewer) mReviewer.get()).getField("Reading");
        }

        return pinyin;
    }

}

// not truncated
