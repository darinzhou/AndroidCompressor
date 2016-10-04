package com.comcast.androidcompressor.compressor;

import android.graphics.Point;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

public class X1PhotosOutputFormat implements MediaOutputFormat {
    public static final String TAG = "X1PhotosOutputFormat";

    public static final String VIDEO_CODEC = MediaFormatExtraConstants.MIMETYPE_VIDEO_AVC; //"video/avc"; // H.264 Advanced Video Coding
    public static final String AUDIO_CODEC = MediaFormatExtraConstants.MIMETYPE_AUDIO_AAC; //"audio/mp4a-latm";

    public static final int DEFAULT_DIM_SCALE = 36;
    public static final int DEFAULT_LONGER_LENGTH = 16 * DEFAULT_DIM_SCALE;
    public static final int DEFAULT_SHORTER_LENGTH = 9 * DEFAULT_DIM_SCALE;

    private static final int DEFAULT_AUDIO_BITRATE = 96 * 1024;    // bit/sec
    private static final int DEFAULT_AUDIO_CHANNEL_COUNT = 1;

    private static final int DEFAULT_FRAME_RATE = 30;       // fps
    private static final int DEFAULT_I_FRAME_INTERVAL = 1;  // seconds between I-frames

    private static final int DEFAULT_MOTION_FACTOR = 4;

//    private static final int DEFAULT_VIDEO_BITRATE = 2000 * 1024;    // bit/sec

    private int mWidth;
    private int mHeight;
    private int mFrameRate;
    private int mIFrameInterval;
    private int mVideoBitrate;
    private int mAudioBitrate;
    private int mAudioChannels;
    private int mAudioSampleRate;
    private int mLongerLength;
    private int mShorterLength;
    private boolean mIsFormalizingVideoOrientation;

    public X1PhotosOutputFormat() {
        this(true);
    }

    public X1PhotosOutputFormat(boolean isFormalizingVideoOrientation) {
        this(isFormalizingVideoOrientation, DEFAULT_FRAME_RATE, DEFAULT_LONGER_LENGTH, DEFAULT_SHORTER_LENGTH);
    }

    public X1PhotosOutputFormat(boolean isFormalizingVideoOrientation, int frameRate) {
        this(isFormalizingVideoOrientation, frameRate, DEFAULT_LONGER_LENGTH, DEFAULT_SHORTER_LENGTH);
    }

    public X1PhotosOutputFormat(boolean isFormalizingVideoOrientation, int frameRate, int longerLength, int shorterLength) {
        this(isFormalizingVideoOrientation, frameRate, DEFAULT_I_FRAME_INTERVAL, DEFAULT_AUDIO_BITRATE, DEFAULT_AUDIO_CHANNEL_COUNT,
                longerLength, shorterLength);
    }

    public X1PhotosOutputFormat(boolean isFormalizingVideoOrientation, int frameRate, int iFrameInterval, int audioBitrate, int audioChannels,
                                int longerLength, int shorterLength) {
        mIsFormalizingVideoOrientation = isFormalizingVideoOrientation;
        mFrameRate = frameRate;
        mIFrameInterval = iFrameInterval;
        mAudioBitrate = audioBitrate;
        mAudioChannels = audioChannels;
        mLongerLength = longerLength;
        mShorterLength = shorterLength;
    }

    @Override
    public MediaFormat createVideoOutputFormat(MediaFormat inputFormat) {
//        // not scale up frame rate
//        try {
//            int frameRate = inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
//            Log.d(TAG, "Original Frame Rate: " + frameRate);
//            if (mFrameRate > frameRate) {
//                mFrameRate = frameRate;
//            }
//        } catch (Exception e) {}
//
//        // not scale up i frame interval
//        try {
//            int ifi = inputFormat.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL);
//            Log.d(TAG, "Original I Frame Interval: " + ifi);
//            if (mIFrameInterval < ifi) {
//                mIFrameInterval = ifi;
//            }
//        } catch (Exception e) {}

        // rotation
        int rotation = 0;
        try {
            rotation = inputFormat.getInteger(MediaFormatExtraConstants.KEY_ROTATION_DEGREES);
            Log.d(TAG, "Original Rotation: " + rotation);
        } catch (Exception e) {
        }

        // adjust dimension
        int width = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
        int height = inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        Log.d(TAG, "Original Width: " + width);
        Log.d(TAG, "Original Height: " + height);
        Point size = adjustDimension(width, height, mLongerLength, mShorterLength);
        if (rotation % 180 != 0 && isFormalizingVideoOrientation()) {
            mWidth = size.y;
            mHeight = size.x;
        } else {
            mWidth = size.x;
            mHeight = size.y;
        }
        Log.d(TAG, "New Width: " + mWidth);
        Log.d(TAG, "New Height: " + mHeight);

        // calculate bitrate
        mVideoBitrate = calculateBitrate(mWidth, mHeight, mFrameRate);
        Log.d(TAG, "New Bitrate: " + mVideoBitrate);

        // create format
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_CODEC, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mVideoBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIFrameInterval);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        return format;
    }

    @Override
    public MediaFormat createAudioOutputFormat(MediaFormat inputFormat) {
        // Use original sample rate, as resampling is not supported yet.
        mAudioSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);

//        // don't scale up audio bitrate
//        try {
//            int bitRate = inputFormat.getInteger(MediaFormat.KEY_BIT_RATE);
//            Log.d(TAG, "Original Audio Bitrate: " + bitRate);
//            if (mAudioBitrate > bitRate) {
//                mAudioBitrate = bitRate;
//            }
//        } catch (Exception e) {}
//
//        // don't scale up audio channels
//        try {
//            int channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
//            Log.d(TAG, "Original Audio Channel Count: " + channelCount);
//            if (mAudioChannels > channelCount) {
//                mAudioChannels = channelCount;
//            }
//        } catch (Exception e) {}

        MediaFormat format = MediaFormat.createAudioFormat(AUDIO_CODEC, mAudioSampleRate, mAudioChannels);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mAudioBitrate);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        return format;
    }

    @Override
    public boolean isFormalizingVideoOrientation() {
        return mIsFormalizingVideoOrientation;
    }

    public static Point adjustDimension(int width, int height, int longerLength, int shorterLength) {
        int sw = longerLength;
        int sh = shorterLength;
        if (width < height) {
            sw = shorterLength;
            sh = longerLength;
        }

        float r1 = (float) sw / width;
        float r2 = (float) sh / height;
        float r = Math.min(Math.min(r1, r2), 1.0f);

        int tw = (int) (r * width);
        int th = (int) (r * height);
        if (tw % 2 != 0) {
            tw--;
        }
        if (th % 2 != 0) {
            th--;
        }

        return new Point(tw, th);
    }

    //    Kush gauge: pixel count x motion factor x 0.07 ÷ 1000 = bit rate in kbps
//    (frame width x height = pixel count) and motion factor is 1,2, 3 or 4
    public static int calculateBitrate(int width, int height, int frameRate) {
        return (int) (width * height * frameRate * DEFAULT_MOTION_FACTOR * 0.07);
    }

    public int getTargetWidth() {
        return mWidth;
    }

    public int getTargetHeight() {
        return mHeight;
    }

    public int getTargetFrameRate() {
        return mFrameRate;
    }

    public int getTargetVideoBitRate() {
        return mVideoBitrate;
    }

    public int getTargetAudioBitRate() {
        return mAudioBitrate;
    }

    public int getTargetAudioChannlCount() {
        return mAudioChannels;
    }

    public int getTargetAudioSampleRate() {
        return mAudioSampleRate;
    }
}
