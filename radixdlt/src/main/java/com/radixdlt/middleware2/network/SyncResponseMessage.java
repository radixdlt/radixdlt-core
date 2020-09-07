/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware2.network;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.ledger.CommittedCommand;
import org.radix.network.messaging.Message;

/**
 * Message with sync atoms as a response to sync request
 */
@SerializerId2("message.sync.response")
public final class SyncResponseMessage extends Message {
	@JsonProperty("commands")
	@DsonOutput(Output.ALL)
	private final ImmutableList<CommittedCommand> commands;

	SyncResponseMessage() {
		// Serializer only
		super(0);
		this.commands = null;
	}

	public SyncResponseMessage(int magic, ImmutableList<CommittedCommand> commands) {
		super(magic);
		this.commands = commands;
	}

	public ImmutableList<CommittedCommand> getCommands() {
		return commands == null ? ImmutableList.of() : commands;
	}

	@Override
	public String toString() {
		return String.format("%s{commands=%s}", getClass().getSimpleName(), commands);
	}
}
