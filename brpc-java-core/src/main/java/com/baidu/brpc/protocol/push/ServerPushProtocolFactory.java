package com.baidu.brpc.protocol.push;

import com.baidu.brpc.protocol.Options;
import com.baidu.brpc.protocol.Protocol;
import com.baidu.brpc.protocol.ProtocolFactory;
import com.baidu.brpc.protocol.push.impl.DefaultServerPushProtocol;

public class ServerPushProtocolFactory implements ProtocolFactory {

	@Override
	public Integer getProtocolType() {
		return Options.ProtocolType.PROTOCOL_BAIDU_JSON_RPC_JSON.getNumber();
	}

	@Override
	public Integer getPriority() {
		return ProtocolFactory.DEFAULT_PRIORITY;
	}

	@Override
	public Protocol createProtocol(String encoding) {
		return new DefaultServerPushProtocol(encoding);
	}
}
