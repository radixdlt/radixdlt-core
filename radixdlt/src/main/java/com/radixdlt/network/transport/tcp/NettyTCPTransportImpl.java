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

package com.radixdlt.network.transport.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.network.messaging.InboundMessageConsumer;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportControl;
import com.radixdlt.network.transport.TransportMetadata;
import com.radixdlt.network.transport.netty.LogSink;
import com.radixdlt.network.transport.netty.LoggingHandler;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

final class NettyTCPTransportImpl implements NettyTCPTransport {
	private static final Logger log = LogManager.getLogger("transport.tcp");

	// Set this to true to see a detailed hexdump of sent/received data at runtime
	private final boolean debugData;

	// Default values if none specified in either localMetadata or config
	@VisibleForTesting
	static final String DEFAULT_HOST = "0.0.0.0";
	@VisibleForTesting
	static final int    DEFAULT_PORT = 30000;

	private static final int RCV_BUF_SIZE = TCPConstants.MAX_PACKET_LENGTH * 2;
	private static final int SND_BUF_SIZE = TCPConstants.MAX_PACKET_LENGTH * 2;
	private static final int BACKLOG_SIZE = 100;

	private final TransportMetadata localMetadata;

	private final int priority;
	private final AtomicInteger threadCounter = new AtomicInteger(0);
	private final InetSocketAddress bindAddress;
	private final Object channelLock = new Object();

	private final TCPTransportControl control;

	private Channel channel;
	private Bootstrap outboundBootstrap;


	@Inject
	NettyTCPTransportImpl(
		TCPConfiguration config,
		@Named("local") TransportMetadata localMetadata,
		TCPTransportOutboundConnectionFactory outboundFactory,
		TCPTransportControlFactory controlFactory
	) {
		String providedHost = localMetadata.get(TCPConstants.METADATA_HOST);
		if (providedHost == null) {
			providedHost = config.networkAddress(DEFAULT_HOST);
		}
		String portString = localMetadata.get(TCPConstants.METADATA_PORT);
		final int port;
		if (portString == null) {
			port = config.networkPort(DEFAULT_PORT);
		} else {
			port = Integer.parseInt(portString);
		}
		this.localMetadata = StaticTransportMetadata.of(
			TCPConstants.METADATA_HOST, providedHost,
			TCPConstants.METADATA_PORT, String.valueOf(port)
		);
		this.priority = config.priority(0);
		this.debugData = config.debugData(false);
		this.control = controlFactory.create(config, outboundFactory, this);
		this.bindAddress = new InetSocketAddress(providedHost, port);
	}

	@Override
	public String name() {
		return TCPConstants.NAME;
	}

	@Override
	public TransportControl control() {
		return control;
	}

	@Override
	public TransportMetadata localMetadata() {
		return localMetadata;
	}

	@Override
	public boolean canHandle(byte[] message) {
		return (message == null) || (message.length <= TCPConstants.MAX_PACKET_LENGTH);
	}

	@Override
	public int priority() {
		return this.priority;
	}

	@Override
	public void start(InboundMessageConsumer messageSink) {
		if (log.isInfoEnabled()) {
			log.info("TCP transport {}", localAddress());
		}

		EventLoopGroup serverGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup(1, this::createThread);

		this.outboundBootstrap = new Bootstrap();
		this.outboundBootstrap.group(workerGroup)
			.channel(NioSocketChannel.class)
			.option(ChannelOption.SO_KEEPALIVE, true)
			.handler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					setupChannel(ch, messageSink, true);
				}
			});

		ServerBootstrap b = new ServerBootstrap();
		b.group(serverGroup, workerGroup)
			.channel(NioServerSocketChannel.class)
			.option(ChannelOption.SO_BACKLOG, BACKLOG_SIZE)
			.childOption(ChannelOption.SO_KEEPALIVE, true)
			.childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					log.info("Connection from {}:{}", ch.remoteAddress().getHostString(), ch.remoteAddress().getPort());
					setupChannel(ch, messageSink, false);
				}
			});
		if (log.isDebugEnabled() || log.isTraceEnabled()) {
			b.handler(new LoggingHandler(LogSink.using(log), this.debugData));
		}
		try {
			synchronized (channelLock) {
				close();
				this.channel = b.bind(this.bindAddress).sync().channel();
			}
		} catch (InterruptedException e) {
			// Abort!
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			throw new UncheckedIOException("Error while opening channel", e);
		}
	}

	private void setupChannel(SocketChannel ch, InboundMessageConsumer messageSink, boolean isOutbound) {
		final int packetLength = TCPConstants.MAX_PACKET_LENGTH + TCPConstants.LENGTH_HEADER;
		final int headerLength = TCPConstants.LENGTH_HEADER;
		ch.config()
			.setReceiveBufferSize(RCV_BUF_SIZE)
			.setSendBufferSize(SND_BUF_SIZE)
			.setOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(packetLength));
		if (log.isDebugEnabled()) {
			ch.pipeline().addLast(new LoggingHandler(LogSink.using(log), debugData));
		}
		if (isOutbound) {
			ch.pipeline()
				.addLast("connections", control.handler());
		}
		ch.pipeline()
			.addLast("unpack", new LengthFieldBasedFrameDecoder(packetLength, 0, headerLength, 0, headerLength))
			.addLast("onboard", new TCPNettyMessageHandler(messageSink));
		ch.pipeline()
			.addLast("pack", new LengthFieldPrepender(headerLength));
	}

	@Override
	public ChannelFuture createChannel(String host, int port) {
		log.info("Establishing connection to {}:{}", host, port);
		return this.outboundBootstrap.connect(host, port)
			.addListener(v -> log.info("Established connection to {}:{}", host, port));
	}

	@Override
	public void close() throws IOException {
		synchronized (this.channelLock) {
			closeSafely(this.control);
			if (this.channel != null) {
				closeSafely(this.channel::close);
			}
			this.channel = null;
		}
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), localAddress());
	}

	private String localAddress() {
		return String.format("%s:%s", bindAddress.getAddress().getHostAddress(), bindAddress.getPort());
	}

	private Thread createThread(Runnable r) {
		String threadName = String.format("TCP handler %s - %s", localAddress(), threadCounter.incrementAndGet());
		if (log.isDebugEnabled()) {
			log.debug("New thread: {}", threadName);
		}
		return new Thread(r, threadName);
	}

	private void closeSafely(Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (IOException | UncheckedIOException e) {
				log.warn(String.format("Error while closing %s", c), e);
			}
		}
	}
}
