package io.flutter.plugins.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import io.flutter.plugins.camera.features.renderscript.Toolkit;
import io.flutter.plugins.camera.features.renderscript.YuvFormat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ImageUtils {

    public static Bitmap yuv420ToBitmap(Image image) {
        return Toolkit.INSTANCE.yuvToRgbBitmap(
                ImageUtils.YUV420toNV21(image),
                image.getWidth(),
                image.getHeight(),
                YuvFormat.NV21);
    }

    public static Bitmap rotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.setRotate(angle,0,0);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    public static byte[] getNV21(int inputWidth, int inputHeight, Bitmap bitmap, YuvFormat imageFormat) {

        int[] argb = new int[inputWidth * inputHeight];
        bitmap.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte[] nv21 = Toolkit.INSTANCE.rgbToYuv(argb, inputWidth, inputHeight, imageFormat);

        return nv21;
    }

    public static Bitmap getScaledBitmap(Bitmap bitmap, int dstWidth, int dstHeight) {
        return Bitmap.createScaledBitmap(bitmap, dstWidth, dstHeight, false);
    }

    public static Bitmap NV21toJPEG(byte[] nv21, int width, int height, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        yuv.compressToJpeg(new Rect(0, 0, width, height), quality, out);
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());
    }

    public static byte[] YUV420toNV21(Image image) {
        if (image == null || image.getFormat() != ImageFormat.YUV_420_888) return null;

        Rect imageCrop = image.getCropRect();
        Image.Plane[] planes = image.getPlanes();
        int pixelCount = imageCrop.width() * imageCrop.height();

        byte[] outputBuffer = new byte[pixelCount * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];

        for (int i = 0; i < planes.length; i++) {
            // How many values are read in input for each output value written
            // Only the Y plane has a value for every pixel, U and V have half the resolution i.e.
            //
            // Y Plane            U Plane    V Plane
            // ===============    =======    =======
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            int outputStride = 0;

            // The index in the output buffer the next value will be written at
            // For Y it's zero, for U and V we start at the end of Y and interleave them i.e.
            //
            // First chunk        Second chunk
            // ===============    ===============
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            int outputOffset = 1;

            switch (i) {
                case 0:
                    outputStride = 1;
                    outputOffset = 0;
                    break;
                case 1:
                    outputStride = 2;
                    outputOffset = pixelCount + 1;
                    break;
                case 2:
                    outputStride = 2;
                    outputOffset = pixelCount;
                    break;
            }

            ByteBuffer planeBuffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();

            // We have to divide the width and height by two if it's not the Y plane
            Rect planeCrop = imageCrop;
            if (i != 0) {
                planeCrop = new Rect(imageCrop.left / 2, imageCrop.top / 2, imageCrop.right / 2, imageCrop.bottom / 2);
            }

            int planeWidth = planeCrop.width();
            int planeHeight = planeCrop.height();

            // Intermediate buffer used to store the bytes of each row
            byte[] rowBuffer = new byte[planes[i].getRowStride()];

            int rowLength = 0;
            if (pixelStride == 1 && outputStride == 1) {
                rowLength = planeWidth;
            } else {
                // Take into account that the stride may include data from pixels other than this
                // particular plane and row, and that could be between pixels and not after every
                // pixel:
                //
                // |---- Pixel stride ----|                    Row ends here --> |
                // | Pixel 1 | Other Data | Pixel 2 | Other Data | ... | Pixel N |
                //
                // We need to get (N-1) * (pixel stride bytes) per row + 1 byte for the last pixel
                rowLength = (planeWidth - 1) * pixelStride + 1;
            }

            for (int row = 0; row < planeHeight; row++) {
                // Move buffer position to the beginning of this row
                planeBuffer.position(
                        (row + planeCrop.top) * rowStride + planeCrop.left * pixelStride);

                if (pixelStride == 1 && outputStride == 1) {
                    // When there is a single stride value for pixel and output, we can just copy
                    // the entire row in a single step
                    planeBuffer.get(outputBuffer, outputOffset, rowLength);
                    outputOffset += rowLength;
                } else {
                    // When either pixel or output have a stride > 1 we must copy pixel by pixel
                    planeBuffer.get(rowBuffer, 0, rowLength);
                    for (int col = 0; col < planeWidth; col++) {
                        outputBuffer[outputOffset] = rowBuffer[col * pixelStride];
                        outputOffset += outputStride;
                    }
                }
            }
        }
        return outputBuffer;
    }

    public static byte[] bitmapToByteArray(Bitmap bitmap) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        return output.toByteArray();
    }
}
