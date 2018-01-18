package com.ichi2.libanki;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import com.ichi2.anki.AnkiDroidApp;

import timber.log.Timber;

/**
 * Determine preferred sample rates
 * Created by koen on 12/24/17.
 */

public class SampleRates {

    /* default values */
    int mRecordSampleRate = -1;
    int mRecordBufferSizeInBytes = -1;
    int mTrackSampleRate = -1;
    int mTrackBufferSizeInBytes = -1;

    boolean validAudioRecordSampleRate(int sample_rate, int buffer_size) {
        AudioRecord recorder = null;
        try {
            int minBufferSizeInBytes = AudioRecord.getMinBufferSize(sample_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            Timber.i("AudioRecord: trying sample rate: " + sample_rate + " buffer size in bytes: " + buffer_size + " minimum buffer size in bytes: " + minBufferSizeInBytes);
            if (minBufferSizeInBytes < 0) return false;
            if (buffer_size < minBufferSizeInBytes) return false;
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sample_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer_size);
            if (recorder.getState() == recorder.STATE_UNINITIALIZED) return false;
        } catch(IllegalArgumentException e) {
            return false; // cannot sample at this rate
        } finally {
            if(recorder != null)
                recorder.release();
        }
        return true;
    }

    boolean validAudioTrackSampleRate(int sample_rate, int buffer_size) {
        AudioTrack track = null;
        try {
            int minBufferSizeInBytes = AudioTrack.getMinBufferSize(sample_rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            Timber.i("AudioTrack: trying sample rate: " + sample_rate + " buffer size in bytes: " + buffer_size + " minimum buffer size in bytes: " + minBufferSizeInBytes);
            if (minBufferSizeInBytes < 0) return false;
            if (buffer_size < minBufferSizeInBytes) return false;
            track = new AudioTrack(AudioManager.STREAM_MUSIC, sample_rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer_size, AudioTrack.MODE_STREAM);
            if (track.getState() == track.STATE_UNINITIALIZED) return false;
        } catch(IllegalArgumentException e) {
            return false; // cannot sample at this rate
        } finally {
            if(track != null)
                track.release();
        }
        return true;
    }

    SampleRates(){

        // Commmon sample rate values are: 5644800, 2822400, 352800, 192000, 176400, 96000, 88200, 50400, 50000, 48000,47250, 44100, 44056, 37800, 32000, 22050, 16000, 11025, 8000, 4800

        // Sample rates, in order of desireability. The zero values are replaced by device-specific values.
        int[] sample_rates = new int[]{0, 0, 96000, 88200, 50400, 50000, 48000, 47250, 44100, 44056, 37800, 32000, 22050 };
        int[] buffer_sizes = new int[]{1024, 2048, 4096, 8192};

        /* fill in system values for sample rate */
        Context context = AnkiDroidApp.getInstance();
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        sample_rates[0] = Integer.parseInt(audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
        sample_rates[1] = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);

        sample_rates = new int[] {48000, 44100, 22050};

        /* trial and error */
        for(int sample_rate : sample_rates) {
            for (int buffer_size : buffer_sizes) {
                if (validAudioTrackSampleRate(sample_rate, buffer_size)) {
                    mTrackSampleRate = sample_rate;
                    mTrackBufferSizeInBytes = buffer_size;
                    break;
                }
            }
            if (mTrackSampleRate > 0) break;
        }

        if (mTrackSampleRate <= 0) {
            /* default values */
            mTrackSampleRate = 22050;
            mTrackBufferSizeInBytes = 2048;
        }

        Timber.i("AudioTrack sample rate: " + mTrackSampleRate + " buffer size: " + mTrackBufferSizeInBytes);

        for(int sample_rate : sample_rates) {
            for (int buffer_size : buffer_sizes) {
                if (validAudioRecordSampleRate(sample_rate, buffer_size)) {
                    mRecordSampleRate = sample_rate;
                    mRecordBufferSizeInBytes = buffer_size;
                    break; // no need to check larger buffer sizes
                }
            }
            if (mRecordSampleRate > 0) break;
        }

        if (mRecordSampleRate <= 0) {
            /* default values */
            mRecordSampleRate = 22050;
            mRecordBufferSizeInBytes = 2048;
        }
        Timber.i("AudioRecord sample rate: " + mRecordSampleRate + " buffer size: " + mRecordBufferSizeInBytes);
    }

    public int TrackSampleRate() { return mTrackSampleRate; }

    public int TrackBufferSizeInBytes() { return mTrackBufferSizeInBytes; }

    public int TrackBufferSizeInSamples() { return mTrackBufferSizeInBytes / 2; } // 16-bit samples

    public int RecordSampleRate() { return mRecordSampleRate; }

    public int RecordBufferSizeInBytes() { return mRecordBufferSizeInBytes; }

    public int RecordBufferSizeInSamples() { return mRecordBufferSizeInBytes / 2; } // 16-bit samples

}
// not truncated
