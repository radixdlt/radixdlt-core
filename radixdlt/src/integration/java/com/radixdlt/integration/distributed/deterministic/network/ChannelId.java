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
 * ID for a channel between two nodes.
 */
public final class ChannelId {
	private final int senderIndex;
	private final int receiverIndex;

	private ChannelId(int senderIndex, int receiverIndex) {
		this.senderIndex = senderIndex;
		this.receiverIndex = receiverIndex;
	}

	public static ChannelId of(int senderIndex, int receiverIndex) {
		return new ChannelId(senderIndex, receiverIndex);
	}

	public int receiverIndex() {
		return this.receiverIndex;
	}

	public int senderIndex() {
		return this.senderIndex;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.senderIndex, this.receiverIndex);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ChannelId)) {
			return false;
		}

		ChannelId other = (ChannelId) obj;
		return this.senderIndex == other.senderIndex && this.receiverIndex == other.receiverIndex;
	}

	@Override
	public String toString() {
		return this.senderIndex + "->" + this.receiverIndex;
	}
}

