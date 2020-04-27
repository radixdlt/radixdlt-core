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

package org.radix.network.discovery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import com.google.common.collect.Iterables;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerPredicate;
import com.radixdlt.network.addressbook.PeerWithTransport;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.tcp.TCPConstants;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BootstrapDiscoveryTest {
	// Mocks
	private RuntimeProperties config;
	private URL url;
	private URLConnection conn;
	private Universe universe;

	@Before
	public void setUp() throws IOException {
		// create stubs
		config = defaultProperties();
		universe = mock(Universe.class);
		url = mock(URL.class);
		conn = mock(URLConnection.class);

		// initialize stubs
		doReturn(8192).when(config).get("messaging.inbound.queue_max", 8192);

		when(config.get(eq("network.discovery.connection.retries"), anyInt())).thenReturn(1);
		when(config.get(eq("network.discovery.connection.cooldown"), anyInt())).thenReturn(1);
		when(config.get(eq("network.connections.in"), anyInt())).thenReturn(8);
		when(config.get(eq("network.connections.out"), anyInt())).thenReturn(8);

		when(config.get(eq("network.discovery.connection.timeout"), anyInt())).thenReturn(60000);
		when(config.get(eq("network.discovery.read.timeout"), anyInt())).thenReturn(60000);
		when(config.get(eq("network.discovery.allow_tls_bypass"), anyInt())).thenReturn(0);

		when(universe.getPort()).thenReturn(30000);

		when(url.openConnection()).thenReturn(conn);
	}

	@After
	public void tearDown() {
		// Make sure throwing interrupted exception doesn't affect other tests
		Thread.interrupted();
	}

	@Test
	public void testToHost_Empty() {
		assertEquals("", BootstrapDiscovery.toHost(new byte[0], 0));
	}

	@Test
	public void testToHost() {
		for (int b = Byte.MIN_VALUE; b <= Byte.MAX_VALUE; b++) {
			byte[] buf = new byte[] { (byte) b };
			if ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:-.".indexOf(0xff & b) != -1) {
				assertEquals(new String(buf, StandardCharsets.US_ASCII), BootstrapDiscovery.toHost(buf, buf.length));
			} else {
				assertNull(BootstrapDiscovery.toHost(buf, buf.length));
			}
		}
	}

	@Test
	public void testGetNextNode() throws IOException {
		String expected = "1.1.1.1";
		doReturn(expected.length()).when(conn).getContentLength();
		doReturn(new ByteArrayInputStream(expected.getBytes(StandardCharsets.US_ASCII))).when(conn).getInputStream();
		assertEquals(expected, new BootstrapDiscovery(config, universe).getNextNode(url));
	}

	@Test
	public void testGetNextNode_RuntimeException() throws IOException {
		doThrow(new RuntimeException()).when(conn).connect();
		assertNull(new BootstrapDiscovery(config, universe).getNextNode(url));
	}

	public void testGetNextNode_InterruptedException() throws InterruptedException {
		doThrow(new InterruptedException()).when(Thread.class);
		Thread.sleep(anyLong());
		assertNull(new BootstrapDiscovery(config, universe).getNextNode(url));
	}

	@Test
	public void testConstructor_Seeds() {
		doReturn("").when(config).get("network.discovery.urls", "");
		doReturn("1.1.1.1").when(config).get("network.seeds", "");
		BootstrapDiscovery testSubject = new BootstrapDiscovery(config, universe);
		Set<?> hosts = Whitebox.getInternalState(testSubject, "hosts");
		assertEquals(1, hosts.size());
	}

	@Test(expected = IllegalStateException.class)
	public void testConstructor_NeedHTTPS() {
		doReturn("http://example.com").when(config).get("network.discovery.urls", "");
		assertNotNull(new BootstrapDiscovery(config, universe));
	}

	@Test
	public void testDiscoverNoFilter() throws UnknownHostException {
		doReturn("").when(config).get("network.discovery.urls", "");
		doReturn("1.1.1.1").when(config).get("network.seeds", "");
		BootstrapDiscovery discovery = new BootstrapDiscovery(config, universe);

		TransportInfo ti = discovery.toDefaultTransportInfo("1.1.1.1");
		PeerWithTransport pwt = new PeerWithTransport(ti);
		AddressBook addressbook = mock(AddressBook.class);
		doReturn(pwt).when(addressbook).peer(ti);
		Collection<TransportInfo> results = discovery.discover(addressbook, null);
		assertEquals(ti, Iterables.getOnlyElement(results));
	}

	@Test
	public void testDiscoverFilter() throws UnknownHostException {
		doReturn("").when(config).get("network.discovery.urls", "");
		doReturn("1.1.1.1,2.2.2.2,3.3.3.3").when(config).get("network.seeds", "");
		BootstrapDiscovery discovery = new BootstrapDiscovery(config, universe);

		TransportInfo ti1 = discovery.toDefaultTransportInfo("1.1.1.1");
		PeerWithTransport pwt1 = new PeerWithTransport(ti1);
		TransportInfo ti2 = discovery.toDefaultTransportInfo("2.2.2.2");
		PeerWithTransport pwt2 = new PeerWithTransport(ti2);
		TransportInfo ti3 = discovery.toDefaultTransportInfo("3.3.3.3");
		AddressBook addressbook = mock(AddressBook.class);
		doReturn(pwt1).when(addressbook).peer(ti1);
		doReturn(pwt2).when(addressbook).peer(ti2);
		doReturn(null).when(addressbook).peer(ti3);
		PeerPredicate predicate = p -> p.connectionData("TCP").get(TCPConstants.METADATA_HOST).equals("1.1.1.1");
		Collection<TransportInfo> results = discovery.discover(addressbook, predicate);
		assertEquals(ti1, Iterables.getOnlyElement(results));
	}

	private static RuntimeProperties defaultProperties() {
		RuntimeProperties properties = mock(RuntimeProperties.class);
		doAnswer(invocation -> invocation.getArgument(1)).when(properties).get(any(), any());
		return properties;
	}
}
