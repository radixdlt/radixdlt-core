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

package com.radixdlt;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.systeminfo.InMemorySystemInfoManager;
import com.radixdlt.systeminfo.InfoRx;
import com.radixdlt.systeminfo.Timeout;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.middleware2.InfoSupplier;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.properties.RuntimeProperties;

import java.util.Objects;

import org.radix.Radix;

public class SystemInfoModule extends AbstractModule {
	private static final int DEFAULT_VERTEX_BUFFER_SIZE = 16;
	private static final long DEFAULT_VERTEX_UPDATE_FREQ = 1_000L;
	private final RuntimeProperties runtimeProperties;

	public SystemInfoModule(RuntimeProperties runtimeProperties) {
		this.runtimeProperties = Objects.requireNonNull(runtimeProperties);
	}

	@Provides
	@Singleton
	private InMemorySystemInfoManager infoStateRunner(InfoRx infoRx) {
		final int vertexBufferSize = runtimeProperties.get("api.debug.vertex_buffer_size", DEFAULT_VERTEX_BUFFER_SIZE);
		final long vertexUpdateFrequency = runtimeProperties.get("api.debug.vertex_update_freq", DEFAULT_VERTEX_UPDATE_FREQ);
		return new InMemorySystemInfoManager(infoRx, vertexBufferSize, vertexUpdateFrequency);
	}

	@Provides
	@Singleton
	private InfoSupplier infoSupplier(
		SystemCounters counters,
		InMemorySystemInfoManager infoStateManager
	) {
		return () -> {
			EpochView currentEpochView = infoStateManager.getCurrentView();
			Timeout timeout = infoStateManager.getLastTimeout();
			QuorumCertificate highQC = infoStateManager.getHighestQC();

			return ImmutableMap.of(
				"epochManager", ImmutableMap.of(
					"highQC", highQC != null ? ImmutableMap.of(
						"epoch", highQC.getProposed().getEpoch(),
						"view", highQC.getView().number(),
						"vertexId", highQC.getProposed().getId()
					)
					: ImmutableMap.of(),
					"currentView", ImmutableMap.of(
						"epoch", currentEpochView.getEpoch(),
						"view", currentEpochView.getView().number()
					),
					"lastTimeout", timeout != null ? ImmutableMap.of(
						"epoch", timeout.getEpochView().getEpoch(),
						"view", timeout.getEpochView().getView().number(),
						"leader", timeout.getLeader().toString()
					)
					: ImmutableMap.of()
				),
				"counters", counters.toMap(),
				"system_version", Radix.systemVersionInfo()
			);
		};
	}
}
