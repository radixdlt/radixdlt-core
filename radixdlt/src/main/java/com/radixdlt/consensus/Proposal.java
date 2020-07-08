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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;

/**
 * Represents a proposal made by a leader in a round of consensus
 */
@SerializerId2("consensus.proposal")
@Immutable // author cannot be but is effectively final because of serializer
public final class Proposal implements RequiresSyncConsensusEvent {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("vertex")
	@DsonOutput(Output.ALL)
	private final Vertex vertex;

	private ECPublicKey author;

	@JsonProperty("signature")
	@DsonOutput(Output.ALL)
	private final ECDSASignature signature;

	@JsonProperty("committedQC")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate committedQC;

	Proposal() {
		// Serializer only
		this.vertex = null;
		this.author = null;
		this.signature = null;
		this.committedQC = null;
	}

	public Proposal(Vertex vertex, QuorumCertificate committedQC, ECPublicKey author, ECDSASignature signature) {
		this.vertex = Objects.requireNonNull(vertex);
		this.committedQC = committedQC;
		this.author = Objects.requireNonNull(author);
		this.signature = Objects.requireNonNull(signature);
	}

	@Override
	public long getEpoch() {
		return vertex.getEpoch();
	}

	@Override
	public QuorumCertificate getQC() {
		return vertex.getQC();
	}

	@Override
	public QuorumCertificate getCommittedQC() {
		return committedQC;
	}

	@Override
	public ECPublicKey getAuthor() {
		return author;
	}

	public Vertex getVertex() {
		return vertex;
	}

	@JsonProperty("author")
	@DsonOutput(Output.ALL)
	private byte[] getSerializerAuthor() {
		return this.author == null ? null : this.author.getBytes();
	}

	@JsonProperty("author")
	private void setSerializerAuthor(byte[] author) throws CryptoException {
		this.author = (author == null) ? null : new ECPublicKey(author);
	}

	@Override
	public String toString() {
		String who = author == null ? null : author.euid().toString().substring(0, 6);
		return String.format("%s{vertex=%s author=%s}", getClass().getSimpleName(), vertex, who);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.author, this.vertex, this.signature, this.committedQC);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof Proposal) {
			Proposal other = (Proposal) o;
			return
				Objects.equals(this.author, other.author)
					&& Objects.equals(this.vertex, other.vertex)
					&& Objects.equals(this.signature, other.signature)
					&& Objects.equals(this.committedQC, other.committedQC);
		}
		return false;
	}
}
