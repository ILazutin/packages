package io.flutter.plugins.camera;

import android.graphics.Bitmap;
import android.graphics.YuvImage;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Queue;

public class LivePhotoSaver implements Runnable {
    private final Queue<YuvImage> bitmaps;
    private final File file;
    private final Callback callback;

    LivePhotoSaver(@NonNull Queue<YuvImage> bitmaps, @NonNull File file, @NonNull Callback callback) {
        this.bitmaps = bitmaps;
        this.file = file;
        this.callback = callback;
    }

    @Override
    public void run() {
        BitmapToVideoEncoder bitmapToVideoEncoder = new BitmapToVideoEncoder(new BitmapToVideoEncoder.IBitmapToVideoEncoderCallback() {
            @Override
            public void onEncodingComplete(File outputFile) {
                callback.onComplete(0);
            }
        });

        bitmapToVideoEncoder.startEncoding(1920, 1080, file);
        for (YuvImage yuvImage : bitmaps) {
            bitmapToVideoEncoder.queueFrame(CameraUtils.getBitmapFromYuvImage(yuvImage));
        }
        bitmapToVideoEncoder.stopEncoding();
    }

    public interface Callback {
        /**
         * Called when the image file has been saved successfully.
         *
         * @param status - The result status of operation.
         */
        void onComplete(int status);

        /**
         * Called when an error is encountered while saving the image file.
         *
         * @param errorCode - The error code.
         * @param errorMessage - The human readable error message.
         */
        void onError(String errorCode, String errorMessage);
    }
}
