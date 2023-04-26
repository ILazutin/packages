/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include <stdint.h>
#include <x86intrin.h>

namespace renderscript {

/* Unsigned extend packed 8-bit integer (in LBS) into packed 32-bit integer */
static inline __m128i cvtepu8_epi32(__m128i x) {
#if defined(__SSE4_1__)
    return _mm_cvtepu8_epi32(x);
#elif defined(__SSSE3__)
    const __m128i M8to32 = _mm_set_epi32(0xffffff03, 0xffffff02, 0xffffff01, 0xffffff00);
    x = _mm_shuffle_epi8(x, M8to32);
    return x;
#else
#   error "Require at least SSSE3"
#endif
}

static inline __m128i packus_epi32(__m128i lo, __m128i hi) {
#if defined(__SSE4_1__)
    return _mm_packus_epi32(lo, hi);
#elif defined(__SSSE3__)
    const __m128i C0 = _mm_set_epi32(0x0000, 0x0000, 0x0000, 0x0000);
    const __m128i C1 = _mm_set_epi32(0xffff, 0xffff, 0xffff, 0xffff);
    const __m128i M32to16L = _mm_set_epi32(0xffffffff, 0xffffffff, 0x0d0c0908, 0x05040100);
    const __m128i M32to16H = _mm_set_epi32(0x0d0c0908, 0x05040100, 0xffffffff, 0xffffffff);
    lo = _mm_and_si128(lo, _mm_cmpgt_epi32(lo, C0));
    lo = _mm_or_si128(lo, _mm_cmpgt_epi32(lo, C1));
    hi = _mm_and_si128(hi, _mm_cmpgt_epi32(hi, C0));
    hi = _mm_or_si128(hi, _mm_cmpgt_epi32(hi, C1));
    return _mm_or_si128(_mm_shuffle_epi8(lo, M32to16L),
                        _mm_shuffle_epi8(hi, M32to16H));
#else
#   error "Require at least SSSE3"
#endif
}

static inline __m128i mullo_epi32(__m128i x, __m128i y) {
#if defined(__SSE4_1__)
    return _mm_mullo_epi32(x, y);
#elif defined(__SSSE3__)
    const __m128i Meven = _mm_set_epi32(0x00000000, 0xffffffff, 0x00000000, 0xffffffff);
    __m128i even = _mm_mul_epu32(x, y);
    __m128i odd = _mm_mul_epu32(_mm_srli_si128(x, 4),
                                _mm_srli_si128(y, 4));
    even = _mm_and_si128(even, Meven);
    odd = _mm_and_si128(odd, Meven);
    return _mm_or_si128(even, _mm_slli_si128(odd, 4));
#else
#   error "Require at least SSSE3"
#endif
}

/* 'mask' must packed 8-bit of 0x00 or 0xff */
static inline __m128i blendv_epi8(__m128i x, __m128i y, __m128i mask) {
#if defined(__SSE4_1__)
    return _mm_blendv_epi8(x, y, mask);
#elif defined(__SSSE3__)
    return _mm_or_si128(_mm_andnot_si128(mask, x), _mm_and_si128(y, mask));
#else
#   error "Require at least SSSE3"
#endif
}

void rsdIntrinsicYuv_K(void *dst,
                       const unsigned char *pY, const unsigned char *pUV,
                       uint32_t count, const short *param) {
    __m128i biasY, biasUV;
    __m128i c0, c1, c2, c3, c4;

    biasY = _mm_set1_epi32(param[8]);   /*  16 */
    biasUV = _mm_set1_epi32(param[16]); /* 128 */

    c0 = _mm_set1_epi32(param[0]);  /*  298 */
    c1 = _mm_set1_epi32(param[1]);  /*  409 */
    c2 = _mm_set1_epi32(param[2]);  /* -100 */
    c3 = _mm_set1_epi32(param[3]);  /*  516 */
    c4 = _mm_set1_epi32(param[4]);  /* -208 */

    __m128i Y, UV, U, V, R, G, B, A;

    A = _mm_set1_epi32(255);
    uint32_t i;

    for (i = 0; i < (count << 1); ++i) {
        Y = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pY));
        UV = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pUV));

        Y = _mm_sub_epi32(Y, biasY);
        UV = _mm_sub_epi32(UV, biasUV);

        U = _mm_shuffle_epi32(UV, 0xf5);
        V = _mm_shuffle_epi32(UV, 0xa0);

        Y = mullo_epi32(Y, c0);

        R = _mm_add_epi32(Y, mullo_epi32(V, c1));
        R = _mm_add_epi32(R, biasUV);
        R = _mm_srai_epi32(R, 8);

        G = _mm_add_epi32(Y, mullo_epi32(U, c2));
        G = _mm_add_epi32(G, mullo_epi32(V, c4));
        G = _mm_add_epi32(G, biasUV);
        G = _mm_srai_epi32(G, 8);

        B = _mm_add_epi32(Y, mullo_epi32(U, c3));
        B = _mm_add_epi32(B, biasUV);
        B = _mm_srai_epi32(B, 8);

        __m128i y1, y2, y3, y4;

        y1 = packus_epi32(R, G);
        y2 = packus_epi32(B, A);
        y3 = _mm_packus_epi16(y1, y2);
        const __m128i T4x4 = _mm_set_epi8(15, 11, 7, 3,
                                          14, 10, 6, 2,
                                          13,  9, 5, 1,
                                          12,  8, 4, 0);
        y4 = _mm_shuffle_epi8(y3, T4x4);
        _mm_storeu_si128((__m128i *)dst, y4);
        pY += 4;
        pUV += 4;
        dst = (__m128i *)dst + 1;
    }
}

