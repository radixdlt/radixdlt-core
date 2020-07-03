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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * System counters interface.
 */
public interface SystemCounters {

	enum CounterType {
		CONSENSUS_INDIRECT_PARENT("consensus.indirect_parent"),
		CONSENSUS_REJECTED("consensus.rejected"),
		CONSENSUS_SYNC_SUCCESS("consensus.sync_success"),
		CONSENSUS_SYNC_EXCEPTION("consensus.sync_exception"),
		CONSENSUS_EVENTS_QUEUED_INITIAL("consensus.events_queued_initial"),
		CONSENSUS_EVENTS_QUEUED_SYNC("consensus.events_queued_sync"),
		CONSENSUS_TIMEOUT("consensus.timeout"),
		CONSENSUS_TIMEOUT_VIEW("consensus.timeout_view"),
		CONSENSUS_VERTEXSTORE_SIZE("consensus.vertexstore_size"),
		CONSENSUS_VIEW("consensus.view"),
		CONSENSUS_PROCESSED("consensus.processed"),
		// TODO: Move some CONSENSUS epoch related counters into epoch
		EPOCHS_EPOCH("epochs.epoch"),
		LEDGER_STATE_VERSION("ledger.state_version"),
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
		MESSAGES_OUTBOUND_SENT("messages.outbound.sent");

		private final String jsonPath;

		private final AtomicLong counter = new AtomicLong(0);

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
	 * Returns the current values as a map.
	 * @return the current values as a map
	 */
	Map<String, Object> toMap();

	/**
	 * Resets <b>all</b> the counters to <b>zero</b>.
	 */
	void reset();


	/**
	 * Provides the system wide set of counters which should be used by the radyx services.
	 * @return a <b>thread safe</b> SystemCounters implementation which is backed by system wide counters.
	 */
	static SystemCounters getInstance() {
		return new SystemCountersImpl(counterType -> counterType.counter, System.currentTimeMillis());
	}

	/**
	 * Creates a brand new set of counters backed by the given supplier.
	 * <b>This method should be used only for simulation or testing.</b>
	 * @param supplier of counters
	 * @return  a <b>thread safe</b> SystemCounters implementation which are <b>different</b> from
	 * the system-wide once provided by the getInstance method.
	 */
	static SystemCounters newInstance(Function<CounterType, AtomicLong> supplier) {
		return new SystemCountersImpl(supplier, System.currentTimeMillis());
	}

}