/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware2.network;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;

import com.radixdlt.environment.RemoteEventDispatcher;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Objects;

import java.util.Optional;
import java.util.Set;

import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;

import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.BFTEventsRx;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageListener;
import com.radixdlt.universe.Universe;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * BFT Network sending and receiving layer used on top of the MessageCentral
 * layer.
 */
public final class MessageCentralBFTNetwork implements ProposalBroadcaster, BFTEventsRx {
	private static final Logger log = LogManager.getLogger();

	private final BFTNode self;
	private final int magic;
	private final AddressBook addressBook;
	private final MessageCentral messageCentral;
	private final PublishSubject<ConsensusEvent> localMessages;

	@Inject
	public MessageCentralBFTNetwork(
		@Self BFTNode self,
		Universe universe,
		AddressBook addressBook,
		MessageCentral messageCentral
	) {
		this.magic = universe.getMagic();
		this.self = Objects.requireNonNull(self);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.localMessages = PublishSubject.create();
	}

	@Override
	public Observable<ConsensusEvent> bftEvents() {
		return Observable.<ConsensusEvent>create(emitter -> {
			MessageListener<ConsensusEventMessage> listener = (src, msg) -> emitter.onNext(msg.getConsensusMessage());
			this.messageCentral.addListener(ConsensusEventMessage.class, listener);
			emitter.setCancellable(() -> this.messageCentral.removeListener(listener));
		}).mergeWith(localMessages.observeOn(Schedulers.io())).delay(100, TimeUnit.MILLISECONDS);
	}

	@Override
	public void broadcastProposal(Proposal proposal, Set<BFTNode> nodes) {
		for (BFTNode node : nodes) {
			if (this.self.equals(node)) {
				this.localMessages.onNext(proposal);
			} else {
				ConsensusEventMessage message = new ConsensusEventMessage(this.magic, proposal);
				send(message, node);
			}
		}
	}

	public RemoteEventDispatcher<Vote> voteDispatcher() {
		return this::sendVote;
	}

	private void sendVote(BFTNode receiver, Vote vote) {
		if (this.self.equals(receiver)) {
			this.localMessages.onNext(vote);
		} else {
			ConsensusEventMessage message = new ConsensusEventMessage(this.magic, vote);
			send(message, receiver);
		}
	}

	private boolean send(Message message, BFTNode recipient) {
		Optional<PeerWithSystem> peer = this.addressBook.peer(recipient.getKey().euid());

		if (!peer.isPresent()) {
			log.error("{}: Peer {} not present", this.self, recipient);
			return false;
		} else {
			this.messageCentral.send(peer.get(), message);
			return true;
		}
	}
}
