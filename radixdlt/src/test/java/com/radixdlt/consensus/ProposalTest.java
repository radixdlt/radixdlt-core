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

package com.radixdlt.consensus;

import com.radixdlt.consensus.bft.BFTNode;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.crypto.ECDSASignature;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ProposalTest {
	private Proposal proposal;
	private Vertex vertex;
	private BFTNode node;
	private ECDSASignature signature;
	private QuorumCertificate commitQc;
	private long payload;

	@Before
	public void setUp() {
		this.vertex = mock(Vertex.class);
		this.node = mock(BFTNode.class);
		this.signature = mock(ECDSASignature.class);
		this.commitQc = mock(QuorumCertificate.class);
		this.payload = 123456L;

		this.proposal = new Proposal(vertex, commitQc, node, signature, this.payload);
	}

	@Test
	public void testGetters() {
		assertThat(this.proposal.getVertex()).isEqualTo(vertex);
		assertThat(this.proposal.getCommittedQC()).isEqualTo(commitQc);
		assertThat(this.proposal.getPayload()).isEqualTo(payload);
	}

	@Test
	public void testToString() {
		assertThat(this.proposal).isNotNull();
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(Proposal.class)
			.verify();
	}

	@Test
	public void sensibleToString() {
		String s = this.proposal.toString();
		assertThat(s).contains(vertex.toString());
	}
}