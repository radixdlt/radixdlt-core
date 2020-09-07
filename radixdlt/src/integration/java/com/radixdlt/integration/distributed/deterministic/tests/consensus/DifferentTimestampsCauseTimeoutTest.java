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

package com.radixdlt.integration.distributed.deterministic.tests.consensus;

import java.util.LinkedList;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignature;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest;
import com.radixdlt.integration.distributed.deterministic.network.ChannelId;
import com.radixdlt.integration.distributed.deterministic.network.ControlledMessage;
import com.radixdlt.integration.distributed.deterministic.network.MessageMutator;
import com.radixdlt.integration.distributed.deterministic.network.MessageSelector;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;

import static org.junit.Assert.fail;

public class DifferentTimestampsCauseTimeoutTest {
	@Test
	public void when_four_nodes_receive_qcs_with_same_timestamps__quorum_is_achieved() {
		final int numNodes = 4;

		LinkedList<Pair<ChannelId, Class<?>>> processingSequence = Lists.newLinkedList();
		processingSequence.add(Pair.of(ChannelId.of(0, 1), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 1), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 1), NewView.class));

		// Proposal here has genesis qc, which has no timestamps
		processingSequence.add(Pair.of(ChannelId.of(1, 0), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 2), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 3), Proposal.class));

		processingSequence.add(Pair.of(ChannelId.of(0, 1), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 1), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 1), Vote.class));

		processingSequence.add(Pair.of(ChannelId.of(0, 2), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 2), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 2), NewView.class));

		// Proposal here should have timestamps from previous view
		// They are not mutated in this test
		processingSequence.add(Pair.of(ChannelId.of(2, 0), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 1), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 3), Proposal.class));

		processingSequence.add(Pair.of(ChannelId.of(0, 2), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 2), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 2), Vote.class));

		processingSequence.add(Pair.of(ChannelId.of(0, 3), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 3), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 3), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 3), NewView.class));

		processingSequence.add(Pair.of(ChannelId.of(3, 0), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 1), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 2), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 3), Proposal.class));

		DeterministicTest.builder()
			.numNodes(numNodes)
			.alwaysSynced()
			.messageSelector(sequenceSelector(processingSequence))
			.messageMutator(mutateProposalsBy(0))
			.build()
			.run();
	}

	@Test
	public void when_four_nodes_receive_qcs_with_different_timestamps__quorum_is_not_achieved() {
		final int numNodes = 4;

		LinkedList<Pair<ChannelId, Class<?>>> processingSequence = Lists.newLinkedList();
		processingSequence.add(Pair.of(ChannelId.of(0, 1), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 1), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 1), NewView.class));

		// Proposal here has genesis qc, which has no timestamps
		processingSequence.add(Pair.of(ChannelId.of(1, 0), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 2), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 3), Proposal.class));

		processingSequence.add(Pair.of(ChannelId.of(0, 1), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 1), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 1), Vote.class));

		processingSequence.add(Pair.of(ChannelId.of(0, 2), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 2), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), NewView.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 2), NewView.class));

		// Proposal here should have timestamps from previous view
		// They are mutated in this test
		processingSequence.add(Pair.of(ChannelId.of(2, 0), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 1), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 3), Proposal.class));

		processingSequence.add(Pair.of(ChannelId.of(0, 2), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 2), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 2), Vote.class));

		// Timeouts from nodes
		processingSequence.add(Pair.of(ChannelId.of(0, 0), LocalTimeout.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), LocalTimeout.class));
		// 2 (leader) will have already moved on to next view from the NewView messages
		processingSequence.add(Pair.of(ChannelId.of(3, 3), LocalTimeout.class));

		DeterministicTest.builder()
			.numNodes(numNodes)
			.alwaysSynced()
			.messageSelector(sequenceSelector(processingSequence))
			.messageMutator(mutateProposalsBy(1))
			.build()
			.run();
	}

	private MessageMutator mutateProposalsBy(int factor) {
		return (rank, message, queue) -> {
			ControlledMessage messageToUse = message;
			Object msg = message.message();
			if (msg instanceof Proposal) {
				Proposal p = (Proposal) msg;
				int receiverIndex = message.channelId().receiverIndex();
				messageToUse = new ControlledMessage(message.channelId(), mutateProposal(p, receiverIndex * factor));
			}
			queue.add(rank, messageToUse);
			return true;
		};
	}

	private Proposal mutateProposal(Proposal p, int destination) {
		QuorumCertificate committedQC = p.getCommittedQC();
		BFTNode author = p.getAuthor();
		Vertex vertex = p.getVertex();
		ECDSASignature signature = p.getSignature();

		return new Proposal(mutateVertex(vertex, destination), committedQC, author, signature, 0L);
	}

	private Vertex mutateVertex(Vertex v, int destination) {
		long epoch = v.getEpoch();
		QuorumCertificate qc = v.getQC();
		View view = v.getView();
		Command command = v.getCommand();

		return new Vertex(epoch, mutateQC(qc,  destination), view, command);
	}

	private QuorumCertificate mutateQC(QuorumCertificate qc, int destination) {
		TimestampedECDSASignatures signatures = qc.getTimestampedSignatures();
		VoteData voteData = qc.getVoteData();

		return new QuorumCertificate(voteData, mutateTimestampedSignatures(signatures, destination));
	}

	private TimestampedECDSASignatures mutateTimestampedSignatures(TimestampedECDSASignatures signatures, int destination) {
		Map<BFTNode, TimestampedECDSASignature> sigs = signatures.getSignatures();
		return new TimestampedECDSASignatures(sigs.entrySet().stream()
			.map(e -> Pair.of(e.getKey(), mutateTimestampedSignature(e.getValue(), destination)))
			.collect(ImmutableMap.toImmutableMap(Pair::getFirst, Pair::getSecond)));
	}

	private TimestampedECDSASignature mutateTimestampedSignature(TimestampedECDSASignature signature, int destination) {
		long timestamp = signature.timestamp();
		UInt256 weight = signature.weight();
		ECDSASignature sig = signature.signature();

		return TimestampedECDSASignature.from(timestamp + destination, weight, sig);
	}

	private MessageSelector sequenceSelector(LinkedList<Pair<ChannelId, Class<?>>> processingSequence) {
		return messages -> {
			if (processingSequence.isEmpty()) {
				// We have finished
				return null;
			}
			final Pair<ChannelId, Class<?>> messageDetails = processingSequence.pop();
			final ChannelId channel = messageDetails.getFirst();
			final Class<?> msgClass = messageDetails.getSecond();
			for (ControlledMessage message : messages) {
				if (channel.equals(message.channelId())
					&& msgClass.isAssignableFrom(message.message().getClass())) {
					return message;
				}
			}
			fail(String.format("Can't find %s message %s: %s", msgClass.getSimpleName(), channel, messages));
			return null; // Not required, but compiler can't tell that fail throws exception
		};
	}
}
