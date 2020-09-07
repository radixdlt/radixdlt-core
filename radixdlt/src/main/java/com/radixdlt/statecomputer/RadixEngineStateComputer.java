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

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.ledger.CommittedCommand;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.utils.UInt256;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Wraps the Radix Engine and emits messages based on success or failure
 */
public final class RadixEngineStateComputer implements StateComputer {

	// TODO: Refactor committed command when commit logic is re-written
	// TODO: as currently it's mostly loosely coupled logic
	public interface CommittedAtomWithResult {
		CommittedAtom getCommittedAtom();
		CommittedAtomWithResult ifSuccess(Consumer<ImmutableSet<EUID>> successConsumer);
		CommittedAtomWithResult ifError(Consumer<RadixEngineException> errorConsumer);
	}

	// TODO: Remove this temporary interface
	public interface CommittedAtomSender {
		void sendCommittedAtom(CommittedAtomWithResult committedAtomWithResult);
	}

	private final Serialization serialization;
	private final RadixEngine<LedgerAtom> radixEngine;
	private final View epochChangeView;
	private final CommittedCommandsReader committedCommandsReader;
	private final CommittedAtomSender committedAtomSender;
	private final Object lock = new Object();
	private final LinkedList<CommittedCommand> unstoredCommittedAtoms = new LinkedList<>();
	private final Set<BFTValidator> nextValidatorSet;

	public RadixEngineStateComputer(
		ImmutableSet<BFTValidator> initialNextValidatorSet,
		Serialization serialization,
		RadixEngine<LedgerAtom> radixEngine,
		View epochChangeView,
		CommittedCommandsReader committedCommandsReader,
		CommittedAtomSender committedAtomSender
	) {
		if (epochChangeView.isGenesis()) {
			throw new IllegalArgumentException("Epoch change view must not be genesis.");
		}

		this.nextValidatorSet = new HashSet<>(initialNextValidatorSet);
		this.serialization = Objects.requireNonNull(serialization);
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.epochChangeView = epochChangeView;
		this.committedCommandsReader = Objects.requireNonNull(committedCommandsReader);
		this.committedAtomSender = Objects.requireNonNull(committedAtomSender);
	}

	// TODO Move this to a different class class when unstored committed atoms is fixed
	public List<CommittedCommand> getCommittedCommands(long stateVersion, int batchSize) {
		// TODO: This may still return an empty list as we still count state versions for atoms which
		// TODO: never make it into the radix engine due to state errors. This is because we only check
		// TODO: validity on commit rather than on proposal/prepare.
		// TODO: remove 100 hardcode limit
		List<CommittedCommand> storedCommittedAtoms = committedCommandsReader.getCommittedCommands(stateVersion, batchSize);

		// TODO: Remove
		final List<CommittedCommand> copy;
		synchronized (lock) {
			copy = new ArrayList<>(unstoredCommittedAtoms);
		}

		return Streams.concat(
			storedCommittedAtoms.stream(),
			copy.stream().filter(a -> a.getVertexMetadata().getPreparedCommand().getStateVersion() > stateVersion)
		)
			.sorted(Comparator.comparingLong(a -> a.getVertexMetadata().getPreparedCommand().getStateVersion()))
			.collect(ImmutableList.toImmutableList());
	}

	@Override
	public boolean prepare(Vertex vertex) {
		return vertex.getView().compareTo(epochChangeView) >= 0;
	}

	private ClientAtom mapCommand(Command command) {
		try {
			return serialization.fromDson(command.getPayload(), ClientAtom.class);
		} catch (DeserializeException e) {
			return null;
		}
	}

	@Override
	public Optional<BFTValidatorSet> commit(Command command, VertexMetadata vertexMetadata) {
		boolean storedInRadixEngine = false;
		final ClientAtom clientAtom = command != null ? this.mapCommand(command) : null;
		if (clientAtom != null) {
			final CommittedAtom committedAtom = new CommittedAtom(clientAtom, vertexMetadata);
			try {
				// TODO: execute list of commands instead
				this.radixEngine.checkAndStore(committedAtom);
				storedInRadixEngine = true;

				// TODO: Move into radix engine
				clientAtom.getCMInstruction().getMicroInstructions().stream()
					.filter(i -> i.getParticle() instanceof RegisteredValidatorParticle)
					.filter(CMMicroInstruction::isCheckSpin)
					.forEach(i -> {
						RadixAddress nextValidatorAddress = ((RegisteredValidatorParticle) i.getParticle()).getAddress();
						BFTNode validatorNode = BFTNode.create(nextValidatorAddress.getPublicKey());
						BFTValidator nextValidator = BFTValidator.from(validatorNode, UInt256.ONE);
						if (i.getCheckSpin() == Spin.NEUTRAL) {
							nextValidatorSet.add(nextValidator);
						} else {
							// Never let validator set get less than two otherwise bad things can happen
							// TODO: Fix this limit and implement within radix engine
							if (nextValidatorSet.size() > 2) {
								nextValidatorSet.remove(nextValidator);
							}
						}
					});

			} catch (RadixEngineException e) {
				// TODO: Don't check for state computer errors for now so that we don't
				// TODO: have to deal with failing leader proposals
				// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals

				// TODO: move VIRTUAL_STATE_CONFLICT to static check
				committedAtomSender.sendCommittedAtom(CommittedAtoms.error(committedAtom, e));
			}
		}

		if (!storedInRadixEngine) {
			this.unstoredCommittedAtoms.add(new CommittedCommand(command, vertexMetadata));
		}

		if (vertexMetadata.getPreparedCommand().isEndOfEpoch()) {
			BFTValidatorSet validatorSet = BFTValidatorSet.from(nextValidatorSet);
			return Optional.of(validatorSet);
		}

		return Optional.empty();
	}
}
