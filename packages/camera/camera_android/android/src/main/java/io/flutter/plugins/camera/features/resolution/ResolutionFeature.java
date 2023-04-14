// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.camera.features.resolution;

import static java.lang.Math.max;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.EncoderProfiles;
import android.os.Build;
import android.util.Size;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import io.flutter.plugins.camera.CameraProperties;
import io.flutter.plugins.camera.features.CameraFeature;
import java.util.Arrays;
import java.util.List;

/**
 * Controls the resolutions configuration on the {@link android.hardware.camera2} API.
 *
 * <p>The {@link ResolutionFeature} is responsible for converting the platform independent {@link
 * ResolutionPreset} into a {@link android.media.CamcorderProfile} which contains all the properties
 * required to configure the resolution using the {@link android.hardware.camera2} API.
 */
public class ResolutionFeature extends CameraFeature<ResolutionPreset> {
  private Size captureSize;
  private Size previewSize;
  private CamcorderProfile recordingProfileLegacy;
  private EncoderProfiles recordingProfile;
  private ResolutionPreset currentSetting;
  private ResolutionAspectRatio aspectRatio;
  private CameraManager cameraManager;
  private int cameraId;

  /**
   * Creates a new instance of the {@link ResolutionFeature}.
   *
   * @param cameraProperties Collection of characteristics for the current camera device.
   * @param resolutionPreset Platform agnostic enum containing resolution information.
   * @param cameraName Camera identifier of the camera for which to configure the resolution.
   */
  public ResolutionFeature(
      CameraProperties cameraProperties, CameraManager cameraManager, ResolutionPreset resolutionPreset, ResolutionAspectRatio aspectRatio, String cameraName) {
    super(cameraProperties);
    this.cameraManager = cameraManager;
    this.currentSetting = resolutionPreset;
    this.aspectRatio = aspectRatio;
    try {
      this.cameraId = Integer.parseInt(cameraName, 10);
    } catch (NumberFormatException e) {
      this.cameraId = -1;
      return;
    }
    configureResolution(resolutionPreset, cameraId);
  }

  /**
   * Gets the {@link android.media.CamcorderProfile} containing the information to configure the
   * resolution using the {@link android.hardware.camera2} API.
   *
   * @return Resolution information to configure the {@link android.hardware.camera2} API.
   */
  public CamcorderProfile getRecordingProfileLegacy() {
    return this.recordingProfileLegacy;
  }

  public EncoderProfiles getRecordingProfile() {
    return this.recordingProfile;
  }

  /**
   * Gets the optimal preview size based on the configured resolution.
   *
   * @return The optimal preview size.
   */
  public Size getPreviewSize() {
    return this.previewSize;
  }

  /**
   * Gets the optimal capture size based on the configured resolution.
   *
   * @return The optimal capture size.
   */
  public Size getCaptureSize() {
    try {
      CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraProperties.getCameraName());
      StreamConfigurationMap configs = characteristics.get(
              CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      Size[] outputSizes = new Size[0];
      try {
        outputSizes = configs.getOutputSizes(ImageFormat.JPEG);
      } catch (Exception exception) {
        Log.e("CameraResolution", exception.toString());
      }

      Size[] highRes = new Size[0];
      try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
          highRes = configs.getHighResolutionOutputSizes(ImageFormat.JPEG);
        }
      } catch (Exception exception) {
        Log.e("CameraResolution", exception.toString());
      }

      Log.d("CAMERA SIZES HIGH", Arrays.toString(highRes));
      Log.d("CAMERA SIZES ALL", Arrays.toString(outputSizes));

