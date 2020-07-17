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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.SyncedStateComputer.CommittedInstruction;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.utils.Panicker;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * An atom which has been committed by the BFT
 *
 * TODO: add commit signature proof
 */
@Immutable
@SerializerId2("consensus.committed_atom")
public final class CommittedAtom implements LedgerAtom, CommittedInstruction {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private final ClientAtom clientAtom;

	// TODO: include some sort of signature proof for genesis atom
	@JsonProperty("commit_qc")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate commitQC;

	@JsonCreator
	public CommittedAtom(
		@JsonProperty("atom") ClientAtom clientAtom,
		@JsonProperty("commit_qc") QuorumCertificate commitQC
	) {
		if (!commitQC.getCommitted().isPresent()) {
			throw new IllegalStateException("Specified QC is not for a commit!");
		}
		this.clientAtom = clientAtom;
		this.commitQC = commitQC;
	}

	public ClientAtom getClientAtom() {
		return clientAtom;
	}

	public QuorumCertificate getCommitQC() {
		return this.commitQC;
	}

	@Override
	public VertexMetadata getVertexMetadata() {
		// Constructor ensures that committed vertex always present
		return this.commitQC.getCommitted().orElseThrow(() -> Panicker.panic("Logic error in ComittedAtom"));
	}

	@Override
	public CMInstruction getCMInstruction() {
		return clientAtom.getCMInstruction();
	}

	@Override
	public AID getAID() {
		return clientAtom.getAID();
	}

	@Override
	public int hashCode() {
		return Objects.hash(clientAtom, commitQC);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof CommittedAtom)) {
			return false;
		}

		CommittedAtom other = (CommittedAtom) o;
		return Objects.equals(other.clientAtom, this.clientAtom)
			&& Objects.equals(other.commitQC, this.commitQC);
	}

	@Override
	public ImmutableMap<String, String> getMetaData() {
		return clientAtom.getMetaData();
	}

	@Override
	public Hash getPowFeeHash() {
		return clientAtom.getPowFeeHash();
	}

	@Override
	public String toString() {
		return String.format("%s{atom=%s meta=%s}", getClass().getSimpleName(), clientAtom != null ? clientAtom.getAID() : null, commitQC);
	}
}
