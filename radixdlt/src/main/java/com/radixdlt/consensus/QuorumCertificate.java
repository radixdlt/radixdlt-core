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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;

import java.util.Optional;

@SerializerId2("consensus.qc")
public final class QuorumCertificate {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("signatures")
	@DsonOutput(Output.ALL)
	private final TimestampedECDSASignatures signatures;

	@JsonProperty("vote_data")
	@DsonOutput(Output.ALL)
	private final VoteData voteData;

	@JsonCreator
	public QuorumCertificate(
		@JsonProperty("vote_data") VoteData voteData,
		@JsonProperty("signatures") TimestampedECDSASignatures signatures
	) {
		this.voteData = Objects.requireNonNull(voteData);
		this.signatures = Objects.requireNonNull(signatures);
	}

	/**
	 * Create a mocked QC for genesis vertex
	 * @param genesisVertex the vertex to create a qc for
	 * @return a mocked QC
	 */
	public static QuorumCertificate ofGenesis(Vertex genesisVertex) {
		if (!genesisVertex.getView().isGenesis()) {
			throw new IllegalArgumentException(String.format("Vertex is not genesis: %s", genesisVertex));
		}

		VertexMetadata vertexMetadata = VertexMetadata.ofVertex(genesisVertex, false);
		final VoteData voteData = new VoteData(vertexMetadata, vertexMetadata, vertexMetadata);
		return new QuorumCertificate(voteData, new TimestampedECDSASignatures());
	}

	public View getView() {
		return voteData.getProposed().getView();
	}

	public VertexMetadata getProposed() {
		return voteData.getProposed();
	}

	public VertexMetadata getParent() {
		return voteData.getParent();
	}

	public Optional<VertexMetadata> getCommitted() {
		return voteData.getCommitted();
	}

	public VoteData getVoteData() {
		return voteData;
	}

	public TimestampedECDSASignatures getSignatures() {
		return signatures;
	}

	public long quorumTime(ValidatorSet validatorSet) {
		// Note that signatures are not rechecked here.
		// They are expected to be checked before the QC is formed.
		// No real effort is made to ensure the QC is valid for this validator set.
		UInt256 totalPower = UInt256.ZERO;
		ImmutableMap<ECPublicKey, Validator> validators = validatorSet.validatorsByKey();
		List<Pair<Long, UInt256>> weightedTimes = Lists.newArrayList();
		for (Map.Entry<ECPublicKey, Pair<Long, ECDSASignature>> e : getSignatures().getSignatures().entrySet()) {
			ECPublicKey thisKey = e.getKey();
			Validator v = validators.get(thisKey);
			if (v != null) {
				UInt256 power = v.getPower();
				totalPower = totalPower.add(power);
				weightedTimes.add(Pair.of(e.getValue().getFirst(), power));
			}
		}
		if (totalPower.isZero()) {
			throw new IllegalStateException("Zero validator power for this QC");
		}
		UInt256 median = totalPower.shiftRight(); // Divide by 2
		// Sort ascending by timestamp
		weightedTimes.sort(Comparator.comparing(Pair::getFirst));
		for (Pair<Long, UInt256> w : weightedTimes) {
			UInt256 weight = w.getSecond();
			if (median.compareTo(weight) <= 0) {
				return w.getFirst();
			}
			median = median.subtract(w.getSecond());
		}
		throw new IllegalStateException("Logic error");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		QuorumCertificate that = (QuorumCertificate) o;
		return Objects.equals(signatures, that.signatures)
			&& Objects.equals(voteData, that.voteData);
	}

	@Override
	public int hashCode() {
		return Objects.hash(signatures, voteData);
	}

	@Override
	public String toString() {
		return String.format("QC{view=%s}", this.getView());
	}
}
