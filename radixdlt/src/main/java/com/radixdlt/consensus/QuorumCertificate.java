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

package com.radixdlt.consensus;

import com.radixdlt.crypto.DefaultSignatures;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.Signatures;

import java.util.Objects;

public final class QuorumCertificate {
	private final Vote vote;
	private final Signatures signatures;

	public QuorumCertificate(Vote vote, Signatures signatures) {
		if (!QuorumCertificate.isVoteSignedByAllSignatures(vote, signatures)) {
			throw new IllegalArgumentException("Vote should have been signed by all signatures");
		}

		this.vote = Objects.requireNonNull(vote);
		this.signatures = Objects.requireNonNull(signatures);
	}

	public QuorumCertificate(Vote vote) {
		this(vote, DefaultSignatures.single(vote.publicKey(), vote.signature()));
	}

	public long getRound() {
		return vote.getRound();
	}

	public Signatures signatures() {
		return this.signatures();
	}

	/**
	 * Aggregates a QC with another vote ({@code otherVote}), which {@code round} must match the round of {@code vote},
	 * effectively concatenating the the {@code signatures} of this QC with the {@code signature} of the {@code otherVote}.
	 * @param otherVote Another vote of this round with a signature to concatenate.
	 * @return a new QC with concatenated signatures for the vote.
	 */
	public QuorumCertificate aggregateVote(Vote otherVote) {
		if (this.vote.getRound() != otherVote.getRound()) {
			throw new IllegalArgumentException("Cant merge Votes for different rounds");
		}
		return new QuorumCertificate(this.vote, this.signatures.concatenate(otherVote.publicKey(), otherVote.signature()));
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof QuorumCertificate)) {
			return false;
		}

		QuorumCertificate qc = (QuorumCertificate) o;
		return Objects.equals(qc.vote, this.vote);
	}

	@Override
	public int hashCode() {
		return vote.hashCode();
	}

	private static boolean isVoteSignedByAllSignatures(Vote vote, Signatures signatures) {
		Hash hash = vote.signedMessage().hash();
		return signatures.hasSignedMessage(hash, signatures.keyToSignatures().size());
	}
}
