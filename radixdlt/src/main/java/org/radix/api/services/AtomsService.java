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

package org.radix.api.services;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.api.DeserializationFailure;
import com.radixdlt.api.CommittedAtomsRx;
import com.radixdlt.api.SubmissionErrorsRx;
import com.radixdlt.api.SubmissionFailure;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.mempool.SubmissionControl;

import com.radixdlt.middleware2.store.StoredCommittedCommand;
import com.radixdlt.statecomputer.ClientAtomToBinaryConverter;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomWithResult;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.radixdlt.middleware2.ClientAtom;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.radixdlt.middleware2.store.CommandToBinaryConverter;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryStore;

import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.radix.api.AtomQuery;
import org.radix.api.observable.AtomEventObserver;
import org.radix.api.observable.Disposable;
import org.radix.api.observable.ObservedAtomEvents;
import org.radix.api.observable.Observable;
import com.radixdlt.identifiers.AID;

public class AtomsService {
	private static int  NUMBER_OF_THREADS = 8;
	/**
	 * Some of these may block for a short while so keep a few.
	 * TODO: remove the blocking
	 */
	private final static ExecutorService executorService = Executors.newFixedThreadPool(
		NUMBER_OF_THREADS,
		new ThreadFactoryBuilder().setNameFormat("AtomsService-%d").build()
	);

	private final Set<AtomEventObserver> atomEventObservers = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final Object singleAtomObserversLock = new Object();
	private final Map<AID, List<AtomStatusListener>> singleAtomObservers = new HashMap<>();
	private final Object deleteOnEventSingleAtomObserversLock = new Object();
	private final Map<AID, List<SingleAtomListener>> deleteOnEventSingleAtomObservers = new HashMap<>();

	private final Serialization serialization = DefaultSerialization.getInstance();

	private final SubmissionControl submissionControl;
	private final CommandToBinaryConverter commandToBinaryConverter;
	private final ClientAtomToBinaryConverter clientAtomToBinaryConverter;
	private final LedgerEntryStore store;
	private final CompositeDisposable disposable;

	private final SubmissionErrorsRx submissionErrorsRx;
	private final CommittedAtomsRx committedAtomsRx;

	public AtomsService(
		SubmissionErrorsRx submissionErrorsRx,
		CommittedAtomsRx committedAtomsRx,
		LedgerEntryStore store,
		SubmissionControl submissionControl,
		CommandToBinaryConverter commandToBinaryConverter,
		ClientAtomToBinaryConverter clientAtomToBinaryConverter
	) {
		this.submissionErrorsRx = Objects.requireNonNull(submissionErrorsRx);
		this.submissionControl = Objects.requireNonNull(submissionControl);
		this.store = Objects.requireNonNull(store);
		this.commandToBinaryConverter = Objects.requireNonNull(commandToBinaryConverter);
		this.clientAtomToBinaryConverter = Objects.requireNonNull(clientAtomToBinaryConverter);
		this.disposable = new CompositeDisposable();
		this.committedAtomsRx = committedAtomsRx;
	}

	private void processExecutedCommand(CommittedAtomWithResult committedAtomWithResult) {
		CommittedAtom committedAtom = committedAtomWithResult.getCommittedAtom();
		committedAtomWithResult.ifSuccess(indicies -> this.atomEventObservers.forEach(observer -> observer.tryNext(committedAtom, indicies)));
		for (SingleAtomListener subscriber : getDeleteOnEventListeners(committedAtom.getAID())) {
			committedAtomWithResult
				.ifSuccess(indicies -> subscriber.onStored())
				.ifError(subscriber::onStoredFailure);
		}
		for (AtomStatusListener atomStatusListener : getAtomStatusListeners(committedAtom.getAID())) {
			committedAtomWithResult
				.ifSuccess(indicies -> atomStatusListener.onStored(committedAtom))
				.ifError(e -> atomStatusListener.onStoredFailure(committedAtom, e));
		}
	}

	private void processSubmissionFailure(SubmissionFailure e) {
		List<SingleAtomListener> subscribers = removeDeleteOnEventListeners(e.getClientAtom().getAID());
		if (subscribers != null) {
			subscribers.forEach(subscriber -> subscriber.onError(e.getClientAtom().getAID(), e.getException()));
		}

		for (AtomStatusListener singleAtomListener : getAtomStatusListeners(e.getClientAtom().getAID())) {
			singleAtomListener.onError(e.getException());
		}
	}

	private void processDeserializationFailure(DeserializationFailure e) {
		List<SingleAtomListener> subscribers = removeDeleteOnEventListeners(e.getAtom().getAID());
		if (subscribers != null) {
			subscribers.forEach(subscriber -> subscriber.onError(e.getAtom().getAID(), e.getException()));
		}

		for (AtomStatusListener singleAtomListener : getAtomStatusListeners(e.getAtom().getAID())) {
			singleAtomListener.onError(e.getException());
		}
	}

