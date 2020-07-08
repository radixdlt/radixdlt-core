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

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;

/**
 * Sender which goes nowhere
 */
public enum EmptySyncVerticesRPCSender implements SyncVerticesRPCSender {
	INSTANCE;
	@Override
	public void sendGetVerticesRequest(Hash id, ECPublicKey node, int count, Object opaque) {
		// empty
	}

	@Override
	public void sendGetVerticesResponse(GetVerticesRequest originalRequest, ImmutableList<Vertex> vertices) {
		// empty
	}

	@Override
	public void sendGetVerticesErrorResponse(GetVerticesRequest originalRequest, QuorumCertificate highestQC,
		QuorumCertificate highestCommittedQC) {
		// empty
	}
}
