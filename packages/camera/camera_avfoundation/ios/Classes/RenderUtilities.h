//
//  RenderUtilities.h
//  Pods
//
//  Created by Ilya Lazutin on 01.04.2023.
//

#import <Foundation/Foundation.h>
#import <CoreImage/CoreImage.h>

#ifndef RenderUtilities_h
#define RenderUtilities_h

@interface RenderUtilities : NSObject

- (CVPixelBufferRef)progressPixelBuffer:(CVPixelBufferRef)pixelBuffer;
- (CVPixelBufferRef)crop:(CVPixelBufferRef)pixelBuffer
                cropRect:(CGRect)cropRect;
- (CVPixelBufferRef)crop:(CVPixelBufferRef)pixelBuffer
                cropRect:(CGRect)cropRect
               scaleSize:(CGSize)scaleSize;
- (CVPixelBufferRef)scale:(CVPixelBufferRef)pixelBuffer
                   toSize:(CGSize)scaleSize;
- (CVPixelBufferRef)createCroppedPixelBuffer:(CVPixelBufferRef)sourcePixelBuffer
                                croppingRect:(CGRect)croppingRect;

@end

#endif /* RenderUtilities_h */
