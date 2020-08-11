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

import com.radixdlt.syncer.SyncServiceRunner.LocalSyncRequestsRx;
import com.radixdlt.syncer.SyncServiceRunner.SyncTimeoutsRx;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.radixdlt.middleware2.CommittedAtom;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SyncServiceRunnerTest {

	private SyncServiceRunner syncServiceRunner;
	private LocalSyncRequestsRx localSyncRequestsRx;
	private SyncTimeoutsRx syncTimeoutsRx;
	private StateSyncNetwork stateSyncNetwork;
	private SyncServiceProcessor syncServiceProcessor;
	private Subject<SyncRequest> requestsSubject;
	private Subject<ImmutableList<CommittedAtom>> responsesSubject;

	@Before
	public void setUp() {
		this.localSyncRequestsRx = mock(LocalSyncRequestsRx.class);
		when(localSyncRequestsRx.localSyncRequests()).thenReturn(Observable.never());

		this.syncTimeoutsRx = mock(SyncTimeoutsRx.class);
		when(syncTimeoutsRx.timeouts()).thenReturn(Observable.never());

		this.stateSyncNetwork = mock(StateSyncNetwork.class);
		when(stateSyncNetwork.syncRequests()).thenReturn(Observable.never());

		this.responsesSubject = PublishSubject.create();
		when(stateSyncNetwork.syncResponses()).thenReturn(responsesSubject);

		this.requestsSubject = PublishSubject.create();
		when(stateSyncNetwork.syncRequests()).thenReturn(requestsSubject);

		this.syncServiceProcessor = mock(SyncServiceProcessor.class);
		syncServiceRunner = new SyncServiceRunner(
			localSyncRequestsRx,
			syncTimeoutsRx,
			stateSyncNetwork,
			syncServiceProcessor
		);
	}

	@After
	public void tearDown() {
		syncServiceRunner.stop();
	}

	@Test
	public void when_sync_request__then_it_is_processed() {
		SyncRequest syncRequest = mock(SyncRequest.class);
		syncServiceRunner.start();
		requestsSubject.onNext(syncRequest);
		verify(syncServiceProcessor, timeout(1000).times(1)).processSyncRequest(eq(syncRequest));
	}

	@Test
	public void when_sync_response__then_it_is_processed() {
		ImmutableList<CommittedAtom> committedAtomList = ImmutableList.of(mock(CommittedAtom.class));
		syncServiceRunner.start();
		responsesSubject.onNext(committedAtomList);
		verify(syncServiceProcessor, timeout(1000).times(1)).processSyncResponse(eq(committedAtomList));
	}
}