package com.ultracreation;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketException;
import java.net.NetworkInterface;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UltracreationSocket extends CordovaPlugin
{
    private static final String TAG = "UltracreationSocket";

    private Map<Integer, SocketData> sockets = new ConcurrentHashMap<Integer, SocketData>();
    private int nextSocket = 1;

    private static final String ERROR_BIND = "Bind error";
    private static final String ERROR_CONNECT = "Connect error";
    private static final String ERROR_CLOSE = "Socket close";
    private static final String ERROR_NOT_CREATE = "Socket not create";
    private static final String ERROR_NOT_SERVER = "Only tcpServer can accept";
    private static final String ERROR_NOT_CLIENT = "Only Client can accept";
    private static final String ERROR_ACCEPT = "Accept error";
    private static final String ERROR_UDP_WRITE = "UDP write error";
    private static final String ERROR_GETPEER = "Get peer info error";
    private static final String ERROR_GETLOCAL = "Get local info error";

    private static final int ERROR_CODE = -1;

    private static final int SHUTDOWN_READ = 0;
    private static final int SHUTDOWN_WRITE = 1;
    private static final int SHUTDOWN_READ_WRITE = 2;

    private static final int OPTION_REUSERADDR = 1;
    private static final int OPTION_BROADCAST = 2;

    @Override
    public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        if ("socket".equals(action)) {
            socket(args, callbackContext);
        } else if ("bind".equals(action)) {
            bind(args, callbackContext);
        } else if ("listen".equals(action)) {
            listen(args, callbackContext);
        } else if ("accept".equals(action)) {
            accept(args, callbackContext);
        } else if ("select".equals(action)) {
            select(args, callbackContext);
        } else if ("send".equals(action)) {
            send(args, callbackContext);
        } else if ("recv".equals(action)) {
            recv(args, callbackContext);
        } else if ("recvfrom".equals(action)) {
            recvfrom(args, callbackContext);
        } else if ("connect".equals(action)) {
            connect(args, callbackContext);
        } else if ("sendto".equals(action)) {
            sendto(args, callbackContext);
        } else if ("recvfrom".equals(action)) {
            recvfrom(args, callbackContext);
        } else if ("close".equals(action)) {
            close(args, callbackContext);
        } else if ("shutdown".equals(action)) {
            shutdown(args, callbackContext);
        } else if ("setreuseraddr".equals(action)) {
            setsockopt(args, OPTION_REUSERADDR, callbackContext);
        } else if ("setbroadcast".equals(action)) {
            setsockopt(args, OPTION_BROADCAST, callbackContext);
        } else if ("getsockname".equals(action)) {
            getsockname(args, callbackContext);
        } else if ("getpeername".equals(action)) {
            getpeername(args, callbackContext);
        } else if ("getifaddrs".equals(action)) {
            getifaddrs(callbackContext);
        } else {
            return false;
        }
        return true;
    }

    private void getifaddrs(final CallbackContext context) throws JSONException {
        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                try
                {
                    JSONArray array = getLocalInetAddresses(true, false);
                    context.sendPluginResult(new PluginResult(PluginResult.Status.OK, array));
                }
                catch (JSONException e)
                {
                    Log.e(TAG, "getifaddrs err!");
                    context.error(ERROR_CODE);
                }
            }
        });

    }

    private JSONArray getLocalInetAddresses(boolean getIPv4, boolean getIPv6) throws JSONException {
        JSONArray arrayIPAddress = new JSONArray();

        // Get all network interfaces
        Enumeration<NetworkInterface> networkInterfaces;
        try {
            networkInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return arrayIPAddress;
        }

        if (networkInterfaces == null)
            return arrayIPAddress;

        // For every suitable network interface, get all IP addresses
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface card = networkInterfaces.nextElement();

            try {
                // skip devices, not suitable to search gateways for
                if (card.isLoopback() || card.isPointToPoint() ||
                        card.isVirtual() || !card.isUp())
                    continue;
            } catch (SocketException e) {
                continue;
            }

            Enumeration<InetAddress> addresses = card.getInetAddresses();

            if (addresses == null)
                continue;

            while (addresses.hasMoreElements()) {
                InetAddress inetAddress = addresses.nextElement();

                if (!getIPv4 || !getIPv6) {
                    if (getIPv4 && !Inet4Address.class.isInstance(inetAddress))
                        continue;

                    if (getIPv6 && !Inet6Address.class.isInstance(inetAddress))
                        continue;
                }

                JSONObject jaddr = new JSONObject();
                jaddr.put("name", card.getDisplayName());
                jaddr.put("addr", inetAddress.getHostAddress());
                arrayIPAddress.put(jaddr);
            }
        }

        return arrayIPAddress;
    }

    private void getpeername(CordovaArgs args, final CallbackContext context) throws JSONException {
        final int socketId = args.getInt(0);
        final SocketData sd = sockets.get(Integer.valueOf(socketId));
        if (sd == null) {
            Log.d(TAG, ERROR_NOT_CREATE);
            context.error(ERROR_CODE);
            return;
        }
        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    sd.getpeername(context);
                } catch (Exception e) {
                    Log.d(TAG, "getpeername fail");
                    context.error(ERROR_CODE);
                    e.printStackTrace();
                }
            }
        });
    }

    private void getsockname(CordovaArgs args, final CallbackContext context) throws JSONException {
        final int socketId = args.getInt(0);
        final SocketData sd = sockets.get(Integer.valueOf(socketId));
        if (sd == null) {
            Log.d(TAG, ERROR_NOT_CREATE);
            context.error(ERROR_CODE);
            return;
        }
        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    sd.getsockname(context);
                } catch (Exception e) {
                    Log.d(TAG, "getsockname fail");
                    context.error(ERROR_CODE);
                    e.printStackTrace();
                }
            }
        });
    }


    public void onDestroy() {
        destroyAllSockets();
    }

    public void onReset() {
        destroyAllSockets();
    }

    private void destroyAllSockets() {
        if (sockets.isEmpty()) return;

        Log.i(TAG, "Destroying all open sockets");

        for (Map.Entry<Integer, SocketData> entry : sockets.entrySet()) {
            SocketData sd = entry.getValue();
            try {
                sd.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        sockets.clear();
    }

    private void setsockopt(CordovaArgs args, final int option, final CallbackContext context) throws JSONException {
        final int socketId = args.getInt(0);
        final boolean isSet = args.getBoolean(1);
        final SocketData sd = sockets.get(Integer.valueOf(socketId));

        if (sd == null) {
            Log.d(TAG, ERROR_NOT_CREATE);
            context.error(ERROR_CODE);
            return;
        }
        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    sd.setsockopt(isSet, option);
                    context.success();
                } catch (Exception e) {
                    Log.d(TAG, "setsockopt fail =" + option);
                    e.printStackTrace();
                    context.error(ERROR_CODE);
                }
            }
        });
    }

    private void shutdown(CordovaArgs args, final CallbackContext context) throws JSONException {
        final int socketId = args.getInt(0);
        final int how = args.getInt(1);
        final SocketData sd = sockets.get(Integer.valueOf(socketId));
        if (sd == null) {
            Log.d(TAG, ERROR_NOT_CREATE);
            context.error(ERROR_CODE);
            return;
        }
        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    sd.shutdown(how);
                    context.success();
                } catch (Exception e) {
                    Log.d(TAG, "shutdown fail");
                    context.error(ERROR_CODE);
                    e.printStackTrace();
                }
            }
        });
    }

    private void close(CordovaArgs args, final CallbackContext context) throws JSONException {
        final int socketId = args.getInt(0);

        final SocketData sd = sockets.get(Integer.valueOf(socketId));
        if (sd == null) {
            context.error(ERROR_CODE);
            Log.d(TAG, ERROR_BIND);
            return;
        }
        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                try {
					sockets.remove(Integer.valueOf(socketId));
                    sd.close();
                    context.success();
                } catch (Exception e) {
                    Log.d(TAG, "close fail");
                    context.error(ERROR_CODE);
                    e.printStackTrace();
                }
            }
        });

    }

    private SocketType get(String type) {
        return type.equals("udp")
                ? SocketType.UDP
                : (type.equals("tcp_server") ? SocketType.TCP_SERVER : SocketType.TCP);
    }

    private void socket(CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        String socketType = args.getString(0);
        if (TextUtils.isEmpty(socketType))
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, -1));
        if (socketType.equals("tcp") || socketType.equals("udp") || socketType.equals("tcp_server")) {
            SocketData sd = new SocketData(get(socketType));
            int id = addSocket(sd);
            sd.setSocketId(id);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, id));
        }
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, -1));
    }

    public void listen(final CordovaArgs args, final CallbackContext context) throws JSONException {
        String[] info = args.getString(1).split(":");
        final int socketId = args.getInt(0);

        final SocketData sd = sockets.get(socketId);
        if (sd == null) {
            context.error(ERROR_CODE);
            Log.d(TAG, ERROR_BIND);
            return;
        }

        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                boolean success = sd.listen();
                if (success)
                    context.sendPluginResult(new PluginResult(PluginResult.Status.OK, 1));
                else {
                    context.error(ERROR_CODE);
                    Log.d(TAG, ERROR_BIND);
                }
            }
        });
    }

    public void bind(final CordovaArgs args, final CallbackContext context) throws JSONException {
        String[] info = args.getString(1).split(":");
        final int socketId = args.getInt(0);
        final String address = info[0];
        final int port = Integer.parseInt(info[1]);

        final SocketData sd = sockets.get(socketId);
        if (sd == null) {
            context.error(ERROR_CODE);
            Log.d(TAG, ERROR_BIND);
            return;
        }
        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    sd.bind(address, port);
                    context.success();
                } catch (Exception e) {
                    e.printStackTrace();
                    context.error(ERROR_CODE);
                    Log.d(TAG, ERROR_BIND);
                }
            }
        });
    }

    public void accept(CordovaArgs args, final CallbackContext context) throws JSONException {
        int socketId = args.getInt(0);
        final SocketData sd = sockets.get(socketId);
        if (sd == null) {
            context.error(ERROR_CODE);
            Log.d(TAG, ERROR_ACCEPT);
            return;
        }

        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    sd.accept(context);
                } catch (Exception e) {
                    e.printStackTrace();
                    context.error(ERROR_CODE);
                    Log.d(TAG, ERROR_ACCEPT);
                }
            }
        });

    }

    public void select(CordovaArgs args, final CallbackContext context) throws JSONException
    {
        final List<Integer> socketIdList = getList(args.getJSONArray(0));

        if (socketIdList == null || socketIdList.size() == 0)
        {
            context.error(ERROR_CODE);
            return;
        }

        final int timeout = args.getInt(1);
        final int time = timeout < 0 ? 0 : timeout;
        cordova.getThreadPool().submit(new Runnable()
        {
            @Override public void run()
            {
                try
                {
                    Selector selector;
                    synchronized (UltracreationSocket.this)
                    {
                        selector = Selector.open();
                    }
                    JSONArray array = new JSONArray();

                    for (int i = 0; i < socketIdList.size(); i++)
                    {
                        int socketId = socketIdList.get(i);
                        final SocketData sd = sockets.get(socketId);
                        if (sd != null)
                            sd.select(selector);
                    }

                    int select = selector.select(time);
                    if (select > 0)
                        array = checkSelector(selector, context);
                    selector.close();

                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, array);
                    context.sendPluginResult(pluginResult);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    Log.d(TAG, ERROR_CLOSE);
                    context.error(ERROR_CODE);
                }
            }
        });
    }


    private JSONArray checkSelector(Selector selector, final CallbackContext context) throws Exception {
        JSONArray array = new JSONArray();
        Iterator iterator = selector.selectedKeys().iterator();

        while (iterator.hasNext()) {
            SelectionKey key = null;
            try {
                key = (SelectionKey) iterator.next();
                //记住一定要remove这个key，否则之后的新连接将被阻塞无法连接服务器
                iterator.remove();
                Integer socketId = (Integer) key.attachment();
                if (socketId == null)
                    continue;
                if (key.isValid() && key.isAcceptable()) {
                    if (!hasIds(array, socketId))
                        array.put(socketId.intValue());
                }

                if (key.isValid() && key.isReadable()) {
                    if (!hasIds(array, socketId))
                        array.put(socketId.intValue());
                }
//                            if (key.isValid() && key.isWritable()) {
//                            }
            } catch (Exception e) {
                context.error(ERROR_CODE);
                e.printStackTrace();
                try {
                    if (key != null) {
                        key.cancel();
                        key.channel().close();
                    }
                    if (selector != null) {
                        selector.close();
                        selector = null;
                    }
                } catch (Exception e1) {
                    e1.printStackTrace();
                }

            }
        }
//        selector.close();
        return array;
    }


    private boolean hasIds(JSONArray array, int id) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            int anInt = array.getInt(i);
            if (anInt == id)
                return true;
        }
        return false;
    }

    private List<Integer> getList(JSONArray jsonArray) throws JSONException {
        List<Integer> socketIdList = new ArrayList<Integer>();
        for (int i = 0; i < jsonArray.length(); i++) {
            int socketId = jsonArray.getInt(i);
            socketIdList.add(socketId);
        }

        return socketIdList;
    }

    public void send(CordovaArgs args, final CallbackContext context) throws JSONException {
        int socketId = args.getInt(0);
        final byte[] data = args.getArrayBuffer(1);

        final SocketData sd = sockets.get(Integer.valueOf(socketId));
        if (sd == null) {
            context.error(ERROR_CODE);
            Log.d(TAG, ERROR_NOT_CREATE);
            return;
        }
        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                int result = sd.send(data, context);
                if (result <= 0) {
                    context.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, ERROR_CODE));
                } else {
                    context.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
                }
            }
        });
    }

    public void recv(CordovaArgs args, final CallbackContext context) throws JSONException {
        int socketId = args.getInt(0);
        final int bufferSize = args.getInt(1);

        final SocketData sd = sockets.get(Integer.valueOf(socketId));
        if (sd == null) {
            context.error(ERROR_CODE);
            Log.d(TAG, ERROR_NOT_CREATE);
            return;
        }

        // Will call the callback once it has some data.

        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                sd.recv(bufferSize, context);
            }
        });
    }

    public void recvfrom(CordovaArgs args, final CallbackContext context) throws JSONException {
        int socketId = args.getInt(0);
        final int bufferSize = args.getInt(1);

        final SocketData sd = sockets.get(Integer.valueOf(socketId));
        if (sd == null) {
            context.error(ERROR_CODE);
            Log.d(TAG, ERROR_NOT_CREATE);
            return;
        }

        // Will call the callback once it has some data.

        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    sd.recvfrom(bufferSize, context);
                } catch (Exception e) {
                    e.printStackTrace();
                    context.error(ERROR_CODE);
                }
            }
        });
    }

    private void connect(CordovaArgs args, final CallbackContext context) throws JSONException {
        String[] info = args.getString(1).split(":");
        final int socketId = args.getInt(0);
        final String address = info[0];
        final int port = Integer.parseInt(info[1]);

        final SocketData sd = sockets.get(Integer.valueOf(socketId));
        if (sd == null) {
            context.error(ERROR_CODE);
            Log.d(TAG, ERROR_NOT_CREATE);
            return;
        }

        // The SocketData.connect() method will callback appropriately
        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    sd.connect(address, port, context);
                } catch (Exception e) {
                    context.error(ERROR_CODE);
                    Log.d(TAG, ERROR_CONNECT);
                    e.printStackTrace();
                }
            }
        });
    }

    private void sendto(CordovaArgs args, final CallbackContext context) throws JSONException {
        String[] info = args.getString(1).split(":");
        final int socketId = args.getInt(0);
        final String address = info[0];
        final int port = Integer.parseInt(info[1]);
        final byte[] data = args.getArrayBuffer(2);

        final SocketData sd = sockets.get(Integer.valueOf(socketId));
        if (sd == null) {
            context.error(ERROR_CODE);
            Log.d(TAG, ERROR_NOT_CREATE);
            return;
        }

        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    int result = sd.sendto(address, port, data);
                    if (result <= 0) {
                        context.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, ERROR_CODE));
                    } else {
                        context.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    context.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, -1));
                }
            }
        });
    }

    private synchronized int addSocket(SocketData sd) {
        sockets.put(Integer.valueOf(nextSocket), sd);
        return nextSocket++;
    }

    private enum SocketType {
        TCP, TCP_SERVER, UDP
    }

    private class SocketData {

        private ServerSocketChannel tcpServer;
        private SocketChannel tcpSocket;
        private int socketId;

        private DatagramChannel udpSocket;
        private Charset charset = Charset.forName("UTF-8");

        private SocketType type;

        // Cached values used by UDP read()/write().
        // These are the REMOTE address and port.
        private InetAddress address;
        private int port;

        private boolean isClose;
        private int localPort;

        private boolean connected = false; // Only applies to UDP, where connect() restricts who the tcpSocket will receive from.
        private boolean isBinded = false;


        public SocketData(SocketType type) {
            this.type = type;
        }

        public SocketData(SocketChannel incoming) {
            this.type = SocketType.TCP;
            tcpSocket = incoming;
            connected = true;
            address = incoming.socket().getInetAddress();
            port = incoming.socket().getPort();
        }

        public void setSocketId(int socketId) {
            this.socketId = socketId;
        }

        public void connect(String address, int port, final CallbackContext context) throws Exception {
            if (type == SocketType.TCP_SERVER) {
                context.error(ERROR_CODE);
                Log.d(TAG, ERROR_NOT_CLIENT);
            }
            init();
            if (type == SocketType.TCP) {
                tcpSocket.socket().connect(new InetSocketAddress(address, port),6000);
            } else {
                udpSocket.connect(new InetSocketAddress(address, port));
            }

            context.success();
            connected = true;
        }

        public boolean listen() {
            return isBinded;
        }

        public void bind(String address, int port) throws Exception {
            init();
            InetSocketAddress isa = new InetSocketAddress(address, port);
            if (type == SocketType.TCP) {
                tcpSocket.socket().bind(isa);
                isBinded = true;
            } else if (type == SocketType.TCP_SERVER) {
                tcpServer.socket().bind(isa);
                isBinded = true;
            } else {
                udpSocket.socket().bind(isa);
                isBinded = true;
            }
        }

        public void close() throws Exception {
            if (tcpServer != null || tcpSocket != null || udpSocket != null) {
                closeAll();
            }
        }

        private void closeAll() throws Exception {
            connected = false;
            isClose = true;

            if (tcpServer != null)
                tcpServer.close();

            if (tcpSocket != null)
                tcpSocket.close();

            if (udpSocket != null)
                udpSocket.close();


            tcpSocket = null;
            udpSocket = null;
            tcpServer = null;
        }

        public void select(Selector selector) throws Exception {
            selectImply(selector);
        }

        private void selectImply(Selector selector) throws Exception {
            init();
            if (type == SocketType.TCP) {
                tcpSocket.configureBlocking(false);
                SelectionKey register = tcpSocket.register(selector, SelectionKey.OP_READ);
                register.attach(socketId);
            } else if (type == SocketType.TCP_SERVER) {
                tcpServer.configureBlocking(false);
                SelectionKey register = tcpServer.register(selector, SelectionKey.OP_ACCEPT);
                register.attach(socketId);
            } else {
                udpSocket.configureBlocking(false);
                SelectionKey register = udpSocket.register(selector, SelectionKey.OP_READ);
                register.attach(socketId);
            }
        }


        public int send(byte[] data, final CallbackContext context) {
            int write = 0;
            try {
                if (type == SocketType.TCP) {
                    if (tcpSocket == null) {
                        System.out.println("send tcpSocket is null");
                        return 0;
                    }
                    write = tcpSocket.write(ByteBuffer.wrap(data));
                } else {
                    if (udpSocket == null) {
                        System.out.println("send udpSocket is null");
                        return 0;
                    }
                    write = udpSocket.write(ByteBuffer.wrap(data));
                }
            } catch (IOException e) {
                context.error(ERROR_CODE);
                e.printStackTrace();
            }
            return write;
        }

        public void setsockopt(boolean enable, int option) throws Exception {
            init();
            if (option == OPTION_REUSERADDR) {
                setReuseAddress(enable);
            } else if (option == OPTION_BROADCAST) {
                setBroadcast(enable);
            }
        }

        private void setBroadcast(boolean enable) throws Exception {
            if (type == SocketType.UDP) {
                udpSocket.socket().setBroadcast(enable);
            }
        }

        public void getsockname(final CallbackContext context) throws Exception {
            if (type == SocketType.TCP && tcpSocket != null) {
                String address = tcpSocket.socket().getLocalAddress().getHostAddress() + ":" + tcpSocket.socket().getLocalPort();
                if (!TextUtils.isEmpty(address)) {
                    context.success(address);
                    return;
                }
            } else if (type == SocketType.TCP_SERVER && tcpServer != null) {
                String hostAddress = tcpServer.socket().getInetAddress().getHostAddress();
                String finalAddress = hostAddress.equals("::") ? "0.0.0.0" : (hostAddress.equals("::1") ? "127.0.0.1" : hostAddress);
                String address = finalAddress + ":" + tcpServer.socket().getLocalPort();
                if (!TextUtils.isEmpty(address)) {
                    context.success(address);
                    return;
                }
            } else if (type == SocketType.UDP && udpSocket != null) {
                String address = udpSocket.socket().getLocalAddress().getHostAddress() + ":" + udpSocket.socket().getLocalPort();
                if (!TextUtils.isEmpty(address)) {
                    context.success(address);
                    return;
                }
            }
            Log.d(TAG, ERROR_GETLOCAL);
            context.error(ERROR_CODE);
        }

        public void getpeername(final CallbackContext context) throws Exception {
            if (type == SocketType.TCP && tcpSocket != null) {
                String address = tcpSocket.socket().getInetAddress().getHostAddress() + ":" + tcpSocket.socket().getPort();
                if (!TextUtils.isEmpty(address)) {
                    context.success(address);
                    return;
                }
            } else if (type == SocketType.UDP && udpSocket != null) {
                String address = udpSocket.socket().getInetAddress().getHostAddress() + ":" + udpSocket.socket().getPort();
                if (!TextUtils.isEmpty(address)) {
                    context.success(address);
                    return;
                }
            }
            Log.d(TAG, ERROR_GETPEER);
            context.error(ERROR_CODE);
        }

        private void setReuseAddress(boolean enable) throws Exception {
            if (type == SocketType.TCP) {
                tcpSocket.socket().setReuseAddress(enable);
            } else if (type == SocketType.TCP_SERVER) {
                tcpServer.socket().setReuseAddress(enable);
            } else {
                udpSocket.socket().setReuseAddress(enable);
            }
        }

        public void shutdown(int how) throws Exception {
            if (type == SocketType.TCP && tcpSocket != null) {
                if (!tcpSocket.socket().isClosed()) {
                    switch (how) {
                        case SHUTDOWN_READ_WRITE:
                            tcpSocket.socket().shutdownInput();
                            tcpSocket.socket().shutdownOutput();
                            break;
                        case SHUTDOWN_READ:
                            tcpSocket.socket().shutdownInput();
                            break;
                        case SHUTDOWN_WRITE:
                            tcpSocket.socket().shutdownOutput();
                            break;
                    }
                }
            }
        }

        public void recv(int size, CallbackContext context) {

            //***用channel.read()获取客户端消息***//
            //：接收时需要考虑字节长度
            if (type == SocketType.TCP_SERVER) {
                context.error(ERROR_CODE);
                return;
            }

            if (type == SocketType.TCP && tcpSocket == null) {
                context.error(ERROR_CODE);
                System.out.println("tcpSocket is null");
                return;
            }


            if (type == SocketType.UDP && udpSocket == null) {
                context.error(ERROR_CODE);
                System.out.println("udpSocket is null");
                return;
            }

            ByteBuffer buf = ByteBuffer.allocate(size);
            byte[] data = new byte[1];
            int bytesRead = 0;
            if (type == SocketType.TCP) {
                try {
                    bytesRead = tcpSocket.read(buf);
                    if (bytesRead > 0) {
                        buf.flip();
                        data = new byte[bytesRead];
                        buf.get(data, 0, data.length);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    context.error(ERROR_CODE);
                    System.out.println(ERROR_CLOSE);
                }
            } else {
                try {
                    udpSocket.receive(buf);
                    buf.flip();
                    bytesRead = buf.remaining();
                    data = new byte[bytesRead];
                    buf.get(data, 0, data.length);
                } catch (IOException e) {
                    e.printStackTrace();
                    context.error(ERROR_CODE);
                    System.out.println(ERROR_CLOSE);
                }
            }

            if (bytesRead < 0) {
//                try {
//                    if (type == SocketType.TCP)
//                        tcpSocket.close();
//                    else
//                        udpSocket.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }

//                context.error(ERROR_CLOSE);
//                System.out.println(ERROR_CLOSE);
                data = new byte[0];
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, data);
                context.sendPluginResult(pluginResult);

            } else {
                if (data.length > 0) {
                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, data);
//                    pluginResult.setKeepCallback(true);
                    context.sendPluginResult(pluginResult);
                }
            }

        }

        public void recvfrom(int size, CallbackContext context) throws Exception {
            if (type == SocketType.UDP && udpSocket != null) {
                ByteBuffer buf = ByteBuffer.allocate(size);
                byte[] data = new byte[1];
                int bytesRead = 0;

                InetSocketAddress address = (InetSocketAddress)udpSocket.receive(buf);
                buf.flip();
                bytesRead = buf.remaining();
                data = new byte[bytesRead];
                buf.get(data, 0, data.length);

                String result = address.getHostString() + ":" + address.getPort();

                JSONObject object = new JSONObject();
                object.put("SocketAddr",result);
                object.put("ByteBase64", Base64.encodeToString(data,Base64.NO_WRAP));

                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, object);
                context.sendPluginResult(pluginResult);

            } else {
                context.error(ERROR_CODE);
            }
        }


        public int sendto(String address, int port, byte[] data) throws Exception {
            init();
            ByteBuffer buf = ByteBuffer.allocate(data.length);
            buf.clear();
            buf.put(data);
            buf.flip();

            return udpSocket.send(buf, new InetSocketAddress(address, port));
        }

        private synchronized void init() throws Exception {
            if (type == SocketType.TCP) {
                if (tcpSocket == null)
                    tcpSocket = SocketChannel.open();
            } else if (type == SocketType.TCP_SERVER) {
                if (tcpServer == null)
                    tcpServer = ServerSocketChannel.open();
            } else {
                if (udpSocket == null)
                    udpSocket = DatagramChannel.open();
//                udpSocket.socket().setBroadcast(true);
            }
        }

        public void accept(CallbackContext context) throws Exception {
            if (type != SocketType.TCP_SERVER || tcpServer == null) {
                System.out.println("accept() is not supported on client sockets. Call listen() first.");
                Log.d(TAG, ERROR_ACCEPT);
                throw new Exception(ERROR_ACCEPT);
            }

            //设置为非阻塞
            SocketChannel sc = null;
            if ((sc = tcpServer.accept()) != null) {
                SocketData sd = new SocketData(sc);
                int id = UltracreationSocket.this.addSocket(sd);
                sd.setSocketId(id);
                String address = sc.socket().getInetAddress().getHostAddress() + ":" + sc.socket().getPort();
                JSONObject result = new JSONObject();
                result.put("SocketId", id);
                result.put("SocketAddr", address);
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
                context.sendPluginResult(pluginResult);
            }

        }
    }
}
