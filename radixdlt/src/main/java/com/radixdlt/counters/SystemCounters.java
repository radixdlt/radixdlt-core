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

package com.radixdlt.counters;

import java.util.Map;

/**
 * System counters interface.
 */
public interface SystemCounters {

	enum CounterType {
		// Please keep these sorted

		BFT_INDIRECT_PARENT("bft.indirect_parent"),
		BFT_PROCESSED("bft.processed"),
		BFT_PROPOSALS_MADE("bft.proposals_made"),
		BFT_REJECTED("bft.rejected"),
		BFT_TIMEOUT("bft.timeout"),
		BFT_VERTEX_STORE_SIZE("bft.vertex_store_size"),
		BFT_VOTE_RTT_COUNT("bft.vote_rtt.count"),
		BFT_VOTE_RTT_MAX("bft.vote_rtt.max"),
		BFT_VOTE_RTT_MEAN("bft.vote_rtt.mean"),
		BFT_VOTE_RTT_MIN("bft.vote_rtt.min"),
		BFT_VOTE_RTT_SIGMA("bft.vote_rtt.sigma"),

		EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS("epoch_manager.queued_consensus_events"),

		LEDGER_PROCESSED("ledger.processed"), // LEDGER_PROCESSED = BFT_PROCESSED + LEDGER_SYNC_PROCESSED + 1 (genesis)
		LEDGER_STATE_VERSION("ledger.state_version"),
		LEDGER_SYNC_PROCESSED("ledger.sync_processed"),

		MEMPOOL_COUNT("mempool.count"),
		MEMPOOL_MAXCOUNT("mempool.maxcount"),

		MESSAGES_INBOUND_BADSIGNATURE("messages.inbound.badsignature"),
		MESSAGES_INBOUND_DISCARDED("messages.inbound.discarded"),
		MESSAGES_INBOUND_PENDING("messages.inbound.pending"),
		MESSAGES_INBOUND_PROCESSED("messages.inbound.processed"),
		MESSAGES_INBOUND_RECEIVED("messages.inbound.received"),
		MESSAGES_OUTBOUND_ABORTED("messages.outbound.aborted"),
		MESSAGES_OUTBOUND_PENDING("messages.outbound.pending"),
		MESSAGES_OUTBOUND_PROCESSED("messages.outbound.processed"),
		MESSAGES_OUTBOUND_SENT("messages.outbound.sent"),

		NETWORKING_TCP_OPENED("networking.tcp.opened"),
		NETWORKING_TCP_CLOSED("networking.tcp.closed"),
		NETWORKING_SENT_BYTES("networking.sent_bytes"),
		NETWORKING_RECEIVED_BYTES("networking.received_bytes"),

		SIGNATURES_SIGNED("signatures.signed"),
		SIGNATURES_VERIFIED("signatures.verified");

		private final String jsonPath;

		CounterType(String jsonPath) {
			this.jsonPath = jsonPath;
		}

		public String jsonPath() {
			return this.jsonPath;
		}
	}

	/**
	 * Increments the specified counter, returning the new value.
	 *
	 * @param counterType The counter to increment
	 * @return The new incremented value
	 */
	long increment(CounterType counterType);

	/**
	 * Increments the specified counter by the specified amount,
	 * returning the new value.
	 *
	 * @param counterType The counter to increment
	 * @return The new incremented value
	 */
	long add(CounterType counterType, long amount);

	/**
	 * Sets the specified counter to the specified value,
	 * returning the previous value.
	 *
	 * @param counterType The counter to increment
	 * @return The previous value
	 */
	long set(CounterType counterType, long value);

	/**
	 * Returns the current value of the specified counter.
	 *
	 * @param counterType The counter value to return
	 * @return The current value of the counter
	 */
	long get(CounterType counterType);

	/**
	 * Set a group of values.  Values are updates in such
	 * a way as to prevent read-tearing when {@link #toMap()}
	 * is called.
	 *
	 * @param newValues The values to update.
	 */
	void setAll(Map<CounterType, Long> newValues);

	/**
	 * Returns the current values as a map.
	 * @return the current values as a map
	 */
	Map<String, Object> toMap();
}