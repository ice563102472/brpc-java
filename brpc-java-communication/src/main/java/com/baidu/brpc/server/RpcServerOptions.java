/*
 * Copyright (c) 2018 Baidu, Inc. All Rights Reserved.
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

import com.baidu.brpc.utils.BrpcConstants;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by wenweihu86 on 2017/4/24.
 */
@Setter
@Getter
@NoArgsConstructor
public class RpcServerOptions {
    // The keep alive
    private boolean keepAlive = true;
    private boolean tcpNoDelay = true;
    // so linger
    private int soLinger = 5;
    // backlog
    private int backlog = 1024;
    // receive buffer size
    private int receiveBufferSize = 1024 * 64;
    // send buffer size
    private int sendBufferSize = 1024 * 64;
    /**
     * an {@link IdleStateEvent} whose state is {@link IdleState#ALL_IDLE}
     * will be triggered when no read/write was performed for the specified seconds.
     * Specify {@code 0} to disable.
     */
    private int keepAliveTime = 60;
    // acceptor threads, default use Netty default value
    private int acceptorThreadNum = 1;
    // io threads, default use Netty default value
    private int ioThreadNum = Runtime.getRuntime().availableProcessors();
    // real work threads
    private int workThreadNum = Runtime.getRuntime().availableProcessors();
    /**
     * io event type, netty or jdk
     */
    private int ioEventType = BrpcConstants.IO_EVENT_JDK;
    // The max size
    private int maxSize = Integer.MAX_VALUE;

    private int maxTryTimes = 1;

    // server protocol type
    private Integer protocolType;
    private String encoding = "utf-8";
    // bns port name when deploys on Jarvis environment
    private String jarvisPortName;
    // naming service url
    private String namingServiceUrl = "";
    // share global thread pool between multi rpcServer
    private boolean globalThreadPoolSharing = false;

    public RpcServerOptions(RpcServerOptions options) {
        this.copyFrom(options);
    }

    public void copyFrom(RpcServerOptions options) {
        this.acceptorThreadNum = options.acceptorThreadNum;
        this.backlog = options.backlog;
        this.encoding = options.encoding;
        this.ioThreadNum = options.ioThreadNum;
        this.jarvisPortName = options.jarvisPortName;
        this.keepAlive = options.keepAlive;
        this.keepAliveTime = options.keepAliveTime;
        this.maxSize = options.maxSize;
        this.namingServiceUrl = options.namingServiceUrl;
        this.protocolType = options.protocolType;
        this.receiveBufferSize = options.receiveBufferSize;
        this.sendBufferSize = options.sendBufferSize;
        this.soLinger = options.soLinger;
        this.tcpNoDelay = options.tcpNoDelay;
        this.workThreadNum = options.workThreadNum;
        this.globalThreadPoolSharing = options.globalThreadPoolSharing;
    }

    public String toString() {
        return "RpcServerOptions(keepAlive=" + this.isKeepAlive()
                + ", tcpNoDelay=" + this.isTcpNoDelay()
                + ", soLinger=" + this.getSoLinger()
                + ", backlog=" + this.getBacklog()
                + ", receiveBufferSize=" + this.getReceiveBufferSize()
                + ", sendBufferSize=" + this.getSendBufferSize()
                + ", keepAliveTime=" + this.getKeepAliveTime()
                + ", acceptorThreadNum=" + this.getAcceptorThreadNum()
                + ", ioThreadNum=" + this.getIoThreadNum()
                + ", workThreadNum=" + this.getWorkThreadNum()
                + ", ioEventType=" + this.getIoEventType()
                + ", maxSize=" + this.getMaxSize()
                + ", maxTryTimes=" + this.getMaxTryTimes()
                + ", protocolType=" + this.getProtocolType()
                + ", encoding=" + this.getEncoding()
                + ", jarvisPortName=" + this.getJarvisPortName()
                + ", namingServiceUrl=" + this.getNamingServiceUrl()
                + ", globalThreadPoolSharing=" + this.isGlobalThreadPoolSharing() + ")";
    }
}
