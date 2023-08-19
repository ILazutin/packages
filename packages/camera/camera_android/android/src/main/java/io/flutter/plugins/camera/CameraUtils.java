// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.camera;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import io.flutter.embedding.engine.systemchannels.PlatformChannel;
import io.flutter.plugin.common.MethodChannel;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Provides various utilities for camera. */
public final class CameraUtils {

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
                "Could not serialize device orientation: " + orientation);
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
    List<Map<String, Object>> cameras = getAvailableCameras(activity);

    if (cameras.size() <= 1) {
      result.success(false);
      return;
    }

    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      result.success(false);
      return;
    }

    boolean isMultiCamSupported = (
            measureDevicePerformanceClass(activity) == PERFORMANCE_CLASS_HIGH &&
                    cameras.size() > 1 &&
                    allowPreparingHevcPlayers()
    );

    if (isMultiCamSupported) {
      isMultiCamSupported = activity.getPackageManager().hasSystemFeature("android.hardware.camera.concurrent");

      if (!isMultiCamSupported) {
        int hash = (Build.MANUFACTURER + " " + Build.DEVICE).toUpperCase().hashCode();
        for (int j : dualWhitelistByDevice) {
          if (j == hash) {
            isMultiCamSupported = true;
            break;
          }
        }
      }
      if (!isMultiCamSupported) {
        int hash = (Build.MANUFACTURER + Build.MODEL).toUpperCase().hashCode();
        for (int j : dualWhitelistByModel) {
          if (j == hash) {
            isMultiCamSupported = true;
            break;
          }
        }
      }
    }

    result.success(isMultiCamSupported);
  }

  public static boolean allowPreparingHevcPlayers() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
      return false;
    }

    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
    MediaCodecInfo[] codecInfos = mediaCodecList.getCodecInfos();
    int maxInstances = 0;
    int capabilities = 0;

    for (MediaCodecInfo codecInfo : codecInfos) {
      if (codecInfo.isEncoder()) {
        continue;
      }

      boolean found = false;
      for (int k = 0; k < codecInfo.getSupportedTypes().length; k++) {
        if (codecInfo.getSupportedTypes()[k].contains("video/hevc")) {
          found = true;
          break;
        }
      }
      if (!found) {
        continue;
      }
      capabilities = codecInfo.getCapabilitiesForType("video/hevc").getMaxSupportedInstances();
      if (capabilities > maxInstances) {
        maxInstances = capabilities;
      }
    }

    return maxInstances >= 8;
  }

  private static final int[] dualWhitelistByDevice = new int[] {
          1893745684,  // XIAOMI CUPID
          -215458996,  // XIAOMI VAYU
          -862041025,  // XIAOMI WILLOW
          -1258375037, // XIAOMI INGRES
          -1320049076, // XIAOMI GINKGO
          -215749424,  // XIAOMI LISA
          1901578030,  // XIAOMI LEMON
          -215451421,  // XIAOMI VIVA
          1908491424,  // XIAOMI STONE
          -1321491332, // XIAOMI RAPHAEL
          -1155551678, // XIAOMI MARBLE
          1908524435,  // XIAOMI SURYA
          976847578,   // XIAOMI LAUREL_SPROUT
          -1489198134, // XIAOMI ALIOTH
          1910814392,  // XIAOMI VENUS
          -1634623708, // XIAOMI CEPHEUS
          -713271737,  // OPPO OP4F2F
          -2010722764, // SAMSUNG A52SXQ (A52s 5G)
          1407170066,  // SAMSUNG D2Q (Note10+)
          -821405251,  // SAMSUNG BEYOND2
          -1394190955, // SAMSUNG A71
          -1394190055, // SAMSUNG B4Q
          -1394168012, // SAMSUNG Y2S
          1407170066,  // HUAWEI HWNAM
          1407159934,  // HUAWEI HWCOR
          1407172057,  // HUAWEI HWPCT
          1231389747,  // FAIRPHONE FP3
          -2076538925, // MOTOROLA RSTAR
          41497626,    // MOTOROLA RHODEC
          846150482,   // MOTOROLA CHANNEL
          -1198092731, // MOTOROLA CYPRUS64
          -251277614,  // MOTOROLA HANOIP
          -2078385967, // MOTOROLA PSTAR
          -2073158771, // MOTOROLA VICKY
          1273004781   // MOTOROLA BLACKJACK
//        -1426053134  // REALME REE2ADL1
  };

  private static final int[] dualWhitelistByModel = new int[] {

  };

  private static final int[] LOW_SOC = {
          -1775228513, // EXYNOS 850
          802464304,  // EXYNOS 7872
          802464333,  // EXYNOS 7880
          802464302,  // EXYNOS 7870
          2067362118, // MSM8953
          2067362060, // MSM8937
          2067362084, // MSM8940
          2067362241, // MSM8992
          2067362117, // MSM8952
          2067361998, // MSM8917
          -1853602818 // SDM439
  };

  public final static int PERFORMANCE_CLASS_LOW = 0;
  public final static int PERFORMANCE_CLASS_AVERAGE = 1;
  public final static int PERFORMANCE_CLASS_HIGH = 2;

  public static int measureDevicePerformanceClass(Activity activity) {
    int androidVersion = Build.VERSION.SDK_INT;
    int cpuCount = Runtime.getRuntime().availableProcessors();
    int memoryClass = ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL != null) {
      int hash = Build.SOC_MODEL.toUpperCase().hashCode();
      for (int j : LOW_SOC) {
        if (j == hash) {
          return PERFORMANCE_CLASS_LOW;
        }
      }
    }

    int totalCpuFreq = 0;
    int freqResolved = 0;
    for (int i = 0; i < cpuCount; i++) {
      try {
        RandomAccessFile reader = new RandomAccessFile(String.format(Locale.ENGLISH, "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i), "r");
        String line = reader.readLine();
        if (line != null) {
          totalCpuFreq += Integer.parseInt(line) / 1000;
          freqResolved++;
        }
        reader.close();
      } catch (Throwable ignore) {}
    }
    int maxCpuFreq = freqResolved == 0 ? -1 : (int) Math.ceil(totalCpuFreq / (float) freqResolved);

    long ram = -1;
    try {
      ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
      ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memoryInfo);
      ram = memoryInfo.totalMem;
    } catch (Exception ignore) {}

    int performanceClass;
    if (
            androidVersion < 21 ||
                    cpuCount <= 2 ||
                    memoryClass <= 100 ||
                    cpuCount <= 4 && maxCpuFreq != -1 && maxCpuFreq <= 1250 ||
                    cpuCount <= 4 && maxCpuFreq <= 1600 && memoryClass <= 128 && androidVersion <= 21 ||
                    cpuCount <= 4 && maxCpuFreq <= 1300 && memoryClass <= 128 && androidVersion <= 24 ||
                    ram != -1 && ram < 2L * 1024L * 1024L * 1024L
    ) {
      performanceClass = PERFORMANCE_CLASS_LOW;
    } else if (
            cpuCount < 8 ||
                    memoryClass <= 160 ||
                    maxCpuFreq != -1 && maxCpuFreq <= 2055 ||
                    maxCpuFreq == -1 && cpuCount == 8 && androidVersion <= 23
    ) {
      performanceClass = PERFORMANCE_CLASS_AVERAGE;
    } else {
      performanceClass = PERFORMANCE_CLASS_HIGH;
    }
    Log.d("MULTICAM.PERFORMANCE", "device performance info selected_class = " + performanceClass + " (cpu_count = " + cpuCount + ", freq = " + maxCpuFreq + ", memoryClass = " + memoryClass + ", android version " + androidVersion + ", manufacture " + Build.MANUFACTURER + ")");

    return performanceClass;
  }
}
