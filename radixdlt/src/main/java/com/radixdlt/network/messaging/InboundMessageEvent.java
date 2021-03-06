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

package com.radixdlt.network.messaging;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messages.PeerPongMessage;
import org.radix.network.messaging.Message;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.network.transport.TransportInfo;

/**
 * Inbound message wrapper with priority, time and destination.
 * <p>
 * Note that priority is calculated from a fixed table of priorities for
 * specific message types, and cannot be specified by the user.
 * <p>
 * Time is number of nanoseconds since some arbitrary baseline.
 */
public final class InboundMessageEvent {

	private static final int DEFAULT_PRIORITY = 0;
	// Lower (inc -ve) numbers are higher priority than larger numbers
	private static final Map<Class<?>, Integer> MESSAGE_PRIORITIES = ImmutableMap.of(
		PeerPingMessage.class, Integer.MIN_VALUE,
		PeerPongMessage.class, Integer.MIN_VALUE
	);

	public static Comparator<InboundMessageEvent> comparator() {
		return Comparator.comparingInt(InboundMessageEvent::priority).thenComparingLong(InboundMessageEvent::nanoTimeDiff);
	}

	private final int priority;
	private final long nanoTimeDiff;
	private final TransportInfo source;
	private final Message message;

	InboundMessageEvent(TransportInfo source, Message message, long nanoTimeDiff) {
		this.priority = MESSAGE_PRIORITIES.getOrDefault(message.getClass(), DEFAULT_PRIORITY);
		this.nanoTimeDiff = nanoTimeDiff;
		this.source = source;
		this.message = message;
	}

	/**
	 * Returns the messages priority.
	 *
	 * @return the messages priority.
	 */
	public int priority() {
		return priority;
	}

	/**
	 * Returns the time this event was created as a number of nanoseconds
	 * since some arbitrary baseline.
	 *
	 * @return the time this event was created
	 */
	public long nanoTimeDiff() {
		return nanoTimeDiff;
	}

	/**
	 * Returns the source of the message.
	 *
	 * @return the source or destination of the message.
	 */
	public TransportInfo source() {
		return source;
	}

	/**
	 * Returns the message.
	 *
	 * @return the message.
	 */
	public Message message() {
		return message;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.priority, this.nanoTimeDiff, this.source, this.message);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof InboundMessageEvent) {
			InboundMessageEvent that = (InboundMessageEvent) obj;
			return this.priority == that.priority
				&& this.nanoTimeDiff == that.nanoTimeDiff
				&& Objects.equals(this.source, that.source)
				&& Objects.equals(this.message, that.message);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[priority=%s, nanoTime=%s, peer=%s, message=%s]",
			getClass().getSimpleName(), priority, nanoTimeDiff, source, message);
	}
}