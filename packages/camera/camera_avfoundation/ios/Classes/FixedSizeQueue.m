//
//  FixedSizeQueue.m
//  camera_avfoundation
//
//  Created by Ilya Lazutin on 28.03.2023.
//

#import <Foundation/Foundation.h>
#import "FixedSizeQueue.h"

@interface FixedSizeQueue()

@property(strong, nonatomic) NSMutableArray *buffer;
@property(assign, nonatomic) NSUInteger maxSize;

@end

@implementation FixedSizeQueue

- (instancetype)initWithSize:(NSUInteger)size {
  self = [super init];
  _maxSize = size;
  [self resetBuffer];
  return self;
}

- (void)enqueue:(id)anObject {
  if (_buffer.count == _maxSize) {
    @autoreleasepool {
      id headObject = [self dequeue];
      CFRelease(CFBridgingRetain(headObject));
      headObject = nil;
    }
  }

  [_buffer addObject:anObject];
}

- (id)dequeue {
  if (_buffer.count == 0) return nil;
  @autoreleasepool {
    id headObject = [_buffer objectAtIndex:0];
    if (headObject != nil) {
      [_buffer removeObjectAtIndex:0];
    }
    return headObject;
  }
}

- (void)resetBuffer {
  _buffer = [[NSMutableArray alloc] initWithCapacity:_maxSize];
}

- (BOOL)isNotEmpty {
  return [_buffer count] > 0;
}

- (void)clean {
  while ([self isNotEmpty]) {
    id headObject = [self dequeue];
    CFRelease(CFBridgingRetain(headObject));
    headObject = nil;
  }
}

@end

