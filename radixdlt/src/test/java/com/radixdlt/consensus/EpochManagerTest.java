package com.radixdlt.consensus;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.mempool.Mempool;
import org.junit.Test;

public class EpochManagerTest {
	@Test
	public void when_next_epoch__then_should_create_new_event_coordinator() {
		ECKeyPair keyPair = mock(ECKeyPair.class);
		when(keyPair.getPublicKey()).thenReturn(mock(ECPublicKey.class));

		EpochManager epochManager = new EpochManager(
			mock(ProposalGenerator.class),
			mock(Mempool.class),
			mock(BFTEventSender.class),
			mock(SafetyRules.class),
			mock(Pacemaker.class),
			mock(VertexStore.class),
			mock(PendingVotes.class),
			proposers -> mock(ProposerElection.class),
			keyPair,
			ECKeyPair::sign,
			mock(SystemCounters.class)
		);

		Validator validator = mock(Validator.class);
		when(validator.nodeKey()).thenReturn(mock(ECPublicKey.class));
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(validator));
		BFTEventProcessor processor = epochManager.nextEpoch(validatorSet);
		assertNotNull(processor);
	}
}