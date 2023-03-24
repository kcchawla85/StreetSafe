

// This is a collection of routines which converts various YUV image formats
// to (A)RGB.

#ifndef TENSORFLOW_EXAMPLES_ANDROID_JNI_YUV2RGB_H_
#define TENSORFLOW_EXAMPLES_ANDROID_JNI_YUV2RGB_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void ConvertYUV420ToARGB8888(const uint8_t* const yData,
                             const uint8_t* const uData,
                             const uint8_t* const vData, uint32_t* const output,
                             const int width, const int height,
                             const int y_row_stride, const int uv_row_stride,
                             const int uv_pixel_stride);

// Converts YUV420 semi-planar data to ARGB 8888 data using the supplied width
// and height. The input and output must already be allocated and non-null.
// For efficiency, no error checking is performed.
void ConvertYUV420SPToARGB8888(const uint8_t* const pY,
                               const uint8_t* const pUV, uint32_t* const output,
                               const int width, const int height);

// The same as above, but downsamples each dimension to half size.
void ConvertYUV420SPToARGB8888HalfSize(const uint8_t* const input,
                                       uint32_t* const output, int width,
                                       int height);

// Converts YUV420 semi-planar data to RGB 565 data using the supplied width
// and height. The input and output must already be allocated and non-null.
// For efficiency, no error checking is performed.
void ConvertYUV420SPToRGB565(const uint8_t* const input, uint16_t* const output,
                             const int width, const int height);

#ifdef __cplusplus
}
#endif

#endif  // TENSORFLOW_EXAMPLES_ANDROID_JNI_YUV2RGB_H_
