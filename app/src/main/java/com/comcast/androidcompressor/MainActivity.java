package com.comcast.androidcompressor;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.comcast.androidcompressor.compressor.X1PhotosOutputFormat;
import com.comcast.androidcompressor.compressor.MediaCompressor;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Future;

public class MainActivity extends AppCompatActivity {

    static class Setting {
        public int mFrameRate;
        public int mLongerLength;
        public int mShorterLength;
        public Setting(int framerate, int longerLength, int shortLength) {
            mFrameRate = framerate;
            mLongerLength = longerLength;
            mShorterLength = shortLength;
        }

        public File getFileBeforeProcessing() {
            return new File(
                    Environment.getExternalStorageDirectory()
                            + File.separator
                            + "Android Compressing Test/",
                    mLongerLength + "x" + mShorterLength + "_" + mFrameRate + "_" +
                    new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".mp4");
        }

        public File getFileAfterProcessing(int w, int h, float time) {
            return new File(
                    Environment.getExternalStorageDirectory()
                            + File.separator
                            + "Android Compressing Test/",
                    w + "x" + h + "_" + mFrameRate + "_" +
                            new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) +
                            "_" + Math.round((float)(time/1000.0)) + ".mp4");
        }
    }

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PICK = 1;
    private static final int PROGRESS_BAR_MAX = 1000;
    private Future<Void> mFuture;

    private TextView mTvTime;
    private EditText mEtMaxWidth;
    private EditText mEtMaxHeight;
    private EditText mEtFrameRate;

    private String mDuration;

    private Setting mSetting;
    private int mMaxWidth = 640;
    private int mMaxHeight = 360;
    private int mFrameRate = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        File dir = new File(Environment.getExternalStorageDirectory(), "Android Compressing Test");
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }

        mTvTime = (TextView)findViewById(R.id.textView);
        mEtMaxWidth = (EditText) findViewById(R.id.etMaxWidth);
        mEtMaxHeight = (EditText) findViewById(R.id.etMaxHeight);
        mEtFrameRate = (EditText) findViewById(R.id.etFrameRate);

        mEtMaxWidth.setText(""+mMaxWidth);
        mEtMaxHeight.setText(""+mMaxHeight);
        mEtFrameRate.setText(""+mFrameRate);

        final Button btStart = (Button)findViewById(R.id.select_video_button);
        btStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btStart.requestFocus();
                mEtMaxWidth.clearFocus();
                mEtMaxHeight.clearFocus();
                mEtFrameRate.clearFocus();
                mTvTime.setText("");
                startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("video/*"), REQUEST_CODE_PICK);
            }
        });
        findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mFuture.cancel(true);
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_PICK: {
                final File file;
                if (resultCode == RESULT_OK) {
                    mMaxWidth = Integer.parseInt(mEtMaxWidth.getText().toString());
                    mMaxHeight = Integer.parseInt(mEtMaxHeight.getText().toString());
                    mFrameRate = Integer.parseInt(mEtFrameRate.getText().toString());
//                    mBitRate = X1PhotosOutputFormat.calculateBitrate(mMaxWidth, mMaxHeight, mFrameRate);
                    mSetting = new Setting(mFrameRate, mMaxWidth, mMaxHeight);

                    file = mSetting.getFileBeforeProcessing();

                    ContentResolver resolver = getContentResolver();
                    final ParcelFileDescriptor parcelFileDescriptor;
                    try {
                        parcelFileDescriptor = resolver.openFileDescriptor(data.getData(), "r");
                    } catch (FileNotFoundException e) {
                        Log.w("Could not open '" + data.getDataString() + "'", e);
                        Toast.makeText(MainActivity.this, "File not found.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    final FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                    final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
                    progressBar.setMax(PROGRESS_BAR_MAX);
                    final long startTime = SystemClock.uptimeMillis();

                    final String sourceSize = getFileSizeFromURI(data.getData());

                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    //use one of overloaded setDataSource() functions to set your data source
                    retriever.setDataSource(MainActivity.this, data.getData());

                    final String orientation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                    final String w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                    final String h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

                    String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    long seconds = Long.parseLong(time) / 1000;
                    mDuration = seconds / 60 + ":" + seconds % 60;

                    final X1PhotosOutputFormat targetFormat = new X1PhotosOutputFormat(false, mFrameRate, mMaxWidth, mMaxHeight);

                    MediaCompressor.Listener listener = new MediaCompressor.Listener() {
                        @Override
                        public void onProgress(double progress) {
                            if (progress < 0) {
                                progressBar.setIndeterminate(true);
                            } else {
                                progressBar.setIndeterminate(false);
                                progressBar.setProgress((int) Math.round(progress * PROGRESS_BAR_MAX));
                            }
                        }

                        @Override
                        public void onSucceed() {
                            float sl = Long.parseLong(sourceSize) * 10 / (1024*1024);
                            float tl = file.length() * 10 / (1024*1024);
                            sl /= 10;
                            tl /= 10;
                            String source = "Source size: " + sl + " MB\r\n";
                            String target = "Target size: " + tl + " MB\r\n";
                            long t = SystemClock.uptimeMillis() - startTime;
                            String time = "Time spent: " + t + " ms\r\n";
                            Log.d(TAG, time);
//                            mTvTime.setText(source + target + time);

                            File targetFile = mSetting.getFileAfterProcessing(targetFormat.getTargetWidth(), targetFormat.getTargetHeight(), t);
                            file.renameTo(targetFile);

                            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                            //use one of overloaded setDataSource() functions to set your data source
                            retriever.setDataSource(targetFile.getPath());
                            String newOrientation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                            Log.d(TAG, "Original Orientation: " + orientation);
                            Log.d(TAG, "New Orientation: " + newOrientation);
                            String orientationString = "Original Orientation: " + orientation + "\r\n" + "New Orientation: " + newOrientation + "\r\n";

                            String text = "Duration: " + mDuration + "\r\n" + source + target + time + orientationString +
                                    "Target File: " + targetFile.getName();

                            mTvTime.setText(text);

                            Log.d(TAG, "target file: " + file);
                            Log.d(TAG, "target dimension: " + targetFormat.getTargetWidth() + "x" + targetFormat.getTargetHeight());
                            Log.d(TAG, "target frame rate: " + targetFormat.getTargetFrameRate());
                            Log.d(TAG, "target bit rate: " + (targetFormat.getTargetVideoBitRate() + targetFormat.getTargetAudioBitRate()));

                            onCompressFinished(true, "transcoded file placed on " + file, parcelFileDescriptor);
//                            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(file), "video/mp4"));
                        }

                        @Override
                        public void onCanceled() {
                            onCompressFinished(false, "Transcoder canceled.", parcelFileDescriptor);
                        }

                        @Override
                        public void onFailed(Exception exception) {
                            onCompressFinished(false, "Transcoder error occurred.", parcelFileDescriptor);
                        }

                        @Override
                        public void cancel() {

                        }

                        @Override
                        public void closeInputStream() {

                        }
                    };

                    mFuture = MediaCompressor.getInstance().compress(fileDescriptor, file.getAbsolutePath(),
                            targetFormat, listener);

                    switchButtonEnabled(true);
                }
                break;
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void onCompressFinished(boolean isSuccess, String toastMessage, ParcelFileDescriptor parcelFileDescriptor) {
        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        progressBar.setIndeterminate(false);
        progressBar.setProgress(isSuccess ? PROGRESS_BAR_MAX : 0);
        switchButtonEnabled(false);
//        Toast.makeText(MainActivity.this, toastMessage, Toast.LENGTH_LONG).show();
        try {
            parcelFileDescriptor.close();
        } catch (IOException e) {
            Log.w("Error while closing", e);
        }
    }

    private void switchButtonEnabled(boolean isProgress) {
        findViewById(R.id.select_video_button).setEnabled(!isProgress);
        findViewById(R.id.cancel_button).setEnabled(isProgress);
    }

    private String getFileSizeFromURI(Uri uri) {
        String size = "Unknown";

        if (uri != null) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null, null);

            try {
                if (cursor != null && cursor.moveToFirst()) {

                    String displayName = cursor.getString(
                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    Log.i(TAG, "Display Name: " + displayName);

                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (!cursor.isNull(sizeIndex)) {
                        size = cursor.getString(sizeIndex);
                    }
                    Log.i(TAG, "Size: " + size);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        return size;
    }

}
