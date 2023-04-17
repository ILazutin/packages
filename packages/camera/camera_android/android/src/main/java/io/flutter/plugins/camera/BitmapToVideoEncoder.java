package io.flutter.plugins.camera;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

public class BitmapToVideoEncoder {
    private static final String TAG = BitmapToVideoEncoder.class.getSimpleName();

    private Handler backgroundHandler;

    /** An additional thread for running tasks that shouldn't block the UI. */
    private HandlerThread backgroundHandlerThread;

    private IBitmapToVideoEncoderCallback mCallback;
    private File mOutputFile;
    private Queue<Bitmap> mEncodeQueue = new ConcurrentLinkedQueue<Bitmap>();
    private MediaCodec mediaCodec;
    private MediaMuxer mediaMuxer;

    private Object mFrameSync = new Object();
    private CountDownLatch mNewFrameLatch;

    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private static int mWidth;
    private static int mHeight;
    private static final int BIT_RATE = 16000000;
    private final int frameRate; // Frames per second
    private final int orientation;

    private static final int I_FRAME_INTERVAL = 1;

    private int mGenerateIndex = 0;
    private int mTrackIndex;
    private boolean mNoMoreFrames = false;
    private boolean mAbort = false;

    public interface IBitmapToVideoEncoderCallback {
        void onEncodingComplete(File outputFile);
    }

    public BitmapToVideoEncoder(int frameRate, int orientation, IBitmapToVideoEncoderCallback callback) {
        mCallback = callback;
        this.frameRate = frameRate;
        this.orientation = orientation;
        startBackgroundThread();
    }

    public boolean isEncodingStarted() {
        return (mediaCodec != null) && (mediaMuxer != null) && !mNoMoreFrames && !mAbort;
    }

    public int getActiveBitmaps() {
        return mEncodeQueue.size();
    }

    public void startEncoding(int width, int height, File outputFile) {
        mWidth = width;
        mHeight = height;
        mOutputFile = outputFile;

        String outputFileString;
        try {
            outputFileString = outputFile.getCanonicalPath();
        } catch (IOException e) {
            Log.e(TAG, "Unable to get path for " + outputFile);
            return;
        }

        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        Log.d(TAG, "found codec: " + codecInfo.getName());
        int colorFormat;
        try {
            colorFormat = selectColorFormat(codecInfo, MIME_TYPE);
        } catch (Exception e) {
            colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
        }

        try {
            mediaCodec = MediaCodec.createByCodecName(codecInfo.getName());
        } catch (IOException e) {
            Log.e(TAG, "Unable to create MediaCodec " + e.getMessage());
            return;
        }

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaFormat.setInteger(MediaFormat.KEY_ROTATION, orientation);
        }
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
        try {
            mediaMuxer = new MediaMuxer(outputFileString, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mediaMuxer.setOrientationHint(orientation);
        } catch (IOException e) {
            Log.e(TAG,"MediaMuxer creation failed. " + e.getMessage());
            return;
        }

        Log.d(TAG, "Initialization complete. Starting encoder...");

        backgroundHandler.post(this::encode);
    }

    public void stopEncoding() {
        if (mediaCodec == null || mediaMuxer == null) {
            Log.d(TAG, "Failed to stop encoding since it never started");
            return;
        }
        Log.d(TAG, "Stopping encoding");

        mNoMoreFrames = true;

        synchronized (mFrameSync) {
            if ((mNewFrameLatch != null) && (mNewFrameLatch.getCount() > 0)) {
                mNewFrameLatch.countDown();
            }
        }
    }

    public void abortEncoding() {
        if (mediaCodec == null || mediaMuxer == null) {
            Log.d(TAG, "Failed to abort encoding since it never started");
            return;
        }
        Log.d(TAG, "Aborting encoding");

        mNoMoreFrames = true;
        mAbort = true;
        mEncodeQueue = new ConcurrentLinkedQueue<Bitmap>(); // Drop all frames

        synchronized (mFrameSync) {
            if ((mNewFrameLatch != null) && (mNewFrameLatch.getCount() > 0)) {
                mNewFrameLatch.countDown();
            }
        }
    }

    public void queueFrame(Bitmap bitmap) {
        if (mediaCodec == null || mediaMuxer == null) {
            Log.d(TAG, "Failed to queue frame. Encoding not started");
            return;
        }

        Log.d(TAG, "Queueing frame");
        mEncodeQueue.add(bitmap);

        synchronized (mFrameSync) {
            if ((mNewFrameLatch != null) && (mNewFrameLatch.getCount() > 0)) {
                mNewFrameLatch.countDown();
            }
        }
    }

