package com.comcast.androidcompressor.compressor;

import android.media.MediaCodec;
import android.os.Build;

import java.nio.ByteBuffer;

/**
 * A Wrapper to MediaCodec that facilitates the use of API-dependent get{Input/Output}Buffer methods,
 * in order to prevent: http://stackoverflow.com/q/30646885
 */
public class MediaCodecOutputBuffers {

    final private MediaCodec mMediaCodec;
    final private ByteBuffer[] mOutputBuffers;

    public MediaCodecOutputBuffers(MediaCodec mediaCodec) {
        mMediaCodec = mediaCodec;

        if (Build.VERSION.SDK_INT >= 21) {
            mOutputBuffers = null;
        } else {
            mOutputBuffers = mediaCodec.getOutputBuffers();
        }
    }

    public ByteBuffer getOutputBuffer(final int index) {
        if (Build.VERSION.SDK_INT >= 21) {
            return mMediaCodec.getOutputBuffer(index);
        }
        return mOutputBuffers[index];
    }
}
