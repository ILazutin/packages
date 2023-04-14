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

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import io.flutter.embedding.engine.systemchannels.PlatformChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Provides various utilities for camera. */
public final class CameraUtils {

  private static Handler backgroundHandler;

  /** An additional thread for running tasks that shouldn't block the UI. */
  private static HandlerThread backgroundHandlerThread;

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

  public static boolean isMultiCamSupported(Activity activity) throws CameraAccessException {
    List<Map<String, Object>> cameras = getAvailableCameras(activity);

    if (cameras.size() <= 1) return false;

    CameraManager cameraManager = getCameraManager(activity);

    final CameraDevice[] initializedCameras = new CameraDevice[2];

    final boolean[] isMultiCamSupported = {true};

    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      return false;
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
      return false;
    }

    startBackgroundThread();

    try {
      cameraManager.openCamera(firstCameraName,
              new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice device) {
                  initializedCameras[0] = device;
                }

                @Override
                public void onClosed(@NonNull CameraDevice camera) {
                  initializedCameras[0] = null;
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                  initializedCameras[0].close();
                  initializedCameras[0] = null;
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int errorCode) {
                  switch (errorCode) {
                    case ERROR_MAX_CAMERAS_IN_USE:
                      isMultiCamSupported[0] = false;
                      break;
                    default:
                      break;
                  }

                }
              }, backgroundHandler);
    } catch (Exception e) {
      stopBackgroundThread();
      return false;
    }

    if (initializedCameras[0] == null) {
      stopBackgroundThread();
      return false;
    }

    try {
      cameraManager.openCamera(secondCameraName,
              new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice device) {
                  initializedCameras[1] = device;
                }

                @Override
                public void onClosed(@NonNull CameraDevice camera) {
                  initializedCameras[1] = null;
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                  initializedCameras[1].close();
                  initializedCameras[1] = null;
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int errorCode) {
                  switch (errorCode) {
                    case ERROR_MAX_CAMERAS_IN_USE:
                      isMultiCamSupported[0] = false;
                      break;
                    default:
                      break;
                  }

                }
              }, backgroundHandler);
    } catch (CameraAccessException e) {
      isMultiCamSupported[0] = false;
    }

    stopBackgroundThread();

    if (initializedCameras[0] != null) {
      initializedCameras[0].close();
      initializedCameras[0] = null;
    }
    if (initializedCameras[1] != null) {
      initializedCameras[1].close();
      initializedCameras[1] = null;
    }

    return isMultiCamSupported[0];
  }

  private static void startBackgroundThread() {
    if (backgroundHandlerThread != null) {
      return;
    }

    backgroundHandlerThread = Camera.HandlerThreadFactory.create("CameraBackground");
    try {
      backgroundHandlerThread.start();
    } catch (IllegalThreadStateException e) {
      // Ignore exception in case the thread has already started.
    }
    backgroundHandler = Camera.HandlerFactory.create(backgroundHandlerThread.getLooper());
  }

  /** Stops the background thread and its {@link Handler}. */
  private static void stopBackgroundThread() {
    if (backgroundHandlerThread != null) {
      backgroundHandlerThread.quitSafely();
    }
    backgroundHandlerThread = null;
    backgroundHandler = null;
  }
}
