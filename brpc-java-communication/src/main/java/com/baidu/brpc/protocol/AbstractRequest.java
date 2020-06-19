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
package com.baidu.brpc.protocol;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import com.baidu.brpc.RpcMethodInfo;
import com.baidu.brpc.client.CommunicationClient;
import com.baidu.brpc.client.RpcCallback;
import com.baidu.brpc.client.RpcFuture;
import com.baidu.brpc.exceptions.RpcException;
import com.baidu.brpc.protocol.nshead.NSHead;
import com.baidu.brpc.protocol.push.SPHead;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class AbstractRequest implements Request {
    private Object msg;
    // used to find RpcFuture, application can not set it.
    private long correlationId;
    // used to identify request for application, application can set it.
    private long logId;
    private Object target;
    private Method targetMethod;
    private RpcMethodInfo rpcMethodInfo;
    private String serviceName;
    private String methodName;
    private Object[] args;
    private NSHead nsHead;
    private SPHead spHead;
    private Map<String, Object> kvAttachment;
    private ByteBuf binaryAttachment;
    private int compressType;
    private RpcException exception;
    private Channel channel;
    private Set<CommunicationClient> selectedInstances;
    private String auth;
    private Long traceId;
    private Long spanId;
    private Long parentSpanId;
    private RpcCallback callback;
    private String serviceTag;
    private Integer readTimeoutMillis;
    private Integer writeTimeoutMillis;
    private String clientName;
    private boolean oneWay; // if false, do not need send response.
    private RpcFuture rpcFuture; // just used by client
    private ByteBuf sendBuf; // just used by client
    private CommunicationClient communicationClient;
    private boolean heartbeat;

    /**
     * 订阅信息，客户端请求时，将订阅的服务信息存入
     * - Stargate使用
     */
    private SubscribeInfo subscribeInfo;

    @Override
    public void reset() {
        msg = null;
        logId = -1;
        target = null;
        targetMethod = null;
        rpcMethodInfo = null;
        serviceName = null;
        methodName = null;
        args = null;
        nsHead = null;
        kvAttachment = null;
        binaryAttachment = null;
        compressType = 0;
        exception = null;
        channel = null;
        selectedInstances = null;
        traceId = null;
        spanId = null;
        parentSpanId = null;
        callback = null;
        serviceTag = null;
        readTimeoutMillis = null;
        writeTimeoutMillis = null;
        oneWay = false;
        rpcFuture = null;
        sendBuf = null;
        communicationClient = null;
        heartbeat = false;
    }

    @Override
    public Request retain() {
        if (binaryAttachment != null) {
            binaryAttachment.retain();
        }
        return this;
    }

    @Override
    public void release() {
        if (binaryAttachment != null && binaryAttachment.refCnt() > 0) {
            binaryAttachment.release();
            binaryAttachment = null;
        }
    }
}
