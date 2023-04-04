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

- (CVPixelBufferRef)scale:(CVPixelBufferRef)pixelBuffer
                   toSize:(CGSize)scaleSize;

@end

#endif /* RenderUtilities_h */
