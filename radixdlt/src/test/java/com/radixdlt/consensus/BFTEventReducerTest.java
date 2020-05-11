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

package com.radixdlt.consensus;

import com.google.common.collect.Lists;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.identifiers.AID;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyViolationException;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.utils.Ints;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BFTEventReducerTest {
    private static final ECKeyPair SELF_KEY = ECKeyPair.generateNew();

	private BFTEventReducer reducer;
	private ProposalGenerator proposalGenerator;
	private ProposerElection proposerElection;
	private SafetyRules safetyRules;
	private Pacemaker pacemaker;
	private PendingVotes pendingVotes;
	private Mempool mempool;
	private BFTEventSender sender;
	private VertexStore vertexStore;
	private ValidatorSet validatorSet;
	private SystemCounters counters;

	@Before
	public void setUp() {
		this.proposalGenerator = mock(ProposalGenerator.class);
		this.mempool = mock(Mempool.class);
		this.sender = mock(BFTEventSender.class);
		this.safetyRules = mock(SafetyRules.class);
		this.pacemaker = mock(Pacemaker.class);
		this.vertexStore = mock(VertexStore.class);
		this.pendingVotes = mock(PendingVotes.class);
		this.proposerElection = mock(ProposerElection.class);
		this.validatorSet = mock(ValidatorSet.class);
		this.counters = mock(SystemCounters.class);

		this.reducer = new BFTEventReducer(
			proposalGenerator,
			mempool,
			sender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			SELF_KEY,
			validatorSet,
			counters
		);
	}

	private static AID makeAID(int n) {
		byte[] temp = new byte[AID.BYTES];
		Ints.copyTo(n, temp, AID.BYTES - Integer.BYTES);
		return AID.from(temp);
	}

	@Test
	public void when_start__then_should_proceed_to_first_view() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View view = mock(View.class);
		when(qc.getView()).thenReturn(view);
		when(proposerElection.getProposer(any())).thenReturn(SELF_KEY.getPublicKey());
		when(vertexStore.getHighestQC()).thenReturn(qc);
		when(pacemaker.processQC(eq(view))).thenReturn(Optional.of(mock(View.class)));
		reducer.start();
		verify(pacemaker, times(1)).processQC(eq(view));
		verify(sender, times(1)).sendNewView(any(), any());
	}

	@Test
	public void when_processing_vote_as_not_proposer__then_nothing_happens() {
		Vote voteMessage = mock(Vote.class);
		VertexMetadata proposal = new VertexMetadata(View.of(2), Hash.random(), 2);
		VertexMetadata parent = new VertexMetadata(View.of(1), Hash.random(), 1);
		VoteData voteData = new VoteData(proposal, parent);
		when(voteMessage.getVoteData()).thenReturn(voteData);

		reducer.processVote(voteMessage);
		verify(safetyRules, times(0)).process(any(QuorumCertificate.class));
		verify(pacemaker, times(0)).processQC(any());
	}

	@Test
	public void when_processing_vote_as_a_proposer_and_quorum_is_reached__then_a_new_view_is_sent() {
		when(proposerElection.getProposer(any())).thenReturn(SELF_KEY.getPublicKey());

		Vote vote = mock(Vote.class);
		VertexMetadata proposal = new VertexMetadata(View.of(2), Hash.random(), 2);
		VertexMetadata parent = new VertexMetadata(View.of(1), Hash.random(), 1);
		VoteData voteData = new VoteData(proposal, parent);
		when(vote.getVoteData()).thenReturn(voteData);

		QuorumCertificate qc = mock(QuorumCertificate.class);
		View view = mock(View.class);
		when(qc.getView()).thenReturn(view);
		when(pendingVotes.insertVote(eq(vote), any())).thenReturn(Optional.of(qc));
		when(mempool.getAtoms(anyInt(), any())).thenReturn(Lists.newArrayList());
		when(pacemaker.getCurrentView()).thenReturn(mock(View.class));
		when(pacemaker.processQC(eq(view))).thenReturn(Optional.of(mock(View.class)));
		when(vertexStore.syncToQC(eq(qc))).thenReturn(true);

		reducer.processVote(vote);

		verify(sender, times(1)).sendNewView(any(), any());
	}

	@Test
	public void when_processing_relevant_local_timeout__then_new_view_is_emitted_and_counter_increment() {
        when(proposerElection.getProposer(any())).thenReturn(ECKeyPair.generateNew().getPublicKey());
		when(pacemaker.processLocalTimeout(any())).thenReturn(Optional.of(View.of(1)));
		when(pacemaker.getCurrentView()).thenReturn(View.of(1));
		reducer.processLocalTimeout(View.of(0L));
		verify(sender, times(1)).sendNewView(any(), any());
		verify(counters, times(1)).increment(eq(CounterType.CONSENSUS_TIMEOUT));
	}

	@Test
	public void when_processing_irrelevant_local_timeout__then_new_view_is_not_emitted_and_no_counter_increment() {
		when(pacemaker.processLocalTimeout(any())).thenReturn(Optional.empty());
		reducer.processLocalTimeout(View.of(0L));
		verify(sender, times(0)).sendNewView(any(), any());
		verify(counters, times(0)).increment(eq(CounterType.CONSENSUS_TIMEOUT));
	}


	@Test
	public void when_processing_new_view_as_proposer__then_new_view_is_emitted_and_proposal_is_sent() {
		NewView newView = mock(NewView.class);
		when(newView.getQC()).thenReturn(mock(QuorumCertificate.class));
		when(newView.getView()).thenReturn(View.of(0L));
		when(pacemaker.getCurrentView()).thenReturn(View.of(0L));
		when(pacemaker.processNewView(any(), any())).thenReturn(Optional.of(View.of(1L)));
		when(proposerElection.getProposer(any())).thenReturn(SELF_KEY.getPublicKey());
		when(proposalGenerator.generateProposal(eq(View.of(1L)))).thenReturn(mock(Vertex.class));
		reducer.processNewView(newView);
		verify(pacemaker, times(1)).processNewView(any(), any());
		verify(sender, times(1)).broadcastProposal(any());
	}


	@Test
	public void when_processing_invalid_proposal__then_atom_is_rejected() throws Exception {
		View currentView = View.of(123);

		Vertex proposedVertex = mock(Vertex.class);
		SimpleRadixEngineAtom proposedAtom = mock(SimpleRadixEngineAtom.class);
		AID aid = makeAID(7); // no special significance
		when(proposedAtom.getAID()).thenReturn(aid);
		when(proposedVertex.getAtom()).thenReturn(proposedAtom);
		when(proposedVertex.getQC()).thenReturn(mock(QuorumCertificate.class));
		when(proposedVertex.getView()).thenReturn(currentView);

		Proposal proposal = mock(Proposal.class);
		when(proposal.getVertex()).thenReturn(proposedVertex);

		doThrow(new VertexInsertionException("Test", new RadixEngineException(RadixEngineErrorCode.CM_ERROR, DataPointer.ofAtom())))
			.when(vertexStore).insertVertex(any());
		when(pacemaker.processQC(any())).thenReturn(Optional.empty());
		when(pacemaker.getCurrentView()).thenReturn(currentView);
		reducer.processProposal(proposal);
		verify(mempool, times(1)).removeRejectedAtom(eq(aid));
	}

	@Test
	public void when_processing_valid_stored_proposal__then_atom_is_voted_on_and_new_view() throws SafetyViolationException {
		View currentView = View.of(123);

		when(proposerElection.getProposer(any())).thenReturn(ECKeyPair.generateNew().getPublicKey());

		Vertex proposedVertex = mock(Vertex.class);
		SimpleRadixEngineAtom proposedAtom = mock(SimpleRadixEngineAtom.class);
		AID aid = makeAID(7); // no special significance
		when(proposedAtom.getAID()).thenReturn(aid);
		when(proposedVertex.getAtom()).thenReturn(proposedAtom);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View qcView = mock(View.class);
		when(qc.getView()).thenReturn(qcView);
		when(proposedVertex.getQC()).thenReturn(qc);
		when(proposedVertex.getView()).thenReturn(currentView);

		Proposal proposal = mock(Proposal.class);
		when(proposal.getVertex()).thenReturn(proposedVertex);

		when(pacemaker.getCurrentView()).thenReturn(currentView);
		Vote vote = mock(Vote.class);
		doReturn(vote).when(safetyRules).voteFor(eq(proposedVertex));
		when(pacemaker.processQC(eq(qcView))).thenReturn(Optional.empty());
		when(pacemaker.processQC(eq(currentView))).thenReturn(Optional.of(View.of(124)));

		reducer.processProposal(proposal);

		verify(sender, times(1)).sendVote(eq(vote), any());
		verify(sender, times(1)).sendNewView(any(), any());
	}

	@Test
	public void when_processing_valid_stored_proposal_and_next_leader__then_atom_is_voted_on_and_new_view() throws SafetyViolationException {
		View currentView = View.of(123);

		when(proposerElection.getProposer(eq(currentView))).thenReturn(ECKeyPair.generateNew().getPublicKey());
		when(proposerElection.getProposer(eq(currentView.next()))).thenReturn(SELF_KEY.getPublicKey());

		Vertex proposedVertex = mock(Vertex.class);
		SimpleRadixEngineAtom proposedAtom = mock(SimpleRadixEngineAtom.class);
		AID aid = makeAID(7); // no special significance
		when(proposedAtom.getAID()).thenReturn(aid);
		when(proposedVertex.getAtom()).thenReturn(proposedAtom);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View qcView = mock(View.class);
		when(qc.getView()).thenReturn(qcView);
		when(proposedVertex.getQC()).thenReturn(qc);
		when(proposedVertex.getView()).thenReturn(currentView);

		Proposal proposal = mock(Proposal.class);
		when(proposal.getVertex()).thenReturn(proposedVertex);

		when(pacemaker.getCurrentView()).thenReturn(currentView);
		Vote vote = mock(Vote.class);
		doReturn(vote).when(safetyRules).voteFor(eq(proposedVertex));
		when(pacemaker.processQC(eq(qcView))).thenReturn(Optional.empty());
		when(pacemaker.processQC(eq(currentView))).thenReturn(Optional.of(View.of(124)));

		reducer.processProposal(proposal);

		verify(sender, times(1)).sendVote(eq(vote), any());
		verify(sender, times(0)).sendNewView(any(), any());
	}

	@Test
	public void when_processing_valid_stored_proposal_and_leader__then_atom_is_voted_on_and_no_new_view() throws SafetyViolationException {
		View currentView = View.of(123);

		when(proposerElection.getProposer(eq(currentView))).thenReturn(SELF_KEY.getPublicKey());

		Vertex proposedVertex = mock(Vertex.class);
		SimpleRadixEngineAtom proposedAtom = mock(SimpleRadixEngineAtom.class);
		AID aid = makeAID(7); // no special significance
		when(proposedAtom.getAID()).thenReturn(aid);
		when(proposedVertex.getAtom()).thenReturn(proposedAtom);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View qcView = mock(View.class);
		when(qc.getView()).thenReturn(qcView);
		when(proposedVertex.getQC()).thenReturn(qc);
		when(proposedVertex.getView()).thenReturn(currentView);

		Proposal proposal = mock(Proposal.class);
		when(proposal.getVertex()).thenReturn(proposedVertex);

		when(pacemaker.getCurrentView()).thenReturn(currentView);
		Vote vote = mock(Vote.class);
		doReturn(vote).when(safetyRules).voteFor(eq(proposedVertex));
		when(pacemaker.processQC(eq(qcView))).thenReturn(Optional.empty());
		when(pacemaker.processQC(eq(currentView))).thenReturn(Optional.of(View.of(124)));

		reducer.processProposal(proposal);

		verify(sender, times(1)).sendVote(eq(vote), any());
		verify(sender, times(0)).sendNewView(any(), any());
	}


	@Test
	public void when_processing_valid_stored_proposal_and_there_exists_a_new_commit__the_new_commit_atom_is_removed_from_mempool() {
		View currentView = View.of(123);

		when(pacemaker.processQC(any())).thenReturn(Optional.empty());
		when(pacemaker.getCurrentView()).thenReturn(currentView);

		Vertex proposalVertex = mock(Vertex.class);
		Hash proposalVertexId = mock(Hash.class);
		when(proposalVertex.getId()).thenReturn(proposalVertexId);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(mock(View.class));
		when(proposalVertex.getQC()).thenReturn(qc);
		when(proposalVertex.getView()).thenReturn(currentView);

		Proposal proposal = mock(Proposal.class);
		when(proposal.getVertex()).thenReturn(proposalVertex);

		Hash committedVertexId = mock(Hash.class);
		Vertex committedVertex = mock(Vertex.class);
		SimpleRadixEngineAtom atom = mock(SimpleRadixEngineAtom.class);
		AID aid = mock(AID.class);
		when(atom.getAID()).thenReturn(aid);
		when(committedVertex.getAtom()).thenReturn(atom);

		when(safetyRules.process(eq(qc))).thenReturn(Optional.of(committedVertexId));
		when(vertexStore.commitVertex(eq(committedVertexId))).thenReturn(committedVertex);
		when(proposerElection.getProposer(any())).thenReturn(ECKeyPair.generateNew().getPublicKey());

		reducer.processProposal(proposal);
		verify(mempool, times(1)).removeCommittedAtom(eq(aid));
	}

	@Test
	public void when_processing_get_vertex_request__then_ec_callback_with_response() {
		Hash vertexId = mock(Hash.class);
		Vertex vertex = mock(Vertex.class);
		Consumer<Vertex> callback = mockConsumer();

		GetVertexRequest getVertexRequest = mock(GetVertexRequest.class);
		when(getVertexRequest.getVertexId()).thenReturn(vertexId);
		when(getVertexRequest.getResponder()).thenReturn(callback);

		when(vertexStore.getVertex(eq(vertexId))).thenReturn(vertex);
		reducer.processGetVertexRequest(getVertexRequest);
		verify(callback, times(1)).accept(eq(vertex));
	}

	@SuppressWarnings("unchecked")
	private static <T> Consumer<T> mockConsumer() {
		return mock(Consumer.class);
	}
}
