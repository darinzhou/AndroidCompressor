package com.comcast.androidcompressor.compressor;

import android.media.MediaFormat;

public interface MediaOutputFormat {

    /**
     * Returns preferred video format for encoding.
     *
     * @param inputFormat MediaFormat from MediaExtractor, contains csd-0/csd-1.
     * @return null for passthrough.
     * @throws OutputFormatUnavailableException if input could not be transcoded because of restrictions.
     */
    public MediaFormat createVideoOutputFormat(MediaFormat inputFormat, int inputOrientation);

    /**
     * Caution: this method should return null currently.
     *
     * @return null for passthrough.
     * @throws OutputFormatUnavailableException if input could not be transcoded because of restrictions.
     */
    public MediaFormat createAudioOutputFormat(MediaFormat inputFormat);

    /**
     * Formalizing video orientation: rotates video according to original orientation and set video
     * orientation to zero
     *
     * @return true if asking for formalizing video orientation, otherwise false
     */
    public boolean isFormalizingOrientation();
}
