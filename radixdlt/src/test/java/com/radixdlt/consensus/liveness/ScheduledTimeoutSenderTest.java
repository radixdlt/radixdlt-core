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

package com.radixdlt.consensus.liveness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.radixdlt.consensus.LocalTimeout;
import com.radixdlt.utils.ThreadFactories;

import io.reactivex.rxjava3.observers.TestObserver;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ScheduledTimeoutSenderTest {

	private ScheduledTimeoutSender scheduledTimeoutSender;
	private ScheduledExecutorService executorService;
	private ScheduledExecutorService executorServiceMock;

	@Before
	public void setUp() {
		this.executorService = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("TimeoutSender"));
		this.executorServiceMock = mock(ScheduledExecutorService.class);
		doAnswer(invocation -> {
			// schedule submissions with a small timeout to ensure that control is returned before the
			// "scheduled" runnable is executed, otherwise required events may not be triggered in time
			this.executorService.schedule((Runnable) invocation.getArguments()[0], 10, TimeUnit.MILLISECONDS);
			return null;
		}).when(this.executorServiceMock).schedule(any(Runnable.class), anyLong(), any());

		this.scheduledTimeoutSender = new ScheduledTimeoutSender(this.executorServiceMock);
	}

	@After
	public void tearDown() throws InterruptedException {
		if (this.executorService != null) {
			this.executorService.shutdown();
			this.executorService.awaitTermination(10L, TimeUnit.SECONDS);
		}
	}

	@Test
	public void when_subscribed_to_local_timeouts_and_schedule_timeout__then_a_timeout_event_with_view_is_emitted() {
		TestObserver<LocalTimeout> testObserver = scheduledTimeoutSender.localTimeouts().test();
		LocalTimeout localTimeout = mock(LocalTimeout.class);
		long timeout = 10;
		scheduledTimeoutSender.scheduleTimeout(localTimeout, timeout);
		testObserver.awaitCount(1);
		testObserver.assertNotComplete();
		testObserver.assertValues(localTimeout);
		verify(executorServiceMock, times(1)).schedule(any(Runnable.class), eq(timeout), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	public void when_subscribed_to_local_timeouts_and_schedule_timeout_twice__then_two_timeout_events_are_emitted() {
		TestObserver<LocalTimeout> testObserver = scheduledTimeoutSender.localTimeouts().test();
		LocalTimeout timeout1 = mock(LocalTimeout.class);
		LocalTimeout timeout2 = mock(LocalTimeout.class);
		long timeout = 10;
		scheduledTimeoutSender.scheduleTimeout(timeout1, timeout);
		scheduledTimeoutSender.scheduleTimeout(timeout2, timeout);
		testObserver.awaitCount(2);
		testObserver.assertNotComplete();
		testObserver.assertValues(timeout1, timeout2);
		verify(executorServiceMock, times(2)).schedule(any(Runnable.class), eq(timeout), eq(TimeUnit.MILLISECONDS));
	}
}
