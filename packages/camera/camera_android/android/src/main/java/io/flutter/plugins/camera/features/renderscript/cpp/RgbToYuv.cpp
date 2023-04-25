#include <cstdint>

#include "RenderScriptToolkit.h"
#include "TaskProcessor.h"
#include "Utils.h"

#define LOG_TAG "renderscript.toolkit.YuvToRgb"

static const uint32_t kRed8     = 0x000000ff;
static const uint32_t kGreen8   = 0x0000ff00;
static const uint32_t kBlue8    = 0x00ff0000;

#define R32(rgb)    static_cast<uint8_t>((((rgb) & kRed8)))
#define G32(rgb)    static_cast<uint8_t>((((rgb) & kGreen8) >> 8))
#define B32(rgb)    static_cast<uint8_t>(((rgb) & kBlue8) >> 16)

#define RGB2Y(r, g, b) (uint8_t)(((66 * (r) + 129 * (g) +  25 * (b) + 128) >> 8) +  16)
#define RGB2U(r, g, b) (uint8_t)(((-38 * (r) - 74 * (g) + 112 * (b) + 128) >> 8) + 128)
#define RGB2V(r, g, b) (uint8_t)(((112 * (r) - 94 * (g) -  18 * (b) + 128) >> 8) + 128)

namespace renderscript {

inline size_t roundUpTo16(size_t val) {
    return (val + 15u) & ~15u;
}

class RgbToYuvTask : public Task {
    uint8_t* mOut;
    size_t mCstep;
    size_t mSize;
    size_t mSizeX;
    size_t mSizeY;
    RenderScriptToolkit::YuvFormat mFormat;
    const uint32_t* mInput;

    void kernel(size_t pixelIndex, uint32_t xstart, size_t uIndex, size_t vIndex);
    // Process a 2D tile of the overall work. threadIndex identifies which thread does the work.
    void processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                     size_t endY) override;

   public:
    RgbToYuvTask(const uint32_t* input, uint8_t* output, size_t sizeX, size_t sizeY,
                 RenderScriptToolkit::YuvFormat format)
        : Task{sizeX, sizeY, 4, false, nullptr}, mOut{output} {
        mCstep = 1;
        mSize = sizeX * sizeY;
        mSizeX = sizeX;
        mSizeY = sizeY;
        mInput = input;
        mFormat = format;
    }
};

void RgbToYuvTask::processData(int /* threadIndex */, size_t startX, size_t startY, size_t endX,
                               size_t endY) {
    for (size_t x = startX; x < endX; x++) {
        for (size_t y = startY; y < endY; y++) {
            size_t pixelIndex = mSizeX * y + x;

            size_t uIndex = 0;
            size_t vIndex = 0;
            switch (mFormat) {
                case RenderScriptToolkit::YuvFormat::NV21:
                    uIndex = mSize + ((int(y / 2) * int((mSizeX + 1) / 2)) + (x / 2)) * 2;
                    vIndex = uIndex + 1;
                    break;
                case RenderScriptToolkit::YuvFormat::YV12:
                    uIndex = mSize + mSize / 4 + ((int(y / 2) * int((mSizeX + 1) / 2)) + (x / 2));
                    vIndex = mSize + ((int(y / 2) * int((mSizeX + 1) / 2)) + (x / 2));
                    break;
            }
            kernel(pixelIndex, x, uIndex, vIndex);
        }
    }
}

extern "C" void rsdIntrinsicYuv_K(void *dst, const uchar *Y, const uchar *uv, uint32_t xstart,
                                  size_t xend);
extern "C" void rsdIntrinsicYuvR_K(void *dst, const uchar *Y, const uchar *uv, uint32_t xstart,
                                   size_t xend);
extern "C" void rsdIntrinsicYuv2_K(void *dst, const uchar *Y, const uchar *u, const uchar *v,
                                   size_t xstart, size_t xend);

void RgbToYuvTask::kernel(size_t pixelIndex, uint32_t xstart, size_t uIndex, size_t vIndex) {

    auto pixel = mInput[pixelIndex];

    auto r = R32(pixel);
    auto g = G32(pixel);
    auto b = B32(pixel);

//    if (pixelIndex < 50) {
//        ALOGI("index: %d, rgbpixel %u, pinR %c, pinG %c, pinB %c, uvindex: %d", int(pixelIndex),
//              pixel, r, g, b, int(uvIndex));
//    }

    auto y = RGB2Y((int)r, (int)g, (int)b);
    auto u = RGB2U((int)r, (int)g, (int)b);
    auto v = RGB2V((int)r, (int)g, (int)b);

//    ALOGI("pinY %p, pinV %p, pinU %p", y, u, v);

    mOut[pixelIndex] = y;
    if (xstart % 2 == 0 && pixelIndex % 2 == 0) {
        mOut[uIndex] = u;
        mOut[vIndex] = v;
    }

}

void RenderScriptToolkit::rgbToYuv(const uint32_t* input, uint8_t* output, size_t sizeX,
                                   size_t sizeY, YuvFormat format) {
    RgbToYuvTask task(input, output, sizeX, sizeY, format);
    processor->doTask(&task);
}

}  // namespace renderscript
