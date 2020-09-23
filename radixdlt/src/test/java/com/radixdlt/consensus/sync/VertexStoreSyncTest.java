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

package com.radixdlt.consensus.sync;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.sync.VertexStoreSync.SyncVerticesRequestSender;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.TypedMocks;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class VertexStoreSyncTest {
	private VertexStoreSync vertexStoreSync;
	private VertexStore vertexStore;
	private Comparator<LedgerHeader> ledgerHeaderComparator;
	private SyncVerticesRequestSender syncVerticesRequestSender;
	private SyncLedgerRequestSender syncLedgerRequestSender;
	private Ledger ledger;

	@Before
	public void setup() {
		this.vertexStore = mock(VertexStore.class);
		this.ledgerHeaderComparator = TypedMocks.rmock(Comparator.class);
		this.syncVerticesRequestSender = mock(SyncVerticesRequestSender.class);
		this.syncLedgerRequestSender = mock(SyncLedgerRequestSender.class);
		this.ledger = mock(Ledger.class);

		vertexStoreSync = new VertexStoreSync(
			vertexStore,
			ledgerHeaderComparator,
			syncVerticesRequestSender,
			syncLedgerRequestSender,
			ledger
		);
	}


	@Test
	public void given_synced_store__when_sync_to_qc_with_no_author__then_should_return_true() throws Exception {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(0));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(1));
		BFTHeader header = mock(BFTHeader.class);
		when(header.getView()).thenReturn(View.of(1));
		when(qc.getProposed()).thenReturn(header);
		when(vertexStore.addQC(eq(qc))).thenReturn(true);

		assertThat(vertexStoreSync.syncToQC(qc, vertexStore.getHighestCommittedQC(), null)).isTrue();
	}

	@Test
	public void when_sync_to_qc_with_no_author_and_not_synced__then_should_throw_illegal_state_exception() {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(0));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(1));
		BFTHeader header = mock(BFTHeader.class);
		when(header.getView()).thenReturn(View.of(1));
		when(qc.getProposed()).thenReturn(header);
		when(vertexStore.addQC(eq(qc))).thenReturn(false);

		assertThatThrownBy(() -> vertexStoreSync.syncToQC(qc, vertexStore.getHighestCommittedQC(), null))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_sync_to_qc_and_need_sync_but_have_committed__then_should_request_for_qc_sync() {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(1));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		when(vertexStore.addQC(any())).thenReturn(false);
		BFTHeader header = mock(BFTHeader.class);
		Hash vertexId = mock(Hash.class);
		when(header.getVertexId()).thenReturn(vertexId);
		when(header.getView()).thenReturn(View.of(2));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(header);
		when(qc.getView()).thenReturn(View.of(2));
		QuorumCertificate committedQC = mock(QuorumCertificate.class);
		BFTHeader committedHeader = mock(BFTHeader.class);
		when(committedHeader.getView()).thenReturn(View.of(1));
		when(committedQC.getCommittedAndLedgerStateProof())
			.thenReturn(Optional.of(Pair.of(committedHeader, mock(VerifiedLedgerHeaderAndProof.class))));
		BFTNode author = mock(BFTNode.class);
		vertexStoreSync.syncToQC(qc, committedQC, author);

		verify(syncVerticesRequestSender, times(1)).sendGetVerticesRequest(any(), any(), eq(1), any());
		verify(syncLedgerRequestSender, never()).sendLocalSyncRequest(any());
	}

	@Test
	public void when_sync_to_qc_and_need_sync_but_committed_qc_is_less_than_root__then_should_request_for_qc_sync() {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(2));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		when(vertexStore.addQC(any())).thenReturn(false);
		BFTHeader header = mock(BFTHeader.class);
		Hash vertexId = mock(Hash.class);
		when(header.getVertexId()).thenReturn(vertexId);
		when(header.getView()).thenReturn(View.of(3));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(header);
		when(qc.getView()).thenReturn(View.of(3));
		QuorumCertificate committedQC = mock(QuorumCertificate.class);
		BFTHeader committedHeader = mock(BFTHeader.class);
		when(committedHeader.getView()).thenReturn(View.of(1));
		when(committedQC.getCommittedAndLedgerStateProof())
			.thenReturn(Optional.of(Pair.of(committedHeader, mock(VerifiedLedgerHeaderAndProof.class))));
		BFTNode author = mock(BFTNode.class);
		vertexStoreSync.syncToQC(qc, committedQC, author);

		verify(syncVerticesRequestSender, times(1)).sendGetVerticesRequest(any(), any(), eq(1), any());
		verify(syncLedgerRequestSender, never()).sendLocalSyncRequest(any());
	}

	@Test
	public void when_sync_to_qc_and_need_sync_and_committed_qc_is_greater_than_root__then_should_request_for_committed_sync() {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(2));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		when(vertexStore.addQC(any())).thenReturn(false);
		BFTHeader header = mock(BFTHeader.class);
		Hash vertexId = mock(Hash.class);
		when(header.getVertexId()).thenReturn(vertexId);
		when(header.getView()).thenReturn(View.of(5));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(header);
		when(qc.getView()).thenReturn(View.of(5));
		QuorumCertificate committedQC = mock(QuorumCertificate.class);
		BFTHeader committedHeader = mock(BFTHeader.class);
		when(committedHeader.getView()).thenReturn(View.of(3));
		BFTHeader committedProposed = mock(BFTHeader.class);
		when(committedQC.getProposed()).thenReturn(committedProposed);
		when(committedQC.getCommittedAndLedgerStateProof())
			.thenReturn(Optional.of(Pair.of(committedHeader, mock(VerifiedLedgerHeaderAndProof.class))));
		BFTNode author = mock(BFTNode.class);
		vertexStoreSync.syncToQC(qc, committedQC, author);

		verify(syncVerticesRequestSender, times(1)).sendGetVerticesRequest(any(), any(), eq(3), any());
		verify(syncLedgerRequestSender, never()).sendLocalSyncRequest(any());
	}


	@Test
	public void given_a_qc_sync_in_progress__when_receive_response__should_insert() {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(1));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		when(vertexStore.addQC(any())).thenReturn(false);
		BFTHeader header = mock(BFTHeader.class);
		Hash vertexId = mock(Hash.class);
		when(header.getVertexId()).thenReturn(vertexId);
		when(header.getView()).thenReturn(View.of(2));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(header);
		when(qc.getView()).thenReturn(View.of(2));
		QuorumCertificate committedQC = mock(QuorumCertificate.class);
		BFTHeader committedHeader = mock(BFTHeader.class);
		when(committedHeader.getView()).thenReturn(View.of(1));
		when(committedQC.getCommittedAndLedgerStateProof())
			.thenReturn(Optional.of(Pair.of(committedHeader, mock(VerifiedLedgerHeaderAndProof.class))));
		BFTNode author = mock(BFTNode.class);
		vertexStoreSync.syncToQC(qc, committedQC, author);
		Hash parentId = mock(Hash.class);
		when(vertexStore.containsVertex(eq(parentId))).thenReturn(true);

		VerifiedVertex vertex = mock(VerifiedVertex.class);
		when(vertexStore.addQC(any())).thenReturn(true);
		when(vertex.getParentId()).thenReturn(parentId);
		GetVerticesResponse getVerticesResponse = new GetVerticesResponse(
			mock(BFTNode.class),
			vertexId,
			Collections.singletonList(vertex),
			vertexId
		);
		vertexStoreSync.processGetVerticesResponse(getVerticesResponse);

		verify(vertexStore, times(1)).insertVertex(eq(vertex));
	}
}