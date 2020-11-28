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

package com.radixdlt.consensus.bft;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.store.berkeley.SerializedVertexStoreState;
import com.radixdlt.utils.Pair;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class VerifiedVertexStoreState {
	private final VerifiedVertex root;
	private final VerifiedLedgerHeaderAndProof rootHeader;
	private final HighQC highQC;
	private final ImmutableList<VerifiedVertex> vertices;

	private VerifiedVertexStoreState(
		HighQC highQC,
		VerifiedLedgerHeaderAndProof rootHeader,
		VerifiedVertex root,
		ImmutableList<VerifiedVertex> vertices
	) {
		this.highQC = highQC;
		this.rootHeader = rootHeader;
		this.root = root;
		this.vertices = vertices;
	}

	public static VerifiedVertexStoreState create(
		HighQC highQC,
		VerifiedVertex root
	) {
		return create(highQC, root, ImmutableList.of());
	}

	public static VerifiedVertexStoreState create(
		HighQC highQC,
		VerifiedVertex root,
		ImmutableList<VerifiedVertex> vertices
	) {
		final Pair<BFTHeader, VerifiedLedgerHeaderAndProof> headers = highQC.highestCommittedQC()
			.getCommittedAndLedgerStateProof()
			.orElseThrow(() -> new IllegalStateException(String.format("highQC=%s does not have commit", highQC)));
		VerifiedLedgerHeaderAndProof rootHeader = headers.getSecond();
		BFTHeader bftHeader = headers.getFirst();
		if (!bftHeader.getVertexId().equals(root.getId())) {
			throw new IllegalStateException(String.format("committedHeader=%s does not match rootVertex=%s", bftHeader, root));
		}

		Set<HashCode> seen = new HashSet<>();
		seen.add(root.getId());
		for (VerifiedVertex v : vertices) {
			if (!seen.contains(v.getParentId())) {
				throw new IllegalStateException(String.format("Missing qc=%s {root=%s vertices=%s}", v.getQC(), root, vertices));
			}
			seen.add(v.getId());
		}

		if (seen.stream().noneMatch(highQC.highestCommittedQC().getProposed().getVertexId()::equals)) {
			throw new IllegalStateException(String.format("highQC=%s highCommitted proposed missing {root=%s vertices=%s}", highQC, root, vertices));
		}

		if (seen.stream().noneMatch(highQC.highestCommittedQC().getParent().getVertexId()::equals)) {
			throw new IllegalStateException(String.format("highQC=%s highCommitted parent does not have a corresponding vertex", highQC));
		}

		if (seen.stream().noneMatch(highQC.highestQC().getParent().getVertexId()::equals)) {
			throw new IllegalStateException(String.format("highQC=%s highQC parent does not have a corresponding vertex", highQC));
		}

		if (seen.stream().noneMatch(highQC.highestQC().getProposed().getVertexId()::equals)) {
			throw new IllegalStateException(String.format("highQC=%s highQC proposed does not have a corresponding vertex", highQC));
		}

		return new VerifiedVertexStoreState(highQC, rootHeader, root, vertices);
	}

	public SerializedVertexStoreState toSerialized() {
		return new SerializedVertexStoreState(
			highQC,
			root.toSerializable(),
			vertices.stream().map(VerifiedVertex::toSerializable).collect(ImmutableList.toImmutableList())
		);
	}

	public HighQC getHighQC() {
		return highQC;
	}

	public VerifiedVertex getRoot() {
		return root;
	}

	public ImmutableList<VerifiedVertex> getVertices() {
		return vertices;
	}

	public VerifiedLedgerHeaderAndProof getRootHeader() {
		return rootHeader;
	}

	@Override
	public int hashCode() {
		return Objects.hash(root, rootHeader, highQC, vertices);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof VerifiedVertexStoreState)) {
			return false;
		}

		VerifiedVertexStoreState other = (VerifiedVertexStoreState) o;
		return Objects.equals(this.root, other.root)
			&& Objects.equals(this.rootHeader, other.rootHeader)
			&& Objects.equals(this.highQC, other.highQC)
			&& Objects.equals(this.vertices, other.vertices);
	}
}