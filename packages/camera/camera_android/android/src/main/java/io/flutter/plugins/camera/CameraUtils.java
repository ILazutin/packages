// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.ActivityCompat;

import io.flutter.embedding.engine.systemchannels.PlatformChannel;
import io.flutter.plugin.common.MethodChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Provides various utilities for camera. */
public final class CameraUtils {

  private static Handler[] backgroundHandlers = new Handler[2];
  /** An additional thread for running tasks that shouldn't block the UI. */
  private static HandlerThread[] backgroundHandlerThreads = new HandlerThread[2];
  private static final CameraDevice[] initializedCameras = new CameraDevice[2];

  private CameraUtils() {
  }

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

  public static void isMultiCamSupported(Activity activity, @NonNull final MethodChannel.Result result) throws CameraAccessException {
    final String TAG = "Camera";

    List<Map<String, Object>> cameras = getAvailableCameras(activity);

    if (cameras.size() <= 1) {
      result.success(false);
      return;
    }

    CameraManager cameraManager = getCameraManager(activity);

    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      result.success(false);
      return;
    }

    String firstCameraName = "";
    String secondCameraName = "";
    for (Map<String, Object> camera : cameras) {
      if (firstCameraName.isEmpty() && Objects.requireNonNull(camera.get("lensFacing")).toString().equals("back")) {
        firstCameraName = Objects.requireNonNull(camera.get("name")).toString();
      }

      if (secondCameraName.isEmpty() && Objects.requireNonNull(camera.get("lensFacing")).toString().equals("front")) {
        secondCameraName = Objects.requireNonNull(camera.get("name")).toString();
      }
    }

    if (firstCameraName.isEmpty() || secondCameraName.isEmpty()) {
      result.success(false);
      return;
    }

    startBackgroundThread(0);
    startBackgroundThread(1);

    try {
      cameraManager.openCamera(firstCameraName,
              new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice device) {
                  Log.i(TAG, "first | open | onOpened");
                  initializedCameras[0] = device;
                  if (initializedCameras[1] != null) {
                    initializedCameras[0].close();
                    initializedCameras[1].close();
                    result.success(true);
                  }
                }

                @Override
                public void onClosed(@NonNull CameraDevice camera) {
                  Log.i(TAG, "first | open | onClosed");
                  initializedCameras[0] = null;
                  stopBackgroundThread(0);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                  Log.i(TAG, "first | open | onDisconnected");
                  if (backgroundHandlers[0] != null && initializedCameras[0] != null) {
                    initializedCameras[0].close();
                    initializedCameras[0] = null;
                  }
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int errorCode) {
                  Log.i(TAG, "first | open | onError");
                  cameraDevice.close();
                  if (initializedCameras[1] != null) {
                    try {
                      initializedCameras[1].close();
                      initializedCameras[1] = null;
                    } catch (Exception ignored) {
                    }
                  }
                  result.success(false);
                }
              }, backgroundHandlers[0]);
    } catch (Exception e) {
      stopBackgroundThread(0);
      stopBackgroundThread(1);
      result.success(false);
      return;
    }

    try {
      cameraManager.openCamera(secondCameraName,
              new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice device) {
                  Log.i(TAG, "second | open | onOpened");
                  initializedCameras[1] = device;
                  if (initializedCameras[0] != null) {
                    initializedCameras[0].close();
                    initializedCameras[1].close();
                    result.success(true);
                  }
                }

                @Override
                public void onClosed(@NonNull CameraDevice camera) {
                  Log.i(TAG, "second | open | onClosed");
                  initializedCameras[1] = null;
                  stopBackgroundThread(1);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                  Log.i(TAG, "second | open | onDisconnected");
                  if (backgroundHandlers[1] != null && initializedCameras[1] != null) {
                    initializedCameras[1].close();
                    initializedCameras[1] = null;
                  }
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int errorCode) {
                  Log.i(TAG, "second | open | onError");
                  cameraDevice.close();
                  if (initializedCameras[0] != null) {
                    try {
                      initializedCameras[0].close();
                      initializedCameras[0] = null;
                    } catch (Exception ignored) {
                    }
                  }
                  result.success(false);
                }
              }, backgroundHandlers[1]);
    } catch (CameraAccessException e) {
      result.success(false);
      stopBackgroundThread(0);
      stopBackgroundThread(1);
    }

  }

  private static void startBackgroundThread(int index) {
    if (backgroundHandlerThreads[index] != null) {
      return;
    }

    backgroundHandlerThreads[index] = HandlerThreadFactory.create("CameraUtilsBackgroundFirst");
    try {
      backgroundHandlerThreads[index].start();
    } catch (IllegalThreadStateException e) {
      // Ignore exception in case the thread has already started.
    }
    backgroundHandlers[index] = HandlerFactory.create(backgroundHandlerThreads[index].getLooper());
  }

  /** Stops the background thread and its {@link Handler}. */
  private static void stopBackgroundThread(int index) {
    if (backgroundHandlerThreads[index] != null) {
      backgroundHandlerThreads[index].quitSafely();
    }
    backgroundHandlerThreads[index] = null;
    backgroundHandlers[index] = null;
  }

  /** Factory class that assists in creating a {@link HandlerThread} instance. */
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
