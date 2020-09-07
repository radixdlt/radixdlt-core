/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.distributed.deterministic.network;

import java.util.Objects;

/**
 * A message sent over a channel.
 */
public final class ControlledMessage {
	private final ChannelId channelId;
	private final Object message;

	public ControlledMessage(int senderIndex, int receiverIndex, Object message) {
		this(ChannelId.of(senderIndex, receiverIndex), message);
	}

	public ControlledMessage(ChannelId channelId, Object message) {
		this.channelId = channelId;
		this.message = message;
	}

	public ChannelId channelId() {
		return channelId;
	}

	public Object message() {
		return message;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.channelId, this.message);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ControlledMessage) {
			ControlledMessage that = (ControlledMessage) o;
			return Objects.equals(this.channelId, that.channelId) && Objects.equals(this.message, that.message);
		}
		return false;
	}

	@Override
	public String toString() {
		return channelId + " " + message;
	}
}
