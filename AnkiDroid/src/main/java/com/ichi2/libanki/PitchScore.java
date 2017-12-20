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

import com.fastdtw.dtw.FastDTW;
import com.fastdtw.timeseries.TimeSeries;
import com.fastdtw.timeseries.TimeSeriesBase;
import com.fastdtw.util.Distances;

import java.util.ArrayList;
import java.util.List;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;

import timber.log.Timber;

/**
 * Given two mp3s, calsculate a measure of how well the tone of the first mp3 matches that of the second.
 *
 * Created by koen on 12/19/17.
 */

public class PitchScore {

    private AudioDispatcher mAudioDispatcher;
    private List<Float> timeStamps = new ArrayList<Float>();
    private List<Float> freqInHz = new ArrayList<Float>();


    /* convert sound file to timeseries of frequencies */
    private TimeSeries pitchSeries(String soundPath) {

        /* convert mp3 to pitch values */
        mAudioDispatcher = AudioDispatcherFactory.fromPipe(soundPath, 22050, 1024, 0);

        timeStamps.clear();
        freqInHz.clear();

        mAudioDispatcher.addAudioProcessor(new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
                final float pitchInHz = pitchDetectionResult.getPitch();
                final float secondsProcessed = mAudioDispatcher.secondsProcessed();
                // Timber.i("pitch: " + secondsProcessed + "s " + pitchInHz + " Hz");
                timeStamps.add(new Float(secondsProcessed));
                freqInHz.add(new Float(pitchInHz));
            }
        }));
        mAudioDispatcher.run();

        /* remove initial -1 "no frequency" values */
        while (!timeStamps.isEmpty() && (freqInHz.get(0) == -1)) {
            timeStamps.remove(0);
            freqInHz.remove(0);
        }

        /* remove trailing -1 "no frequency" values */
        while (!timeStamps.isEmpty() && (freqInHz.get(freqInHz.size()-1) == -1)) {
            timeStamps.remove(timeStamps.size()-1);
            freqInHz.remove(freqInHz.size()-1);
        }

        /* take log of frequency; it makes more sense to compare frequencies in log scale. (Bode graphs) */
        for (int i = 0; i < freqInHz.size(); i++)
            if (freqInHz.get(i) > 0) freqInHz.set(i, (float) Math.log10(freqInHz.get(i)));

        // XXX fixme: we also should ignore absolute frequency; does not matter whether baritone or soprano.

        TimeSeries ts;
        TimeSeriesBase.Builder builder = TimeSeriesBase.builder();
        for (int i = 0; i < timeStamps.size(); i++)
            builder.add(timeStamps.get(i), freqInHz.get(i));
        ts = builder.build();

        return ts;
    }

    public double pitchDistance (String soundPath1, String soundPath2) {
        double distance = 0;
        TimeSeries ts1, ts2;

        try {
            ts1 = pitchSeries(soundPath1);
            ts2 = pitchSeries(soundPath2);

            if ((ts1.size() == 0) || (ts2.size() == 0))
                    return -1;

            distance = FastDTW.compare(ts1, ts2, 10, Distances.EUCLIDEAN_DISTANCE).getDistance();

            Timber.i("pitchDistance = " + distance);
            return distance;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1.0;
    }
}

// not truncated
