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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Module;
import com.radixdlt.SyncExecutionModule;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.TestInvariant.TestInvariantError;
import com.radixdlt.integration.distributed.simulation.invariants.epochs.EpochViewInvariant;
import com.radixdlt.integration.distributed.simulation.network.DroppingLatencyProvider;
import com.radixdlt.integration.distributed.simulation.network.OneProposalPerViewDropper;
import com.radixdlt.integration.distributed.simulation.network.RandomLatencyProvider;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.integration.distributed.simulation.invariants.bft.AllProposalsHaveDirectParentsInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.bft.LivenessInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.bft.NoTimeoutsInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.bft.NoneCommittedInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.bft.SafetyInvariant;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.LatencyProvider;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * High level BFT Simulation Test Runner
 */
public class SimulationTest {
	private final ImmutableList<BFTNode> nodes;
	private final LatencyProvider latencyProvider;
	private final ImmutableMap<String, TestInvariant> checks;
	private final int pacemakerTimeout;
	private final Function<Long, BFTValidatorSet> validatorSetMapping;
	private final boolean getVerticesRPCEnabled;
	private final View epochHighView;

	private SimulationTest(
		ImmutableList<BFTNode> nodes,
		LatencyProvider latencyProvider,
		int pacemakerTimeout,
		View epochHighView,
		Function<Long, BFTValidatorSet> validatorSetMapping,
		boolean getVerticesRPCEnabled,
		ImmutableMap<String, TestInvariant> checks
	) {
		this.nodes = nodes;
		this.latencyProvider = latencyProvider;
		this.checks = checks;
		this.pacemakerTimeout = pacemakerTimeout;
		this.epochHighView = epochHighView;
		this.validatorSetMapping = validatorSetMapping;
		this.getVerticesRPCEnabled = getVerticesRPCEnabled;
	}

	public static class Builder {
		private final DroppingLatencyProvider latencyProvider = new DroppingLatencyProvider();
		private final ImmutableMap.Builder<String, TestInvariant> checksBuilder = ImmutableMap.builder();
		private List<BFTNode> nodes = Collections.singletonList(BFTNode.create(ECKeyPair.generateNew().getPublicKey()));
		private int pacemakerTimeout = 12 * SimulationNetwork.DEFAULT_LATENCY;
		private boolean getVerticesRPCEnabled = true;
		private View epochHighView = null;
		private Function<Long, IntStream> epochToNodeIndexMapper;

		private Builder() {
		}

		public Builder addProposalDropper() {
			this.latencyProvider.addDropper(new OneProposalPerViewDropper(ImmutableList.copyOf(nodes), new Random()));
			return this;
		}

		public Builder pacemakerTimeout(int pacemakerTimeout) {
			this.pacemakerTimeout = pacemakerTimeout;
			return this;
		}

		public Builder numNodes(int numNodes) {
			this.nodes = Stream.generate(ECKeyPair::generateNew)
				.limit(numNodes)
				.map(kp -> BFTNode.create(kp.getPublicKey()))
				.collect(Collectors.toList());
			return this;
		}

		public Builder numNodesAndLatencies(int numNodes, int... latencies) {
			if (latencies.length != numNodes) {
				throw new IllegalArgumentException(String.format("Number of latencies (%d) not equal to numNodes (%d)", numNodes, latencies.length));
			}
			this.nodes = Stream.generate(ECKeyPair::generateNew)
				.limit(numNodes)
				.map(kp -> BFTNode.create(kp.getPublicKey()))
				.collect(Collectors.toList());
			Map<BFTNode, Integer> nodeLatencies = IntStream.range(0, numNodes)
				.boxed()
				.collect(Collectors.toMap(i -> this.nodes.get(i), i -> latencies[i]));
			this.latencyProvider.setBase(msg -> Math.max(nodeLatencies.get(msg.getSender()), nodeLatencies.get(msg.getReceiver())));
			return this;
		}

		public Builder epochHighView(View epochHighView) {
			this.epochHighView = epochHighView;
			return this;
		}

		public Builder epochToNodesMapper(Function<Long, IntStream> epochToNodeIndexMapper) {
			this.epochToNodeIndexMapper = epochToNodeIndexMapper;
			return this;
		}

		public Builder setGetVerticesRPCEnabled(boolean getVerticesRPCEnabled) {
			this.getVerticesRPCEnabled = getVerticesRPCEnabled;
			return this;
		}

		public Builder randomLatency(int minLatency, int maxLatency) {
			this.latencyProvider.setBase(new RandomLatencyProvider(minLatency, maxLatency));
			return this;
		}

		public Builder checkLiveness(String invariantName) {
			this.checksBuilder.put(invariantName, new LivenessInvariant(8 * SimulationNetwork.DEFAULT_LATENCY, TimeUnit.MILLISECONDS));
			return this;
		}

