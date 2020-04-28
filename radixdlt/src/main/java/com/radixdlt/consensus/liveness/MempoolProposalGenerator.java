package com.radixdlt.consensus.liveness;

import com.radixdlt.identifiers.AID;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.View;
import com.radixdlt.mempool.Mempool;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Logic for generating new proposals
 */
public final class MempoolProposalGenerator implements ProposalGenerator {
	private final Mempool mempool;
	private final VertexStore vertexStore;

	@Inject
	public MempoolProposalGenerator(
		VertexStore vertexStore,
		Mempool mempool
	) {
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.mempool = Objects.requireNonNull(mempool);
	}

	// TODO: check that next proposal works with current vertexStore state
	@Override
	public Vertex generateProposal(View view) {
		final QuorumCertificate highestQC = vertexStore.getHighestQC();
		final List<Vertex> preparedVertices = vertexStore.getPathFromRoot(highestQC.getProposed().getId());
		final Set<AID> preparedAtoms = preparedVertices.stream()
			.filter(v -> v.getAtom() != null)
			.map(Vertex::getAtom)
			.map(Atom::getAID)
			.collect(Collectors.toSet());

		final List<Atom> atoms = mempool.getAtoms(1, preparedAtoms);

		return Vertex.createVertex(highestQC, view, !atoms.isEmpty() ? atoms.get(0) : null);
	}
}