#import "UltracreationSocket.h"
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <netdb.h>
#include <stdarg.h>
#include <string.h>
#include <fcntl.h>
#include <ifaddrs.h>
#include <net/if.h>

#pragma mark UltracreationSocket interface

@interface UltracreationSocket () {
    NSMutableArray* _sockets;
    NSMutableArray* _ServerSockets;
}
@end


#pragma mark ChromeSocket implementation

@implementation UltracreationSocket

- (void)pluginInitialize
{
    _sockets = [NSMutableArray arrayWithCapacity:5];
    _ServerSockets = [NSMutableArray arrayWithCapacity:5];
}

- (void)destroyAllSockets
{
    NSLog(@"Destroying all open sockets");
    for (int i = 0; i < _sockets.count; i++)
    {
        NSNumber * socketFd = [_sockets objectAtIndex:i];
        NSLog(@"de close = %d",[socketFd intValue]);
//        shutdown([socketFd intValue], 2);
        close([socketFd intValue]);
    }
    [_sockets removeAllObjects];
    for (int i = 0; i < _ServerSockets.count; i++)
    {
        NSNumber * socketFd = [_ServerSockets objectAtIndex:i];
        NSLog(@"server close = %d",[socketFd intValue]);
//        shutdown([socketFd intValue], 2);
        close([socketFd intValue]);
    }
    [_ServerSockets removeAllObjects];
}

- (void)onReset
{
    [self.commandDelegate runInBackground:^{
        [self destroyAllSockets];
    }];
}

- (void)dispose
{
    [self.commandDelegate runInBackground:^{
        [self destroyAllSockets];
    }];
    [super dispose];
}



-(int)getMaxFd:(NSArray*)array
{
    NSLog(@"getMaxFd");
    int maxFd = 0;
    for(int i = 0; i < [array count]; i++)
    {
        NSNumber* temp = [array objectAtIndex:i];
        
        if(temp  != nil && maxFd < [temp intValue])
        {
            maxFd = [temp intValue];
        }
    }
    return maxFd;
}

int set_nonblock(int socket)
{
    int flags = fcntl(socket,F_GETFL,0);
    if(flags < 0){
        NSLog(@"Set server socket nonblock flags failed\n");
        return -1;
    }
    
    if (fcntl(socket, F_SETFL, flags | O_NONBLOCK) < 0)
    {
        NSLog(@"Set server socket nonblock failed\n");
        return -1;
    }
    
    return 1;
}

-(void)resetError
{
    errno = 0;
}

- (void)socket:(CDVInvokedUrlCommand*)command
{
    NSLog(@"socket");
    
    [self.commandDelegate runInBackground:^{
        [self resetError];
        NSLog(@"runInBackground");
        NSString* socketMode = [command argumentAtIndex:0];
        
        if(socketMode != nil && ([socketMode isEqualToString:@"tcp"] || [socketMode isEqualToString:@"tcp_server"]
                                 || [socketMode isEqualToString:@"udp"]))
        {
            int socketFd;
            if([socketMode isEqualToString:@"udp"])
            {
                socketFd = socket(AF_INET, SOCK_DGRAM, 0);
            }else{
                socketFd = socket(AF_INET, SOCK_STREAM, 0);
            }
            
            if(socketFd < 0)
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
            else
            {
                int block = set_nonblock(socketFd);
                if(block < 0)
                {
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
                }else{
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:socketFd] callbackId:command.callbackId];
                }
                if([socketMode isEqualToString:@"tcp_server"])
                    [_ServerSockets addObject:[NSNumber numberWithInt:socketFd]];
                else
                    [_sockets addObject:[NSNumber numberWithInt:socketFd]];
                NSLog(@"create = %d",socketFd);
            }
        }
        
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
    }];
    
}

