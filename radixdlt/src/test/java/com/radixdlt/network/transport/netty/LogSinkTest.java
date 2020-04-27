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

package com.radixdlt.network.transport.netty;

import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LogSinkTest {

	private Logger log;
	private LogSink logSink;

	@Before
	public void beforeTest() {
		this.log = mock(Logger.class);
		this.logSink = LogSink.using(log);
	}

	@Test
	public void testIsDebugEnabled() {
		when(log.isDebugEnabled()).thenReturn(true); // false is default

		assertTrue(logSink.isDebugEnabled());
		verify(log, times(1)).isDebugEnabled();
		verifyNoMoreInteractions(log);
	}

	@Test
	public void testDebugMessage() {
		logSink.debug("foo");
		verify(log, times(1)).debug("foo");
		verifyNoMoreInteractions(log);
	}

	@Test
	public void testDebugThrowable() {
		Throwable t = new Throwable();
		logSink.debug("bar", t);
		verify(log, times(1)).debug("bar", t);
		verifyNoMoreInteractions(log);
	}

	@Test
	public void testIsTraceEnabled() {
		when(log.isTraceEnabled()).thenReturn(true); // false is default

		assertTrue(logSink.isTraceEnabled());
		verify(log, times(1)).isTraceEnabled();
		verifyNoMoreInteractions(log);
	}

	@Test
	public void testTraceMessage() {
		logSink.trace("baz");
		verify(log, times(1)).trace("baz");
		verifyNoMoreInteractions(log);
	}
}
