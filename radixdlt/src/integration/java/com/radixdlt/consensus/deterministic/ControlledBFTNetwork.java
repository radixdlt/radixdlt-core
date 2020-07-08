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

package com.radixdlt.consensus.deterministic;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.BFTEventSender;
import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.GetVerticesResponse;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.SyncVerticesRPCSender;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.VertexStore.SyncSender;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker.TimeoutSender;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import java.util.List;
import java.util.Objects;

/**
 * A BFT network supporting the EventCoordinatorNetworkSender interface which
 * stores each message in a queue until they are synchronously popped.
 *
 * This class is not thread safe.
 */
public final class ControlledBFTNetwork {
	private final ImmutableList<ECPublicKey> nodes;
	private final MessageQueue messageQueue;

	ControlledBFTNetwork(ImmutableList<ECPublicKey> nodes) {
		this.nodes = nodes;
		this.messageQueue = new MessageQueue();
	}

	static final class ChannelId {
		private final ECPublicKey sender;
		private final ECPublicKey receiver;

		ChannelId(ECPublicKey sender, ECPublicKey receiver) {
			this.sender = sender;
			this.receiver = receiver;
		}

		public ECPublicKey getReceiver() {
			return receiver;
		}

		public ECPublicKey getSender() {
			return sender;
		}

		@Override
		public int hashCode() {
			return Objects.hash(sender, receiver);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof ChannelId)) {
				return false;
			}

			ChannelId other = (ChannelId) obj;
			return this.sender.equals(other.sender) && this.receiver.equals(other.receiver);
		}

		@Override
		public String toString() {
			return sender.euid().toString().substring(0, 6) + " -> " + receiver.euid().toString().substring(0, 6);
		}
	}

	static final class ControlledMessage {
		private final ChannelId channelId;
		private final Object msg;

		ControlledMessage(ECPublicKey sender, ECPublicKey receiver, Object msg) {
			this.channelId = new ChannelId(sender, receiver);
			this.msg = msg;
		}

		public ChannelId getChannelId() {
			return channelId;
		}

		public Object getMsg() {
			return msg;
		}

		@Override
		public String toString() {
			return channelId + " " + msg;
		}
	}

	private void putMessage(View v, ControlledMessage controlledMessage) {
		messageQueue.add(v, controlledMessage);
	}

	public List<ControlledMessage> peekNextMessages() {
		return this.messageQueue.lowestViewMessages();
	}

	public Object popNextMessage(ChannelId channelId) {
		ControlledMessage msg = messageQueue.pop(channelId);
		return msg.getMsg();
	}

	private static class ControlledGetVerticesRequest implements GetVerticesRequest {
		private final Hash id;
		private final int count;
		private final Object opaque;
		private final ECPublicKey requestor;

		private ControlledGetVerticesRequest(Hash id, int count, ECPublicKey requestor, Object opaque) {
			this.id = id;
			this.count = count;
			this.requestor = requestor;
			this.opaque = opaque;
		}

		@Override
		public Hash getVertexId() {
			return id;
		}

		@Override
		public int getCount() {
			return count;
		}
	}

	public ControlledSender getSender(ECPublicKey sender) {
		return new ControlledSender(sender);
	}

	public final class ControlledSender implements BFTEventSender, SyncSender, SyncVerticesRPCSender, TimeoutSender {
		private final ECPublicKey sender;
		private View currentView = View.genesis();

		private ControlledSender(ECPublicKey sender) {
			this.sender = sender;
		}

		@Override
		public void sendGetVerticesRequest(Hash id, ECPublicKey node, int count, Object opaque) {
			putMessage(this.currentView, new ControlledMessage(sender, node, new ControlledGetVerticesRequest(id, count, sender, opaque)));
		}

		@Override
		public void sendGetVerticesResponse(GetVerticesRequest originalRequest, ImmutableList<Vertex> vertices) {
			ControlledGetVerticesRequest request = (ControlledGetVerticesRequest) originalRequest;
			GetVerticesResponse response = new GetVerticesResponse(request.getVertexId(), vertices, request.opaque);
			putMessage(this.currentView, new ControlledMessage(sender, request.requestor, response));
		}

		@Override
		public void synced(Hash vertexId) {
			putMessage(this.currentView, new ControlledMessage(sender, sender, vertexId));
		}

		@Override
		public void broadcastProposal(Proposal proposal) {
			for (ECPublicKey receiver : nodes) {
				putMessage(this.currentView, new ControlledMessage(sender, receiver, proposal));
			}
		}

		@Override
		public void sendNewView(NewView newView, ECPublicKey newViewLeader) {
			putMessage(this.currentView, new ControlledMessage(sender, newViewLeader, newView));
			this.currentView = newView.getView();
		}

		@Override
		public void sendVote(Vote vote, ECPublicKey leader) {
			putMessage(this.currentView, new ControlledMessage(sender, leader, vote));
		}

		public void committedStateSync(CommittedStateSync committedStateSync) {
			putMessage(this.currentView, new ControlledMessage(sender, sender, committedStateSync));
		}

		@Override
		public void scheduleTimeout(View view, long milliseconds) {
			putMessage(view.next(), new ControlledMessage(sender, sender, view));
		}
	}
}
