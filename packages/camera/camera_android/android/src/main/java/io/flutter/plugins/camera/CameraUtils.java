// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.media.Image;

import io.flutter.embedding.engine.systemchannels.PlatformChannel;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Provides various utilities for camera. */
public final class CameraUtils {

  private CameraUtils() {}

  /**
   * Gets the {@link CameraManager} singleton.
   *
   * @param context The context to get the {@link CameraManager} singleton from.
   * @return The {@link CameraManager} singleton.
   */
  static CameraManager getCameraManager(Context context) {
    return (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
  }

  /**
   * Serializes the {@link PlatformChannel.DeviceOrientation} to a string value.
   *
   * @param orientation The orientation to serialize.
   * @return The serialized orientation.
   * @throws UnsupportedOperationException when the provided orientation not have a corresponding
   *     string value.
   */
  static String serializeDeviceOrientation(PlatformChannel.DeviceOrientation orientation) {
    if (orientation == null)
      throw new UnsupportedOperationException("Could not serialize null device orientation.");
    switch (orientation) {
      case PORTRAIT_UP:
        return "portraitUp";
      case PORTRAIT_DOWN:
        return "portraitDown";
      case LANDSCAPE_LEFT:
        return "landscapeLeft";
      case LANDSCAPE_RIGHT:
        return "landscapeRight";
      default:
        throw new UnsupportedOperationException(
            "Could not serialize device orientation: " + orientation.toString());
    }
  }

  /**
   * Deserializes a string value to its corresponding {@link PlatformChannel.DeviceOrientation}
   * value.
   *
   * @param orientation The string value to deserialize.
   * @return The deserialized orientation.
   * @throws UnsupportedOperationException when the provided string value does not have a
   *     corresponding {@link PlatformChannel.DeviceOrientation}.
   */
  static PlatformChannel.DeviceOrientation deserializeDeviceOrientation(String orientation) {
    if (orientation == null)
      throw new UnsupportedOperationException("Could not deserialize null device orientation.");
    switch (orientation) {
      case "portraitUp":
        return PlatformChannel.DeviceOrientation.PORTRAIT_UP;
      case "portraitDown":
        return PlatformChannel.DeviceOrientation.PORTRAIT_DOWN;
      case "landscapeLeft":
        return PlatformChannel.DeviceOrientation.LANDSCAPE_LEFT;
      case "landscapeRight":
        return PlatformChannel.DeviceOrientation.LANDSCAPE_RIGHT;
      default:
        throw new UnsupportedOperationException(
            "Could not deserialize device orientation: " + orientation);
    }
  }

  /**
   * Gets all the available cameras for the device.
   *
   * @param activity The current Android activity.
   * @return A map of all the available cameras, with their name as their key.
   * @throws CameraAccessException when the camera could not be accessed.
   */
  public static List<Map<String, Object>> getAvailableCameras(Activity activity)
      throws CameraAccessException {
    CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    String[] cameraNames = cameraManager.getCameraIdList();
    List<Map<String, Object>> cameras = new ArrayList<>();
    for (String cameraName : cameraNames) {
      int cameraId;
      try {
        cameraId = Integer.parseInt(cameraName, 10);
      } catch (NumberFormatException e) {
        cameraId = -1;
      }
      if (cameraId < 0) {
        continue;
      }

      HashMap<String, Object> details = new HashMap<>();
      CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraName);
      details.put("name", cameraName);
      int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      details.put("sensorOrientation", sensorOrientation);

      int lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
      switch (lensFacing) {
        case CameraMetadata.LENS_FACING_FRONT:
          details.put("lensFacing", "front");
          break;
        case CameraMetadata.LENS_FACING_BACK:
          details.put("lensFacing", "back");
          break;
        case CameraMetadata.LENS_FACING_EXTERNAL:
          details.put("lensFacing", "external");
          break;
      }
      cameras.add(details);
    }
    return cameras;
  }

  public static Bitmap getBitmap(Image image) {
//    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
//    byte[] bytes = new byte[buffer.capacity()];
//    buffer.get(bytes);
//    byte[] bytes = YUV_420_888toNV21(image);
    byte[] bytes = YUV_420_888toNV21(image);
    YuvImage yuvimage = new YuvImage(bytes, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    yuvimage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, outputStream);
    bytes = outputStream.toByteArray();
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
  }

  public static Bitmap getBitmapFromNV21(byte[] data) {
    YuvImage yuvimage = new YuvImage(data, ImageFormat.NV21, 1920, 1080, null);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    yuvimage.compressToJpeg(new Rect(0, 0, 1920, 1080), 100, outputStream);
    byte[] bytes = outputStream.toByteArray();
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
  }

  public static Bitmap getBitmapFromYuvImage(YuvImage image) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    image.compressToJpeg(new Rect(0, 0, 1920, 1080), 100, outputStream);
    byte[] bytes = outputStream.toByteArray();
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
  }

  public static byte[] getNV21(Image image) {
    return YUV_420_888toNV21(image);
  }

  public static YuvImage getYuvImage(Image image) {
    byte[] bytes = YUV_420_888toNV21(image);
    return new YuvImage(bytes, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
  }

  private static byte[] YUV_420_888toNV21(Image image) {

    int width = image.getWidth();
    int height = image.getHeight();
    int ySize = width*height;
    int uvSize = width*height/4;

    byte[] nv21 = new byte[ySize + uvSize*2];

    ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
    ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
    ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

    int rowStride = image.getPlanes()[0].getRowStride();
    assert(image.getPlanes()[0].getPixelStride() == 1);

    int pos = 0;

    if (rowStride == width) { // likely
      yBuffer.get(nv21, 0, ySize);
      pos += ySize;
    }
    else {
      int yBufferPos = -rowStride; // not an actual position
      for (; pos<ySize; pos+=width) {
        yBufferPos += rowStride;
        yBuffer.position(yBufferPos);
        yBuffer.get(nv21, pos, width);
      }
    }

    rowStride = image.getPlanes()[2].getRowStride();
    int pixelStride = image.getPlanes()[2].getPixelStride();

    assert(rowStride == image.getPlanes()[1].getRowStride());
    assert(pixelStride == image.getPlanes()[1].getPixelStride());

    if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
      // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
      byte savePixel = vBuffer.get(1);
      try {
        vBuffer.put(1, (byte)~savePixel);
        if (uBuffer.get(0) == (byte)~savePixel) {
          vBuffer.put(1, savePixel);
          vBuffer.position(0);
          uBuffer.position(0);
          vBuffer.get(nv21, ySize, 1);
          uBuffer.get(nv21, ySize + 1, uBuffer.remaining());

          return nv21; // shortcut
        }
      }
      catch (ReadOnlyBufferException ex) {
        // unfortunately, we cannot check if vBuffer and uBuffer overlap
      }

      // unfortunately, the check failed. We must save U and V pixel by pixel
      vBuffer.put(1, savePixel);
    }

    // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
    // but performance gain would be less significant

    for (int row=0; row<height/2; row++) {
      for (int col=0; col<width/2; col++) {
        int vuPos = col*pixelStride + row*rowStride;
        nv21[pos++] = vBuffer.get(vuPos);
        nv21[pos++] = uBuffer.get(vuPos);
      }
    }

    return nv21;
  }
}
