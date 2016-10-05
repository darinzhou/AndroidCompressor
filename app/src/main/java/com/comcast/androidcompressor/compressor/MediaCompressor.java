package com.comcast.androidcompressor.compressor;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MediaCompressor {

    public interface Listener {
        /**
         * Called to notify progress.
         *
         * @param progress Progress in [0.0, 1.0] range, or negative value if progress is unknown.
         */
        void onProgress(double progress);

        /**
         * Called when transcode completed.
         */
        void onSucceed();

        /**
         * Called when transcode canceled.
         */
        void onCanceled();

        /**
         * Called when transcode failed.
         */
        void onFailed(Exception exception);

        /**
         * Called to cancel the listener.
         */
        void cancel();

        /**
         * Called to close the input stream.
         */
        void closeInputStream();
    }

    private static final String TAG = "MediaCompressor";
    private static final int MAXIMUM_THREAD = 8; // TODO
    private static volatile MediaCompressor mMediaCompressor;
    private ThreadPoolExecutor mExecutor;

    private LinkedHashMap<Future<Void>, Listener> mTasks;

    private MediaCompressor() {
        int cpus = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = ((cpus > 0 ) ? cpus : 1) * 2;
        mExecutor = new ThreadPoolExecutor(
//                0, MAXIMUM_THREAD,
                maxPoolSize, maxPoolSize,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "MediaCompressor-Worker");
                    }
                });

        mTasks = new LinkedHashMap<>();
    }

    public static MediaCompressor getInstance() {
        if (mMediaCompressor == null) {
            synchronized (MediaCompressor.class) {
                if (mMediaCompressor == null) {
                    mMediaCompressor = new MediaCompressor();
                }
            }
        }
        return mMediaCompressor;
    }

    public void setListener(Future<Void> future, final Listener listener) {
        if (future != null) {

            // get original listener
            final Listener oldListener = mTasks.get(future);

            // merge listeners
            Listener newListerner = new Listener() {
                @Override
                public void onProgress(double progress) {
                    listener.onProgress(progress);
                }

                @Override
                public void onSucceed() {
                    closeInputStream();
                    listener.onSucceed();
                }

                @Override
                public void onCanceled() {
                    closeInputStream();
                    listener.onCanceled();
                }

                @Override
                public void onFailed(Exception exception) {
                    closeInputStream();
                    listener.onFailed(exception);
                }

                @Override
                public void cancel() {
                    listener.cancel();
                }

                @Override
                public void closeInputStream() {
                    oldListener.closeInputStream();
                }
            };

            // update listener
            mTasks.put(future, newListerner);
        }
    }

    public void cancel(Future<Void> future) {
        if (future != null) {
            Listener listener = mTasks.get(future);
            if (listener != null) {
                listener.cancel();
            }
            future.cancel(true);
        }
    }

    /**
     * Transcodes video file asynchronously.
     * Audio track will be kept unchanged.
     *
     * @param inPath            File path for input.
     * @param outPath           File path for output.
     * @param outFormatStrategy Strategy for output video format.
     * @param listener          Listener instance for callback.
     * @throws IOException if input file could not be read.
     */
    public Future<Void> compress(final String inPath, final String outPath, final MediaOutputFormat outFormatStrategy, final Listener listener) {
        FileInputStream fileInputStream = null;
        FileDescriptor inFileDescriptor;
        try {
            fileInputStream = new FileInputStream(inPath);
            inFileDescriptor = fileInputStream.getFD();
        } catch (IOException e) {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException eClose) {
                    Log.e(TAG, "Can't close input stream: ", eClose);
                }
            }
            return null;
        }

        // merge listener
        final FileInputStream finalFileInputStream = fileInputStream;
        Listener newListerner = new Listener() {
            @Override
            public void onProgress(double progress) {
                listener.onProgress(progress);
            }

            @Override
            public void onSucceed() {
                closeInputStream();
                listener.onSucceed();
            }

            @Override
            public void onCanceled() {
                closeInputStream();
                listener.onCanceled();
            }

            @Override
            public void onFailed(Exception exception) {
                closeInputStream();
                listener.onFailed(exception);
            }

            @Override
            public void cancel() {
                listener.cancel();
            }

            @Override
            public void closeInputStream() {
                try {
                    finalFileInputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Can't close input stream: ", e);
                }
            }
        };

        // compress
        return compress(inFileDescriptor, outPath, outFormatStrategy, newListerner);
    }

    /**
     * Transcodes video file asynchronously.
     * Audio track will be kept unchanged.
     *
     * @param inFileDescriptor  FileDescriptor for input.
     * @param outPath           File path for output.
     * @param outFormatStrategy S
     *
     *                          trategy for output video format.
     * @param listener          Listener instance for callback.
     */
    public Future<Void> compress(final FileDescriptor inFileDescriptor, final String outPath, final MediaOutputFormat outFormatStrategy, final Listener listener) {
        Looper looper = Looper.myLooper();
        if (looper == null) looper = Looper.getMainLooper();
        final Handler handler = new Handler(looper);
        final AtomicReference<Future<Void>> futureReference = new AtomicReference<>();
        final Future<Void> createdFuture = mExecutor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Exception caughtException = null;
                try {
                    MediaTranscoderEngine engine = new MediaTranscoderEngine();
                    engine.setProgressCallback(new MediaTranscoderEngine.ProgressCallback() {
                        @Override
                        public void onProgress(final double progress) {
                            handler.post(new Runnable() { // TODO: reuse instance
                                @Override
                                public void run() {
                                    Future<Void> future = futureReference.get();
                                    Listener listener = mTasks.get(future);
                                    if (listener != null) {
                                        listener.onProgress(progress);
                                    }
                                }
                            });
                        }
                    });
                    engine.setDataSource(inFileDescriptor);
                    engine.transcodeVideo(outPath, outFormatStrategy);
                } catch (IOException e) {
                    Log.w(TAG, "Transcode failed: input file (fd: " + inFileDescriptor.toString() + ") not found"
                            + " or could not open output file ('" + outPath + "').", e);
                    caughtException = e;
                } catch (InterruptedException e) {
                    Log.i(TAG, "Cancel transcode video file.", e);
                    caughtException = e;
                } catch (RuntimeException e) {
                    Log.e(TAG, "Fatal error while transcoding, this might be invalid format or bug in engine or Android.", e);
                    caughtException = e;
                }

                final Exception exception = caughtException;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Future<Void> future = futureReference.get();
                        Listener listener = mTasks.get(future);
                        if (listener != null) {
                            if (exception == null) {
                                listener.onSucceed();
                            } else {
                                if (future != null && future.isCancelled()) {
                                    listener.onCanceled();
                                } else {
                                    listener.onFailed(exception);
                                }
                            }
                        }
                    }
                });

                if (exception != null) throw exception;
                return null;
            }
        });

        futureReference.set(createdFuture);
        mTasks.put(futureReference.get(), listener);
        return futureReference.get();
    }

}