- (void)bind:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        NSString* info = [command argumentAtIndex:1];
        NSArray *array = [info componentsSeparatedByString:@":"];
        
        NSNumber* socketId = [command argumentAtIndex:0];
        NSString* address = [array objectAtIndex:0];
        int port = [[array objectAtIndex:1] intValue];
        
        
        struct sockaddr_in sockaddr;
        sockaddr.sin_family = AF_INET;
        if ([address isEqualToString:@"0.0.0.0"])
            sockaddr.sin_addr.s_addr = htonl(INADDR_ANY);
        else{
            int rtn = inet_pton(AF_INET, [address UTF8String], &sockaddr.sin_addr.s_addr);
            if(rtn < 0)
            {
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
                return;
            }
        }
        sockaddr.sin_port = htons(port);
        
        int ret = bind([socketId intValue], (struct sockaddr *)&sockaddr, sizeof(sockaddr));
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
        }else
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:ret] callbackId:command.callbackId];
        }
    }];
}

- (void)listen:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        NSNumber* socketId = [command argumentAtIndex:0];
        NSNumber* backlog = [command argumentAtIndex:1];
        
        int ret = listen([socketId intValue], [backlog intValue]);
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
        }else
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:ret] callbackId:command.callbackId];
        }
    }];
}


- (void)connect:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        NSString* info = [command argumentAtIndex:1];
        NSArray *array = [info componentsSeparatedByString:@":"];
        
        int socketId = [[command argumentAtIndex:0] intValue];
        NSString* address = [array objectAtIndex:0];
        int port = [[array objectAtIndex:1] intValue];
        
        
        struct sockaddr_in sockaddr;
        sockaddr.sin_family = AF_INET;
        if ([address isEqualToString:@"0.0.0.0"])
            sockaddr.sin_addr.s_addr = htonl(INADDR_ANY);
        else{
            int rtn = inet_pton(AF_INET, [address UTF8String], &sockaddr.sin_addr.s_addr);
            if(rtn < 0)
            {
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
                return;
            }
        }
        sockaddr.sin_port = htons(port);
        
        int ret = connect(socketId, (struct sockaddr *)&sockaddr, sizeof(sockaddr));
        
        if (ret < 0) {
            if (errno == EINPROGRESS) {
                [self resetError];
                struct timeval tv;
                tv.tv_sec = 6;
                tv.tv_usec = 0;
                fd_set read_set, write_set;
                FD_ZERO(&read_set);
                FD_SET(socketId, &read_set);
                write_set=read_set;
                socklen_t lon;
                int valopt = 0;
                
                if ((ret = select(socketId+1, &read_set, &write_set, NULL, &tv)) > 0) {
                    lon = sizeof(int);
                    getsockopt(socketId, SOL_SOCKET, SO_ERROR, (void*)(&valopt), &lon);
                    if (valopt) {
                        NSLog(@"Error in connection() %d - %s\n", valopt, strerror(valopt));
                        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
                    }else
                    {
                        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:ret] callbackId:command.callbackId];
                    }
                }
                else {
                    NSLog(@"Timeout or error() %d - %s\n", valopt, strerror(valopt));
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
                }
            } 
            else { 
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
            } 
        }else
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:ret] callbackId:command.callbackId];
        }
    }];
}

- (void)accept:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        NSNumber* socketId = [command argumentAtIndex:0];
        
        struct sockaddr_in sockaddr;
        socklen_t len = sizeof(sockaddr);
        int ret = accept([socketId intValue], (struct sockaddr *)&sockaddr,&len);
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
        }else
        {
            NSMutableDictionary* result = [[NSMutableDictionary alloc] initWithCapacity:2];
            
            NSString* address = [NSString stringWithUTF8String: inet_ntoa(sockaddr.sin_addr)];
            NSString* port = [NSString stringWithFormat:@"%d", ntohs(sockaddr.sin_port)];
            NSString* inetAddress = [address stringByAppendingFormat:@"%@%@",@":",port];
            
            [result setValue:[NSNumber numberWithInt:ret] forKey:@"SocketId"];
            [result setValue:inetAddress forKey:@"SocketAddr"];
            
            [_sockets addObject:[NSNumber numberWithInt:ret]];
            
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:result] callbackId:command.callbackId];
        }
    }];
}



