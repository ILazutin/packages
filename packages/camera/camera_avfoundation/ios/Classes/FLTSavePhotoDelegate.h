// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

@import AVFoundation;
@import Foundation;

#import "FLTThreadSafeFlutterResult.h"
#import "CameraProperties.h"

NS_ASSUME_NONNULL_BEGIN

/// The completion handler block for save photo operations.
/// Can be called from either main queue or IO queue.
/// If failed, `error` will be present and `paths` will be nil. Otherewise, `error` will be nil and
/// `paths` will be present.
/// @param paths the paths for successfully saved photo file and movie file (for LivePhotos).
/// @param error photo capture error or IO error.
typedef void (^FLTSavePhotoDelegateCompletionHandler)(NSArray *_Nullable paths,
                                                      NSError *_Nullable error);

/**
 Delegate object that handles photo capture results.
 */
@interface FLTSavePhotoDelegate : NSObject <AVCapturePhotoCaptureDelegate>

/**
 * Initialize a photo capture delegate.
 * @param path the path for captured photo file.
 * @param ioQueue the queue on which captured photos are written to disk.
 * @param completionHandler The completion handler block for save photo operations. Can
 * be called from either main queue or IO queue.
 */
- (instancetype)initWithPath:(NSString *)path
                     ioQueue:(dispatch_queue_t)ioQueue
             enableLivePhoto:(BOOL)enableLivePhoto
       resolutionAspectRatio:(FLTResolutionAspectRatio)resolutionAspectRatio
                    needCrop:(BOOL)needCrop
           completionHandler:(FLTSavePhotoDelegateCompletionHandler)completionHandler;
@end

NS_ASSUME_NONNULL_END
