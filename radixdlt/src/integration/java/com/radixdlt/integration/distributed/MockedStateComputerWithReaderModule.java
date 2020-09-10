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

package com.radixdlt.integration.distributed;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.sync.CommittedReader;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;

public class MockedStateComputerWithReaderModule extends AbstractModule {
	@Override
	public void configure() {
		bind(StateComputer.class).to(ReadableStateComputer.class).in(Scopes.SINGLETON);
		bind(CommittedReader.class).to(ReadableStateComputer.class).in(Scopes.SINGLETON);
	}

	@Provides
	private BFTConfiguration configuration(VerifiedLedgerHeaderAndProof proof, BFTValidatorSet validatorSet) {
		LedgerHeader nextLedgerHeader = LedgerHeader.create(
			proof.getEpoch() + 1,
			View.genesis(),
			proof.getStateVersion(),
			proof.getCommandId(),
			proof.timestamp(),
			false
		);
		UnverifiedVertex genesis = UnverifiedVertex.createGenesis(nextLedgerHeader);
		VerifiedVertex verifiedGenesis = new VerifiedVertex(genesis, Hash.ZERO_HASH);
		QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(verifiedGenesis, nextLedgerHeader);
		return new BFTConfiguration(validatorSet, verifiedGenesis, genesisQC);
	}

	@Provides
	private VerifiedLedgerHeaderAndProof genesisMetadata() {
		return VerifiedLedgerHeaderAndProof.genesis(Hash.ZERO_HASH);
	}

	@Singleton
	private static class ReadableStateComputer implements StateComputer, CommittedReader {
		private final TreeMap<Long, VerifiedCommandsAndProof> commandsAndProof = new TreeMap<>();

		@Override
		public boolean prepare(VerifiedVertex vertex) {
			return false;
		}

		@Override
		public Optional<BFTValidatorSet> commit(VerifiedCommandsAndProof verifiedCommandsAndProof) {
			commandsAndProof.put(verifiedCommandsAndProof.getFirstVersion(), verifiedCommandsAndProof);
			return Optional.empty();
		}

		@Override
		public VerifiedCommandsAndProof getNextCommittedCommands(long stateVersion, int batchSize) {
			Entry<Long, VerifiedCommandsAndProof> entry = commandsAndProof.higherEntry(stateVersion);
			if (entry != null) {
				return entry.getValue();
			}

			return null;
		}
	}
}