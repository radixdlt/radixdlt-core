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

package com.radixdlt.integration.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.deterministic.DeterministicEpochsConsensusProcessor;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.DeterministicSavedLastEvent;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.integration.distributed.deterministic.NodeEvents;
import com.radixdlt.integration.distributed.deterministic.NodeEventsModule;
import com.radixdlt.integration.distributed.deterministic.SafetyCheckerModule;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.recovery.ModuleForRecoveryTests;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;

import io.reactivex.rxjava3.schedulers.Timed;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.radix.database.DatabaseEnvironment;

/**
 * Various liveness+recovery tests
 */
@RunWith(Parameterized.class)
public class RecoveryLivenessTest {
	private static final Logger logger = LogManager.getLogger();

	@Parameters
	public static Collection<Object[]> numNodes() {
		return List.of(new Object[][] {
			{1, 88L}, {2, 88L}, {3, 88L}, {4, 88L},
			{2, 1L}
		});
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private DeterministicNetwork network;
	private List<Supplier<Injector>> nodeCreators;
	private List<Injector> nodes = new ArrayList<>();
	private final List<ECKeyPair> nodeKeys;
	private final long epochCeilingView;

	@Inject
	private NodeEvents nodeEvents;

	public RecoveryLivenessTest(int numNodes, long epochCeilingView) {
		this.nodeKeys = Stream.generate(ECKeyPair::generateNew).limit(numNodes).collect(Collectors.toList());
		this.epochCeilingView = epochCeilingView;
	}

	@Before
	public void setup() {
		this.network = new DeterministicNetwork(
			nodeKeys.stream().map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList()),
			MessageSelector.firstSelector(),
			MessageMutator.nothing()
		);

		List<BFTNode> allNodes = nodeKeys.stream()
			.map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList());