    private void encode() {

        Log.d(TAG, "Encoder started");

        boolean needRelease = false;

        while(true) {
            if (mNoMoreFrames && (mEncodeQueue.size() == 0)) break;

            Bitmap bitmap = mEncodeQueue.poll();
            if (bitmap ==  null) {
                synchronized (mFrameSync) {
                    mNewFrameLatch = new CountDownLatch(1);
                }

                try {
                    mNewFrameLatch.await();
                } catch (InterruptedException e) {}

                bitmap = mEncodeQueue.poll();
            }

            if (bitmap == null) continue;

            Bitmap scaledBitmap = ImageUtils.getScaledBitmap(bitmap, mWidth, mHeight);

            byte[] byteConvertFrame = ImageUtils.getNV21(scaledBitmap.getWidth(), scaledBitmap.getHeight(), scaledBitmap);

            long TIMEOUT_USEC = 0;
            int inputBufIndex = mediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
            long ptsUsec = computePresentationTime(mGenerateIndex, frameRate);
            if (inputBufIndex >= 0) {
                final ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufIndex);
                inputBuffer.clear();
                inputBuffer.put(byteConvertFrame);
                mediaCodec.queueInputBuffer(inputBufIndex, 0, byteConvertFrame.length, ptsUsec, 0);
                mGenerateIndex++;
            }
            bitmap.recycle();

            MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
            int encoderStatus = mediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                Log.e(TAG, "No output from encoder available");
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder
                MediaFormat newFormat = mediaCodec.getOutputFormat();
                mTrackIndex = mediaMuxer.addTrack(newFormat);
                mediaMuxer.start();
            } else if (encoderStatus < 0) {
                Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
            } else if (mBufferInfo.size != 0) {
                ByteBuffer encodedData = mediaCodec.getOutputBuffer(encoderStatus);
                if (encodedData == null) {
                    Log.e(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                } else {
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    mediaMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    mediaCodec.releaseOutputBuffer(encoderStatus, false);
                    needRelease = true;
                }
            }
        }

        if (needRelease) {
            release();
        }

        if (!mAbort) {
            mCallback.onEncodingComplete(mOutputFile);
        }
    }

    private void release() {
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
            Log.d(TAG,"RELEASE CODEC");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d("LivePhoto.ENCODER.STOP", LocalDateTime.now().toString());
            }
        }
        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
            Log.d(TAG,"RELEASE MUXER");
        }
    }

    private MediaCodecInfo selectCodec(String mimeType) {
        MediaCodecList codecs = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MediaCodecInfo[] codecInfoList = codecs.getCodecInfos();
        for (MediaCodecInfo codecInfo : codecInfoList) {
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private static int selectColorFormat(MediaCodecInfo codecInfo,
                                         String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo
                .getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        return 0; // not reached
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        // these are the formats we know how to handle for
        return colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    }

    private long computePresentationTime(long frameIndex, int framerate) {
        return 132 + frameIndex * 1000000 / framerate;
    }

    public void startBackgroundThread() {
        if (backgroundHandlerThread != null) {
            return;
        }

        backgroundHandlerThread = HandlerThreadFactory.create("BitmapToVideoEncoderBackground");
        try {
            backgroundHandlerThread.start();
        } catch (IllegalThreadStateException e) {
            // Ignore exception in case the thread has already started.
        }
        backgroundHandler = HandlerFactory.create(backgroundHandlerThread.getLooper());
    }

    /** Stops the background thread and its {@link Handler}. */
    public void stopBackgroundThread() {
        if (backgroundHandlerThread != null) {
            backgroundHandlerThread.quitSafely();
        }
        backgroundHandlerThread = null;
        backgroundHandler = null;
    }

    static class HandlerThreadFactory {
        /**
         * Creates a new instance of the {@link HandlerThread} class.
         *
         * <p>This method is visible for testing purposes only and should never be used outside this *
         * class.
         *
         * @param name to give to the HandlerThread.
         * @return new instance of the {@link HandlerThread} class.
         */
        @VisibleForTesting
        public static HandlerThread create(String name) {
            return new HandlerThread(name);
        }
    }

    /** Factory class that assists in creating a {@link Handler} instance. */
    static class HandlerFactory {
        /**
         * Creates a new instance of the {@link Handler} class.
         *
         * <p>This method is visible for testing purposes only and should never be used outside this *
         * class.
         *
         * @param looper to give to the Handler.
         * @return new instance of the {@link Handler} class.
         */
        @VisibleForTesting
        public static Handler create(Looper looper) {
            return new Handler(looper);
        }
    }
}