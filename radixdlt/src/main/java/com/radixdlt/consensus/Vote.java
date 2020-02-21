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

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.Signature;

import java.util.Objects;

/**
 * Represents a vote on a vertex
 */
public final class Vote {
	private final long round;
	private final SignedMessage signedMessage;

	/**
	 * Create a vote for a given round with a {@link SignedMessage} (by replicas) of the hash.
	 * Note that the hash must reflect the given round.
	 * This is a temporary method as Vote will be expanded to maintain this invariant itself.
	 */
	public Vote(long round, SignedMessage signedMessage) {
		this.round = round;
		this.signedMessage = Objects.requireNonNull(signedMessage, "'signedMessage' is required");
	}

	/**
	 * Create a vote for a given round with a certain hash and a signature (by a replica) of the hash.
	 * Note that the hash must reflect the given round.
	 * This is a temporary method as Vote will be expanded to maintain this invariant itself.
	 */
	public Vote(long round, Hash hash, Signature signature, ECPublicKey publicKey) {
		this(round, new SignedMessage(hash, signature, publicKey));
	}

	public long getRound() {
		return round;
	}

	public SignedMessage signedMessage() {
		return signedMessage;
	}

	public ECPublicKey publicKey() {
		return this.signedMessage.publicKey();
	}

	public Signature signature() {
		return this.signedMessage.signature();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Vote vote = (Vote) o;
		return round == vote.round
				&& signedMessage.equals(vote.signedMessage);
	}

	@Override
	public int hashCode() {
		return Objects.hash(round, signedMessage);
	}

	@Override
	public String toString() {
		return String.format(
				"%s{round=%s, signedMessage=%s}",
				getClass().getSimpleName(),
				this.round,
				this.signedMessage
		);
	}
}