		public Builder checkLiveness(String invariantName, long duration, TimeUnit timeUnit) {
			this.checksBuilder.put(invariantName, new LivenessInvariant(duration, timeUnit));
			return this;
		}

		public Builder checkSafety(String invariantName) {
			this.checksBuilder.put(invariantName, new SafetyInvariant());
			return this;
		}

		public Builder checkNoTimeouts(String invariantName) {
			this.checksBuilder.put(invariantName, new NoTimeoutsInvariant());
			return this;
		}

		public Builder checkAllProposalsHaveDirectParents(String invariantName) {
			this.checksBuilder.put(invariantName, new AllProposalsHaveDirectParentsInvariant());
			return this;
		}

		public Builder checkNoneCommitted(String invariantName) {
			this.checksBuilder.put(invariantName, new NoneCommittedInvariant());
			return this;
		}

		public Builder checkEpochHighView(String invariantName, View epochHighView) {
			this.checksBuilder.put(invariantName, new EpochViewInvariant(epochHighView));
			return this;
		}

		public SimulationTest build() {
			Function<Long, BFTValidatorSet> epochToValidatorSetMapping =
				epochToNodeIndexMapper == null
					? epoch -> BFTValidatorSet.from(
						nodes.stream()
							.map(node -> BFTValidator.from(node, UInt256.ONE))
							.collect(Collectors.toList()))
					: epochToNodeIndexMapper.andThen(indices -> BFTValidatorSet.from(
						indices.mapToObj(nodes::get)
							.map(node -> BFTValidator.from(node, UInt256.ONE))
							.collect(Collectors.toList())));
			return new SimulationTest(
				ImmutableList.copyOf(nodes),
				latencyProvider.copyOf(),
				pacemakerTimeout,
				epochHighView,
				epochToValidatorSetMapping,
				getVerticesRPCEnabled,
				this.checksBuilder.build()
			);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	private Observable<Pair<String, Optional<TestInvariantError>>> runChecks(RunningNetwork runningNetwork, long duration, TimeUnit timeUnit) {
		List<Pair<String, Observable<Pair<String, TestInvariantError>>>> assertions = this.checks.keySet().stream()
			.map(name -> {
				TestInvariant check = this.checks.get(name);
				return
					Pair.of(
						name,
						check.check(runningNetwork).map(e -> Pair.of(name, e)).publish().autoConnect(2)
					);
			})
			.collect(Collectors.toList());

		Single<String> firstErrorSignal = Observable.merge(assertions.stream().map(Pair::getSecond).collect(Collectors.toList()))
			.firstOrError()
			.map(Pair::getFirst);

		List<Single<Pair<String, Optional<TestInvariantError>>>> results = assertions.stream()
			.map(assertion -> assertion.getSecond()
				.takeUntil(firstErrorSignal.flatMapObservable(name ->
					!assertion.getFirst().equals(name) ? Observable.just(name) : Observable.never()))
				.takeUntil(Observable.timer(duration, timeUnit))
				.map(e -> Optional.of(e.getSecond()))
				.first(Optional.empty())
				.map(result -> Pair.of(assertion.getFirst(), result))
			)
			.collect(Collectors.toList());

		return Single.merge(results).toObservable();
	}

	/**
	 * Runs the test for a given time. Returns either once the duration has passed or if a check has failed.
	 * Returns a map from the check name to the result.
	 *
	 * @param duration duration to run test for
	 * @param timeUnit time unit of duration
	 * @return map of check results
	 */
	public Map<String, Optional<TestInvariantError>> run(long duration, TimeUnit timeUnit) {
		SimulationNetwork network = SimulationNetwork.builder()
			.latencyProvider(this.latencyProvider)
			.build();

		ImmutableList.Builder<Module> syncExecutionModules = ImmutableList.builder();

		if (epochHighView == null) {
			BFTValidatorSet validatorSet = BFTValidatorSet.from(
				nodes.stream()
					.map(node -> BFTValidator.from(node, UInt256.ONE))
					.collect(Collectors.toList())
			);
			syncExecutionModules.add(new MockedSyncExecutionModule(validatorSet));
		} else {
			ConcurrentHashMap<Long, CommittedAtom> sharedCommittedAtoms = new ConcurrentHashMap<>();
			syncExecutionModules.add(new SyncExecutionModule());
			syncExecutionModules.add(new MockedSyncServiceAndStateComputerModule(sharedCommittedAtoms, epochHighView, validatorSetMapping));
		}

		SimulationNodes bftNetwork =  new SimulationNodes(nodes, network, pacemakerTimeout, syncExecutionModules.build(), getVerticesRPCEnabled);
		RunningNetwork runningNetwork = bftNetwork.start();

		return runChecks(runningNetwork, duration, timeUnit)
			.doFinally(bftNetwork::stop)
			.blockingStream()
			.collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
	}
}