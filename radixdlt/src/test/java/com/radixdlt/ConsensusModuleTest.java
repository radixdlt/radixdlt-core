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

package com.radixdlt;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignature;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker.PacemakerInfoSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.BFTSync.BFTSyncTimeoutScheduler;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.PacemakerTimeoutSender;
import com.radixdlt.consensus.liveness.ProceedToViewSender;
import com.radixdlt.consensus.sync.SyncLedgerRequestSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;

public class ConsensusModuleTest {
	@Inject
	private BFTSync bftSync;

	@Inject
	private VertexStore vertexStore;

	@Inject
	private Hasher hasher;

	private BFTConfiguration bftConfiguration;

	private ECKeyPair ecKeyPair;
	private SyncVerticesRequestSender requestSender;

	@Before
	public void setup() {
		this.bftConfiguration = mock(BFTConfiguration.class);
		UnverifiedVertex genesis = UnverifiedVertex.createGenesis(LedgerHeader.genesis(HashUtils.zero256(), null));
		VerifiedVertex hashedGenesis = new VerifiedVertex(genesis, HashUtils.zero256());
		QuorumCertificate qc = QuorumCertificate.ofGenesis(hashedGenesis, LedgerHeader.genesis(HashUtils.zero256(), null));
		when(bftConfiguration.getGenesisVertex()).thenReturn(hashedGenesis);
		when(bftConfiguration.getGenesisQC()).thenReturn(qc);
		when(bftConfiguration.getGenesisHeader()).thenReturn(mock(VerifiedLedgerHeaderAndProof.class));
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		BFTValidator validator = mock(BFTValidator.class);
		when(validator.getPower()).thenReturn(UInt256.ONE);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(validator));
		when(validatorSet.nodes()).thenReturn(ImmutableSet.of(mock(BFTNode.class)));
		when(bftConfiguration.getValidatorSet()).thenReturn(validatorSet);
		this.ecKeyPair = ECKeyPair.generateNew();
		this.requestSender = mock(SyncVerticesRequestSender.class);

		Guice.createInjector(
			new ConsensusModule(),
			new CryptoModule(),
			getExternalModule()
		).injectMembers(this);
	}

	private Module getExternalModule() {
		return new AbstractModule() {
			@Override
			protected void configure() {
				bind(BFTUpdateSender.class).toInstance(mock(BFTUpdateSender.class));
				bind(Ledger.class).toInstance(mock(Ledger.class));
				bind(SyncLedgerRequestSender.class).toInstance(mock(SyncLedgerRequestSender.class));
				bind(ProceedToViewSender.class).toInstance(mock(ProceedToViewSender.class));
				bind(ProposalBroadcaster.class).toInstance(mock(ProposalBroadcaster.class));
				bind(SyncVerticesRequestSender.class).toInstance(requestSender);
				bind(SyncVerticesResponseSender.class).toInstance(mock(SyncVerticesResponseSender.class));
				bind(NextCommandGenerator.class).toInstance(mock(NextCommandGenerator.class));
				bind(SystemCounters.class).toInstance(mock(SystemCounters.class));
				bind(TimeSupplier.class).toInstance(mock(TimeSupplier.class));
				bind(PacemakerInfoSender.class).toInstance(mock(PacemakerInfoSender.class));
				bind(PacemakerTimeoutSender.class).toInstance(mock(PacemakerTimeoutSender.class));
				bind(BFTSyncTimeoutScheduler.class).toInstance(mock(BFTSyncTimeoutScheduler.class));
				bind(BFTConfiguration.class).toInstance(bftConfiguration);
				bindConstant().annotatedWith(BFTSyncPatienceMillis.class).to(200);
				bindConstant().annotatedWith(PacemakerTimeout.class).to(1000L);
				bindConstant().annotatedWith(PacemakerRate.class).to(2.0);
				bindConstant().annotatedWith(PacemakerMaxExponent.class).to(6);

				ECKeyPair ecKeyPair = ECKeyPair.generateNew();
				bind(HashSigner.class).toInstance(ecKeyPair::sign);
			}

			@Provides
			@Self
			private BFTNode bftNode() {
				return BFTNode.create(ecKeyPair.getPublicKey());
			}
		};
	}

	private Pair<QuorumCertificate, VerifiedVertex> createNextVertex(QuorumCertificate parent, BFTNode bftNode) {
		UnverifiedVertex unverifiedVertex = new UnverifiedVertex(parent, View.of(1), new Command(new byte[] {0}));
		HashCode hash = hasher.hash(unverifiedVertex);
		VerifiedVertex verifiedVertex = new VerifiedVertex(unverifiedVertex, hash);
		BFTHeader next = new BFTHeader(
			View.of(1),
			verifiedVertex.getId(),
			LedgerHeader.create(1, View.of(1), new AccumulatorState(1, HashUtils.zero256()), 1)
		);
		VoteData voteData = new VoteData(
			next,
			parent.getProposed(),
			parent.getParent()
		);
		QuorumCertificate unsyncedQC = new QuorumCertificate(
			voteData,
			new TimestampedECDSASignatures(ImmutableMap.of(bftNode, TimestampedECDSASignature.from(0, UInt256.ONE, new ECDSASignature())))
		);

		return Pair.of(unsyncedQC, verifiedVertex);
	}

	@Test
	public void on_sync_request_timeout_should_retry() {
		// Arrange
		BFTNode bftNode = BFTNode.random();
		QuorumCertificate parent = vertexStore.highQC().highestQC();
		Pair<QuorumCertificate, VerifiedVertex> nextVertex = createNextVertex(parent, bftNode);
		HighQC unsyncedHighQC = HighQC.from(nextVertex.getFirst(), nextVertex.getFirst());
		bftSync.syncToQC(unsyncedHighQC, bftNode);

		// Act
		bftSync.processGetVerticesLocalTimeout(new LocalGetVerticesRequest(nextVertex.getSecond().getId(), 1));

		// Assert
		verify(requestSender, times(2))
			.sendGetVerticesRequest(eq(bftNode), argThat(r -> r.getCount() == 1 && r.getVertexId().equals(nextVertex.getSecond().getId())));
	}

	@Test
	public void on_synced_to_vertex_should_request_for_parent() {
		// Arrange
		BFTNode bftNode = BFTNode.random();
		QuorumCertificate parent = vertexStore.highQC().highestQC();
		Pair<QuorumCertificate, VerifiedVertex> nextVertex = createNextVertex(parent, bftNode);
		Pair<QuorumCertificate, VerifiedVertex> nextNextVertex = createNextVertex(nextVertex.getFirst(), bftNode);
		HighQC unsyncedHighQC = HighQC.from(nextNextVertex.getFirst(), nextNextVertex.getFirst());
		bftSync.syncToQC(unsyncedHighQC, bftNode);

		// Act
		GetVerticesResponse response = new GetVerticesResponse(bftNode, ImmutableList.of(nextNextVertex.getSecond()));
		bftSync.processGetVerticesResponse(response);

		// Assert
		verify(requestSender, times(1))
			.sendGetVerticesRequest(eq(bftNode), argThat(r -> r.getCount() == 1 && r.getVertexId().equals(nextVertex.getSecond().getId())));
	}
}
