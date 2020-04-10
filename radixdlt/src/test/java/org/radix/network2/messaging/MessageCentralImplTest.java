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

package org.radix.network2.messaging;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import org.junit.Before;
import org.junit.Test;
import org.radix.events.Events;
import org.radix.network.Interfaces;
import org.radix.network.messages.TestMessage;
import org.radix.network.messaging.Message;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.messaging.MessagingDummyConfigurations.DummyTransport;
import org.radix.network2.messaging.MessagingDummyConfigurations.DummyTransportOutboundConnection;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportInfo;
import org.radix.time.Timestamps;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.events.QueueFullEvent;
import org.xerial.snappy.Snappy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MessageCentralImplTest {

	static class TestBlockingQueue extends PriorityBlockingQueue<MessageEvent> {
		private final AtomicLong offered = new AtomicLong(0);
		private final AtomicBoolean full = new AtomicBoolean(false);

		TestBlockingQueue() {
			super();
		}

		@Override
		public boolean offer(MessageEvent e) {
			this.offered.incrementAndGet();
			if (this.full.get()) {
				return false;
			} else {
				return super.offer(e);
			}
		}

		long offered() {
			return this.offered.get();
		}

		boolean setFull(boolean b) {
			return this.full.compareAndSet(!b, b);
		}
	}

	private Serialization serialization;
	private DummyTransportOutboundConnection toc;
	private DummyTransport dt;
	private MessageCentralImpl mci;
	private TestBlockingQueue inboundQueuex;
	private TestBlockingQueue outboundQueuex;
	private Events events;

	@Before
	public void testSetup() {
		this.serialization = DefaultSerialization.getInstance();
		MessageCentralConfiguration conf = new MessagingDummyConfigurations.DummyMessageCentralConfiguration();

		// Curse you singletons
		Universe universe = mock(Universe.class);
		doReturn(0).when(universe).getMagic();
		RuntimeProperties runtimeProperties = mock(RuntimeProperties.class);
		doReturn("").when(runtimeProperties).get(eq("network.whitelist"), any());
		AddressBook addressBook = mock(AddressBook.class);
		Peer peer = mock(Peer.class);
		doReturn(peer).when(addressBook).peer(any(TransportInfo.class));

		// Other scaffolding
		this.toc = new DummyTransportOutboundConnection();
		this.dt = new DummyTransport(this.toc);

		// Safe, as this is a dummy transport with no underlying resources
		@SuppressWarnings("resource")
		MessagingDummyConfigurations.DummyTransportManager transportManager = new MessagingDummyConfigurations.DummyTransportManager(this.dt);

		this.events = mock(Events.class);
		inboundQueuex = new TestBlockingQueue();
		outboundQueuex = new TestBlockingQueue();
		EventQueueFactory<MessageEvent> queueFactory = eventQueueFactoryMock();
		doReturn(inboundQueuex).when(queueFactory).createEventQueue(conf.messagingInboundQueueMax(0));
		doReturn(outboundQueuex).when(queueFactory).createEventQueue(conf.messagingOutboundQueueMax(0));
		Interfaces interfaces = mock(Interfaces.class);
		doReturn(false).when(interfaces).isSelf(any());
		LocalSystem localSystem = mock(LocalSystem.class);
		this.mci = new MessageCentralImpl(new MessagingDummyConfigurations.DummyMessageCentralConfiguration(), serialization, transportManager, events, addressBook, System::currentTimeMillis,
				queueFactory, interfaces, localSystem);
	}

	@Test
	public void testConstruct() {
		// Make sure start called on our transport
		assertNotNull(dt.getMessageSink());
	}

	@Test
	public void testClose() {
		mci.close();
		assertTrue(dt.isClosed());
	}

	@Test
	public void testSend() throws InterruptedException {
		Message msg = new TestMessage(1);
		Peer peer = mock(Peer.class);
		mci.send(peer, msg);
		assertTrue(toc.getCountDownLatch().await(10, TimeUnit.SECONDS));
		assertTrue(toc.isSent());
	}

	@Test
	public void testSendMessageDeliveredToTransport() throws InterruptedException {
		Message msg = new TestMessage(1);
		Peer peer = mock(Peer.class);

		int numberOfRequests = 6;
		CountDownLatch receivedFlag = new CountDownLatch(numberOfRequests);
		toc.setCountDownLatch(receivedFlag);
		for (int i = 0; i < numberOfRequests; i++) {
			mci.send(peer, msg);
		}

		assertTrue(receivedFlag.await(10, TimeUnit.SECONDS));
		assertEquals(numberOfRequests, toc.getMessages().size());
	}

	@Test
	public void testInjectMessageDeliveredToListeners() throws InterruptedException {
		Message msg = new TestMessage(1);
		msg.setTimestamp(Timestamps.DEFAULT, System.currentTimeMillis());
		Peer peer = mock(Peer.class);

		int numberOfRequests = 6;
		CountDownLatch receivedFlag = new CountDownLatch(numberOfRequests);
		List<Message> messages = new ArrayList<>();
		mci.addListener(TestMessage.class, (source, message) -> {
			messages.add(message);
			receivedFlag.countDown();
		});

		for (int i = 0; i < numberOfRequests; i++) {
			mci.inject(peer, msg);
		}
		assertTrue(receivedFlag.await(10, TimeUnit.SECONDS));
		assertEquals(numberOfRequests, messages.size());
		assertEquals(numberOfRequests, inboundQueuex.offered());
	}

	@Test
	public void testInjectQueueIsFull() {
		testQueueIsFull(inboundQueuex, (peer, message) -> mci.inject(peer, message));
	}

	@Test
	public void testSendQueueIsFull() {
		testQueueIsFull(outboundQueuex, (peer, message) -> mci.send(peer, message));
	}

	private void testQueueIsFull(TestBlockingQueue queue, BiConsumer<Peer, Message> biConsumer) {
		queue.setFull(true);
		Message msg = new TestMessage(1);
		Peer peer = mock(Peer.class);

		int numberOfRequests = 6;
		for (int i = 0; i < numberOfRequests; i++) {
			biConsumer.accept(peer, msg);
		}
		verify(events, times(numberOfRequests)).broadcast(any(QueueFullEvent.class));
	}

	@Test
	public void testInbound() throws IOException, InterruptedException {
		Message msg = new TestMessage(1);
		byte[] data = Snappy.compress(serialization.toDson(msg, Output.WIRE));

		AtomicReference<Message> receivedMessage = new AtomicReference<>();
		Semaphore receivedFlag = new Semaphore(0);

		mci.addListener(msg.getClass(), (peer, messsage) -> {
			receivedMessage.set(messsage);
			receivedFlag.release();
		});

		TransportInfo source = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		InboundMessage message = InboundMessage.of(source, data);
		dt.inboundMessage(message);

		assertTrue(receivedFlag.tryAcquire(10, TimeUnit.SECONDS));
		assertNotNull(receivedMessage.get());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAddNullListener() {
		mci.addListener(TestMessage.class, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAddListenerTwice() {
		MessageListener<TestMessage> listener = (source, message) -> {};
		mci.addListener(TestMessage.class, listener);
		mci.addListener(TestMessage.class, listener);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRemoveNullListener() {
		mci.removeListener(TestMessage.class, null);
	}

	@Test
	public void testAddRemoveListener() {
		MessageListener<TestMessage> listener1 = (source, message) -> {};
		MessageListener<TestMessage> listener2 = (source, message) -> {};

		mci.addListener(TestMessage.class, listener1);
		assertEquals(1, mci.listenersSize());

		mci.addListener(TestMessage.class, listener2);
		assertEquals(2, mci.listenersSize());

		mci.removeListener(TestMessage.class, listener1);
		assertEquals(1, mci.listenersSize());

		mci.removeListener(TestMessage.class, listener1);
		assertEquals(1, mci.listenersSize());

		mci.removeListener(TestMessage.class, listener2);
		assertEquals(0, mci.listenersSize());
	}

	@Test
	public void testRemoveUnspecifiedListener() {
		MessageListener<TestMessage> listener1 = (source, message) -> {};
		MessageListener<TestMessage> listener2 = (source, message) -> {};

		mci.addListener(TestMessage.class, listener1);
		assertEquals(1, mci.listenersSize());

		mci.addListener(TestMessage.class, listener2);
		assertEquals(2, mci.listenersSize());

		mci.removeListener(listener1);
		assertEquals(1, mci.listenersSize());

		mci.removeListener(listener1);
		assertEquals(1, mci.listenersSize());

		mci.removeListener(listener2);
		assertEquals(0, mci.listenersSize());
	}

	@SuppressWarnings("unchecked")
	private <T> EventQueueFactory<T> eventQueueFactoryMock() {
		return mock(EventQueueFactory.class);
	}
}
