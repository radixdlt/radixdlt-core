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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.GetVerticesResponse;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.deterministic.ControlledBFTNetwork.ChannelId;
import com.radixdlt.consensus.deterministic.ControlledBFTNetwork.ControlledMessage;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.utils.UInt256;
import org.checkerframework.checker.nullness.Opt;

import javax.swing.text.html.Option;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A deterministic BFT test where each event that occurs in the BFT network
 * is emitted and processed synchronously by the caller.
 */
public class BFTDeterministicTest {
	private final ImmutableList<ControlledBFTNode> nodes;
	private final ImmutableList<ECPublicKey> pks;
	private final ControlledBFTNetwork network;

	public BFTDeterministicTest(int numNodes, boolean enableGetVerticesRPC) {
		this(numNodes, enableGetVerticesRPC, () -> {
			throw new UnsupportedOperationException();
		});
	}

	public BFTDeterministicTest(int numNodes, boolean enableGetVerticesRPC, BooleanSupplier syncedSupplier) {
		ImmutableList<ECKeyPair> keys = Stream.generate(ECKeyPair::generateNew)
			.limit(numNodes)
			.sorted(Comparator.<ECKeyPair, EUID>comparing(k -> k.getPublicKey().euid()).reversed())
			.collect(ImmutableList.toImmutableList());
		this.pks = keys.stream()
			.map(ECKeyPair::getPublicKey)
			.collect(ImmutableList.toImmutableList());
		this.network = new ControlledBFTNetwork(pks);
		ValidatorSet validatorSet = ValidatorSet.from(
			pks.stream().map(pk -> Validator.from(pk, UInt256.ONE)).collect(Collectors.toList())
		);

		this.nodes = keys.stream()
			.map(key -> new ControlledBFTNode(
				key,
				network.getSender(key.getPublicKey()),
				new WeightedRotatingLeaders(validatorSet, Comparator.comparing(v -> v.nodeKey().euid()), 5),
				validatorSet,
				enableGetVerticesRPC,
				syncedSupplier
			))
			.collect(ImmutableList.toImmutableList());
	}

	public void start() {
		nodes.forEach(ControlledBFTNode::start);
	}

	public void processNextMsg(int toIndex, int fromIndex, Class<?> expectedClass) {
		ChannelId channelId = new ChannelId(pks.get(fromIndex), pks.get(toIndex));
		Object msg = network.popNextMessage(channelId);
		assertThat(msg).isInstanceOf(expectedClass);
		nodes.get(toIndex).processNext(msg);
	}

	public void processNextMsg(Random random) {
		processNextMsg(random, (c, m) -> true);
	}

	public void processNextMsg(Random random, BiPredicate<Integer, Object> filter) {
		processNextMsgFilterBasedOnSenderReceiverAndMessage(random, (processedMessage) -> filter.test(processedMessage.getReceiverId(), processedMessage.getMessage()));
	}

	public static final class ProcessedMessage {
		private Object message;
		private int senderId;
		private int receiverId;
		ProcessedMessage(Object message, int senderId, int receiverId) {
			this.message = Objects.requireNonNull(message);
			this.senderId = senderId;
			this.receiverId = receiverId;
		}

		public Object getMessage() {
			return message;
		}

		public int getSenderId() {
			return senderId;
		}

		public int getReceiverId() {
			return receiverId;
		}
	}

	public void processNextMsgFilterBasedOnSenderReceiverAndMessage(Random random, Function<ProcessedMessage, Boolean> filter) {
		List<ControlledMessage> possibleMsgs = network.peekNextMessages();
		if (possibleMsgs.isEmpty()) {
			throw new IllegalStateException("No messages available (Lost Responsiveness)");
		}

		int indexOfNextMessage =  random.nextInt(possibleMsgs.size());
		ControlledMessage nextControlledMessage = possibleMsgs.get(indexOfNextMessage);
		ChannelId channelId = nextControlledMessage.getChannelId();
		Object msg = network.popNextMessage(channelId);
	
		int senderIndex = pks.indexOf(channelId.getSender());
		int receiverIndex = pks.indexOf(channelId.getReceiver());

		if (filter.apply(new ProcessedMessage(msg, senderIndex, receiverIndex))) {
			nodes.get(receiverIndex).processNext(msg);
		}
	}



	public SystemCounters getSystemCounters(int nodeIndex) {
		return nodes.get(nodeIndex).getSystemCounters();
	}
}
