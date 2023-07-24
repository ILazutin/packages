package io.flutter.plugins.camera;

import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class LivePhotoSaver implements Runnable {
    private final CircularFifoQueue<Bitmap> bitmaps;
    private final File file;
    private final Callback callback;
    private final int orientation;
    private final int width;
    private final int height;
    private final int frameRate;

    private final DartMessenger dartMessenger;

    LivePhotoSaver(@NonNull CircularFifoQueue<Bitmap> bitmaps, @NonNull File file, int orientation, int width, int height, int frameRate, DartMessenger dartMessenger, @NonNull Callback callback) {
        this.bitmaps = bitmaps;
        this.file = file;
        this.orientation = orientation;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.dartMessenger = dartMessenger;
        this.callback = callback;
    }

    @Override
    public void run() {
        callback.onComplete(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDateTime start = LocalDateTime.now();
            Log.d("LIVEPHOTO.FRAMES.THREAD", start.toString());
        }
        List<Bitmap> frames = new ArrayList<Bitmap>();
        for (int index = 0; index < bitmaps.size(); index++) {
            frames.add(bitmaps.get(index));
        }
        dartMessenger.sendLivePhotoFramesEvent(frames);

        boolean saveVideoFile = false;
        if (saveVideoFile) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LocalDateTime start = LocalDateTime.of(2023, 1, 1, 1, 1);
                start = LocalDateTime.now();
                Log.d("LivePhoto.START", start.toString());
//        }
                BitmapToVideoEncoder bitmapToVideoEncoder = new BitmapToVideoEncoder(frameRate, orientation, outputFile -> callback.onComplete(0));

                bitmapToVideoEncoder.startEncoding(width, height, file);
                for (Bitmap bitmap : bitmaps) {
                    if (bitmap == null) return;
//            Log.d("LivePhoto.BitmapSize", String.format("size: %d, width: %d, height: %d", bitmap.getByteCount() / 1024, bitmap.getWidth(), bitmap.getHeight()));
                    bitmapToVideoEncoder.queueFrame(bitmap);
                }
                bitmapToVideoEncoder.stopEncoding();
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d("LivePhoto.STOP", LocalDateTime.now().toString());
                long diff = ChronoUnit.MILLIS.between(start, LocalDateTime.now());
                Log.d("LivePhoto.DURATION", String.format("%f seconds for %d frames", diff / 1000.0, bitmaps.size()));
            }
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
