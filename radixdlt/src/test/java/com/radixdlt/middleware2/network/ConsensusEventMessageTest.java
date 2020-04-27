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

import org.junit.Test;

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ConsensusEventMessageTest {

	@Test
	public void sensibleToStringNewView() {
		NewView m = mock(NewView.class);
		ConsensusEventMessage msg1 = new ConsensusEventMessage(0, m);
		String s1 = msg1.toString();
		assertThat(s1, containsString(ConsensusEventMessage.class.getSimpleName()));
		assertThat(s1, containsString(m.toString()));

		assertTrue(msg1.getConsensusMessage() instanceof NewView);
	}

	@Test
	public void sensibleToStringProposal() {
		Proposal m = mock(Proposal.class);
		ConsensusEventMessage msg1 = new ConsensusEventMessage(0, m);
		String s1 = msg1.toString();
		assertThat(s1, containsString(ConsensusEventMessage.class.getSimpleName()));
		assertThat(s1, containsString(m.toString()));

		assertTrue(msg1.getConsensusMessage() instanceof Proposal);
	}

	@Test
	public void sensibleToStringVote() {
		Vote m = mock(Vote.class);
		ConsensusEventMessage msg1 = new ConsensusEventMessage(0, m);
		String s1 = msg1.toString();
		assertThat(s1, containsString(ConsensusEventMessage.class.getSimpleName()));
		assertThat(s1, containsString(m.toString()));

		assertTrue(msg1.getConsensusMessage() instanceof Vote);
	}

	@Test
	public void sensibleToStringNone() {
		ConsensusEventMessage msg1 = new ConsensusEventMessage();
		String s1 = msg1.toString();
		assertThat(s1, containsString(ConsensusEventMessage.class.getSimpleName()));
		assertThat(s1, containsString("null"));
	}

	@Test(expected = IllegalStateException.class)
	public void failedConsensusMessage() {
		ConsensusEventMessage msg1 = new ConsensusEventMessage();
		assertNotNull(msg1.getConsensusMessage());
	}

}