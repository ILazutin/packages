package io.flutter.plugins.camera;

import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Queue;

public class LivePhotoSaver implements Runnable {
    private final Queue<Bitmap> bitmaps;
    private final File file;
    private final Callback callback;
    private final int orientation;
    private final int width;
    private final int height;
    private final int frameRate;

    LivePhotoSaver(@NonNull Queue<Bitmap> bitmaps, @NonNull File file, int orientation, int width, int height, int frameRate, @NonNull Callback callback) {
        this.bitmaps = bitmaps;
        this.file = file;
        this.orientation = orientation;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.callback = callback;
    }

    @Override
    public void run() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("LivePhoto.START", LocalDateTime.now().toString());
        }
        BitmapToVideoEncoder bitmapToVideoEncoder = new BitmapToVideoEncoder(frameRate, orientation, outputFile -> callback.onComplete(0));

        bitmapToVideoEncoder.startEncoding(width, height, file);
        for (Bitmap bitmap : bitmaps) {
            if (bitmap == null) return;
            bitmapToVideoEncoder.queueFrame(bitmap);
        }
        bitmapToVideoEncoder.stopEncoding();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("LivePhoto.STOP", LocalDateTime.now().toString());
        }
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
