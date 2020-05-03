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

package com.radixdlt.consensus.functional.synchronous;

import com.radixdlt.consensus.functional.BFTFunctionalTest;
import java.util.Random;
import org.junit.Test;

public class TimeoutsDisabledTest {

	@Test
	public void when_run_4_correct_nodes_with_random_latency_and_timeouts_disabled__then_bft_should_be_responsive() {
		final Random random = new Random(12345);
		final BFTFunctionalTest test = new BFTFunctionalTest(4);

		test.start();
		for (int step = 0; step < 100000; step++) {
			test.processNextMsg(random);
		}
	}

	@Test
	public void when_run_100_correct_nodes_with_random_latency_and_timeouts_disabled__then_bft_should_be_responsive() {
		final Random random = new Random(12345);
		final BFTFunctionalTest test = new BFTFunctionalTest(100);

		test.start();
		for (int step = 0; step < 100000; step++) {
			test.processNextMsg(random);
		}
	}
}