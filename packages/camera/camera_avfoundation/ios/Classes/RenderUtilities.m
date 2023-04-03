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
    CVPixelBufferPoolRef _bufferPool;
    CFDictionaryRef _bufferPoolAuxAttributes;
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
//    EAGLContext *eaglContext = [[EAGLContext alloc] initWithAPI:kEAGLRenderingAPIOpenGLES2];
//    _ciContext = [CIContext contextWithEAGLContext:eaglContext options:@{kCIContextWorkingColorSpace : [NSNull null]}];
    _ciContext = [[CIContext alloc] init];
    ResizeFilter = [CIFilter filterWithName:@"CILanczosScaleTransform"];
  }
  return self;
}

- (CVPixelBufferRef)progressPixelBuffer:(CVPixelBufferRef)pixelBuffer
{
  return pixelBuffer;
//    if (!_bufferPool) {
//        [self initializeBuffersWithPixelBuffer:pixelBuffer];
//    }
//
//    OSStatus err = noErr;
//    CVPixelBufferRef renderedOutputPixelBuffer = NULL;
//
//    err = CVPixelBufferPoolCreatePixelBuffer( kCFAllocatorDefault, _bufferPool, &renderedOutputPixelBuffer );
//    if ( err ) {
//        NSLog(@"Cannot obtain a pixel buffer from the buffer pool (%d)", (int)err );
//        return NULL;
//    }
//
//    CIImage *inputCIImage = [CIImage imageWithCVPixelBuffer:pixelBuffer];
////    [MonoFilter setValue:inputCIImage forKeyPath:@"inputImage"];
////    CIImage *outputCIImage = [MonoFilter outputImage];
//
//    // render the filtered image out to a pixel buffer (no locking needed as CIContext's render method will do that)
//    [_ciContext render:outputCIImage toCVPixelBuffer:renderedOutputPixelBuffer bounds:[outputCIImage extent] colorSpace:_rgbColorSpace];
//
//    return renderedOutputPixelBuffer;
}

- (CVPixelBufferRef)crop:(CVPixelBufferRef)pixelBuffer
                cropRect:(CGRect)cropRect {
//  return pixelBuffer;
    if (!_bufferPool) {
        [self initializeBuffersWithPixelBuffer:pixelBuffer];
    }
    
    OSStatus err = noErr;
    CVPixelBufferRef renderedOutputPixelBuffer = NULL;
    
    err = CVPixelBufferPoolCreatePixelBuffer( kCFAllocatorDefault, _bufferPool, &renderedOutputPixelBuffer );
    if ( err ) {
        NSLog(@"Cannot obtain a pixel buffer from the buffer pool (%d)", (int)err );
        return NULL;
    }
    
    CIImage *inputCIImage = [CIImage imageWithCVPixelBuffer:pixelBuffer];
//    [MonoFilter setValue:inputCIImage forKeyPath:@"inputImage"];
//    CIImage *outputCIImage = [MonoFilter outputImage];
  CIFilter *cropFilter = [CIFilter filterWithName:@"CICrop"];
  CIVector *cropVector =[CIVector vectorWithX:cropRect.origin.x Y:cropRect.origin.y Z: cropRect.size.height W: cropRect.size.width];

  [cropFilter setValue:inputCIImage forKey:@"inputImage"];
  [cropFilter setValue:cropVector forKey:@"inputRectangle"];

  CIImage *outputCIImage = [cropFilter valueForKey:@"outputImage"];

//  CIImage *outputCIImage = [inputCIImage imageByCroppingToRect:cropRect];
//  outputCIImage = [outputCIImage imageByApplyingTransform:CGAffineTransformMakeTranslation(-cropRect.origin.x, -cropRect.origin.y)];
  
 
//  CGAffineTransform transformFilter = [CGAffineTransform new];
//  var affineTransform = CGAffineTransform.MakeTranslation (-150, 150);
//  transformFilter.Transform = affineTransform;
//  transformFilter.Image = croppedImaged;
//  CIImage transformedImage = transformFilter.OutputImage;
  
//  CIFilter* transform = [CIFilter filterWithName:@"CIAffineTransform"];
//  CGAffineTransform *affineTransform = CGAffineTransform;
//  [affineTransform translateXBy:-150.0 yBy:-150.0];
//  [transform setValue:affineTransform forKey:@"inputTransform"];
//  [transform setValue:croppedImage forKey:@"inputImage"];
//  CIImage* transformedImage = [transform valueForKey:@"outputImage"];
    
    // render the filtered image out to a pixel buffer (no locking needed as CIContext's render method will do that)
    [_ciContext render:outputCIImage toCVPixelBuffer:renderedOutputPixelBuffer bounds:[outputCIImage extent] colorSpace:_rgbColorSpace];
  
  inputCIImage = nil;
  outputCIImage = nil;
  
    return renderedOutputPixelBuffer;
}

