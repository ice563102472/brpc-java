/*
 * Copyright (c) 2019 Baidu, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.baidu.brpc.server;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.baidu.brpc.protocol.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baidu.brpc.ChannelInfo;
import com.baidu.brpc.client.AsyncAwareFuture;
import com.baidu.brpc.client.RpcFuture;
import com.baidu.brpc.exceptions.RpcException;
import com.baidu.brpc.interceptor.Interceptor;
import com.baidu.brpc.interceptor.ServerInvokeInterceptor;
import com.baidu.brpc.naming.BrpcURL;
import com.baidu.brpc.naming.NamingOptions;
import com.baidu.brpc.naming.NamingService;
import com.baidu.brpc.naming.NamingServiceFactory;
import com.baidu.brpc.naming.NamingServiceFactoryManager;
import com.baidu.brpc.naming.RegisterInfo;
import com.baidu.brpc.protocol.push.ServerPushProtocol;
import com.baidu.brpc.server.handler.RpcServerChannelIdleHandler;
import com.baidu.brpc.server.handler.RpcServerHandler;
import com.baidu.brpc.server.push.RegisterServiceImpl;
import com.baidu.brpc.spi.ExtensionLoaderManager;
import com.baidu.brpc.thread.ClientTimeoutTimerInstance;
import com.baidu.brpc.thread.ShutDownManager;
import com.baidu.brpc.utils.BrpcConstants;
import com.baidu.brpc.utils.CollectionUtils;
import com.baidu.brpc.utils.CustomThreadFactory;
import com.baidu.brpc.utils.NetUtils;
import com.baidu.brpc.utils.ThreadPool;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollMode;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import lombok.Getter;

/**
 * Created by wenweihu86 on 2017/4/24.
 */
@Getter
public class RpcServer {
    private static final Logger LOG = LoggerFactory.getLogger(RpcServer.class);

    private RpcServerOptions rpcServerOptions = new RpcServerOptions();

    /**
     * host to bind
     */
    private String host;

    /**
     * port to bind
     */
    private int port;

    /**
     * netty bootstrap
     */
    private ServerBootstrap bootstrap;

    /**
     * netty io thread pool
     */
    private EventLoopGroup bossGroup;
    // netty io thread pool
    private EventLoopGroup workerGroup;
    private List<Interceptor> interceptors = new ArrayList<Interceptor>();
    private Protocol protocol;
    private ThreadPool threadPool;
    private List<ThreadPool> customThreadPools = new ArrayList<ThreadPool>();
    private NamingService namingService;
    private List<Object> serviceList = new ArrayList<Object>();
    private List<RegisterInfo> registerInfoList = new ArrayList<RegisterInfo>();
    private ServerStatus serverStatus;
    private AtomicBoolean stop = new AtomicBoolean(false);

    private Timer timeoutTimer;

    public RpcServer(int port) {
        this(null, port, new RpcServerOptions(), null);
    }

    public RpcServer(String host, int port) {
        this(host, port, new RpcServerOptions(), null);
    }

    public RpcServer(int port, RpcServerOptions options) {
        this(null, port, options, null);
    }

    public RpcServer(String host, int port, RpcServerOptions options) {
        this(host, port, options, null);
    }

    public RpcServer(int port, RpcServerOptions options, List<Interceptor> interceptors) {
        this(null, port, options, interceptors);
    }

