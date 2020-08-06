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

package com.radixdlt.api;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochView;
import org.junit.Before;
import org.junit.Test;

public class TimeoutTest {
	private EpochView epochView;
	private BFTNode leader;
	private Timeout timeout;

	@Before
	public void setup() {
		this.epochView = mock(EpochView.class);
		this.leader = mock(BFTNode.class);

		this.timeout = new Timeout(this.epochView, this.leader);
	}

	@Test
	public void test_getters() {
		assertThat(this.timeout.getEpochView()).isEqualTo(epochView);
		assertThat(this.timeout.getLeader()).isEqualTo(leader);
	}

	@Test
	public void test_toString() {
		assertThat(this.timeout.toString()).isNotNull();
	}
}