- (CVPixelBufferRef)crop:(CVPixelBufferRef)pixelBuffer
                cropRect:(CGRect)cropRect
               scaleSize:(CGSize)scaleSize {
//  return pixelBuffer;
    if (!_bufferPool) {
        [self initializeBuffersWithPixelBuffer:pixelBuffer];
    }
    
    OSStatus err = noErr;
    CVPixelBufferRef renderedOutputPixelBuffer = NULL;
    
    err = CVPixelBufferPoolCreatePixelBuffer( kCFAllocatorDefault, _bufferPool, &renderedOutputPixelBuffer );
    if ( err ) {
        NSLog(@"Cannot obtain a pixel buffer from the buffer pool (%d)", (int)err );
        return NULL;
    }
    
    CIImage *inputCIImage = [CIImage imageWithCVPixelBuffer:pixelBuffer];
//    [MonoFilter setValue:inputCIImage forKeyPath:@"inputImage"];
//    CIImage *outputCIImage = [MonoFilter outputImage];
//  CIFilter *cropFilter = [CIFilter filterWithName:@"CICrop"];
//  CIVector *cropVector =[CIVector vectorWithX:cropRect.origin.x Y:cropRect.origin.y Z: cropRect.size.height W: cropRect.size.width];
//
//  [cropFilter setValue:inputCIImage forKey:@"inputImage"];
//  [cropFilter setValue:cropVector forKey:@"inputRectangle"];
//
//  CIImage *outputCIImage = [cropFilter valueForKey:@"outputImage"];

//  CGFloat scaleX = scaleSize.width / CGRectGetWidth(outputCIImage.extent);
//  CGFloat scaleY = scaleSize.height / CGRectGetHeight(outputCIImage.extent);
  
  
//  outputCIImage = [inputCIImage imageByApplyingTransform:CGAffineTransformMakeScale(scaleX, scaleY)];
  
  CIFilter *resizeFilter = [CIFilter filterWithName:@"CILanczosScaleTransform"];
  
  CGFloat scale = CGRectGetHeight(inputCIImage.extent) / cropRect.size.height;
  CGFloat aspectRatio = cropRect.size.width / (inputCIImage.extent.size.width * scale);
  
  [resizeFilter setValue:inputCIImage forKey:kCIInputImageKey];
  [resizeFilter setValue:[NSNumber numberWithDouble:scale] forKey:kCIInputScaleKey];
  [resizeFilter setValue:[NSNumber numberWithDouble:aspectRatio] forKey:kCIInputAspectRatioKey];
  
  CIImage *outputCIImage = [resizeFilter outputImage];

//       Due to the way [CIContext:render:toCVPixelBuffer] works, we need to translate the image so the cropped section is at the origin
//  outputCIImage = [outputCIImage imageByApplyingTransform:CGAffineTransformMakeTranslation(-outputCIImage.extent.origin.x, -outputCIImage.extent.origin.y)];

//  CIImage *outputCIImage = [inputCIImage imageByCroppingToRect:cropRect];
//  outputCIImage = [outputCIImage imageByApplyingTransform:CGAffineTransformMakeTranslation(-cropRect.origin.x, -cropRect.origin.y)];
  
 
//  CGAffineTransform transformFilter = [CGAffineTransform new];
//  var affineTransform = CGAffineTransform.MakeTranslation (-150, 150);
//  transformFilter.Transform = affineTransform;
//  transformFilter.Image = croppedImaged;
//  CIImage transformedImage = transformFilter.OutputImage;
  
//  CIFilter* transform = [CIFilter filterWithName:@"CIAffineTransform"];
//  CGAffineTransform *affineTransform = CGAffineTransform;
//  [affineTransform translateXBy:-150.0 yBy:-150.0];
//  [transform setValue:affineTransform forKey:@"inputTransform"];
//  [transform setValue:croppedImage forKey:@"inputImage"];
//  CIImage* transformedImage = [transform valueForKey:@"outputImage"];
    
    // render the filtered image out to a pixel buffer (no locking needed as CIContext's render method will do that)
    [_ciContext render:outputCIImage toCVPixelBuffer:renderedOutputPixelBuffer bounds:[outputCIImage extent] colorSpace:_rgbColorSpace];
  
  inputCIImage = nil;
  outputCIImage = nil;
  resizeFilter = nil;
  
    return renderedOutputPixelBuffer;
}

