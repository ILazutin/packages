package io.flutter.plugins.camera;

import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class LivePhotoSaver implements Runnable {
    private final CircularFifoQueue<Bitmap> bitmaps;
    private final Callback callback;
    private final int orientation;
    private final DartMessenger dartMessenger;

    LivePhotoSaver(@NonNull CircularFifoQueue<Bitmap> bitmaps, int orientation, DartMessenger dartMessenger, @NonNull Callback callback) {
        this.bitmaps = bitmaps;
        this.orientation = orientation;
        this.dartMessenger = dartMessenger;
        this.callback = callback;
    }

    @Override
    public void run() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDateTime start = LocalDateTime.now();
            Log.d("LIVEPHOTO.FRAMES.THREAD", start.toString());
        }
        List<Bitmap> frames = new ArrayList<Bitmap>();
        for (int index = 0; index < bitmaps.size(); index++) {
            frames.add(bitmaps.get(index));
        }
        dartMessenger.sendLivePhotoFramesEvent(frames, orientation);
        callback.onComplete(0);
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
