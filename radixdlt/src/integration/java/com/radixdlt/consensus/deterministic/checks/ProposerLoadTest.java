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

package com.radixdlt.consensus.deterministic.checks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.deterministic.BFTDeterministicTest;
import com.radixdlt.consensus.deterministic.ControlledBFTNetwork;
import com.radixdlt.crypto.ECPublicKey;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class ProposerLoadTest {

	@Test
	public void when_run_4_correct_nodes_with_channel_order_random_and_timeouts_disabled__then_proposal_load_should_be_completely_uniform() {
		final Random random = new Random(12345);
		int nodeCount = 4;
		final BFTDeterministicTest test = new BFTDeterministicTest(nodeCount, false);

		test.start();
		for (int step = 0; step < 10_000; step++) {
			test.processNextMsg(random);
		}

		ControlledBFTNetwork network = test.getNetwork();
		ImmutableList<ECPublicKey> nodes = network.getNodes();

		ImmutableMap<ECPublicKey, Integer> numberOfProposalsByNodes = network.getNumberOfProposalsByNodes();
		int totalNumberOfProposals = numberOfProposalsByNodes.values().stream().mapToInt(Integer::intValue).sum();

		for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
			ECPublicKey node = nodes.get(nodeIndex);
			float numberOfProposalsByNodeNonNormalized = numberOfProposalsByNodes.getOrDefault(node, 0).floatValue();
			float numberOfProposalsByNodeNormalized = numberOfProposalsByNodeNonNormalized / (float) totalNumberOfProposals;

			assertEquals(1.0f / (float) nodeCount, numberOfProposalsByNodeNormalized, 0);
		}
	}
}
