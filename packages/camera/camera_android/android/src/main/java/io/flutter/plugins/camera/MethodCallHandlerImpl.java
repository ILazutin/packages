// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.camera;

import android.app.Activity;
import android.hardware.camera2.CameraAccessException;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.flutter.embedding.engine.systemchannels.PlatformChannel;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugins.camera.CameraPermissions.PermissionsRegistry;
import io.flutter.plugins.camera.features.CameraFeatureFactoryImpl;
import io.flutter.plugins.camera.features.Point;
import io.flutter.plugins.camera.features.autofocus.FocusMode;
import io.flutter.plugins.camera.features.exposurelock.ExposureMode;
import io.flutter.plugins.camera.features.flash.FlashMode;
import io.flutter.plugins.camera.features.resolution.ResolutionAspectRatio;
import io.flutter.plugins.camera.features.resolution.ResolutionPreset;
import io.flutter.view.TextureRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class MethodCallHandlerImpl implements MethodChannel.MethodCallHandler {
  private final Activity activity;
  private final BinaryMessenger messenger;
  private final CameraPermissions cameraPermissions;
  private final PermissionsRegistry permissionsRegistry;
  private final TextureRegistry textureRegistry;
  private final MethodChannel methodChannel;
  private final EventChannel imageStreamChannel;
  private @Nullable Camera camera;
  private @Nullable Camera secondCamera;
  private List<String> mainCameraFiles = new ArrayList<>();
  private List<String> secondCameraFiles = new ArrayList<>();

  MethodCallHandlerImpl(
      Activity activity,
      BinaryMessenger messenger,
      CameraPermissions cameraPermissions,
      PermissionsRegistry permissionsAdder,
      TextureRegistry textureRegistry) {
    this.activity = activity;
    this.messenger = messenger;
    this.cameraPermissions = cameraPermissions;
    this.permissionsRegistry = permissionsAdder;
    this.textureRegistry = textureRegistry;

    methodChannel = new MethodChannel(messenger, "plugins.flutter.io/camera_android");
    imageStreamChannel =
        new EventChannel(messenger, "plugins.flutter.io/camera_android/imageStream");
    methodChannel.setMethodCallHandler(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull final Result result) {
    switch (call.method) {
      case "availableCameras":
        try {
          result.success(CameraUtils.getAvailableCameras(activity));
        } catch (Exception e) {
          handleException(e, result);
        }
        break;
      case "isMultiCamSupported":
        try {
          CameraUtils.isMultiCamSupported(activity, result);
        } catch (Exception e) {
          handleException(e, result);
        }
        break;
      case "create":
        {
          if (camera != null) {
            camera.close();
          }

          if (secondCamera != null) {
            secondCamera.close();
          }

          cameraPermissions.requestPermissions(
              activity,
              permissionsRegistry,
              call.argument("enableAudio"),
              (String errCode, String errDesc) -> {
                if (errCode == null) {
                  try {
                    instantiateCamera(call, result);
                  } catch (Exception e) {
                    handleException(e, result);
                  }
                } else {
                  result.error(errCode, errDesc, null);
                }
              });
          break;
        }
      case "initialize":
        {
          if (camera != null) {
            try {
              camera.open(call.argument("imageFormatGroup"));
              if (secondCamera != null) {
                secondCamera.open(null);
              }
              result.success(null);
            } catch (Exception e) {
              handleException(e, result);
            }
          } else {
            result.error(
                "cameraNotFound",
                "Camera not found. Please call the 'create' method before calling 'initialize'.",
                null);
          }
          break;
        }
      case "takePicture":
        {
          assert camera != null;
          mainCameraFiles.clear();
          secondCameraFiles.clear();
          camera.takePicture(result, secondCamera == null ? null : new Camera.TakePictureCallback() {
            @Override
            public void onComplete(List<String> files) {
              mainCameraFiles.addAll(files);
              if (secondCameraFiles.size() > 0) {
                mainCameraFiles.addAll(secondCameraFiles);
                camera.sendTakePictureResult(result, mainCameraFiles);
              }
            }

            @Override
            public void onError(String errorCode, String errorMessage, @Nullable Object errorDetails) {
              camera.sendError(result, errorCode, errorMessage, errorDetails);
            }

            @Override
            public void onCameraErrorEvent(@Nullable String description) {
              camera.sendCameraErrorEvent(description);
            }
          });
          if (secondCamera != null) {
            secondCamera.takePicture(result, new Camera.TakePictureCallback() {
              @Override
              public void onComplete(List<String> files) {
                secondCameraFiles.addAll(files);
                if (mainCameraFiles.size() > 0) {
                  mainCameraFiles.addAll(secondCameraFiles);
                  camera.sendTakePictureResult(result, mainCameraFiles);
                }
              }

              @Override
              public void onError(String errorCode, String errorMessage, @Nullable Object errorDetails) {
                if (mainCameraFiles.size() > 0) {
                  camera.sendTakePictureResult(result, mainCameraFiles);
                }
              }

              @Override
              public void onCameraErrorEvent(@Nullable String description) {
                if (mainCameraFiles.size() > 0) {
                  camera.sendTakePictureResult(result, mainCameraFiles);
                }
              }
            });
          }
          break;
        }
      case "prepareForVideoRecording":
        {
          // This optimization is not required for Android.
          result.success(null);
          break;
        }
      case "startVideoRecording":
        {
          camera.startVideoRecording(
              result,
              Objects.equals(call.argument("enableStream"), true) ? imageStreamChannel : null);
          break;
        }
      case "stopVideoRecording":
        {
          camera.stopVideoRecording(result);
          break;
        }
      case "pauseVideoRecording":
        {
          camera.pauseVideoRecording(result);
          break;
        }
      case "resumeVideoRecording":
        {
          camera.resumeVideoRecording(result);
          break;
        }
      case "setFlashMode":
        {
          String modeStr = call.argument("mode");
          FlashMode mode = FlashMode.getValueForString(modeStr);
          if (mode == null) {
            result.error("setFlashModeFailed", "Unknown flash mode " + modeStr, null);
            return;
          }
          try {
            camera.setFlashMode(result, mode);
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "setExposureMode":
        {
          String modeStr = call.argument("mode");
          ExposureMode mode = ExposureMode.getValueForString(modeStr);
          if (mode == null) {
            result.error("setExposureModeFailed", "Unknown exposure mode " + modeStr, null);
            return;
          }
          try {
            camera.setExposureMode(result, mode);
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "setExposurePoint":
        {
          Boolean reset = call.argument("reset");
          Double x = null;
          Double y = null;
          if (reset == null || !reset) {
            x = call.argument("x");
            y = call.argument("y");
          }
          try {
            camera.setExposurePoint(result, new Point(x, y));
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "getMinExposureOffset":
        {
          try {
            result.success(camera.getMinExposureOffset());
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "getMaxExposureOffset":
        {
          try {
            result.success(camera.getMaxExposureOffset());
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "getExposureOffsetStepSize":
        {
          try {
            result.success(camera.getExposureOffsetStepSize());
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "setExposureOffset":
        {
          try {
            camera.setExposureOffset(result, call.argument("offset"));
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "setFocusMode":
        {
          String modeStr = call.argument("mode");
          FocusMode mode = FocusMode.getValueForString(modeStr);
          if (mode == null) {
            result.error("setFocusModeFailed", "Unknown focus mode " + modeStr, null);
            return;
          }
          try {
            camera.setFocusMode(result, mode);
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "setFocusPoint":
        {
          Boolean reset = call.argument("reset");
          Double x = null;
          Double y = null;
          if (reset == null || !reset) {
            x = call.argument("x");
            y = call.argument("y");
          }
          try {
            camera.setFocusPoint(result, new Point(x, y));
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "startImageStream":
        {
          try {
            camera.startPreviewWithImageStream(imageStreamChannel);
            result.success(null);
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "stopImageStream":
        {
          try {
            camera.startPreview();
            result.success(null);
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "getMaxZoomLevel":
        {
          assert camera != null;

          try {
            float maxZoomLevel = camera.getMaxZoomLevel();
            result.success(maxZoomLevel);
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "getMinZoomLevel":
        {
          assert camera != null;

          try {
            float minZoomLevel = camera.getMinZoomLevel();
            result.success(minZoomLevel);
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "setZoomLevel":
        {
          assert camera != null;

          Double zoom = call.argument("zoom");

          if (zoom == null) {
            result.error(
                "ZOOM_ERROR", "setZoomLevel is called without specifying a zoom level.", null);
            return;
          }

          try {
            camera.setZoomLevel(result, zoom.floatValue());
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "lockCaptureOrientation":
        {
          PlatformChannel.DeviceOrientation orientation =
              CameraUtils.deserializeDeviceOrientation(call.argument("orientation"));

          try {
            camera.lockCaptureOrientation(orientation);
            if (secondCamera != null) {
              secondCamera.lockCaptureOrientation(orientation);
            }
            result.success(null);
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "unlockCaptureOrientation":
        {
          try {
            camera.unlockCaptureOrientation();
            if (secondCamera != null) {
              secondCamera.unlockCaptureOrientation();
            }
            result.success(null);
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "pausePreview":
        {
          try {
            camera.pausePreview();
            result.success(null);
          } catch (Exception e) {
            handleException(e, result);
          }
          break;
        }
      case "resumePreview":
        {
          camera.resumePreview();
          result.success(null);
          break;
        }
      case "dispose":
        {
          if (camera != null) {
            camera.dispose();
            camera = null;
          }
          if (secondCamera != null) {
            secondCamera.dispose();
            secondCamera = null;
          }
          result.success(null);
          break;
        }
      default:
        result.notImplemented();
        break;
    }
  }

  void stopListening() {
    methodChannel.setMethodCallHandler(null);
  }

  private void instantiateCamera(MethodCall call, Result result) throws CameraAccessException {
    String cameraName = call.argument("cameraName");
    String preset = call.argument("resolutionPreset");
    String aspectRatio = call.argument("resolutionAspectRatio");
    boolean enableAudio = call.argument("enableAudio");
    boolean enableLivePhoto = Boolean.TRUE.equals(call.argument("enableLivePhoto"));
    int livePhotoMaxDuration = call.argument("livePhotoMaxDuration");
    String secondCameraName = call.argument("secondCameraName");

    TextureRegistry.SurfaceTextureEntry flutterSurfaceTexture =
        textureRegistry.createSurfaceTexture();
    DartMessenger dartMessenger =
        new DartMessenger(
            messenger, flutterSurfaceTexture.id(), new Handler(Looper.getMainLooper()));
    CameraProperties cameraProperties =
        new CameraPropertiesImpl(cameraName, CameraUtils.getCameraManager(activity));
    ResolutionPreset resolutionPreset = ResolutionPreset.valueOf(preset);
    ResolutionAspectRatio resolutionAspectRatio = ResolutionAspectRatio.valueOf(aspectRatio);
    CameraProperties secondCameraProperties = secondCameraName == null ? null : new CameraPropertiesImpl(secondCameraName, CameraUtils.getCameraManager(activity));

    camera =
        new Camera(
            activity,
            flutterSurfaceTexture,
            new CameraFeatureFactoryImpl(),
            dartMessenger,
            cameraProperties,
            resolutionPreset,
            resolutionAspectRatio,
            enableAudio,
            enableLivePhoto,
            livePhotoMaxDuration
        );

    if (secondCameraProperties != null) {
      TextureRegistry.SurfaceTextureEntry secondFlutterSurfaceTexture =
              textureRegistry.createSurfaceTexture();
      DartMessenger secondDartMessenger =
              new DartMessenger(
                      messenger, secondFlutterSurfaceTexture.id(), new Handler(Looper.getMainLooper()));
      secondCamera =
              new Camera(
                      activity,
                      secondFlutterSurfaceTexture,
                      new CameraFeatureFactoryImpl(),
                      secondDartMessenger,
                      secondCameraProperties,
                      resolutionPreset,
                      resolutionAspectRatio,
                      enableAudio,
                      false,
                      0
              );
    }

    Map<String, Object> reply = new HashMap<>();
    reply.put("cameraId", flutterSurfaceTexture.id());
    result.success(reply);
  }

  // We move catching CameraAccessException out of onMethodCall because it causes a crash
  // on plugin registration for sdks incompatible with Camera2 (< 21). We want this plugin to
  // to be able to compile with <21 sdks for apps that want the camera and support earlier version.
  @SuppressWarnings("ConstantConditions")
  private void handleException(Exception exception, Result result) {
    if (exception instanceof CameraAccessException) {
      result.error("CameraAccess", exception.getMessage(), null);
      return;
    }

    // CameraAccessException can not be cast to a RuntimeException.
    throw (RuntimeException) exception;
  }
}
