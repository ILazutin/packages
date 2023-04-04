//
//  RenderUtilities.m
//  camera_avfoundation
//
//  Created by Ilya Lazutin on 01.04.2023.
//

#import <Foundation/Foundation.h>
#import "RenderUtilities.h"

@import Accelerate;

#define RETAINED_BUFFER_COUNT 6

@interface RenderUtilities (){
    CIContext *_ciContext;
    CGColorSpaceRef _rgbColorSpace;
    CIFilter *ResizeFilter;
}

@end

@implementation RenderUtilities

- (void)dealloc
{
    [self deleteBuffers];
}

- (instancetype)init
{
  self = [super init];
  if (self) {
    _rgbColorSpace = CGColorSpaceCreateDeviceRGB();
    _ciContext = [[CIContext alloc] init];
    ResizeFilter = [CIFilter filterWithName:@"CILanczosScaleTransform"];
  }
  return self;
}

- (CVPixelBufferRef)scale:(CVPixelBufferRef)pixelBuffer
                   toSize:(CGSize)scaleSize {
  
  CIImage *inputCIImage = [CIImage imageWithCVPixelBuffer:pixelBuffer];
  
  CGFloat scale = CGRectGetHeight(inputCIImage.extent) / scaleSize.height;
  CGFloat aspectRatio = scaleSize.width / (inputCIImage.extent.size.width * scale);
  
  [ResizeFilter setValue:inputCIImage forKey:kCIInputImageKey];
  [ResizeFilter setValue:[NSNumber numberWithDouble:scale] forKey:kCIInputScaleKey];
  [ResizeFilter setValue:[NSNumber numberWithDouble:aspectRatio] forKey:kCIInputAspectRatioKey];
  
  CIImage *outputCIImage = [ResizeFilter outputImage];
  
  // render the filtered image out to a pixel buffer (no locking needed as CIContext's render method will do that)
  [_ciContext render:outputCIImage toCVPixelBuffer:pixelBuffer bounds:[outputCIImage extent] colorSpace:_rgbColorSpace];
  
  inputCIImage = nil;
  outputCIImage = nil;
  [_ciContext clearCaches];
  
  return pixelBuffer;
}

- (void)deleteBuffers
{
    if ( _ciContext ) {
        _ciContext = nil;
    }
    if ( _rgbColorSpace ) {
        CFRelease( _rgbColorSpace );
        _rgbColorSpace = NULL;
    }
}

@end
