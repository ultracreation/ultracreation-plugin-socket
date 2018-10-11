var stringToBytes = function(content) {
    var array = new Uint8Array(content.length);
    for (var i = 0, l = content.length; i < l; i++) {
      array[i] = content.charCodeAt(i);
    }
    return array.buffer;
 };

exports.errno = 0;

var platform = cordova.require('cordova/platform');
var exec = cordova.require('cordova/exec');

exports.socket = function(socketMode, callback) {
    var win = callback && function(socketId) {
        callback(socketId);
        if(socketId < 0)
            exports.errno = socketId;
        else
            exports.errno = 0;
    };
    exec(win, null, 'Socket', 'socket', [socketMode]);
};

exports.bind = function(socketId, info, callback) {
    var win = callback && function() {
        exports.errno = 0;
        callback(0);
    };
    var fail = callback && function(code) {
        exports.errno = code;
        callback(-1);
    };
    exec(win, fail, 'Socket', 'bind', [socketId, info]);
};

exports.listen = function(socketId, backlog, callback) {
    var win = callback && function() {
        exports.errno = 0;
        callback(0);
    };
    var fail = callback && function(code) {
        exports.errno = code;
        callback(-1);
    };
    exec(win, fail, 'Socket', 'listen', [socketId, backlog]);
};

exports.accept = function(socketId, callback) {
    var win = callback && function(result) {
        exports.errno = 0;
        callback(result);
    };
    var fail = callback && function(code) {
        exports.errno = code;
        var empty = {SocketId:-1};
        callback(empty);
    };
    exec(win, fail, 'Socket', 'accept', [socketId]);
};

exports.select_readfds = function(socketId, timeout, callback) {

    var win = callback && function(set) {
        exports.errno = 0;
        callback(set);
    };
    var fail = callback && function(code) {
        var error_array = [-1];
        exports.errno = code;
        callback(error_array);
    };
    exec(win, fail, 'Socket', 'select', [socketId, timeout]);
};

exports.send = function(socketId, data, callback) {
    var type = Object.prototype.toString.call(data).slice(8, -1);
    if (type != 'ArrayBuffer') {
        throw new Error('chrome.socket.write - data is not an ArrayBuffer! (Got: ' + type + ')');
    }
    var win = callback && function(bytesWritten) {
        exports.errno = 0;
        callback(bytesWritten);
    };

    var fail = callback && function(code) {
        exports.errno = code;
        callback(-1);
    };
    exec(win, fail, 'Socket', 'send', [socketId, data]);
};

exports.recv = function(socketId, size, callback) {
    var win = callback && function(data) {
        exports.errno = 0;
        callback(data);
    };

    var fail = callback && function(code) {
        var readInfo = new Uint8Array(0);
        exports.errno = code;
        callback(readInfo.buffer);
    };
    exec(win, fail, 'Socket', 'recv', [socketId, size]);
};

exports.close = function(socketId,callback) {
    var win = callback && function() {
          exports.errno = 0;
          callback(0);
    };

    var fail = callback && function(code) {
        exports.errno = code;
        callback(-1);
    };
    exec(win, fail, 'Socket', 'close', [socketId]);
};

exports.shutdown = function(socketId,how,callback) {
    var win = callback && function() {
          exports.errno = 0;
          callback(0);
    };

    var fail = callback && function(code) {
        exports.errno = code;
        callback(-1);
    };
    exec(win, fail, 'Socket', 'shutdown', [socketId,how]);
};

exports.setreuseraddr = function(socketId,opt,callback) {
    var win = callback && function() {
          exports.errno = 0;
          callback(0);
    };

    var fail = callback && function(code) {
        exports.errno = code;
        callback(-1);
    };
    exec(win, fail, 'Socket', 'setreuseraddr', [socketId,opt]);
};

exports.setbroadcast = function(socketId,opt,callback) {
    var win = callback && function() {
          exports.errno = 0;
          callback(0);
    };

    var fail = callback && function(code) {
        exports.errno = code;
        callback(-1);
    };
    exec(win, fail, 'Socket', 'setbroadcast', [socketId,opt]);
};


exports.getsockname = function(socketId, callback) {
    var win = callback && function(address) {
        exports.errno = 0;
        callback(address);
    };

    var fail = callback && function(code) {
        exports.errno = code;
        callback("");
    };
    exec(win, fail, 'Socket', 'getsockname', [socketId]);
};

exports.getpeername = function(socketId, callback) {
    var win = callback && function(address) {
        exports.errno = 0;
        callback(address);
    };

    var fail = callback && function(code) {
        exports.errno = code;
        callback("");
    };
    exec(win, fail, 'Socket', 'getpeername', [socketId]);
};

exports.connect = function(socketId, info, callback) {
    var win = callback && function() {
        exports.errno = 0;
        callback(0);
    };
    var fail = callback && function(code) {
        exports.errno = code;
        callback(-1);
    };
    exec(win, fail, 'Socket', 'connect', [socketId, info]);
};


exports.sendto = function(socketId, data, info, callback) {
    var win = callback && function(bytesWritten) {
        exports.errno = 0;
        callback(bytesWritten);
    };
    var fail = callback && function(code) {
        exports.errno = code;
        callback(-1);
    };
    exec(win, fail, 'Socket', 'sendto', [socketId, info, data]);
};

exports.recvfrom = function(socketId, bufferSize, callback) {
    var win = callback && function(result) {
        exports.errno = 0;
        callback(result);
    };

    var fail = callback && function(code) {
        exports.errno = code;
        var readInfo = {
            ByteBase64: ""
        };
        callback(readInfo);
    };
    exec(win, fail, 'Socket', 'recvfrom', [socketId, bufferSize]);
};

exports.getifaddrs = function(callback) {
    var win = callback && function(result) {
        exports.errno = 0;
        callback(result);
    };
    var fail = callback && function(code) {
        exports.errno = code;
        var empty = [];
        callback(empty);
    };
    exec(win, fail, 'Socket', 'getifaddrs', []);
};
