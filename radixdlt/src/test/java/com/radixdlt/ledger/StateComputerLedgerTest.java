/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.PreparedCommand;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.StateComputerLedger.CommittedSender;
import com.radixdlt.ledger.StateComputerLedger.CommittedStateSyncSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.mempool.Mempool;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class StateComputerLedgerTest {

	private Mempool mempool;
	private StateComputer stateComputer;
	private StateComputerLedger stateComputerLedger;
	private CommittedStateSyncSender committedStateSyncSender;
	private CommittedSender committedSender;

	private SystemCounters counters;

	@Before
	public void setup() {
		this.mempool = mock(Mempool.class);
		// No type check issues with mocking generic here
		this.stateComputer = mock(StateComputer.class);
		this.committedStateSyncSender = mock(CommittedStateSyncSender.class);
		this.counters = mock(SystemCounters.class);
		this.committedSender = mock(CommittedSender.class);

		this.stateComputerLedger = new StateComputerLedger(
			1233,
			mempool,
			stateComputer,
			committedStateSyncSender,
			committedSender,
			counters
		);
	}

	@Test
	public void when_generate_proposal_with_empty_prepared__then_generate_proposal_should_return_atom() {
		Command command = mock(Command.class);
		when(mempool.getCommands(anyInt(), anySet())).thenReturn(Collections.singletonList(command));
		Command nextCommand = stateComputerLedger.generateNextCommand(View.of(1), Collections.emptySet());
		assertThat(command).isEqualTo(nextCommand);
	}

	@Test
	public void when_prepare_with_no_command_and_not_end_of_epoch__then_should_return_same_state_version() {
		Vertex vertex = mock(Vertex.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(qc);

		VertexMetadata parent = mock(VertexMetadata.class);
		when(parent.isEndOfEpoch()).thenReturn(false);
		when(parent.getStateVersion()).thenReturn(12345L);
		when(qc.getProposed()).thenReturn(parent);
		when(qc.getTimestampedSignatures()).thenReturn(new TimestampedECDSASignatures());

		PreparedCommand preparedCommand = stateComputerLedger.prepare(vertex);
		assertThat(preparedCommand.getNextValidatorSet()).isEmpty();
		assertThat(preparedCommand.getStateVersion()).isEqualTo(12345L);
	}

	@Test
	public void when_prepare_and_parent_is_end_of_epoch__then_should_return_same_state_version() {
		Vertex vertex = mock(Vertex.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(qc);
		VertexMetadata parent = mock(VertexMetadata.class);
		when(parent.isEndOfEpoch()).thenReturn(true);
		when(parent.getStateVersion()).thenReturn(12345L);
		when(qc.getProposed()).thenReturn(parent);
		when(qc.getTimestampedSignatures()).thenReturn(new TimestampedECDSASignatures());

		PreparedCommand preparedCommand = stateComputerLedger.prepare(vertex);
		assertThat(preparedCommand.getNextValidatorSet()).isEmpty();
		assertThat(preparedCommand.getStateVersion()).isEqualTo(12345L);
	}

	@Test
	public void when_prepare_with_no_command_and_end_of_epoch__then_should_return_next_state_version() {
		Vertex vertex = mock(Vertex.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(qc);
		VertexMetadata parent = mock(VertexMetadata.class);
		when(parent.isEndOfEpoch()).thenReturn(false);
		when(parent.getStateVersion()).thenReturn(12345L);
		when(qc.getProposed()).thenReturn(parent);
		when(qc.getTimestampedSignatures()).thenReturn(new TimestampedECDSASignatures());
		when(stateComputer.prepare(eq(vertex))).thenReturn(Optional.of(mock(BFTValidatorSet.class)));

		PreparedCommand preparedCommand = stateComputerLedger.prepare(vertex);
		assertThat(preparedCommand.getNextValidatorSet()).isNotEmpty();
		assertThat(preparedCommand.getStateVersion()).isEqualTo(12346L);
	}

	@Test
	public void when_prepare_with_command_and_not_end_of_epoch__then_should_return_next_state_version() {
		Vertex vertex = mock(Vertex.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(qc);
		VertexMetadata parent = mock(VertexMetadata.class);
		when(parent.isEndOfEpoch()).thenReturn(false);
		when(parent.getStateVersion()).thenReturn(12345L);
		when(qc.getProposed()).thenReturn(parent);
		when(qc.getTimestampedSignatures()).thenReturn(new TimestampedECDSASignatures());
		when(vertex.getCommand()).thenReturn(mock(Command.class));

		PreparedCommand preparedCommand = stateComputerLedger.prepare(vertex);
		assertThat(preparedCommand.getNextValidatorSet()).isEmpty();
		assertThat(preparedCommand.getStateVersion()).isEqualTo(12346L);
	}

	@Test
	public void when_commit_below_current_version__then_nothing_happens() {
		Command command = mock(Command.class);
		Hash hash = mock(Hash.class);
		when(command.getHash()).thenReturn(hash);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		when(vertexMetadata.getStateVersion()).then(i -> 1233L);

		stateComputerLedger.commit(command, vertexMetadata);
		verify(stateComputer, never()).commit(any(), any());
		verify(mempool, never()).removeCommitted(any());
		verify(committedSender, never()).sendCommitted(any(), any());
	}

	@Test
	public void when_commit__then_correct_messages_are_sent() {
		Command command = mock(Command.class);
		Hash hash = mock(Hash.class);
		when(command.getHash()).thenReturn(hash);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		when(vertexMetadata.getStateVersion()).then(i -> 1234L);

		stateComputerLedger.commit(command, vertexMetadata);
		verify(stateComputer, times(1)).commit(eq(command), eq(vertexMetadata));
		verify(mempool, times(1)).removeCommitted(eq(hash));
		verify(committedSender, times(1)).sendCommitted(any(), any());
	}

	@Test
	public void when_check_sync_and_synced__then_return_sync_handler() {
		VertexMetadata nextVertexMetadata = mock(VertexMetadata.class);
		when(nextVertexMetadata.getStateVersion()).thenReturn(1230L);
		Runnable onSynced = mock(Runnable.class);
		Runnable onNotSynced = mock(Runnable.class);
		stateComputerLedger
			.ifCommitSynced(nextVertexMetadata)
			.then(onSynced)
			.elseExecuteAndSendMessageOnSync(onNotSynced, mock(Object.class));
		verify(onSynced, times(1)).run();
		verify(onNotSynced, never()).run();
	}

	@Test
	public void when_check_sync__will_complete_when_higher_or_equal_state_version() {
		VertexMetadata nextVertexMetadata = mock(VertexMetadata.class);
		when(nextVertexMetadata.getStateVersion()).thenReturn(1234L);

		Runnable onSynced = mock(Runnable.class);
		Runnable onNotSynced = mock(Runnable.class);
		stateComputerLedger
			.ifCommitSynced(nextVertexMetadata)
			.then(onSynced)
			.elseExecuteAndSendMessageOnSync(onNotSynced, mock(Object.class));
		verify(committedStateSyncSender, never()).sendCommittedStateSync(anyLong(), any());
		verify(onSynced, never()).run();
		verify(onNotSynced, times(1)).run();

		stateComputerLedger.commit(mock(Command.class), nextVertexMetadata);

		verify(committedStateSyncSender, timeout(5000).atLeast(1)).sendCommittedStateSync(anyLong(), any());
	}
}