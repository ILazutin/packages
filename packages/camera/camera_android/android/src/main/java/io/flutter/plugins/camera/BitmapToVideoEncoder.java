package io.flutter.plugins.camera;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.flutter.plugins.camera.features.renderscript.YuvFormat;

public class BitmapToVideoEncoder {
    private static final String TAG = BitmapToVideoEncoder.class.getSimpleName();

    private IBitmapToVideoEncoderCallback mCallback;
    private File mOutputFile;
    private Queue<Bitmap> mEncodeQueue = new ConcurrentLinkedQueue<Bitmap>();
    private MediaCodec mediaCodec;
    private MediaMuxer mediaMuxer;

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
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
    private YuvFormat imageFormat = YuvFormat.NV21;

    public interface IBitmapToVideoEncoderCallback {
        void onEncodingComplete(File outputFile);
    }

    public BitmapToVideoEncoder(int frameRate, int orientation, IBitmapToVideoEncoderCallback callback) {
        mCallback = callback;
        this.frameRate = frameRate;
        this.orientation = orientation;
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
        if (colorFormat == 0) {
            colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
        }

        if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            imageFormat = YuvFormat.YV12;
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

    }

    public void stopEncoding() {
        if (mediaCodec == null || mediaMuxer == null) {
            Log.d(TAG, "Failed to stop encoding since it never started");
            return;
        }
        Log.d(TAG, "Stopping encoding");

        mNoMoreFrames = true;
        encode();
        release();
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
    }

    public void queueFrame(Bitmap bitmap) {
        if (mediaCodec == null || mediaMuxer == null) {
            Log.d(TAG, "Failed to queue frame. Encoding not started");
            return;
        }

        mEncodeQueue.add(bitmap);
        encode();
    }

    private void encode() {

        if (mNoMoreFrames) {
            return;
        }

        if (mEncodeQueue.size() == 0) return;

        Bitmap bitmap = mEncodeQueue.poll();
        if (bitmap == null) {
            bitmap = mEncodeQueue.poll();
        }

        if (bitmap == null) return;

        Bitmap scaledBitmap = ImageUtils.getScaledBitmap(bitmap, mWidth, mHeight);

        byte[] byteConvertFrame = ImageUtils.getNV21(
                scaledBitmap.getWidth(), scaledBitmap.getHeight(), scaledBitmap, imageFormat);

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
        scaledBitmap.recycle();

        MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
        int encoderStatus = mediaCodec.dequeueOutputBuffer(mBufferInfo, 10000);
        if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
            // no output available yet
            Log.e(TAG, "No output from encoder available");
        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            // not expected for an encoder
            MediaFormat newFormat = mediaCodec.getOutputFormat();
            mTrackIndex = mediaMuxer.addTrack(newFormat);
            mediaMuxer.start();
            mBufferInfo = new MediaCodec.BufferInfo();
            encoderStatus = mediaCodec.dequeueOutputBuffer(mBufferInfo, 10000);
        } else if (encoderStatus < 0) {
            Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
        }
        if (mBufferInfo.size != 0) {
            ByteBuffer encodedData = mediaCodec.getOutputBuffer(encoderStatus);
            if (encodedData == null) {
                Log.e(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
            } else {
                encodedData.position(mBufferInfo.offset);
                encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                mediaMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                mediaCodec.releaseOutputBuffer(encoderStatus, false);
            }
        }

        if (mNoMoreFrames || mAbort) {
            release();
        }

        if (!mAbort && mNoMoreFrames) {
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
        return colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                || colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar
                || colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                || colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar;
    }

    private long computePresentationTime(long frameIndex, int framerate) {
        return 132 + frameIndex * 1000000 / framerate;
    }

}