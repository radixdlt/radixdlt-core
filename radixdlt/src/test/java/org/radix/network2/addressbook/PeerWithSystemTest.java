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

package org.radix.network2.addressbook;

import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportException;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.udp.UDPConstants;
import org.radix.universe.system.RadixSystem;

import com.google.common.collect.ImmutableList;
import com.radixdlt.identifiers.EUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;

public class PeerWithSystemTest {

	private EUID nid;
	private TransportInfo dummy;
	private RadixSystem system;
	private PeerWithSystem pws;

	@Before
	public void setUp() throws Exception {
		this.nid = EUID.ONE;
		this.dummy = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());
		this.system = mock(RadixSystem.class);
		when(this.system.supportedTransports()).thenAnswer(invocation -> Stream.of(this.dummy));
		when(this.system.getNID()).thenReturn(this.nid);
		this.pws = new PeerWithSystem(this.system);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testToString() {
		String s = this.pws.toString();

		assertThat(s, containsString("PeerWithSystem")); // class name
		assertThat(s, containsString(this.nid.toString())); // nid
		assertThat(s, containsString("No UDP data"));

		EUID nid = EUID.TWO;
		TransportInfo fakeUdp = TransportInfo.of(UDPConstants.UDP_NAME,
			StaticTransportMetadata.of(
				UDPConstants.METADATA_UDP_HOST, "127.0.0.1",
				UDPConstants.METADATA_UDP_PORT, "10000"
			)
		);
		RadixSystem system = mock(RadixSystem.class);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(fakeUdp));
		when(system.getNID()).thenReturn(nid);
		PeerWithSystem pws = new PeerWithSystem(system);
		String s2 = pws.toString();
		assertThat(s2, containsString("PeerWithSystem")); // class name
		assertThat(s2, containsString(nid.toString())); // nid
		assertThat(s2, containsString(fakeUdp.metadata().get(UDPConstants.METADATA_UDP_HOST)));
		assertThat(s2, containsString(fakeUdp.metadata().get(UDPConstants.METADATA_UDP_PORT)));
	}

	@Test
	public void testGetNID() {
		assertThat(this.pws.getNID(), is(this.nid));
	}

	@Test
	public void testHasNID() {
		assertThat(this.pws.hasNID(), is(true));
	}

	@Test
	public void testSupportsTransport() {
		assertThat(this.pws.supportsTransport("NONESUCH"), is(false));
		assertThat(this.pws.supportsTransport(this.dummy.name()), is(true));
	}

	@Test
	public void testSupportedTransports() {
		ImmutableList<TransportInfo> tis = this.pws.supportedTransports().collect(ImmutableList.toImmutableList());
		assertThat(tis, contains(this.dummy));
	}

	@Test
	public void testConnectionData() {
		assertThat(this.pws.connectionData(this.dummy.name()), is(this.dummy.metadata()));
	}

	@Test(expected = TransportException.class)
	public void testConnectionDataThrows() {
		this.pws.connectionData("ANY");
		fail();
	}

	@Test
	public void testHasSystem() {
		assertThat(this.pws.hasSystem(), is(true));
	}

	@Test
	public void testGetSystem() {
		assertThat(this.pws.getSystem(), sameInstance(this.system));
	}
}
