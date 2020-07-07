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

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.deterministic.BFTDeterministicTest;
import com.radixdlt.consensus.deterministic.BFTDeterministicTest.ProcessedMessage;
import com.radixdlt.consensus.deterministic.BFTDeterministicTest.SyncAndTimeout;
import com.radixdlt.counters.SystemCounters;

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.*;

import org.assertj.core.util.Sets;
import org.junit.Test;

import java.util.Random;
import java.util.Set;

public class OneProposalTimeoutResponsiveTest {
	private final Random random = new Random(123456);
	private final Set<Integer> nodesCompleted = Sets.newHashSet();

	private void run(int numNodes, long numViews, long dropFrequency) {
		final BFTDeterministicTest test = new BFTDeterministicTest(numNodes, SyncAndTimeout.SYNC_AND_TIMEOUT, random::nextBoolean);
		test.start();

		nodesCompleted.clear();
		while (nodesCompleted.size() < numNodes) {
			test.processNextMsgFilterBasedOnSenderReceiverAndMessage(random, pm -> processMessage(pm, numViews, dropFrequency));
		}

		long requiredIndirectParents = (numViews - 1) / dropFrequency; // Edge case if dropFrequency a factor of numViews
		long requiredTimeouts = numViews / dropFrequency;

		for (int nodeIndex = 0; nodeIndex < numNodes; ++nodeIndex) {
			SystemCounters counters = test.getSystemCounters(nodeIndex);
			long numberOfIndirectParents = counters.get(SystemCounters.CounterType.CONSENSUS_INDIRECT_PARENT);
			long numberOfTimeouts = counters.get(SystemCounters.CounterType.CONSENSUS_TIMEOUT);
			// FIXME: Checks should be exact, but issues with synchronisation prevent this
			assertThat("Number of indirect parent proposals", numberOfIndirectParents, greaterThanOrEqualTo(requiredIndirectParents));
			assertThat("Number of timeouts", numberOfTimeouts, greaterThanOrEqualTo(requiredTimeouts));
		}
	}

	private boolean processMessage(ProcessedMessage processedMessage, long numViews, long dropFrequency) {
		Object msg = processedMessage.getMessage();
		if (msg instanceof NewView) {
			NewView nv = (NewView) msg;
			if (nv.getView().number() > numViews) {
				this.nodesCompleted.add(processedMessage.getSenderId());
				return false;
			}
		}
		if (msg instanceof Proposal) {
			final Proposal proposal = (Proposal) msg;
			final View view = proposal.getVertex().getView();
			final long viewNumber = view.number();

			return viewNumber % dropFrequency != 0;
		}
		return true;
	}

	@Test
	public void when_run_3_correct_nodes_with_1_timeout__then_bft_should_be_responsive() {
		this.run(3, 300_000, 100);
	}

	@Test
	public void when_run_4_correct_nodes_with_1_timeout__then_bft_should_be_responsive() {
		this.run(4, 300_000, 100);
	}

	@Test
	public void when_run_100_correct_nodes_with_1_timeout__then_bft_should_be_responsive() {
		this.run(100, 30_000, 100);
	}

}
