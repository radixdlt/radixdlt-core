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

package com.radixdlt.middleware2.network;

import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.GetVertexRequest;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.Hash;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class TestEventCoordinatorNetworkTest {
	private ECPublicKey validatorId = ECKeyPair.generateNew().getPublicKey();
	private ECPublicKey validatorId2 = ECKeyPair.generateNew().getPublicKey();

	@Test
	public void when_send_new_view_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(validatorId).sendNewView(newView, validatorId);
		testObserver.awaitCount(1);
		testObserver.assertValue(newView);
	}

	@Test
	public void when_send_new_view_to_self_twice__then_should_receive_both() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(validatorId).sendNewView(newView, validatorId);
		network.getNetworkSender(validatorId).sendNewView(newView, validatorId);
		testObserver.awaitCount(2);
		testObserver.assertValues(newView, newView);
	}

	@Test
	public void when_self_and_other_send_new_view_to_self__then_should_receive_both() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(validatorId).sendNewView(newView, validatorId);
		network.getNetworkSender(validatorId2).sendNewView(newView, validatorId);
		testObserver.awaitCount(2);
		testObserver.assertValues(newView, newView);
	}

	@Test
	public void when_send_vote_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		Vote vote = mock(Vote.class);
		network.getNetworkSender(validatorId).sendVote(vote, validatorId);
		testObserver.awaitCount(1);
		testObserver.assertValue(vote);
	}

	@Test
	public void when_broadcast_proposal__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		Proposal proposal = mock(Proposal.class);
		network.getNetworkSender(validatorId).broadcastProposal(proposal);
		testObserver.awaitCount(1);
		testObserver.assertValue(proposal);
	}

	@Test
	public void when_disabling_messages_and_send_new_view_message_to_other_node__then_should_not_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder()
			.latencyProvider(msg -> -1)
			.build();

		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetworkRx(validatorId2).consensusEvents()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(validatorId).sendNewView(newView, validatorId);
		testObserver.awaitCount(1);
		testObserver.assertEmpty();
	}

	@Test
	public void when_send_get_vertex_request_to_another_node__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
		Hash vertexId = mock(Hash.class);

		TestObserver<GetVertexRequest> rpcRequestListener = TestObserver.create();
		network.getNetworkRx(validatorId2).rpcRequests().subscribe(rpcRequestListener);
		TestObserver<Vertex> testObserver = TestObserver.create();
		network.getNetworkSender(validatorId).getVertex(vertexId, validatorId2)
			.subscribe(testObserver);

		rpcRequestListener.awaitCount(1);
		rpcRequestListener.assertValueAt(0, r -> r.getVertexId().equals(vertexId));

		Vertex response = mock(Vertex.class);
		when(response.getId()).thenReturn(vertexId);
		rpcRequestListener.values().get(0).getResponder().accept(response);

		testObserver.awaitCount(1);
		testObserver.assertComplete();
		testObserver.assertValue(response);
	}
}