    public RpcServer(String host, int port,
                     final RpcServerOptions options,
                     List<Interceptor> interceptors) {
        this.host = host;
        this.port = port;
        if (options != null) {
            try {
                this.rpcServerOptions.copyFrom(options);
            } catch (Exception ex) {
                LOG.warn("init options failed, so use default");
            }
        }
        if (interceptors != null) {
            this.interceptors.addAll(interceptors);
        }
        ExtensionLoaderManager.getInstance().loadAllExtensions(rpcServerOptions.getEncoding());
        if (StringUtils.isNotBlank(rpcServerOptions.getNamingServiceUrl())) {
            BrpcURL url = new BrpcURL(rpcServerOptions.getNamingServiceUrl());
            NamingServiceFactory namingServiceFactory = NamingServiceFactoryManager.getInstance()
                    .getNamingServiceFactory(url.getSchema());
            this.namingService = namingServiceFactory.createNamingService(url);
        }
        // find protocol
        if (rpcServerOptions.getProtocolType() != null) {
            this.protocol = ProtocolManager.getInstance().getProtocol(rpcServerOptions.getProtocolType());
        }

        threadPool = new ThreadPool(rpcServerOptions.getWorkThreadNum(),
                new CustomThreadFactory("server-work-thread"));
        bootstrap = new ServerBootstrap();
        if (rpcServerOptions.getIoEventType() == BrpcConstants.IO_EVENT_NETTY_EPOLL) {
            bossGroup = new EpollEventLoopGroup(rpcServerOptions.getAcceptorThreadNum(),
                    new CustomThreadFactory("server-acceptor-thread"));
            workerGroup = new EpollEventLoopGroup(rpcServerOptions.getIoThreadNum(),
                    new CustomThreadFactory("server-io-thread"));
            ((EpollEventLoopGroup) bossGroup).setIoRatio(100);
            ((EpollEventLoopGroup) workerGroup).setIoRatio(100);
            bootstrap.channel(EpollServerSocketChannel.class);
            bootstrap.option(EpollChannelOption.EPOLL_MODE, EpollMode.EDGE_TRIGGERED);
            bootstrap.childOption(EpollChannelOption.EPOLL_MODE, EpollMode.EDGE_TRIGGERED);
            LOG.info("use netty epoll edge trigger mode");
        } else {
            bossGroup = new NioEventLoopGroup(rpcServerOptions.getAcceptorThreadNum(),
                    new CustomThreadFactory("server-acceptor-thread"));
            workerGroup = new NioEventLoopGroup(rpcServerOptions.getIoThreadNum(),
                    new CustomThreadFactory("server-io-thread"));
            ((NioEventLoopGroup) bossGroup).setIoRatio(100);
            ((NioEventLoopGroup) workerGroup).setIoRatio(100);
            bootstrap.channel(NioServerSocketChannel.class);
            LOG.info("use jdk nio event mode");
        }

        bootstrap.option(ChannelOption.SO_BACKLOG, rpcServerOptions.getBacklog());
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, rpcServerOptions.isKeepAlive());
        bootstrap.childOption(ChannelOption.TCP_NODELAY, rpcServerOptions.isTcpNoDelay());
        bootstrap.childOption(ChannelOption.SO_REUSEADDR, true);
        bootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        bootstrap.childOption(ChannelOption.SO_LINGER, rpcServerOptions.getSoLinger());
        bootstrap.childOption(ChannelOption.SO_SNDBUF, rpcServerOptions.getSendBufferSize());
        bootstrap.childOption(ChannelOption.SO_RCVBUF, rpcServerOptions.getReceiveBufferSize());
        final RpcServerHandler rpcServerHandler = new RpcServerHandler(RpcServer.this);
        ChannelInitializer<SocketChannel> initializer = new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(
                        "idleStateAwareHandler", new IdleStateHandler(
                                rpcServerOptions.getReaderIdleTime(),
                                rpcServerOptions.getWriterIdleTime(),
                                rpcServerOptions.getKeepAliveTime()));
                ch.pipeline().addLast("idle", new RpcServerChannelIdleHandler());
                ch.pipeline().addLast(rpcServerHandler);
            }
        };
        bootstrap.group(bossGroup, workerGroup).childHandler(initializer);

        this.serverStatus = new ServerStatus(this);

        // register shutdown hook to jvm
        ShutDownManager.getInstance();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                RpcServer.this.shutdown();
            }
        }));

        timeoutTimer = ClientTimeoutTimerInstance.getOrCreateInstance();

        // 注册serverPush的内部服务接口
        if (protocol instanceof ServerPushProtocol) {
            registerService(new RegisterServiceImpl());
        }
    }

    public void registerService(Object service) {
        registerService(service, null, null, null);
    }

    public void registerService(Object service, NamingOptions namingOptions) {
        registerService(service, null, namingOptions, null);
    }

    public void registerService(Object service, Class targetClass, NamingOptions namingOptions) {
        registerService(service, targetClass, namingOptions, null);
    }

    public void registerService(Object service, RpcServerOptions serverOptions) {
        registerService(service, null, null, serverOptions);
    }

    /**
     * register service which can be accessed by client
     *
     * @param service       the service object which implement rpc interface.
     * @param namingOptions register center info
     * @param serverOptions service own custom RpcServerOptions
     *                      if not null, the service will not use the shared thread pool.
     */
    public void registerService(Object service, Class targetClass, NamingOptions namingOptions,
                                RpcServerOptions serverOptions) {
        serviceList.add(service);
        RegisterInfo registerInfo = null;
        if (namingOptions != null) {
            registerInfo = new RegisterInfo(namingOptions);
        } else {
            registerInfo = new RegisterInfo();
        }
        if (targetClass != null) {
            registerInfo.setInterfaceName(targetClass.getInterfaces()[0].getName());
        } else {
            registerInfo.setInterfaceName(service.getClass().getInterfaces()[0].getName());
        }
        registerInfo.setHost(NetUtils.getLocalAddress().getHostAddress());
        registerInfo.setPort(port);
        ServiceManager serviceManager = ServiceManager.getInstance();
        ThreadPool customThreadPool = threadPool;
        if (serverOptions != null) {
            customThreadPool = new ThreadPool(serverOptions.getWorkThreadNum(),
                    new CustomThreadFactory(service.getClass().getSimpleName() + "-work-thread"));
            customThreadPools.add(customThreadPool);
        }

        if (targetClass == null) {
            serviceManager.registerService(service, customThreadPool);
        } else {
            serviceManager.registerService(targetClass, service, customThreadPool);
        }
        registerInfoList.add(registerInfo);
    }

    public void start() {
        this.interceptors.add(new ServerInvokeInterceptor());
        try {
            // 判断是否在jarvis环境，若是jarvis环境则以环境变量port为准，否则以用户自定义的port为准
            if (rpcServerOptions.getJarvisPortName() != null) {
                if (System.getenv(rpcServerOptions.getJarvisPortName()) != null) {
                    this.port = Integer.valueOf(System.getenv(rpcServerOptions.getJarvisPortName()));
                }
            }
            ChannelFuture channelFuture;
            if (null != host) {
                channelFuture = bootstrap.bind(host, port);
            } else {
                channelFuture = bootstrap.bind(port);
            }
            channelFuture.sync();
            if (namingService != null) {
                for (RegisterInfo registerInfo : registerInfoList) {
                    namingService.register(registerInfo);
                }
            }
        } catch (InterruptedException e) {
            LOG.error("server failed to start, {}", e.getMessage());
        }
        if (LOG.isInfoEnabled()) {
            LOG.info("server started on port={} success", port);
        }
    }

    public void shutdown() {
        if (stop.compareAndSet(false, true)) {
            if (namingService != null) {
                for (RegisterInfo registerInfo : registerInfoList) {
                    namingService.unregister(registerInfo);
                }
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully().syncUninterruptibly();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().syncUninterruptibly();
            }
            if (threadPool != null) {
                threadPool.stop();
            }
            if (CollectionUtils.isNotEmpty(customThreadPools)) {
                LOG.info("clean customized thread pool");
                for (ThreadPool pool : customThreadPools) {
                    pool.stop();
                }
            }
        }
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public <T> AsyncAwareFuture<T> sendServerPush(Request request) {
        Channel channel = request.getChannel();
        ChannelInfo orCreateServerChannelInfo = ChannelInfo.getOrCreateServerChannelInfo(channel); // todo
        // create RpcFuture object
        RpcFuture rpcFuture = new ServerPushRpcFuture();
        rpcFuture.setRpcMethodInfo(request.getRpcMethodInfo());
        rpcFuture.setCallback(request.getCallback());
        rpcFuture.setChannelInfo(orCreateServerChannelInfo);
        // generate logId
        final long logId = PushServerRpcFutureManager.getInstance().putRpcFuture(rpcFuture);

        // final long logId = FastFutureStore.getInstance(0).put(rpcFuture);
        request.setLogId(logId);
        request.getSpHead().setLogId(logId);
        // read write timeout
        final long readTimeout = request.getReadTimeoutMillis();
        final long writeTimeout = request.getWriteTimeoutMillis();
        Timeout timeout = timeoutTimer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                long timeoutLogId = logId;
                PushServerRpcFutureManager rpcFutureManager = PushServerRpcFutureManager.getInstance();
                RpcFuture rpcFuture = rpcFutureManager.removeRpcFuture(timeoutLogId);

                if (rpcFuture != null) {
                    long elapseTime = System.currentTimeMillis() - rpcFuture.getStartTime();
                    String errMsg = String.format("request timeout,logId=%d,ip=%s,port=%d,elapse=%dms",
                            logId, "?", port, elapseTime);
                    LOG.info(errMsg);
                    Response response = protocol.createResponse();
                    response.setException(new RpcException(RpcException.TIMEOUT_EXCEPTION, errMsg));
                    response.setRpcFuture(rpcFuture);
                    rpcFuture.handleResponse(response);
                } else {
                    LOG.error("timeout rpc is missing, logId={}", timeoutLogId);
                    throw new RpcException(RpcException.UNKNOWN_EXCEPTION, "timeout rpc is missing");
                }
            }
        }, readTimeout, TimeUnit.MILLISECONDS);

        // set the missing parameters
        rpcFuture.setTimeout(timeout);
        try {
            // netty will release the send buffer after sent.
            // we retain here, so it can be used when rpc retry.
            request.retain();

            ByteBuf byteBuf = protocol.encodeRequest(request);
            ChannelFuture sendFuture = channel.writeAndFlush(byteBuf);
            sendFuture.awaitUninterruptibly(writeTimeout);
            if (!sendFuture.isSuccess()) {
                if (!(sendFuture.cause() instanceof ClosedChannelException)) {
                    LOG.warn("send request failed, channelActive={}, ex=",
                            channel.isActive(), sendFuture.cause());
                }
                String errMsg = String.format("send request failed, channelActive=%b, ex=%s",
                        channel.isActive(), sendFuture.cause().getMessage());
                throw new RpcException(RpcException.NETWORK_EXCEPTION, errMsg);
            }
        } catch (Exception ex) {
            timeout.cancel();
            if (ex instanceof RpcException) {
                throw (RpcException) ex;
            } else {
                throw new RpcException(RpcException.SERIALIZATION_EXCEPTION, ex.getMessage());
            }
        }

        return rpcFuture;
    }

}