	public void start() {
		io.reactivex.rxjava3.disposables.Disposable lastStoredAtomDisposable = committedAtomsRx.committedAtoms()
			.observeOn(Schedulers.io())
			.subscribe(this::processExecutedCommand);
		this.disposable.add(lastStoredAtomDisposable);

		io.reactivex.rxjava3.disposables.Disposable submissionFailuresDisposable = submissionErrorsRx.submissionFailures()
			.observeOn(Schedulers.io())
			.subscribe(this::processSubmissionFailure);
		this.disposable.add(submissionFailuresDisposable);

		io.reactivex.rxjava3.disposables.Disposable deserializationFailures = submissionErrorsRx.deserializationFailures()
			.observeOn(Schedulers.io())
			.subscribe(this::processDeserializationFailure);
		this.disposable.add(deserializationFailures);
	}

	public void stop() {
		this.disposable.dispose();
	}

	public AID submitAtom(JSONObject jsonAtom, SingleAtomListener subscriber) {
		AtomicReference<AID> aid = new AtomicReference<>();
		try {
			this.submissionControl.submitAtom(jsonAtom, atom -> {
				aid.set(atom.getAID());
				subscribeToSubmission(subscriber, atom);
			});
			return aid.get();
		} catch (MempoolRejectedException e) {
			if (subscriber != null) {
				AID atomId = aid.get();
				removeDeleteOnEventListener(atomId, subscriber);
				subscriber.onError(atomId, e);
			}
			throw new IllegalStateException(e);
		}
	}

	private void subscribeToSubmission(SingleAtomListener subscriber, ClientAtom atom) {
		if (subscriber != null) {
			addDeleteOnEventListener(atom.getAID(), subscriber);
		}
	}

	public Disposable subscribeAtomStatusNotifications(AID aid, AtomStatusListener subscriber) {
		addAtomStatusListener(aid, subscriber);

		return () -> removeAtomStatusListener(aid, subscriber);
	}

	public Observable<ObservedAtomEvents> getAtomEvents(AtomQuery atomQuery) {
		return observer -> {
			final AtomEventObserver atomEventObserver = new AtomEventObserver(atomQuery, observer, executorService, store, commandToBinaryConverter, clientAtomToBinaryConverter);
			atomEventObserver.start();
			this.atomEventObservers.add(atomEventObserver);

			return () -> {
				this.atomEventObservers.remove(atomEventObserver);
				atomEventObserver.cancel();
			};
		};
	}

	public long getWaitingCount() {
		return this.atomEventObservers.stream().map(AtomEventObserver::isDone).filter(done -> !done).count();
	}

	public JSONObject getAtomsByAtomId(AID atomId) throws JSONException {
		Optional<LedgerEntry> ledgerEntryOptional = store.get(atomId);
		if (ledgerEntryOptional.isPresent()) {
			LedgerEntry ledgerEntry = ledgerEntryOptional.get();
			StoredCommittedCommand committedCommand = commandToBinaryConverter.toCommand(ledgerEntry.getContent());
			ClientAtom clientAtom = committedCommand.getCommand().map(clientAtomToBinaryConverter::toAtom);
			Atom apiAtom = ClientAtom.convertToApiAtom(clientAtom);
			return serialization.toJsonObject(apiAtom, DsonOutput.Output.API);
		}
		throw new RuntimeException("Atom not found");
	}

	private ImmutableList<SingleAtomListener> getDeleteOnEventListeners(AID aid) {
		synchronized (this.deleteOnEventSingleAtomObserversLock) {
			return ImmutableList.copyOf(this.deleteOnEventSingleAtomObservers.getOrDefault(aid, ImmutableList.of()));
		}
	}

	private void addDeleteOnEventListener(AID aid, SingleAtomListener listener) {
		synchronized (this.deleteOnEventSingleAtomObserversLock) {
			this.deleteOnEventSingleAtomObservers.computeIfAbsent(aid, key -> Lists.newArrayList()).add(listener);
		}
	}

	private List<SingleAtomListener> removeDeleteOnEventListeners(AID aid) {
		synchronized (this.deleteOnEventSingleAtomObserversLock) {
			// OK to just remove and return, as it is inaccessible anyway
			return this.deleteOnEventSingleAtomObservers.remove(aid);
		}
	}

	private void removeDeleteOnEventListener(AID aid, SingleAtomListener listener) {
		synchronized (this.deleteOnEventSingleAtomObserversLock) {
			List<SingleAtomListener> listeners = this.deleteOnEventSingleAtomObservers.get(aid);
			if (listeners != null) {
				listeners.remove(listener);
				if (listeners.isEmpty()) {
					this.deleteOnEventSingleAtomObservers.remove(aid);
				}
			}
		}
	}

	private ImmutableList<AtomStatusListener> getAtomStatusListeners(AID aid) {
		synchronized (this.singleAtomObserversLock) {
			return ImmutableList.copyOf(this.singleAtomObservers.getOrDefault(aid, ImmutableList.of()));
		}
	}

	private void addAtomStatusListener(AID aid, AtomStatusListener listener) {
		synchronized (this.singleAtomObserversLock) {
			this.singleAtomObservers.computeIfAbsent(aid, key -> Lists.newArrayList()).add(listener);
		}
	}

	private void removeAtomStatusListener(AID aid, AtomStatusListener listener) {
		synchronized (this.singleAtomObserversLock) {
			List<AtomStatusListener> listeners = this.singleAtomObservers.get(aid);
			if (listeners != null) {
				listeners.remove(listener);
				if (listeners.isEmpty()) {
					this.singleAtomObservers.remove(aid);
				}
			}
		}
	}
}