- (void)select:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        NSArray* selectSet = [command argumentAtIndex:0];
        int time = [[command argumentAtIndex:1] intValue];
        
        int maxFd = [self getMaxFd:selectSet];
        NSLog(@"maxFd = %d",maxFd);
        fd_set readfds;
        FD_ZERO(&readfds); //clear the socket set
        
        for(int i = 0; i < [selectSet count]; i++)
        {
            NSNumber* temp = [selectSet objectAtIndex:i];
            FD_SET([temp intValue],&readfds);
        }
        
        
        int ret;
        struct timeval tv;
        if(time < 0)
        {
            ret = select(maxFd + 1, &readfds , NULL , NULL , NULL);
        }else
        {
            tv.tv_sec = time / 1000;
            tv.tv_usec = (time % 1000) * 1000;
            ret = select(maxFd + 1, &readfds , NULL , NULL , &tv);
        }
        
        
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
        }
        else
        {
            NSMutableArray* result = [NSMutableArray arrayWithCapacity:[selectSet count]];
            for(int i = 0; i < [selectSet count]; i++)
            {
                NSNumber* temp = [selectSet objectAtIndex:i];
                if (FD_ISSET([temp intValue], &readfds))
                {
                    [result addObject:temp];
                }
            }
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:result] callbackId:command.callbackId];
        }
    }];
}

- (void)send:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        int socketId = [[command argumentAtIndex:0] intValue];
        NSData* data = [command argumentAtIndex:1];
        
        const char* buffer = [data bytes];
        int ret = send(socketId, buffer, strlen(buffer), 0);
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
        }else
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:ret] callbackId:command.callbackId];
        }
    }];
}

- (void)sendto:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        int socketId = [[command argumentAtIndex:0] intValue];
        NSData* data = [command argumentAtIndex:2];
        
        NSString* info = [command argumentAtIndex:1];
        NSArray *array = [info componentsSeparatedByString:@":"];
        
        NSString* address = [array objectAtIndex:0];
        int port = [[array objectAtIndex:1] intValue];
        
        
        struct sockaddr_in sockaddr;
        sockaddr.sin_family = AF_INET;
        if ([address isEqualToString:@"0.0.0.0"])
            sockaddr.sin_addr.s_addr = htonl(INADDR_ANY);
        else{
            int rtn = inet_pton(AF_INET, [address UTF8String], &sockaddr.sin_addr.s_addr);
            if(rtn < 0)
            {
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
                return;
            }
        }
        sockaddr.sin_port = htons(port);
        
        const char* buffer = [data bytes];
        int ret = sendto(socketId, buffer, strlen(buffer), 0,(struct sockaddr *)&sockaddr, sizeof(sockaddr));
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
        }else
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:ret] callbackId:command.callbackId];
        }
    }];
}



- (void)recv:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        int socketId = [[command argumentAtIndex:0] intValue];
        int bufferSize = [[command argumentAtIndex:1] intValue];
        
        char buffer[bufferSize];
        int ret = recv(socketId , buffer, sizeof(buffer)-1, 0);
        NSLog(@"recv = %d socket = %d",ret,socketId);
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
            NSLog(@"errno = %d",errno);
        }
        else
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArrayBuffer:[NSData dataWithBytes:buffer length:ret]] callbackId:command.callbackId];
        }
    }];
}



- (void)recvfrom:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        int socketId = [[command argumentAtIndex:0] intValue];
        int bufferSize = [[command argumentAtIndex:1] intValue];
        
        struct sockaddr_in client;
        socklen_t len = sizeof(client);
        char buffer[bufferSize];
        
        int ret = recvfrom(socketId , buffer, sizeof(buffer)-1, 0,(struct sockaddr *)&client,&len);
        NSLog(@"recvfrom = %d socket = %d",ret,socketId);
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
            NSLog(@"errno = %d",errno);
        }
        else
        {
            NSMutableDictionary* result = [[NSMutableDictionary alloc] initWithCapacity:2];
            
            NSData* data = [NSData dataWithBytes:buffer length:ret];
            NSString* base64Str = [data base64EncodedStringWithOptions:0];
            
            
            NSString* address = [NSString stringWithUTF8String: inet_ntoa(client.sin_addr)];
            NSString* port = [NSString stringWithFormat:@"%d", ntohs(client.sin_port)];
            NSString* inetAddress = [address stringByAppendingFormat:@"%@%@",@":",port];
            
            [result setValue:base64Str forKey:@"ByteBase64"];
            [result setValue:inetAddress forKey:@"SocketAddr"];
            
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:result] callbackId:command.callbackId];
        }
    }];
}

