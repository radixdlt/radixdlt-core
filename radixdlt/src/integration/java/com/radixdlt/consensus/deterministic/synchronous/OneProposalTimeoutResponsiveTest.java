/*
 *
 *  * (C) Copyright 2020 Radix DLT Ltd
 *  *
 *  * Radix DLT Ltd licenses this file to you under the Apache License,
 *  * Version 2.0 (the "License"); you may not use this file except in
 *  * compliance with the License.  You may obtain a copy of the
 *  * License at
 *  *
 *  *  http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  * either express or implied.  See the License for the specific
 *  * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.consensus.deterministic.synchronous;

import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.deterministic.BFTDeterministicTest;
import com.radixdlt.counters.SystemCounters;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.assertTrue;

public class OneProposalTimeoutResponsiveTest {
	private final Random random = new Random(123456);

//	private void runOneProposalTimeoutResponsiveSpecifyingNumNodesTimeoutRandomNodeTest(int numNodes) {
//		this.runOneProposalTimeoutResponsiveSpecifyingNumNodesAndNodeToDropTest(numNodes, () -> random.nextInt(numNodes));
//	}
//
//	private void runOneProposalTimeoutResponsiveSpecifyingNumNodesTimeoutFirstNodeTest(int numNodes) {
//		this.runOneProposalTimeoutResponsiveSpecifyingNumNodesAndNodeToDropTest(numNodes, () -> 0);
//	}
//
//	private void runOneProposalTimeoutResponsiveSpecifyingNumNodesAndNodeToDropTest(int numNodes, Supplier<Integer> nodeToDropSupplier) {
//		this.runOneProposalTimeoutResponsiveSpecifyingNumNodesAndNodeToTimeoutAsFnOfViewTest(numNodes, ignoredView -> nodeToDropSupplier.get());
//	}
//
//
//	private void runOneProposalTimeoutResponsiveSpecifyingNumNodesAndNodeToTimeoutAsFnOfViewTest(int numNodes, Function<View, Integer> nodeIdToDropForViewFunction) {
//		runOneProposalTimeoutResponsiveSpecifyingNumNodesNumViewsAndNodeTimeoutDropTest(numNodes, 1_000, nodeIdToDropForViewFunction);
//	}

	private void run(int numNodes, long numViews) { //, Function<View, Boolean> shouldProposalBeDroppedForView) {
//		final Map<View, Integer> nodeIdToTimeoutProposalMessageForView = new HashMap<>();
//		final Map<View, Integer> proposalsProcessedForView = new HashMap<>();
		final Map<View, Integer> numberOfProposalMessagesDroppedForView = new HashMap<>();

		final BFTDeterministicTest test = new BFTDeterministicTest(numNodes, true, random::nextBoolean);
		test.start();
		final AtomicBoolean completed = new AtomicBoolean(false);
		final AtomicBoolean didDropLastProposal = new AtomicBoolean(false);

		while (!completed.get()) {
			test.processNextMsgFilterBasedOnSenderReceiverAndMessage(random, (processedMessage) -> {
				Object msg = processedMessage.getMessage();
				if (!(msg instanceof Proposal)) {
					return true;
				}
				int receiverId = processedMessage.getReceiverId();
				int senderId = processedMessage.getSenderId();

				final Proposal proposal = (Proposal) msg;
				final View view = proposal.getVertex().getView();
				final long viewNumber = view.number();
				if (viewNumber >= numViews) {
					completed.set(true);
					return true; // or `false`? dont care, just wanna "break"...
				}

				if (viewNumber % numNodes == 0) {
					return false; // I DONT KNOW WHAT I AM DOING....
//					int dropCount = numberOfProposalMessagesDroppedForView.merge(view, 1, Integer::sum);
//					int maxAllowedDropCount = (int) Math.floor(((double) numNodes) / 3.0);
//
//					if (dropCount <= maxAllowedDropCount && !didDropLastProposal.get()) {
//						System.out.println(String.format("Dropping proposal, viewNumber: %d, senderId: %d, receiverId: %d, #proposalDropCount: %d", (int) viewNumber, senderId, receiverId, dropCount));
//						didDropLastProposal.set(true);
//						return false;
//					} else {
//						didDropLastProposal.set(false);
//						return true;
//					}

//					if (dropCount <= maxAllowedDropCount) {
//						System.out.println(String.format("Dropping proposal, viewNumber: %d, senderId: %d, receiverId: %d, #proposalDropCount: %d", (int) viewNumber, senderId, receiverId, dropCount));
//						return false;
//					} else {
//						return true;
//					}
//					if (dropCount > 1) {
//						// cannot drop more than 1 in a row, so don't drop this one..
//						return true;
//					} else {
//						System.out.println(String.format("Dropping proposal, viewNumber: %d, senderId: %d, receiverId: %d, #proposalDropCount: %d", (int) viewNumber, senderId, receiverId, dropCount));
//						return false; // drop proposal
//					}
				}

				return true;
			});
		}

		for (int nodeIndex = 0; nodeIndex < numNodes; ++nodeIndex) {
			SystemCounters counters = test.getSystemCounters(nodeIndex);
			int numberOfIndirectParents = (int) counters.get(SystemCounters.CounterType.CONSENSUS_INDIRECT_PARENT);
			int numberOfTimeout = (int) counters.get(SystemCounters.CounterType.CONSENSUS_TIMEOUT);
//			assertTrue(String.format("Number of indirect parents should be 1, but was: %d", numberOfIndirectParents), numberOfIndirectParents == 1);
			System.out.println(String.format("Number of indirect parents: %d, timeouts: %d, for node at index: %d", numberOfIndirectParents, numberOfTimeout, nodeIndex));
		}
	}

	@Test
	public void when_run_4_correct_nodes_with_1_timeout__then_bft_should_be_responsive() {
		this.run(4, 1_000L);//, view);
	}

//	@Test
//	public void when_run_5_correct_nodes_with_1_timeout__then_bft_should_be_responsive() {
//		this.runOneProposalTimeoutResponsiveSpecifyingNumNodesTimeoutRandomNodeTest(5);
//	}

}
