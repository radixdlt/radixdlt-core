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

package com.radixdlt.consensus.simulation.invariants.epochs;

import com.radixdlt.consensus.View;
import com.radixdlt.consensus.simulation.TestInvariant;
import com.radixdlt.consensus.simulation.network.SimulatedNetwork.RunningNetwork;
import io.reactivex.rxjava3.core.Observable;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Invariant which checks that there is only ever one view which exceeds the epochHighView for
 * every epoch.
 */
public class EpochViewInvariant implements TestInvariant {
	private final View epochHighView;

	public EpochViewInvariant(View epochHighView) {
		this.epochHighView = Objects.requireNonNull(epochHighView);
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		final Map<Long, Set<View>> viewsOverEpochHighView = new ConcurrentHashMap<>();

		return Observable.merge(
			network.getNodes().stream().map(node -> network.getVertexStoreEvents(node).committedVertices()).collect(Collectors.toList())
		)
			.flatMap(vertex -> {
				final long epoch = vertex.getEpoch();
				final View view = vertex.getView();
				if (view.compareTo(epochHighView) >= 0) {
					final Set<View> views = viewsOverEpochHighView.computeIfAbsent(epoch, e -> new HashSet<>());
					views.add(view);
					if (views.size() > 1) {
						return Observable.just(
							new TestInvariantError(
								String.format("Multiple vertices (%s) committed over epochHighView %s",
									views,
									epochHighView
								)
							)
						);
					}
				}

				return Observable.empty();
			});
	}

}