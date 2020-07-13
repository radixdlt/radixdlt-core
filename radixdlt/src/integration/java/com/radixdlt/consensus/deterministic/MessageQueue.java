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

package com.radixdlt.consensus.deterministic;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.radixdlt.consensus.deterministic.ControlledNetwork.ChannelId;
import com.radixdlt.consensus.deterministic.ControlledNetwork.ControlledMessage;
import com.radixdlt.consensus.deterministic.ControlledNetwork.EpochAndView;

/**
 * Queue for messages by view.
 */
final class MessageQueue {
	// Should really be an AbstractSequentialList, but I don't really want to have
	// to implement a ListIterator.  Performance seems adequate for now.
	final class ArrayOfLinkedLists<T> extends AbstractList<T> {

		private final Object[] lists;
		private final int size;

		ArrayOfLinkedLists(Collection<LinkedList<T>> lists) {
			this.lists = lists.toArray();
			int count = 0;
			for (int i = 0; i < this.lists.length; ++i) {
				count += list(i).size();
			}
			this.size = count;
		}

		@SuppressWarnings("unchecked")
		private LinkedList<T> list(int i) {
			return (LinkedList<T>) lists[i];
		}

		@Override
		public T get(int index) {
			int currentIndex = index;
			for (int i = 0; i < this.lists.length; ++i) {
				LinkedList<T> list = list(i);
				int listSize = list.size();
				if (currentIndex < listSize) {
					return list.get(currentIndex);
				} else {
					currentIndex -= listSize;
				}
			}
			throw new ArrayIndexOutOfBoundsException(index);
		}

		@Override
		public int size() {
			return this.size;
		}
	}

	private final HashMap<EpochAndView, HashMap<ChannelId, LinkedList<ControlledMessage>>> messageQueue = Maps.newHashMap();
	private EpochAndView minimumEpochAndView = null; // Cached minimum view

	MessageQueue() {
		// Nothing here for now
	}

	void add(EpochAndView eav, ControlledMessage item) {
		this.messageQueue.computeIfAbsent(eav, k -> Maps.newHashMap()).computeIfAbsent(item.getChannelId(), k -> Lists.newLinkedList()).add(item);
		if (this.minimumEpochAndView == null || eav.compareTo(this.minimumEpochAndView) < 0) {
			this.minimumEpochAndView = eav;
		}
	}

	ControlledMessage pop(ChannelId channelId) {
		HashMap<ChannelId, LinkedList<ControlledMessage>> msgMap = this.messageQueue.get(this.minimumEpochAndView);
		if (msgMap == null) {
			return painfulPop(channelId);
		}
		LinkedList<ControlledMessage> msgs = msgMap.get(channelId);
		if (msgs == null) {
			return painfulPop(channelId);
		}
		ControlledMessage item = msgs.pop();
		if (msgs.isEmpty()) {
			msgMap.remove(channelId);
			if (msgMap.isEmpty()) {
				this.messageQueue.remove(this.minimumEpochAndView);
				this.minimumEpochAndView = minimumKey(this.messageQueue.keySet());
			}
		}
		return item;
	}

	// Really only here to work with processNextMsg(int, int, Class<?>) in BFTDeterministicTest
	private ControlledMessage painfulPop(ChannelId channelId) {
		List<Map.Entry<EpochAndView, HashMap<ChannelId, LinkedList<ControlledMessage>>>> entries = Lists.newArrayList(this.messageQueue.entrySet());
		Collections.sort(entries, Map.Entry.comparingByKey());
		for (Map.Entry<EpochAndView, HashMap<ChannelId, LinkedList<ControlledMessage>>> entry : entries) {
			HashMap<ChannelId, LinkedList<ControlledMessage>> msgMap = entry.getValue();
			LinkedList<ControlledMessage> msgs = msgMap.get(channelId);
			if (msgs != null) {
				ControlledMessage item = msgs.pop();
				if (msgs.isEmpty()) {
					msgMap.remove(channelId);
					if (msgMap.isEmpty()) {
						this.messageQueue.remove(entry.getKey());
						// Can't affect minimumView if we are here
					}
				}
				return item;
			}
		}
		throw new NoSuchElementException();
	}

	List<ControlledMessage> lowestViewMessages() {
		if (this.messageQueue.isEmpty()) {
			return Collections.emptyList();
		}
		return new ArrayOfLinkedLists<>(this.messageQueue.get(this.minimumEpochAndView).values());
	}

	@Override
	public String toString() {
		return this.messageQueue.toString();
	}

	// Believe it or not, this is faster, when coupled with minimumView
	// caching, than using a TreeMap for nodes == 100.
	private static EpochAndView minimumKey(Set<EpochAndView> eavs) {
		if (eavs.isEmpty()) {
			return null;
		}
		List<EpochAndView> views = Lists.newArrayList(eavs);
		Collections.sort(views);
		return views.get(0);
	}
}