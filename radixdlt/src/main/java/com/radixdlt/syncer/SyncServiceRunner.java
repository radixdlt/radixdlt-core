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

package com.radixdlt.syncer;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.execution.RadixEngineExecutor;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.utils.ThreadFactories;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SyncServiceRunner {

	private static final Logger log = LogManager.getLogger();
	private static final int MAX_REQUESTS_TO_SEND = 20;
	private final RadixEngineExecutor executor;
	private final Consumer<CommittedAtom> atomProcessor;
	private final int batchSize;
	private final int maxAtomsQueueSize;
	private final long patience;

	private final StateSyncNetwork stateSyncNetwork;
	private final TreeSet<CommittedAtom> commitedAtoms = new TreeSet<>(Comparator.comparingLong(a -> a.getVertexMetadata().getStateVersion()));
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("SyncManager"));

	private long syncToTargetVersion = -1;
	private long syncToCurrentVersion = -1;

	private ScheduledFuture<?> timeoutChecker;
	private final AddressBook addressBook;

	public SyncServiceRunner(
		RadixEngineExecutor executor,
		StateSyncNetwork stateSyncNetwork,
		AddressBook addressBook,
		Consumer<CommittedAtom> atomProcessor,
		int batchSize,
		long patience
	) {
		this.executor = Objects.requireNonNull(executor);
		this.stateSyncNetwork = Objects.requireNonNull(stateSyncNetwork);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.atomProcessor = Objects.requireNonNull(atomProcessor);
		if (batchSize <= 0) {
			throw new IllegalArgumentException();
		}
		this.batchSize = batchSize;
		if (patience <= 0) {
			throw new IllegalArgumentException();
		}
		this.patience = patience;
		// we limit the size of the queue in order to avoid memory issues
		this.maxAtomsQueueSize = MAX_REQUESTS_TO_SEND * batchSize * 2;
	}

	/**
	 * Start the service
	 */
	public void start() {
		stateSyncNetwork.syncRequests()
			.observeOn(Schedulers.io())
			.subscribe(syncRequest -> {
				log.debug("SYNC_REQUEST: {}", syncRequest);
				Peer peer = syncRequest.getPeer();
				long stateVersion = syncRequest.getStateVersion();
				// TODO: This may still return an empty list as we still count state versions for atoms which
				// TODO: never make it into the radix engine due to state errors. This is because we only check
				// TODO: validity on commit rather than on proposal/prepare.
				List<CommittedAtom> committedAtoms = executor.getCommittedAtoms(stateVersion, batchSize);
				log.debug("SYNC_REQUEST: SENDING_RESPONSE size: {}", committedAtoms.size());
				stateSyncNetwork.sendSyncResponse(peer, committedAtoms);
			});

		stateSyncNetwork.syncResponses()
			.observeOn(Schedulers.io())
			.subscribe(syncResponse -> {
				// TODO: Check validity of response
				log.debug("SYNC_RESPONSE: size: {}", syncResponse.size());
				this.syncAtoms(syncResponse);
			});
	}

	public void syncToVersion(long targetVersion, long currentVersion, List<BFTNode> target) {
		executorService.execute(() -> {
			if (currentVersion > this.syncToCurrentVersion) {
				this.syncToCurrentVersion = currentVersion;
			}

			if (targetVersion <= this.syncToCurrentVersion) {
				return;
			}

			this.syncToTargetVersion = targetVersion;
			sendSyncRequests(target);
		});
	}

	void syncAtoms(ImmutableList<CommittedAtom> atoms) {
		executorService.execute(() -> {
			for (CommittedAtom atom : atoms) {
				long atomVersion = atom.getVertexMetadata().getStateVersion();
				if (atomVersion > this.syncToCurrentVersion) {
					if (commitedAtoms.size() < maxAtomsQueueSize) { // check if there is enough space
						commitedAtoms.add(atom);
					} else { // not enough space available
						CommittedAtom last = commitedAtoms.last();
						// will added it only if it must be applied BEFORE the most recent atom we have
						if (last.getVertexMetadata().getStateVersion() > atomVersion) {
							commitedAtoms.pollLast(); // remove the most recent available
							commitedAtoms.add(atom);
						}
					}
				}
			}

			for (CommittedAtom crtAtom : commitedAtoms) {
				this.atomProcessor.accept(crtAtom);
				this.syncToCurrentVersion = crtAtom.getVertexMetadata().getStateVersion();
			}

			if (timeoutChecker != null) {
				timeoutChecker.cancel(false);
			}
		});
	}

	private void sendSyncRequest(long version, List<BFTNode> target) {
		List<Peer> peers = target.stream()
			.map(BFTNode::getKey)
			.map(pk -> addressBook.peer(pk.euid()))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.filter(Peer::hasSystem)
			.collect(Collectors.toList());
		if (peers.isEmpty()) {
			throw new IllegalStateException("Unable to find peer");
		}
		Peer peer = peers.get(ThreadLocalRandom.current().nextInt(peers.size()));
		stateSyncNetwork.sendSyncRequest(peer, version);
	}

	private void sendSyncRequests(List<BFTNode> target) {
		if (syncToCurrentVersion >= syncToTargetVersion) {
			return;
		}
		long size = ((syncToTargetVersion - syncToCurrentVersion) / batchSize);
		if ((syncToTargetVersion - syncToCurrentVersion) % batchSize > 0) {
			size += 1;
		}
		size = Math.min(size, MAX_REQUESTS_TO_SEND);
		for (long i = 0; i < size; i++) {
			sendSyncRequest(syncToCurrentVersion + batchSize * i, target);
		}
		if (timeoutChecker != null) {
			timeoutChecker.cancel(false);
		}
		timeoutChecker = executorService.schedule(() -> sendSyncRequests(target), patience, TimeUnit.SECONDS);
	}

	@VisibleForTesting
	int getMaxAtomsQueueSize() {
		return maxAtomsQueueSize;
	}

	@VisibleForTesting
	long getQueueSize() {
		return commitedAtoms.size();
	}

	public void close() {
		executorService.shutdown();
	}
}