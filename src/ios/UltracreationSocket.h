// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import <Foundation/Foundation.h>
#import <Cordova/CDVPlugin.h>

@interface UltracreationSocket : CDVPlugin

- (void)socket:(CDVInvokedUrlCommand*)command;
- (void)bind:(CDVInvokedUrlCommand*)command;
- (void)listen:(CDVInvokedUrlCommand*)command;

- (void)connect:(CDVInvokedUrlCommand*)command;
- (void)accept:(CDVInvokedUrlCommand*)command;
- (void)select:(CDVInvokedUrlCommand*)command;

- (void)send:(CDVInvokedUrlCommand*)command;
- (void)sendto:(CDVInvokedUrlCommand*)command;
- (void)recv:(CDVInvokedUrlCommand*)command;

- (void)close:(CDVInvokedUrlCommand*)command;
- (void)shutdown:(CDVInvokedUrlCommand*)command;

- (void)setreuseraddr:(CDVInvokedUrlCommand*)command;
- (void)setbroadcast:(CDVInvokedUrlCommand*)command;

- (void)getsockname:(CDVInvokedUrlCommand*)command;
- (void)getpeername:(CDVInvokedUrlCommand*)command;
- (void)getifaddrs:(CDVInvokedUrlCommand*)command;
@end
