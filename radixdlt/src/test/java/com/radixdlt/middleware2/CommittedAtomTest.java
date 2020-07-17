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

package com.radixdlt.middleware2;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.AID;
import nl.jqno.equalsverifier.EqualsVerifier;

import java.util.Optional;

import org.junit.Test;

public class CommittedAtomTest {

	@Test
	public void testGetters() {
		ClientAtom clientAtom = mock(ClientAtom.class);
		when(clientAtom.getAID()).thenReturn(mock(AID.class));
		when(clientAtom.getCMInstruction()).thenReturn(mock(CMInstruction.class));
		when(clientAtom.getPowFeeHash()).thenReturn(mock(Hash.class));
		QuorumCertificate commitQC = mock(QuorumCertificate.class);
		VertexMetadata commitMetadata = mock(VertexMetadata.class);
		when(commitQC.getCommitted()).thenReturn(Optional.of(commitMetadata));
		CommittedAtom committedAtom = new CommittedAtom(clientAtom, commitQC);
		assertThat(committedAtom.getClientAtom()).isEqualTo(clientAtom);
		assertThat(committedAtom.getAID()).isEqualTo(clientAtom.getAID());
		assertThat(committedAtom.getCMInstruction()).isEqualTo(clientAtom.getCMInstruction());
		assertThat(committedAtom.getPowFeeHash()).isEqualTo(clientAtom.getPowFeeHash());
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(CommittedAtom.class)
			.verify();
	}
}