/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android/bitmap.h>
#include <cassert>
#include <jni.h>

#include "RenderScriptToolkit.h"
#include "Utils.h"

#define LOG_TAG "renderscript.toolkit.JniEntryPoints"

using namespace renderscript;

/**
 * I compared using env->GetPrimitiveArrayCritical vs. env->GetByteArrayElements to get access
 * to the underlying data. On Pixel 4, it's actually faster to not use critical. The code is left
 * here if you want to experiment. Note that USE_CRITICAL could block the garbage collector.
 */
// #define USE_CRITICAL

class ByteArrayGuard {
   private:
    JNIEnv* env;
    jbyteArray array;
    jbyte* data;

   public:
    ByteArrayGuard(JNIEnv* env, jbyteArray array) : env{env}, array{array} {
#ifdef USE_CRITICAL
        data = reinterpret_cast<jbyte*>(env->GetPrimitiveArrayCritical(array, nullptr));
#else
        data = env->GetByteArrayElements(array, nullptr);
#endif
    }
    ~ByteArrayGuard() {
#ifdef USE_CRITICAL
        env->ReleasePrimitiveArrayCritical(array, data, 0);
#else
        env->ReleaseByteArrayElements(array, data, 0);
#endif
    }
    uint8_t* get() { return reinterpret_cast<uint8_t*>(data); }
};

class IntArrayGuard {
   private:
    JNIEnv* env;
    jintArray array;
    jint* data;

   public:
    IntArrayGuard(JNIEnv* env, jintArray array) : env{env}, array{array} {
#ifdef USE_CRITICAL
        data = reinterpret_cast<jint*>(env->GetPrimitiveArrayCritical(array, nullptr));
#else
        data = env->GetIntArrayElements(array, nullptr);
#endif
    }
    ~IntArrayGuard() {
#ifdef USE_CRITICAL
        env->ReleasePrimitiveArrayCritical(array, data, 0);
#else
        env->ReleaseIntArrayElements(array, data, 0);
#endif
    }
    int* get() { return reinterpret_cast<int*>(data); }
};

class BitmapGuard {
   private:
    JNIEnv* env;
    jobject bitmap;
    AndroidBitmapInfo info;
    int bytesPerPixel;
    void* bytes;
    bool valid;

   public:
    BitmapGuard(JNIEnv* env, jobject jBitmap) : env{env}, bitmap{jBitmap}, bytes{nullptr} {
        valid = false;
        if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
            ALOGE("AndroidBitmap_getInfo failed");
            return;
        }
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 &&
            info.format != ANDROID_BITMAP_FORMAT_A_8) {
            ALOGE("AndroidBitmap in the wrong format");
            return;
        }
        bytesPerPixel = info.stride / info.width;
        if (bytesPerPixel != 1 && bytesPerPixel != 4) {
            ALOGE("Expected a vector size of 1 or 4. Got %d. Extra padding per line not currently "
                  "supported",
                  bytesPerPixel);
            return;
        }
        if (AndroidBitmap_lockPixels(env, bitmap, &bytes) != ANDROID_BITMAP_RESULT_SUCCESS) {
            ALOGE("AndroidBitmap_lockPixels failed");
            return;
        }
        valid = true;
    }
    ~BitmapGuard() {
        if (valid) {
            AndroidBitmap_unlockPixels(env, bitmap);
        }
    }
    uint8_t* get() const {
        assert(valid);
        return reinterpret_cast<uint8_t*>(bytes);
    }
    int width() const { return info.width; }
    int height() const { return info.height; }
};

extern "C" JNIEXPORT jlong JNICALL
Java_io_flutter_plugins_camera_features_renderscript_Toolkit_createNative(JNIEnv* /*env*/, jobject /*thiz*/) {
    return reinterpret_cast<jlong>(new RenderScriptToolkit());
}

extern "C" JNIEXPORT void JNICALL Java_io_flutter_plugins_camera_features_renderscript_Toolkit_destroyNative(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong native_handle) {
    auto* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    delete toolkit;
}

extern "C" JNIEXPORT void JNICALL Java_io_flutter_plugins_camera_features_renderscript_Toolkit_nativeYuvToRgb(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jbyteArray input_array,
        jbyteArray output_array, jint size_x, jint size_y, jint format) {
    auto* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    ByteArrayGuard input{env, input_array};
    ByteArrayGuard output{env, output_array};

    toolkit->yuvToRgb(input.get(), output.get(), size_x, size_y,
                      static_cast<RenderScriptToolkit::YuvFormat>(format));
}

extern "C" JNIEXPORT void JNICALL Java_io_flutter_plugins_camera_features_renderscript_Toolkit_nativeYuvToRgbBitmap(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jbyteArray input_array, jint size_x,
        jint size_y, jobject output_bitmap, jint format) {
    auto* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    BitmapGuard output{env, output_bitmap};
    ByteArrayGuard input{env, input_array};

    toolkit->yuvToRgb(input.get(), output.get(), size_x, size_y,
                      static_cast<RenderScriptToolkit::YuvFormat>(format));
}

extern "C" JNIEXPORT void JNICALL Java_io_flutter_plugins_camera_features_renderscript_Toolkit_nativeRgbToYuv(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jintArray input_array,
        jbyteArray output_array, jint size_x, jint size_y, jint format) {
    auto* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    IntArrayGuard input{env, input_array};
    ByteArrayGuard output{env, output_array};

    toolkit->rgbToYuv(reinterpret_cast<const uint32_t *>(input.get()), output.get(), size_x, size_y,
                      static_cast<RenderScriptToolkit::YuvFormat>(format));
}
