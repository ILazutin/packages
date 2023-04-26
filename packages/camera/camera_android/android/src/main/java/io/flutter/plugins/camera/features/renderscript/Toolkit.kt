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

package io.flutter.plugins.camera.features.renderscript

import android.graphics.Bitmap

// This string is used for error messages.
private const val externalName = "RenderScript Toolkit"

/**
 * A collection of high-performance graphic utility functions like blur and blend.
 *
 * This toolkit provides ten image manipulation functions: blend, blur, color matrix, convolve,
 * histogram, histogramDot, lut, lut3d, resize, and YUV to RGB. These functions execute
 * multithreaded on the CPU.
 *
 * Most of the functions have two variants: one that manipulates Bitmaps, the other ByteArrays.
 * For ByteArrays, you need to specify the width and height of the data to be processed, as
 * well as the number of bytes per pixel. For most use cases, this will be 4.
 *
 * The Toolkit creates a thread pool that's used for processing the functions. The threads live
 * for the duration of the application. They can be destroyed by calling the method shutdown().
 *
 * This library is thread safe. You can call methods from different poolThreads. The functions will
 * execute sequentially.
 *
 * A native C++ version of this Toolkit is available. Check the RenderScriptToolkit.h file in the
 * cpp directory.
 *
 * This toolkit can be used as a replacement for most RenderScript Intrinsic functions. Compared
 * to RenderScript, it's simpler to use and more than twice as fast on the CPU. However RenderScript
 * Intrinsics allow more flexibility for the type of allocation supported. In particular, this
 * toolkit does not support allocations of floats.
 */
object Toolkit {
    /**
     * Convert an image from YUV to RGB.
     *
     * Converts a YUV buffer to RGB. The input array should be supplied in a supported YUV format.
     * The output is RGBA; the alpha channel will be set to 255.
     *
     * Note that for YV12 and a sizeX that's not a multiple of 32, the RenderScript Intrinsic may
     * not have converted the image correctly. This Toolkit method should.
     *
     * @param inputArray The buffer of the image to be converted.
     * @param sizeX The width in pixels of the image.
     * @param sizeY The height in pixels of the image.
     * @param format Either YV12 or NV21.
     * @return The converted image as a byte array.
     */
    fun yuvToRgb(inputArray: ByteArray, sizeX: Int, sizeY: Int, format: YuvFormat): ByteArray {
        require(sizeX % 2 == 0 && sizeY % 2 == 0) {
            "$externalName yuvToRgb. Non-even dimensions are not supported. " +
                    "$sizeX and $sizeY were provided."
        }

        val outputArray = ByteArray(sizeX * sizeY * 4)
        nativeYuvToRgb(nativeHandle, inputArray, outputArray, sizeX, sizeY, format.value)
        return outputArray
    }

    /**
     * Convert an image from YUV to an RGB Bitmap.
     *
     * Converts a YUV buffer to an RGB Bitmap. The input array should be supplied in a supported
     * YUV format. The output is RGBA; the alpha channel will be set to 255.
     *
     * Note that for YV12 and a sizeX that's not a multiple of 32, the RenderScript Intrinsic may
     * not have converted the image correctly. This Toolkit method should.
     *
     * @param inputArray The buffer of the image to be converted.
     * @param sizeX The width in pixels of the image.
     * @param sizeY The height in pixels of the image.
     * @param format Either YV12 or NV21.
     * @return The converted image.
     */
    fun yuvToRgbBitmap(inputArray: ByteArray, sizeX: Int, sizeY: Int, format: YuvFormat): Bitmap {
        require(sizeX % 2 == 0 && sizeY % 2 == 0) {
            "$externalName yuvToRgbBitmap. Non-even dimensions are not supported. " +
                    "$sizeX and $sizeY were provided."
        }

        val outputBitmap = Bitmap.createBitmap(sizeX, sizeY, Bitmap.Config.ARGB_8888)
        nativeYuvToRgbBitmap(nativeHandle, inputArray, sizeX, sizeY, outputBitmap, format.value)
        return outputBitmap
    }

    fun rgbToYuv(inputArray: IntArray, sizeX: Int, sizeY: Int, format: YuvFormat): ByteArray {
        require(sizeX % 2 == 0 && sizeY % 2 == 0) {
            "$externalName rgbToYuv. Non-even dimensions are not supported. " +
                    "$sizeX and $sizeY were provided."
        }

        val outputArray = ByteArray(sizeX * sizeY * 3 / 2)
        nativeRgbToYuv(nativeHandle, inputArray, outputArray, sizeX, sizeY, format.value)
        return outputArray
    }

    private var nativeHandle: Long = 0

    init {
        System.loadLibrary("renderscript-toolkit")
        nativeHandle = createNative()
    }

    /**
     * Shutdown the thread pool.
     *
     * Waits for the threads to complete their work and destroys them.
     *
     * An application should call this method only if it is sure that it won't call the
     * toolkit again, as it is irreversible.
     */
    fun shutdown() {
        destroyNative(nativeHandle)
        nativeHandle = 0
    }

    private external fun createNative(): Long

    private external fun destroyNative(nativeHandle: Long)

    private external fun nativeYuvToRgb(
        nativeHandle: Long,
        inputArray: ByteArray,
        outputArray: ByteArray,
        sizeX: Int,
        sizeY: Int,
        format: Int
    )

    private external fun nativeYuvToRgbBitmap(
        nativeHandle: Long,
        inputArray: ByteArray,
        sizeX: Int,
        sizeY: Int,
        outputBitmap: Bitmap,
        value: Int
    )

    private external fun nativeRgbToYuv(
        nativeHandle: Long,
        inputArray: IntArray,
        outputArray: ByteArray,
        sizeX: Int,
        sizeY: Int,
        format: Int
    )
}

/**
 * The YUV formats supported by yuvToRgb.
 */
enum class YuvFormat(val value: Int) {
    NV21(0x11),
    YV12(0x32315659),
}
