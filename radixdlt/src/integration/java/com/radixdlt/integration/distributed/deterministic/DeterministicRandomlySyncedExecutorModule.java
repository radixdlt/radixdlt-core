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

package com.radixdlt.integration.distributed.deterministic;

import java.util.Random;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.PreparedCommand;
import com.radixdlt.consensus.SyncedExecutor;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.Hash;
import com.radixdlt.syncer.SyncExecutor.CommittedStateSyncSender;

/**
 * Module that appears to be synced at random.
 */
public class DeterministicRandomlySyncedExecutorModule extends AbstractModule {
	private final Random random;

	public DeterministicRandomlySyncedExecutorModule(Random random) {
		this.random = random;
	}

	@Singleton
	@ProvidesIntoSet
	SyncedExecutor syncedExecutor(CommittedStateSyncSender committedStateSyncSender) {
		return new SyncedExecutor() {
			@Override
			public boolean syncTo(VertexMetadata vertexMetadata, ImmutableList<BFTNode> target, Object opaque) {
				if (random.nextBoolean()) {
					return true;
				}
				committedStateSyncSender.sendCommittedStateSync(vertexMetadata.getStateVersion(), opaque);
				return false;
			}

			@Override
			public void commit(Command command, VertexMetadata vertexMetadata) {
				// No-op Mocked execution
			}

			@Override
			public PreparedCommand prepare(Vertex vertex) {
				return PreparedCommand.create(0, Hash.ZERO_HASH);
			}
		};
	}
}