package com.comcast.androidcompressor.compressor;

import android.media.MediaCodec;
import android.os.Build;

import java.nio.ByteBuffer;

/**
 * A Wrapper to MediaCodec that facilitates the use of API-dependent get{Input/Output}Buffer methods,
 * in order to prevent: http://stackoverflow.com/q/30646885
 */
public class MediaCodecInputBuffers {

    final MediaCodec mMediaCodec;
    final ByteBuffer[] mInputBuffers;

    public MediaCodecInputBuffers(MediaCodec mediaCodec) {
        mMediaCodec = mediaCodec;

        if (Build.VERSION.SDK_INT >= 21) {
            mInputBuffers = null;
        } else {
            mInputBuffers = mediaCodec.getInputBuffers();
        }
    }

    public ByteBuffer getInputBuffer(final int index) {
        if (Build.VERSION.SDK_INT >= 21) {
            return mMediaCodec.getInputBuffer(index);
        }
        return mInputBuffers[index];
    }
}
