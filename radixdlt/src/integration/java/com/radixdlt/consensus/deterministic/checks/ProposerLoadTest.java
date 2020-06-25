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

package com.radixdlt.consensus.deterministic.checks;

import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.deterministic.BFTDeterministicTest;
import com.radixdlt.consensus.deterministic.ControlledBFTNetwork;
import com.radixdlt.crypto.ECPublicKey;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;

public class ProposerLoadTest {

	private static <T> void assertMaxValueDiff(Map<T, Integer> map, T keyToReference, int maxDiff) {
		int reference = map.get(keyToReference);
		for (Map.Entry<T, Integer> pair : map.entrySet()) {
			if (pair.getKey() == keyToReference) {
				continue;
			}
			int value = pair.getValue();
			int diff = Math.abs(value - reference);
			assertThat(diff, lessThanOrEqualTo(maxDiff));
		}
	}

	@Test
	public void when_run_4_correct_nodes_with_channel_order_and_timeouts_disabled__then_proposal_load_should_be_completely_uniform() {
		final Random random = new Random(12345);
		int nodeCount = 4;
		final BFTDeterministicTest test = new BFTDeterministicTest(nodeCount, false);

		test.start();
		HashMap<ECPublicKey, Integer> numberOfProposalsByNodes = new HashMap<>();
		int stepCount = 10_000;
		for (int step = 0; step < stepCount; step++) {
			ControlledBFTNetwork.ControlledMessage message = test.processNextMsg(random);
		 	if (message.getMsg() instanceof Proposal) {
				ECPublicKey sender = message.getChannelId().getSender();
				numberOfProposalsByNodes.merge(sender, 1, Integer::sum);
				assertMaxValueDiff(numberOfProposalsByNodes, sender, 6);
			}
		}
	}
}
