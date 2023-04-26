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

#ifndef ANDROID_RENDERSCRIPT_TOOLKIT_TOOLKIT_H
#define ANDROID_RENDERSCRIPT_TOOLKIT_TOOLKIT_H

#include <cstdint>
#include <memory>

namespace renderscript {

class TaskProcessor;

/**
 * Define a range of data to process.
 *
 * This class is used to restrict a Toolkit operation to a rectangular subset of the input
 * tensor.
 *
 * @property startX The index of the first value to be included on the X axis.
 * @property endX The index after the last value to be included on the X axis.
 * @property startY The index of the first value to be included on the Y axis.
 * @property endY The index after the last value to be included on the Y axis.
 */
struct Restriction {
    size_t startX;
    size_t endX;
    size_t startY;
    size_t endY;
};

/**
 * A collection of high-performance graphic utility functions like blur and blend.
 *
 * This toolkit provides ten image manipulation functions: blend, blur, color matrix, convolve,
 * histogram, histogramDot, lut, lut3d, resize, and YUV to RGB. These functions execute
 * multithreaded on the CPU.
 *
 * These functions work over raw byte arrays. You'll need to specify the width and height of
 * the data to be processed, as well as the number of bytes per pixel. For most use cases,
 * this will be 4.
 *
 * You should instantiate the Toolkit once and reuse it throughout your application.
 * On instantiation, the Toolkit creates a thread pool that's used for processing all the functions.
 * You can limit the number of pool threads used by the Toolkit via the constructor. The pool
 * threads are destroyed once the Toolkit is destroyed, after any pending work is done.
 *
 * This library is thread safe. You can call methods from different pool threads. The functions will
 * execute sequentially.
 *
 * A Java/Kotlin Toolkit is available. It calls this library through JNI.
 *
 * This toolkit can be used as a replacement for most RenderScript Intrinsic functions. Compared
 * to RenderScript, it's simpler to use and more than twice as fast on the CPU. However RenderScript
 * Intrinsics allow more flexibility for the type of allocation supported. In particular, this
 * toolkit does not support allocations of floats.
 */
class RenderScriptToolkit {
    /** Each Toolkit method call is converted to a Task. The processor owns the thread pool. It
     * tiles the tasks and schedule them over the pool threads.
     */
    std::unique_ptr<TaskProcessor> processor;

   public:
    /**
     * Creates the pool threads that are used for processing the method calls.
     */
    RenderScriptToolkit(int numberOfThreads = 0);
    /**
     * Destroys the thread pool. This stops any in-progress work; the Toolkit methods called from
     * other pool threads will return without having completed the work. Because of the undefined
     * state of the output buffers, an application should avoid destroying the Toolkit if other pool
     * threads are executing Toolkit methods.
     */
    ~RenderScriptToolkit();

    /**
     * The YUV formats supported by yuvToRgb.
     */
    enum class YuvFormat {
        NV21 = 0x11,
        YV12 = 0x32315659,
    };

    /**
     * Convert an image from YUV to RGB.
     *
     * Converts an Android YUV buffer to RGB. The input allocation should be
     * supplied in a supported YUV format as a YUV cell Allocation.
     * The output is RGBA; the alpha channel will be set to 255.
     *
     * Note that for YV12 and a sizeX that's not a multiple of 32, the
     * RenderScript Intrinsic may not have converted the image correctly.
     * This Toolkit method should.
     *
     * @param in The buffer of the image to be converted.
     * @param out The buffer that receives the converted image.
     * @param sizeX The width in pixels of the image. Must be even.
     * @param sizeY The height in pixels of the image.
     * @param format Either YV12 or NV21.
     */
    void yuvToRgb(const uint8_t* _Nonnull in, uint8_t* _Nonnull out, size_t sizeX, size_t sizeY,
                  YuvFormat format);

    /**
     * Convert an image from YUV to RGB.
     *
     * Converts an Android YUV buffer to RGB. The input allocation should be
     * supplied in a supported YUV format as a YUV cell Allocation.
     * The output is RGBA; the alpha channel will be set to 255.
     *
     * Note that for YV12 and a sizeX that's not a multiple of 32, the
     * RenderScript Intrinsic may not have converted the image correctly.
     * This Toolkit method should.
     *
     * @param in The array of the image pixels to be converted.
     * @param out The buffer that receives the converted image.
     * @param sizeX The width in pixels of the image. Must be even.
     * @param sizeY The height in pixels of the image.
     * @param format Either YV12 or NV21.
     */
    void rgbToYuv(const uint32_t* _Nonnull in, uint8_t* _Nonnull out, size_t sizeX, size_t sizeY,
                  YuvFormat format);
};

}  // namespace renderscript

#endif  // ANDROID_RENDERSCRIPT_TOOLKIT_TOOLKIT_H
