// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "FLTSavePhotoDelegate.h"
#import "FLTSavePhotoDelegate_Test.h"

@interface FLTSavePhotoDelegate ()
/// The file path for the captured photo.
@property(readonly, nonatomic) NSString *path;
/// The queue on which captured photos are written to disk.
@property(readonly, nonatomic) dispatch_queue_t ioQueue;
@property(readonly, nonatomic) BOOL enableLivePhoto;
@property BOOL gotLivePhotoImage;
@property NSString *livePhotoMovie;
@end

@implementation FLTSavePhotoDelegate

- (instancetype)initWithPath:(NSString *)path
                     ioQueue:(dispatch_queue_t)ioQueue
             enableLivePhoto:(BOOL)enableLivePhoto
           completionHandler:(FLTSavePhotoDelegateCompletionHandler)completionHandler {
  self = [super init];
  NSAssert(self, @"super init cannot be nil");
  _path = path;
  _ioQueue = ioQueue;
  _enableLivePhoto = enableLivePhoto;
  _gotLivePhotoImage = false;
  _livePhotoMovie = @"";
  _completionHandler = completionHandler;
  return self;
}

- (void)handlePhotoCaptureResultWithError:(NSError *)error
                        photoDataProvider:(NSData * (^)(void))photoDataProvider {
  if (error) {
    self.completionHandler(nil, error);
    return;
  }
  __weak typeof(self) weakSelf = self;
  dispatch_async(self.ioQueue, ^{
    typeof(self) strongSelf = weakSelf;
    if (!strongSelf) return;

    NSData *data = photoDataProvider();
    NSError *ioError;
    if ([data writeToFile:strongSelf.path options:NSDataWritingAtomic error:&ioError]) {
      if (!self.enableLivePhoto || self.livePhotoMovie.length > 0) {
        strongSelf.completionHandler([NSArray arrayWithObjects: self.path, self.livePhotoMovie, nil], nil);
      }
    } else {
      strongSelf.completionHandler(nil, ioError);
    }
  });
}

- (void)captureOutput:(AVCapturePhotoOutput *)output
    didFinishProcessingPhoto:(AVCapturePhoto *)photo
                       error:(NSError *)error {
  self.gotLivePhotoImage = true;
  [self handlePhotoCaptureResultWithError:error
                        photoDataProvider:^NSData * {
                          return [photo fileDataRepresentation];
                        }];
}

- (void)captureOutput:(AVCapturePhotoOutput *)output
didFinishProcessingLivePhotoToMovieFileAtURL:(NSURL *)outputFileURL
                                    duration:(CMTime)duration
                            photoDisplayTime:(CMTime)photoDisplayTime
                            resolvedSettings:(AVCaptureResolvedPhotoSettings *)resolvedSettings
                error:(NSError *)error {
  if (error) {
    self.completionHandler(nil, error);
    return;
  }
  
  if (!_gotLivePhotoImage) {
    return;
  }
  
  _livePhotoMovie = outputFileURL.path;
  
  self.completionHandler([NSArray arrayWithObjects: self.path, _livePhotoMovie, nil], nil);
}

@end
