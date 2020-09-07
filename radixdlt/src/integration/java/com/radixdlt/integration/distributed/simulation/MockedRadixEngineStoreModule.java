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

package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.PreparedCommand;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.CommittedCommandsReader;
import com.radixdlt.store.EngineStore;
import java.util.Objects;

public class MockedRadixEngineStoreModule extends AbstractModule {
	private final BFTValidatorSet validatorSet;

	public MockedRadixEngineStoreModule(BFTValidatorSet validatorSet) {
		this.validatorSet = Objects.requireNonNull(validatorSet);
	}

	public void configure() {
		bind(CommittedCommandsReader.class).toInstance((stateVersion, limit) -> {
			throw new UnsupportedOperationException();
		});
		bind(Serialization.class).toInstance(DefaultSerialization.getInstance());
		bind(Integer.class).annotatedWith(Names.named("magic")).toInstance(1);
		bind(new TypeLiteral<EngineStore<LedgerAtom>>() { }).to(new TypeLiteral<InMemoryEngineStore<LedgerAtom>>() { })
			.in(Scopes.SINGLETON);
	}

	@Provides
	private BFTValidatorSet genesisValidatorSet() {
		return validatorSet;
	}

	@Provides
	public VertexMetadata genesisVertexMetadata() {
		final PreparedCommand preparedCommand = PreparedCommand.create(0, 0, true);
		return VertexMetadata.ofGenesisAncestor(preparedCommand);
	}
}