		Guice.createInjector(
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(new TypeLiteral<List<BFTNode>>() { }).toInstance(allNodes);
				}
			},
			new SafetyCheckerModule(),
			new NodeEventsModule()
		).injectMembers(this);

		this.nodeCreators = nodeKeys.stream()
			.<Supplier<Injector>>map(k -> () -> createRunner(k, allNodes))
			.collect(Collectors.toList());

		for (Supplier<Injector> nodeCreator : nodeCreators) {
			this.nodes.add(nodeCreator.get());
		}
		this.nodes.forEach(i -> i.getInstance(DeterministicEpochsConsensusProcessor.class).start());
	}

	@After
	public void teardown() {
		this.nodes.forEach(this::stopDatabase);
	}

	private Injector createRunner(ECKeyPair ecKeyPair, List<BFTNode> allNodes) {
		final BFTNode self = BFTNode.create(ecKeyPair.getPublicKey());

		return Guice.createInjector(
			ModuleForRecoveryTests.create(),
			new AbstractModule() {

				@ProvidesIntoSet
				@ProcessOnDispatch
				private EventProcessor<BFTCommittedUpdate> test(@Self BFTNode node) {
					return nodeEvents.processor(node, BFTCommittedUpdate.class);
				}

				@Override
				protected void configure() {
					bind(HashSigner.class).toInstance(ecKeyPair::sign);
					bind(BFTNode.class).annotatedWith(Self.class).toInstance(self);
					bind(new TypeLiteral<List<BFTNode>>() { }).toInstance(allNodes);
					bind(ControlledSenderFactory.class).toInstance(network::createSender);
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(epochCeilingView));

					final RuntimeProperties runtimeProperties;
					// TODO: this constructor/class/inheritance/dependency is horribly broken
					try {
						runtimeProperties = new RuntimeProperties(new JSONObject(), new String[0]);
						runtimeProperties.set("db.location", folder.getRoot().getAbsolutePath() + "/RADIXDB_RECOVERY_TEST_" + self);
					} catch (ParseException e) {
						throw new IllegalStateException();
					}
					bind(RuntimeProperties.class).toInstance(runtimeProperties);
				}
			}
		);
	}

	private void restartNode(int index) {
		this.network.dropMessages(m -> m.channelId().receiverIndex() == index);
		Injector injector = nodeCreators.get(index).get();
		stopDatabase(this.nodes.set(index, injector));

		String bftNode = " " + injector.getInstance(Key.get(BFTNode.class, Self.class));
		ThreadContext.put("bftNode", bftNode);
		try {
			injector.getInstance(DeterministicEpochsConsensusProcessor.class).start();
		} finally {
			ThreadContext.remove("bftNode");
		}
	}

	private Timed<ControlledMessage> processNext() {
		Timed<ControlledMessage> msg = this.network.nextMessage();
		logger.debug("Processing message {}", msg);

		int nodeIndex = msg.value().channelId().receiverIndex();
		Injector injector = this.nodes.get(nodeIndex);
		String bftNode = " " + injector.getInstance(Key.get(BFTNode.class, Self.class));
		ThreadContext.put("bftNode", bftNode);
		try {
			injector.getInstance(DeterministicEpochsConsensusProcessor.class).handleMessage(msg.value().origin(), msg.value().message());
		} finally {
			ThreadContext.remove("bftNode");
		}

		return msg;
	}

	private Optional<EpochView> lastCommitViewEmitted() {
		return network.allMessages().stream()
			.filter(msg -> msg.message() instanceof EpochViewUpdate)
			.map(msg -> (EpochViewUpdate) msg.message())
			.map(e -> new EpochView(e.getEpoch(), e.getViewUpdate().getHighestCommitView()))
			.max(Comparator.naturalOrder());
	}

	private int processUntilNextCommittedEmitted(int maxSteps) {
		EpochView lastCommitted = this.lastCommitViewEmitted().orElse(new EpochView(0, View.genesis()));
		int count = 0;
		int senderIndex;
		do {
			if (count > maxSteps) {
				throw new IllegalStateException("Already lost liveness");
			}

			Timed<ControlledMessage> msg = processNext();
			senderIndex = msg.value().channelId().senderIndex();
			count++;
		} while (this.lastCommitViewEmitted().stream().noneMatch(v -> v.compareTo(lastCommitted) > 0));

		return senderIndex;
	}

	private void processForCount(int messageCount) {
		for (int i = 0; i < messageCount; i++) {
			processNext();
		}
	}

	private void stopDatabase(Injector injector) {
		injector.getInstance(BerkeleyLedgerEntryStore.class).close();
		injector.getInstance(PersistentSafetyStateStore.class).close();
		injector.getInstance(DatabaseEnvironment.class).stop();
	}

	private EpochView latestEpochView() {
		return this.nodes.stream()
			.map(i -> i.getInstance(Key.get(new TypeLiteral<DeterministicSavedLastEvent<EpochViewUpdate>>() { })).getLastEvent())
			.map(EpochViewUpdate::getEpochView)
			.max(Comparator.naturalOrder()).orElse(new EpochView(0, View.genesis()));
	}

	/**
	 * Given that one validator is always alive means that that validator will
	 * always have the latest committed vertex which the validator can sync
	 * others with.
	 */
	@Test
	public void liveness_check_when_restart_all_but_one_node() {
		EpochView epochView = this.latestEpochView();

		for (int restart = 0; restart < 5; restart++) {
			processForCount(5000);

			EpochView nextEpochView = latestEpochView();
			assertThat(nextEpochView).isGreaterThan(epochView);
			epochView = nextEpochView;

			logger.info("Restarting " + restart);
			for (int nodeIndex = 1; nodeIndex < nodes.size(); nodeIndex++) {
				restartNode(nodeIndex);
			}
		}
	}

	@Test
	public void liveness_check_when_restart_node_on_view_update_with_commit() {
		EpochView epochView = this.latestEpochView();

		for (int restart = 0; restart < 5; restart++) {
			processForCount(5000);

			EpochView nextEpochView = latestEpochView();
			assertThat(nextEpochView).isGreaterThan(epochView);
			epochView = nextEpochView;

			int nodeToRestart = processUntilNextCommittedEmitted(5000);

			logger.info("Restarting " + restart);
			for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
				if (nodeIndex != (nodeToRestart + 1) % nodes.size()) {
					restartNode(nodeIndex);
				}
			}
		}
	}

	@Test
	public void liveness_check_when_restart_all_nodes() {
		EpochView epochView = this.latestEpochView();

		for (int restart = 0; restart < 5; restart++) {
			processForCount(5000);

			EpochView nextEpochView = latestEpochView();
			assertThat(nextEpochView).isGreaterThan(epochView);
			epochView = nextEpochView;

			logger.info("Restarting " + restart);
			for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
				restartNode(nodeIndex);
			}
		}
	}
}