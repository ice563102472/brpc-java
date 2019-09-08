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

package com.baidu.brpc.protocol.stargate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

/**
 * copy from StarGate 1.2.18
 */
@Setter
@Getter
@ToString
@NoArgsConstructor
public final class StargateRpcResponsePacket {
	private String id;

	private Object exception;

	private Object result;

	private Map<String, Object> attachments;

	public StargateRpcResponsePacket(String id, Object result, Throwable exception) {
		this.id = id;
		this.result = result;
		this.exception = exception;
	}

}
