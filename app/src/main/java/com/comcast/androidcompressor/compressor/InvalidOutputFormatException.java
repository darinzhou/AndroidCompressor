package com.comcast.androidcompressor.compressor;

public class InvalidOutputFormatException extends RuntimeException {
    public InvalidOutputFormatException(String detailMessage) {
        super(detailMessage);
    }
}