      return getFirstEligibleSizeForAspectRatio(highRes, outputSizes);
    } catch (Exception exception) {
      return this.captureSize;
    }
  }

  public Size getCaptureSizeForPreset(ResolutionPreset resolutionPreset) {
    if (currentSetting.compareTo(resolutionPreset) < 0) {
      resolutionPreset = currentSetting;
    }

    if (!checkIsSupported()) {
      return new Size(0, 0);
    }
    @Nullable Size calculatedCaptureSize = null;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      recordingProfileLegacy = null;
      recordingProfile =
              getBestAvailableCamcorderProfileForResolutionPreset(cameraId, resolutionPreset);
      List<EncoderProfiles.VideoProfile> videoProfiles = recordingProfile.getVideoProfiles();

      EncoderProfiles.VideoProfile defaultVideoProfile = videoProfiles.get(0);

      if (defaultVideoProfile != null) {
        calculatedCaptureSize = new Size(defaultVideoProfile.getWidth(), defaultVideoProfile.getHeight());
      }
    }

    if (calculatedCaptureSize == null) {
      recordingProfile = null;
      recordingProfileLegacy = getBestAvailableCamcorderProfileForResolutionPresetLegacy(cameraId, resolutionPreset);
      int width;
      int height;
      if (recordingProfileLegacy != null) {
        width = recordingProfileLegacy.videoFrameWidth;
        height = recordingProfileLegacy.videoFrameHeight;
      } else {
        width = 1280;
        height = 720;
      }
      calculatedCaptureSize = new Size(width, height);
    }

    return calculatedCaptureSize;
  }

  @Override
  public String getDebugName() {
    return "ResolutionFeature";
  }

  @Override
  public ResolutionPreset getValue() {
    return currentSetting;
  }

  @Override
  public void setValue(ResolutionPreset value) {
    this.currentSetting = value;
    configureResolution(currentSetting, cameraId);
  }

  @Override
  public boolean checkIsSupported() {
    return cameraId >= 0;
  }

  @Override
  public void updateBuilder(CaptureRequest.Builder requestBuilder) {
    // No-op: when setting a resolution there is no need to update the request builder.
  }

  @VisibleForTesting
  static Size computeBestPreviewSize(int cameraId, ResolutionPreset preset, ResolutionAspectRatio aspectRatio)
      throws IndexOutOfBoundsException {
    if (preset.ordinal() > ResolutionPreset.high.ordinal()) {
      preset = ResolutionPreset.high;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      EncoderProfiles profile =
          getBestAvailableCamcorderProfileForResolutionPreset(cameraId, preset);
      List<EncoderProfiles.VideoProfile> videoProfiles = profile.getVideoProfiles();
      EncoderProfiles.VideoProfile defaultVideoProfile = videoProfiles.get(0);

      if (defaultVideoProfile != null) {
        int width = defaultVideoProfile.getWidth();
        int height = defaultVideoProfile.getHeight();
        int maxSize = max(width, height);

        double scale;
        if (aspectRatio == ResolutionAspectRatio.RATIO_16_9) {
          scale = 9/16f;
        } else {
          scale = 3/4f;
        }
        if (maxSize == width) {
          return new Size(maxSize, (int) Math.round(maxSize * scale));
        } else {
          return new Size((int) Math.round(maxSize * scale), maxSize);
        }
      }
    }

    // TODO(camsim99): Suppression is currently safe because legacy code is used as a fallback for SDK < S.
    // This should be removed when reverting that fallback behavior: https://github.com/flutter/flutter/issues/119668.
    CamcorderProfile profile =
        getBestAvailableCamcorderProfileForResolutionPresetLegacy(cameraId, preset);
    int width;
    int height;
    if (profile != null) {
      width = profile.videoFrameWidth;
      height = profile.videoFrameHeight;
    } else {
      width = 1280;
      height = 720;
    }
    int maxSize = max(width, height);

    double scale;
    if (aspectRatio == ResolutionAspectRatio.RATIO_16_9) {
      scale = 9/16f;
    } else {
      scale = 3/4f;
    }

    if (maxSize == width) {
      return new Size(maxSize, (int) Math.round(maxSize * scale));
    } else {
      return new Size((int) Math.round(maxSize * scale), maxSize);
    }
  }

  /**
   * Gets the best possible {@link android.media.CamcorderProfile} for the supplied {@link
   * ResolutionPreset}. Supports SDK < 31.
   *
   * @param cameraId Camera identifier which indicates the device's camera for which to select a
   *     {@link android.media.CamcorderProfile}.
   * @param preset The {@link ResolutionPreset} for which is to be translated to a {@link
   *     android.media.CamcorderProfile}.
   * @return The best possible {@link android.media.CamcorderProfile} that matches the supplied
   *     {@link ResolutionPreset}.
   */
  @TargetApi(Build.VERSION_CODES.R)
  // All of these cases deliberately fall through to get the best available profile.
  @SuppressWarnings({"fallthrough", "deprecation"})
  public static CamcorderProfile getBestAvailableCamcorderProfileForResolutionPresetLegacy(
      int cameraId, ResolutionPreset preset) {
    if (cameraId < 0) {
      throw new AssertionError(
          "getBestAvailableCamcorderProfileForResolutionPreset can only be used with valid (>=0) camera identifiers.");
    }

    switch (preset) {
      case max:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
        }
        // fall through
      case ultraHigh:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_2160P);
        }
        // fall through
      case veryHigh:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_1080P);
        }
        // fall through
      case high:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P);
        }
        // fall through
      case medium:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P);
        }
        // fall through
      case low:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QVGA);
        }
        // fall through
      default:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
        } else {
          throw new IllegalArgumentException(
              "No capture session available for current capture session.");
        }
    }
  }

  @TargetApi(Build.VERSION_CODES.S)
  // All of these cases deliberately fall through to get the best available profile.
  @SuppressWarnings("fallthrough")
  public static EncoderProfiles getBestAvailableCamcorderProfileForResolutionPreset(
      int cameraId, ResolutionPreset preset) {
    if (cameraId < 0) {
      throw new AssertionError(
          "getBestAvailableCamcorderProfileForResolutionPreset can only be used with valid (>=0) camera identifiers.");
    }

    String cameraIdString = Integer.toString(cameraId);

    switch (preset) {
      case max:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_HIGH);
        }
        // fall through
      case ultraHigh:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_2160P);
        }
        // fall through
      case veryHigh:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_1080P);
        }
        // fall through
      case high:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_720P);
        }
        // fall through
      case medium:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_480P);
        }
        // fall through
      case low:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_QVGA);
        }
        // fall through
      default:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_LOW);
        }

        throw new IllegalArgumentException(
            "No capture session available for current capture session.");
    }
  }

  private void configureResolution(ResolutionPreset resolutionPreset, int cameraId)
      throws IndexOutOfBoundsException {
    if (!checkIsSupported()) {
      return;
    }
    boolean captureSizeCalculated = false;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      recordingProfileLegacy = null;
      recordingProfile =
          getBestAvailableCamcorderProfileForResolutionPreset(cameraId, resolutionPreset);
      List<EncoderProfiles.VideoProfile> videoProfiles = recordingProfile.getVideoProfiles();

      EncoderProfiles.VideoProfile defaultVideoProfile = videoProfiles.get(0);

      if (defaultVideoProfile != null) {
        captureSizeCalculated = true;
        int width = defaultVideoProfile.getWidth();
        int height = defaultVideoProfile.getHeight();
        int maxSize = max(width, height);

        double scale;
        if (aspectRatio == ResolutionAspectRatio.RATIO_16_9) {
          scale = 9/16f;
        } else {
          scale = 3/4f;
        }

        if (maxSize == width) {
          captureSize = new Size(maxSize, (int) Math.round(maxSize * scale));
        } else {
          captureSize = new Size((int) Math.round(maxSize * scale), maxSize);
        }
      }
    }

    if (!captureSizeCalculated) {
      recordingProfile = null;
      CamcorderProfile camcorderProfile =
          getBestAvailableCamcorderProfileForResolutionPresetLegacy(cameraId, resolutionPreset);
      recordingProfileLegacy = camcorderProfile;
      int width;
      int height;
      if (recordingProfileLegacy != null) {
        width = recordingProfileLegacy.videoFrameWidth;
        height = recordingProfileLegacy.videoFrameHeight;
      } else {
        width = 1280;
        height = 720;
      }
      int maxSize = max(width, height);

      double scale;
      if (aspectRatio == ResolutionAspectRatio.RATIO_16_9) {
        scale = 9/16f;
      } else {
        scale = 3/4f;
      }

      if (maxSize == width) {
        captureSize = new Size(maxSize, (int) Math.round(maxSize * scale));
      } else {
        captureSize = new Size((int) Math.round(maxSize * scale), maxSize);
      }
    }

    previewSize = computeBestPreviewSize(cameraId, resolutionPreset, aspectRatio);
  }

  private Size getFirstEligibleSizeForAspectRatio(Size[] highResSizes, Size[] standardSizes) {
    double widthDivider;
    double heightDivider;
    if (aspectRatio == ResolutionAspectRatio.RATIO_16_9) {
      widthDivider = 16f;
      heightDivider = 9f;
    } else {
      widthDivider = 4f;
      heightDivider = 3f;
    }

    if (highResSizes != null) {
      for (Size currentSize : highResSizes) {
        if ((currentSize.getWidth() / widthDivider) == (currentSize.getHeight() / heightDivider)) {
          return currentSize;
        }
      }
    }

    if (standardSizes != null) {
      for (Size currentSize : standardSizes) {
        if ((currentSize.getWidth() / widthDivider) == (currentSize.getHeight() / heightDivider)) {
          return currentSize;
        }
      }
    }

    return this.captureSize;
  }
}
