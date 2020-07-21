package com.radixdlt.consensus.sync;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import com.radixdlt.middleware2.CommittedAtom;

public class SyncManager {

	private static final int MAX_REQUESTS_TO_SEND = 20;
	private final Consumer<CommittedAtom> atomProcessor;
	private final LongSupplier versionProvider;
	private final long patience;
	private final long batchSize;

	private final AtomicBoolean active = new AtomicBoolean(false);
	private final ConcurrentSkipListSet<CommittedAtom> commitedAtoms = new ConcurrentSkipListSet<>((a1, a2) -> Long
			.compare(a1.getVertexMetadata().getStateVersion(), a2.getVertexMetadata().getStateVersion()));
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "SyncManagerExecutor");
		t.setDaemon(true);
		return t;
	});

	private LongConsumer requestSender;
	private long targetVersion = -1;
	private ScheduledFuture<?> timeoutChecker;

	public SyncManager(
		Consumer<CommittedAtom> atomProcessor,
		LongSupplier versionProvider,
		long batchSize,
		long patience
	) {
		this.atomProcessor = atomProcessor;
		this.versionProvider = versionProvider;
		this.batchSize = batchSize;
		this.patience = patience;
	}

	public boolean syncToVersion(long targetVersion, LongConsumer requestSender) {
		if (targetVersion <= this.targetVersion) {
			return false;
		}
		this.targetVersion = targetVersion;
		long crtVersion = versionProvider.getAsLong();
		if (crtVersion >= targetVersion) {
			return false;
		}
		active.compareAndSet(false, true);
		this.requestSender = requestSender;
		executorService.execute(this::sendSyncRequests);
		return true;
	}

	public void syncAtoms(List<CommittedAtom> atoms) {
		if (!active.get()) {
			return;
		}
		commitedAtoms.addAll(atoms);
		executorService.execute(this::applyAtoms);
	}

	private void applyAtoms() {		
		Iterator<CommittedAtom> it = commitedAtoms.iterator();
		while (it.hasNext()) {
			CommittedAtom crtAtom = it.next();
			long atomVersion = crtAtom.getVertexMetadata().getStateVersion();
			if (atomVersion <= versionProvider.getAsLong()) {
				it.remove();
			} else if (atomVersion == versionProvider.getAsLong() + 1) {
				atomProcessor.accept(crtAtom);				
				it.remove();
			} else {
				break;
			}
		}
		if (versionProvider.getAsLong() >= targetVersion) {
			active.set(false);
			commitedAtoms.clear();
			if(timeoutChecker != null) { //should not be null but doesn't hurts
				timeoutChecker.cancel(false);	
			}
		}
	}

	private void sendSyncRequests() {
		long crtVersion = versionProvider.getAsLong();
		if(crtVersion >= targetVersion) {
			return;
		}
		long size = ((targetVersion - crtVersion) / batchSize);
		if ((targetVersion - crtVersion) % batchSize > 0) {
			size += 1;
		}
		size = Math.min(size, MAX_REQUESTS_TO_SEND);
		for (long i = 0; i < size; i++) {
			requestSender.accept(crtVersion + batchSize * i);
		}
		timeoutChecker = executorService.schedule(this::sendSyncRequests, patience, TimeUnit.SECONDS);
	}

	public boolean isActive() {
		return active.get();
	}
}
