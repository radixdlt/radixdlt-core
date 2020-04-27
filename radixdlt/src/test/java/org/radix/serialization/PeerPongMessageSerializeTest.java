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

package org.radix.serialization;

import org.junit.Test;
import org.radix.network.messages.PeerPongMessage;
import org.radix.universe.system.RadixSystem;

import com.radixdlt.identifiers.EUID;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Check serialization of UDPPongMessage
 */
public class PeerPongMessageSerializeTest extends SerializeMessageObject<PeerPongMessage> {
	public PeerPongMessageSerializeTest() {
		super(PeerPongMessage.class, PeerPongMessageSerializeTest::get);
	}

	private static PeerPongMessage get() {
		return new PeerPongMessage(0L, getLocalSystem(), 1);
	}

	@Test
	public void sensibleToString() {
		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(EUID.TWO);
		String s = new PeerPongMessage(1234L, system, 0).toString();

		assertThat(s, containsString(PeerPongMessage.class.getSimpleName()));
		assertThat(s, containsString(EUID.TWO.toString()));
		assertThat(s, containsString("1234"));
	}
}
