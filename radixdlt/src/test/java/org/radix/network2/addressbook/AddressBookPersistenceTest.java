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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.radix.database.DatabaseEnvironment;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportInfo;
import org.radix.serialization.RadixTest;
import org.radix.time.Time;
import org.radix.time.Timestamps;

import com.radixdlt.identifiers.EUID;

import static org.junit.Assert.*;

public class AddressBookPersistenceTest extends RadixTest {

	private AddressBookPersistence abp;
	private DatabaseEnvironment dbEnv;

	@Before
	public void setUp() {
		this.dbEnv = new DatabaseEnvironment(getProperties());
		this.abp = new AddressBookPersistence(getSerialization(), dbEnv);
		this.abp.reset();
	}

	@After
	public void tearDown() {
		this.abp.close();
		this.dbEnv.stop();
	}

	@Test
	public void testStart() {
		// No exceptions, and should have a database when done
		this.abp.start();
		assertNotNull(Whitebox.getInternalState(this.abp, "peersByNidDB"));
	}

	@Test
	public void testStop() {
		// No exceptions, and should have no database when done
		this.abp.start();
		assertNotNull(Whitebox.getInternalState(this.abp, "peersByNidDB"));
		this.abp.close();
		assertNull(Whitebox.getInternalState(this.abp, "peersByNidDB"));
	}

	@Test
	public void testReset() {
		this.abp.start();
		assertTrue(this.abp.savePeer(new PeerWithNid(EUID.ONE)));
		AtomicInteger peercount1 = new AtomicInteger(0);
		this.abp.forEachPersistedPeer(p -> peercount1.incrementAndGet());
		assertEquals(1, peercount1.get());

		this.abp.close();
		this.abp.reset();

		this.abp.start();
		AtomicInteger peercount2 = new AtomicInteger(0);
		this.abp.forEachPersistedPeer(p -> peercount2.incrementAndGet());
		assertEquals(0, peercount2.get());
	}

	@Test
	public void testSavePeer() {
		this.abp.start();

		PeerWithNid pwn = new PeerWithNid(EUID.ONE);
		assertTrue(this.abp.savePeer(pwn));
		assertEquals(1, peerCount());
		assertEquals(0L, onlyPeer().getTimestamp(Timestamps.ACTIVE));

		// Update timestamp
		long now = Time.currentTimestamp();
		pwn.setTimestamp(Timestamps.ACTIVE, now);
		assertTrue(this.abp.savePeer(pwn));
		assertEquals(1, peerCount());
		assertEquals(now, onlyPeer().getTimestamp(Timestamps.ACTIVE));

		// Add new peer
		PeerWithNid pwn2 = new PeerWithNid(EUID.TWO);
		assertTrue(this.abp.savePeer(pwn2));
		assertEquals(2, peerCount());

		// Can't save peer without a nid
		TransportInfo ti = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());
		PeerWithTransport pwt = new PeerWithTransport(ti);
		assertFalse(this.abp.savePeer(pwt));
	}

	@Test
	public void testDeletePeer() {
		this.abp.start();

		PeerWithNid pwn1 = new PeerWithNid(EUID.ONE);
		assertTrue(this.abp.savePeer(pwn1));
		assertEquals(1, peerCount());
		PeerWithNid pwn2 = new PeerWithNid(EUID.TWO);
		assertTrue(this.abp.savePeer(pwn2));
		assertEquals(2, peerCount());

		// Delete one, and check that the only one left is the right one
		assertTrue(this.abp.deletePeer(EUID.ONE));
		assertEquals(EUID.TWO, onlyPeer().getNID());

		// Add back the deleted one, and delete the other
		assertTrue(this.abp.savePeer(pwn1));
		assertEquals(2, peerCount());
		assertTrue(this.abp.deletePeer(EUID.TWO));
		assertEquals(EUID.ONE, onlyPeer().getNID());

		// Try to delete something that doesn't exist
		assertFalse(this.abp.deletePeer(EUID.TWO));
	}

	private int peerCount() {
		AtomicInteger peercount = new AtomicInteger(0);
		this.abp.forEachPersistedPeer(p -> peercount.incrementAndGet());
		return peercount.get();
	}

	private Peer onlyPeer() {
		AtomicReference<Peer> peer = new AtomicReference<>();
		this.abp.forEachPersistedPeer(p -> assertTrue(peer.compareAndSet(null, p)));
		assertNotNull(peer.get());
		return peer.get();
	}
}