- (void)close:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        int socketId = [[command argumentAtIndex:0] intValue];
        
        int ret = close(socketId);
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
        }else
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:ret] callbackId:command.callbackId];
        }
    }];
}

- (void)shutdown:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        int socketId = [[command argumentAtIndex:0] intValue];
        int how = [[command argumentAtIndex:1] intValue];
        int ret = shutdown(socketId, how);
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
        }else
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:ret] callbackId:command.callbackId];
        }
    }];
}

- (void)setreuseraddr:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        int socketId = [[command argumentAtIndex:0] intValue];
        int enable = [[command argumentAtIndex:1] intValue];
        NSLog(@"setreuseraddr = %d",enable);
        int ret = setsockopt(socketId, SOL_SOCKET, SO_REUSEADDR, (const char*)&enable, sizeof(enable));
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
        }else
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:ret] callbackId:command.callbackId];
        }
    }];
}

- (void)setbroadcast:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        int socketId = [[command argumentAtIndex:0] intValue];
        int enable = [[command argumentAtIndex:1] intValue];
        
        int ret = setsockopt(socketId, SOL_SOCKET, SO_BROADCAST, (const char*)&enable, sizeof(enable));
        NSLog(@"setbroadcast ret = %d",ret);
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
        }else
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:ret] callbackId:command.callbackId];
        }
    }];
}

- (void)getsockname:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        int socketId = [[command argumentAtIndex:0] intValue];
        
        struct sockaddr_in sin;
        socklen_t len = sizeof(sin);
        
        int ret = getsockname(socketId, (struct sockaddr *)&sin, &len);
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
        }else
        {
            NSString* address = [NSString stringWithUTF8String: inet_ntoa(sin.sin_addr)];
            NSString* port = [NSString stringWithFormat:@"%d", ntohs(sin.sin_port)];
            NSString* result = [address stringByAppendingFormat:@"%@%@",@":",port];
            NSLog(@"result = %@",result);
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:result] callbackId:command.callbackId];
        }
    }];
}

- (void)getpeername:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self resetError];
        int socketId = [[command argumentAtIndex:0] intValue];
        
        struct sockaddr_in peeraddr;
        socklen_t len;
        
        int ret = getpeername(socketId, (struct sockaddr *)&peeraddr, &len);
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
        }
        else
        {
            NSString* address = [NSString stringWithUTF8String: inet_ntoa(peeraddr.sin_addr)];
            NSString* port = [NSString stringWithFormat:@"%d", ntohs(peeraddr.sin_port)];
            NSString* result = [address stringByAppendingFormat:@"%@%@",@":",port];
            NSLog(@"result = %@",result);
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:result] callbackId:command.callbackId];
        }
    }];
}

- (void)getifaddrs:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        struct ifaddrs *interfaces = NULL;
        struct ifaddrs *temp_addr = NULL;
        int ret;
        // retrieve the current interfaces - returns 0 on success
        ret = getifaddrs(&interfaces);

        
        if(ret < 0)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsInt:errno] callbackId:command.callbackId];
        }
        else
        {
            NSMutableArray* result = [NSMutableArray arrayWithCapacity:2];
            temp_addr = interfaces;
            while(temp_addr != NULL) {
                if(temp_addr->ifa_addr->sa_family == AF_INET) {
                    //printf("fa = %s\n",temp_addr->ifa_name);
                    char* info = inet_ntoa(((struct sockaddr_in *)temp_addr->ifa_addr)->sin_addr);
                    NSString* addr = [NSString stringWithUTF8String:info];
                    if(![addr isEqualToString:@"127.0.0.1"])
                        [result addObject:addr];
                    
                }
                
                temp_addr = temp_addr->ifa_next;
            }
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:result] callbackId:command.callbackId];
        }
        
        // Free memory
        freeifaddrs(interfaces);
    }];
}

@end
