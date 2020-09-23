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
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.radixdlt.ModuleRunner;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.LedgerUpdateProcessor;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor;
import com.radixdlt.sync.LocalSyncServiceProcessor;
import com.radixdlt.sync.RemoteSyncResponseProcessor;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier;
import com.radixdlt.sync.SyncServiceRunner;

/**
 * A sync runner which doesn't involve epochs
 */
public class MockedSyncRunnerModule extends AbstractModule {
	@Override
	public void configure() {
		MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class)
			.addBinding("sync").to(Key.get(new TypeLiteral<SyncServiceRunner<LedgerUpdate>>() { }));
		bind(RemoteSyncResponseProcessor.class).to(RemoteSyncResponseValidatorSetVerifier.class).in(Scopes.SINGLETON);
		bind(LocalSyncServiceProcessor.class).to(LocalSyncServiceAccumulatorProcessor.class).in(Scopes.SINGLETON);
		bind(Key.get(new TypeLiteral<LedgerUpdateProcessor<LedgerUpdate>>() { }))
			.to(LocalSyncServiceAccumulatorProcessor.class).in(Scopes.SINGLETON);
	}
}