- (CVPixelBufferRef)scale:(CVPixelBufferRef)pixelBuffer
                   toSize:(CGSize)scaleSize {
//  if (!_bufferPool) {
//    [self initializeBuffersWithPixelBuffer:pixelBuffer];
//  }
  
  OSStatus err = noErr;
//  CVPixelBufferRef renderedOutputPixelBuffer = NULL;
  
//  err = CVPixelBufferPoolCreatePixelBuffer( kCFAllocatorDefault, _bufferPool, &renderedOutputPixelBuffer );
//  if ( err ) {
//    NSLog(@"Cannot obtain a pixel buffer from the buffer pool (%d)", (int)err );
//    return NULL;
//  }
  
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
//  CFRelease( _bufferPool );
  _bufferPool = nil;
  
  return pixelBuffer;
}

- (CVPixelBufferRef)createCroppedPixelBuffer:(CVPixelBufferRef)sourcePixelBuffer
                                croppingRect:(CGRect)croppingRect {

  if (!_bufferPool) {
      [self initializeBuffersWithPixelBuffer:sourcePixelBuffer];
  }
  
  OSStatus err = noErr;
  CVPixelBufferRef renderedOutputPixelBuffer = NULL;
  
  err = CVPixelBufferPoolCreatePixelBuffer( kCFAllocatorDefault, _bufferPool, &renderedOutputPixelBuffer );
  if ( err ) {
      NSLog(@"Cannot obtain a pixel buffer from the buffer pool (%d)", (int)err );
      return NULL;
  }

  OSType inputPixelFormat = CVPixelBufferGetPixelFormatType(sourcePixelBuffer);
    assert(inputPixelFormat == kCVPixelFormatType_32BGRA
           || inputPixelFormat == kCVPixelFormatType_32ABGR
           || inputPixelFormat == kCVPixelFormatType_32ARGB
           || inputPixelFormat == kCVPixelFormatType_32RGBA);

//    assertCropAndScaleValid(sourcePixelBuffer, croppingRect, scaledSize);

    if (CVPixelBufferLockBaseAddress(sourcePixelBuffer, kCVPixelBufferLock_ReadOnly) != kCVReturnSuccess) {
        NSLog(@"Could not lock base address");
        return nil;
    }

  size_t sourceWidth = CVPixelBufferGetWidth(sourcePixelBuffer);
  size_t sourceHeight = CVPixelBufferGetHeight(sourcePixelBuffer);
  size_t bytesPerRow = CVPixelBufferGetBytesPerRow(sourcePixelBuffer);

  void *baseAddress = CVPixelBufferGetBaseAddress(sourcePixelBuffer);
  
  void* destData = malloc(croppingRect.size.height * croppingRect.size.width * 4);
  
  size_t yOffSet = 4 * (croppingRect.size.width * croppingRect.origin.y + croppingRect.origin.x);

  vImage_Buffer srcBuffer = { (void *)baseAddress, sourceWidth, sourceHeight, bytesPerRow};
  vImage_Buffer destBuffer = { (void *)destData+yOffSet, croppingRect.size.height, croppingRect.size.width, croppingRect.size.width * 4};

  vImageTentConvolve_ARGB8888(&srcBuffer, &destBuffer, nil, croppingRect.origin.x, croppingRect.origin.y, 0, 0, 0, kvImagePrintDiagnosticsToConsole);

  CVPixelBufferUnlockBaseAddress(sourcePixelBuffer, kCVPixelBufferLock_ReadOnly);
  
  NSDictionary *options = [NSDictionary dictionaryWithObjectsAndKeys:
      [NSNumber numberWithBool : YES], kCVPixelBufferCGImageCompatibilityKey,
      [NSNumber numberWithBool : YES], kCVPixelBufferCGBitmapContextCompatibilityKey,
      [NSNumber numberWithInt : (int) croppingRect.size.width], kCVPixelBufferWidthKey,
      [NSNumber numberWithInt : (int) croppingRect.size.height], kCVPixelBufferHeightKey,
      nil];

  CVPixelBufferCreateWithBytes(kCFAllocatorDefault, croppingRect.size.width, croppingRect.size.height, inputPixelFormat, destData, croppingRect.size.width, NULL, NULL, (__bridge CFDictionaryRef)options, &renderedOutputPixelBuffer);

    return renderedOutputPixelBuffer;
}

