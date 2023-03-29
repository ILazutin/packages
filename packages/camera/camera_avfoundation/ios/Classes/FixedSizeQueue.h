//
//  FixedSizeQueue.h
//  Pods
//
//  Created by Ilya Lazutin on 28.03.2023.
//

#ifndef FixedSizeQueue_h
#define FixedSizeQueue_h

@interface FixedSizeQueue : NSObject

- (instancetype)initWithSize:(NSUInteger)size;
- (void)enqueue:(id)anObject;
- (id)dequeue;
- (BOOL)isNotEmpty;

@end

#endif /* FixedSizeQueue_h */
