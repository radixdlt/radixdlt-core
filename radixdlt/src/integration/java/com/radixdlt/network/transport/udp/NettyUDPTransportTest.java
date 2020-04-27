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

package com.radixdlt.network.transport.udp;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.radixdlt.network.hostip.HostIp;
import com.radixdlt.network.messaging.InboundMessage;
import com.radixdlt.network.transport.SendResult;
import com.radixdlt.network.transport.TransportControl;
import com.radixdlt.network.transport.TransportOutboundConnection;

import static org.junit.Assert.*;

public class NettyUDPTransportTest {

	private NettyUDPTransportImpl transport1;
	private NettyUDPTransportImpl transport2;

	private AtomicLong packetCounter;
	private AtomicLong byteCounter;

	@Before
	public void setup() {
		transport1 = createTransport("127.0.0.1", 12345);
		transport2 = createTransport("127.0.0.1", 23456);

		packetCounter = new AtomicLong(0L);
		byteCounter = new AtomicLong(0L);
	}

	@After
	public void teardown() throws IOException, InterruptedException {
		if (transport2 != null) {
			transport2.close();
		}
		if (transport1 != null) {
			transport1.close();
		}
		Thread.sleep(500);
	}

	@Test
	public void testThroughputSmallPacket() throws InterruptedException, ExecutionException, IOException {
		// Approximate size of AtomBroadcastMessage
		testThroughput("Small", 112, 200, 30);
	}

	@Test
	public void testThroughputMediumPacket() throws InterruptedException, ExecutionException, IOException {
		// Approximate size of a basic test universe
		testThroughput("Medium", 3600, 100, 30);
	}

	@Test
	public void testThroughputLargePacket() throws InterruptedException, ExecutionException, IOException {
		// Largest packet supported
		testThroughput("Large", UDPConstants.MAX_PACKET_LENGTH - 9, 4, 30);
	}

	// Note that windowSize is to help us not wharrgarbl the O/S too much, as this just results in packets being dropped
	private void testThroughput(String name, int testPacketSize, int windowSize, int testSeconds)
		throws InterruptedException, ExecutionException, IOException {
		transport1.start(this::unexpectedMessage);
		transport2.start(this::handleMessage);

		final long start;
		try (TransportControl control1 = transport1.control();
			 TransportOutboundConnection obc1 = control1.open(transport2.localMetadata()).get()) {

			byte[] packet = new byte[testPacketSize];
			start = System.nanoTime();
			long finish = testSeconds * 1_000_000_000L;
			LinkedList<Future<SendResult>> queue = new LinkedList<>();
			for (;;) {
				queue.add(obc1.send(packet));
				if (System.nanoTime() - start >= finish) {
					break;
				}
				while (queue.size() > windowSize) {
					SendResult sr = waitFor(queue.pop());
					if (!sr.isComplete()) {
						sr.getThrowable().printStackTrace(System.err);
						assertTrue(sr.isComplete()); // fail
					}
				}
			}
		}

		long end = System.nanoTime();
		long receivedBytes = byteCounter.get();
		long receivedPackets = packetCounter.get();
		double duration = (end - start) / 1e9;
		System.out.format("%s Summary:   %.3f kpkt, %.3f Gib, %.3f ms%n",
			name, receivedPackets / 1000.0, receivedBytes / Math.pow(2.0, 30.0), duration * 1000.0);
		System.out.format("%s Bandwidth: %.3f Mib/second%n", name, receivedBytes / duration / 1024.0 / 1024.0);
		System.out.format("%s Packets:   %.3f kpkt/second%n", name, receivedPackets / 1000.0 / duration);
		System.out.println();
	}

	// Separated out here for profiling reasons.  future.get() tends to spin a bit
	// and therefore consume CPU that is not performing any work.  Moving that
	// consumption here in a profiling trace makes it easier to reason about the
	// amount of CPU being consumed actually doing the work.
	private SendResult waitFor(Future<SendResult> future) throws InterruptedException, ExecutionException {
		return future.get();
	}

	private void handleMessage(InboundMessage message) {
		packetCounter.incrementAndGet();
		byteCounter.addAndGet(message.message().length);
	}

	private void unexpectedMessage(InboundMessage message) {
		throw new IllegalStateException("Unexpected message");
	}

	private NettyUDPTransportImpl createTransport(String host, int port) {
		UDPConfiguration config = new UDPConfiguration() {
			@Override
			public int networkPort(int defaultValue) {
				return port;
			}

			@Override
			public String networkAddress(String defaultValue) {
				return host;
			}

			@Override
			public int priority(int defaultValue) {
				return 0;
			}
		};
		HostIp hostip = () -> Optional.of("127.0.0.1");
		Module hostIpModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(HostIp.class).toInstance(hostip);
			}
		};
		Injector injector = Guice.createInjector(hostIpModule, new UDPTransportModule(config));
		return injector.getInstance(NettyUDPTransportImpl.class);
	}
}