- (void)pixelBufferReleaseCallBack:(void *)releaseRefCon
                       baseAddress:(const void *)baseAddress {
    if (baseAddress != NULL) {
        free((void *)baseAddress);
    }
}

- (BOOL)initializeBuffersWithPixelBuffer:(CVPixelBufferRef)pixelBuffer
{
    BOOL success = YES;
    
    _bufferPool = createPixelBufferPool((int32_t)CVPixelBufferGetWidth(pixelBuffer), (int32_t)CVPixelBufferGetHeight(pixelBuffer), CVPixelBufferGetPixelFormatType(pixelBuffer), RETAINED_BUFFER_COUNT);
    if ( ! _bufferPool ) {
        NSLog( @"Problem initializing a buffer pool." );
        success = NO;
        goto bail;
    }
    
    _bufferPoolAuxAttributes = createPixelBufferPoolAuxAttributes(RETAINED_BUFFER_COUNT);
    preallocatePixelBuffersInPool( _bufferPool, _bufferPoolAuxAttributes );
    
bail:
    if ( ! success ) {
        [self deleteBuffers];
    }
    return success;
}

- (void)deleteBuffers
{
    if ( _bufferPool ) {
        CFRelease( _bufferPool );
      _bufferPool = nil;
    }
    if ( _bufferPoolAuxAttributes ) {
        CFRelease( _bufferPoolAuxAttributes );
        _bufferPoolAuxAttributes = NULL;
    }
    if ( _ciContext ) {
        _ciContext = nil;
    }
    if ( _rgbColorSpace ) {
        CFRelease( _rgbColorSpace );
        _rgbColorSpace = NULL;
    }
}

static CVPixelBufferPoolRef createPixelBufferPool( int32_t width, int32_t height, OSType pixelFormat, int32_t maxBufferCount )
{
    CVPixelBufferPoolRef outputPool = NULL;
    
    NSDictionary *sourcePixelBufferOptions = @{ (id)kCVPixelBufferPixelFormatTypeKey : @(pixelFormat),
                                                (id)kCVPixelBufferWidthKey : @(width),
                                                (id)kCVPixelBufferHeightKey : @(height),
                                                (id)kCVPixelFormatOpenGLESCompatibility : @(YES),
                                                (id)kCVPixelBufferIOSurfacePropertiesKey : @{} };
    
    NSDictionary *pixelBufferPoolOptions = @{ (id)kCVPixelBufferPoolMinimumBufferCountKey : @(maxBufferCount) };
    
    CVPixelBufferPoolCreate( kCFAllocatorDefault, (__bridge  CFDictionaryRef)pixelBufferPoolOptions, (__bridge CFDictionaryRef)sourcePixelBufferOptions, &outputPool );
    
    return outputPool;
}

static CFDictionaryRef createPixelBufferPoolAuxAttributes( int32_t maxBufferCount )
{
    // CVPixelBufferPoolCreatePixelBufferWithAuxAttributes() will return kCVReturnWouldExceedAllocationThreshold if we have already vended the max number of buffers
    NSDictionary *auxAttributes = [[NSDictionary alloc] initWithObjectsAndKeys:@(maxBufferCount), (id)kCVPixelBufferPoolAllocationThresholdKey, nil];
    return (CFDictionaryRef)CFBridgingRetain(auxAttributes);
}

static void preallocatePixelBuffersInPool( CVPixelBufferPoolRef pool, CFDictionaryRef auxAttributes )
{
    // Preallocate buffers in the pool, since this is for real-time display/capture
    NSMutableArray *pixelBuffers = [[NSMutableArray alloc] init];
    while ( 1 )
    {
        CVPixelBufferRef pixelBuffer = NULL;
        CVReturn err = CVPixelBufferPoolCreatePixelBufferWithAuxAttributes( kCFAllocatorDefault, pool, auxAttributes, &pixelBuffer );
        
        if ( err == kCVReturnWouldExceedAllocationThreshold ) {
            break;
        }
        assert( err == noErr );
        
        [pixelBuffers addObject:(__bridge id)pixelBuffer];
        CFRelease( pixelBuffer );
    }
}

@end