void rsdIntrinsicYuvR_K(void *dst,
                       const unsigned char *pY, const unsigned char *pUV,
                       uint32_t count, const short *param) {
    __m128i biasY, biasUV;
    __m128i c0, c1, c2, c3, c4;

    biasY = _mm_set1_epi32(param[8]);   /*  16 */
    biasUV = _mm_set1_epi32(param[16]); /* 128 */

    c0 = _mm_set1_epi32(param[0]);  /*  298 */
    c1 = _mm_set1_epi32(param[1]);  /*  409 */
    c2 = _mm_set1_epi32(param[2]);  /* -100 */
    c3 = _mm_set1_epi32(param[3]);  /*  516 */
    c4 = _mm_set1_epi32(param[4]);  /* -208 */

    __m128i Y, UV, U, V, R, G, B, A;

    A = _mm_set1_epi32(255);
    uint32_t i;

    for (i = 0; i < (count << 1); ++i) {
        Y = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pY));
        UV = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pUV));

        Y = _mm_sub_epi32(Y, biasY);
        UV = _mm_sub_epi32(UV, biasUV);

        V = _mm_shuffle_epi32(UV, 0xf5);
        U = _mm_shuffle_epi32(UV, 0xa0);

        Y = mullo_epi32(Y, c0);

        R = _mm_add_epi32(Y, mullo_epi32(V, c1));
        R = _mm_add_epi32(R, biasUV);
        R = _mm_srai_epi32(R, 8);

        G = _mm_add_epi32(Y, mullo_epi32(U, c2));
        G = _mm_add_epi32(G, mullo_epi32(V, c4));
        G = _mm_add_epi32(G, biasUV);
        G = _mm_srai_epi32(G, 8);

        B = _mm_add_epi32(Y, mullo_epi32(U, c3));
        B = _mm_add_epi32(B, biasUV);
        B = _mm_srai_epi32(B, 8);

        __m128i y1, y2, y3, y4;

        y1 = packus_epi32(R, G);
        y2 = packus_epi32(B, A);
        y3 = _mm_packus_epi16(y1, y2);
        const __m128i T4x4 = _mm_set_epi8(15, 11, 7, 3,
                                          14, 10, 6, 2,
                                          13,  9, 5, 1,
                                          12,  8, 4, 0);
        y4 = _mm_shuffle_epi8(y3, T4x4);
        _mm_storeu_si128((__m128i *)dst, y4);
        pY += 4;
        pUV += 4;
        dst = (__m128i *)dst + 1;
    }
}

void rsdIntrinsicYuv2_K(void *dst,
                       const unsigned char *pY, const unsigned char *pU,
                       const unsigned char *pV, uint32_t count, const short *param) {
    __m128i biasY, biasUV;
    __m128i c0, c1, c2, c3, c4;

    biasY = _mm_set1_epi32(param[8]);   /*  16 */
    biasUV = _mm_set1_epi32(param[16]); /* 128 */

    c0 = _mm_set1_epi32(param[0]);  /*  298 */
    c1 = _mm_set1_epi32(param[1]);  /*  409 */
    c2 = _mm_set1_epi32(param[2]);  /* -100 */
    c3 = _mm_set1_epi32(param[3]);  /*  516 */
    c4 = _mm_set1_epi32(param[4]);  /* -208 */

    __m128i Y, U, V, R, G, B, A;

    A = _mm_set1_epi32(255);
    uint32_t i;

    for (i = 0; i < (count << 1); ++i) {
        Y = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pY));
        U = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pU));
		V = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pV));

        Y = _mm_sub_epi32(Y, biasY);
        U = _mm_sub_epi32(U, biasUV);
		V = _mm_sub_epi32(V, biasUV);

        Y = mullo_epi32(Y, c0);

        R = _mm_add_epi32(Y, mullo_epi32(V, c1));
        R = _mm_add_epi32(R, biasUV);
        R = _mm_srai_epi32(R, 8);

        G = _mm_add_epi32(Y, mullo_epi32(U, c2));
        G = _mm_add_epi32(G, mullo_epi32(V, c4));
        G = _mm_add_epi32(G, biasUV);
        G = _mm_srai_epi32(G, 8);

        B = _mm_add_epi32(Y, mullo_epi32(U, c3));
        B = _mm_add_epi32(B, biasUV);
        B = _mm_srai_epi32(B, 8);

        __m128i y1, y2, y3, y4;

        y1 = packus_epi32(R, G);
        y2 = packus_epi32(B, A);
        y3 = _mm_packus_epi16(y1, y2);
        const __m128i T4x4 = _mm_set_epi8(15, 11, 7, 3,
                                          14, 10, 6, 2,
                                          13,  9, 5, 1,
                                          12,  8, 4, 0);
        y4 = _mm_shuffle_epi8(y3, T4x4);
        _mm_storeu_si128((__m128i *)dst, y4);
        pY += 4;
        pU += 4;
		pV += 4;
        dst = (__m128i *)dst + 1;
    }
}

}  // namespace renderscript
