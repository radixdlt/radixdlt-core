/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.safety;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.safety.SafetyState.Builder;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;

import java.util.Objects;
import java.util.Optional;

/**
 * Manages safety of the protocol.
 */
public final class SafetyRules {
	private final ECKeyPair selfKey; // TODO remove signing/address to separate identity management
	private final Hasher hasher;
	private final HashSigner signer;

	private SafetyState state;

	@Inject
	public SafetyRules(
		@Named("self") ECKeyPair selfKey,
		SafetyState initialState,
		Hasher hasher,
		HashSigner signer
	) {
		this.selfKey = Objects.requireNonNull(selfKey);
		this.state = Objects.requireNonNull(initialState);
		this.hasher = Objects.requireNonNull(hasher);
		this.signer = Objects.requireNonNull(signer);
	}

	/**
	 * Process a QC.
	 * @param qc The quorum certificate
	 * @return the just-committed vertex id, if any
	 */
	public Optional<VertexMetadata> process(QuorumCertificate qc) {
		final Builder safetyStateBuilder = this.state.toBuilder();

		// prepare phase on qc's proposed vertex if there is a newer 1-chain
		// keep highest 1-chain as the current "generic" QC
		if (qc.getView().compareTo(this.state.getGenericView().orElse(View.of(0L))) > 0) {
			safetyStateBuilder.qc(qc);
		}

		// pre-commit phase on consecutive qc's proposed vertex
		if (qc.getParent() != null
			&& qc.getParent().getView().compareTo(this.state.getLockedView()) > 0
			&& qc.getParent().getView().next().equals(qc.getView())) {

			safetyStateBuilder.lockedView(qc.getParent().getView());
		}

		// commit phase for a vertex if it's view is greater than last commit.
		// otherwise, it must have already been committed
		final Optional<VertexMetadata> commitMetadata = qc.getCommitted().flatMap(vmd -> {
			if (vmd.getView().compareTo(this.state.getCommittedView()) > 0) {
				safetyStateBuilder.committedView(vmd.getView());
				return Optional.of(vmd);
			}
			return Optional.empty();
		});

		this.state = safetyStateBuilder.build();

		return commitMetadata;
	}

	/**
	 * Create a signed proposal from a vertex
	 * @param proposedVertex vertex to sign
	 * @param highestCommittedQC highest known committed QC
	 * @return signed proposal object for consensus
	 */
	public Proposal signProposal(Vertex proposedVertex, QuorumCertificate highestCommittedQC) {
		final Hash vertexHash = this.hasher.hash(proposedVertex);
		ECDSASignature signature = this.signer.sign(selfKey, vertexHash);
		return new Proposal(proposedVertex, highestCommittedQC, this.selfKey.getPublicKey(), signature);
	}

	private static VoteData constructVoteData(Vertex proposedVertex) {
		final VertexMetadata proposed = VertexMetadata.ofVertex(proposedVertex);
		final VertexMetadata parent = proposedVertex.getQC().getProposed();

		final VertexMetadata toCommit;

		// Add a vertex to commit if creating a quorum for the proposed vertex would
		// create three consecutive qcs.
		if (proposedVertex.getView().equals(proposedVertex.getParentView().next())
			&& !proposedVertex.getParentView().isGenesis() && !proposedVertex.getGrandParentView().isGenesis()
			&& proposedVertex.getParentView().equals(proposedVertex.getGrandParentView().next())
		) {
			toCommit = proposedVertex.getQC().getParent();
		} else {
			toCommit = null;
		}

		return new VoteData(proposed, parent, toCommit);
	}

	/**
	 * Vote for a proposed vertex while ensuring that safety invariants are upheld.
	 * @param proposedVertex The proposed vertex
	 * @return A vote result containing the vote and any committed vertices
	 * @throws SafetyViolationException In case the vertex would violate a safety invariant
	 */
	public Vote voteFor(Vertex proposedVertex) throws SafetyViolationException {
		// ensure vertex does not violate earlier votes
		if (proposedVertex.getView().compareTo(this.state.getLastVotedView()) <= 0) {
			throw new SafetyViolationException(proposedVertex, this.state, String.format(
				"violates earlier vote at %s", this.state.getLastVotedView()));
		}

		// ensure vertex respects locked QC
		if (proposedVertex.getQC().getView().compareTo(this.state.getLockedView()) < 0) {
			throw new SafetyViolationException(proposedVertex, this.state, String.format(
				"does not respect locked view %s", this.state.getLockedView()));
		}

		Builder safetyStateBuilder = this.state.toBuilder();
		safetyStateBuilder.lastVotedView(proposedVertex.getView());

		final VoteData voteData = constructVoteData(proposedVertex);

		final Hash voteHash = hasher.hash(voteData);

		this.state = safetyStateBuilder.build();

		// TODO make signing more robust by including author in signed hash
		ECDSASignature signature = this.signer.sign(selfKey, voteHash);
		return new Vote(selfKey.getPublicKey(), voteData, signature);
	}
}
