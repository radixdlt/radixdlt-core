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

package com.radixdlt.consensus.simulation.tests.epochs;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.consensus.View;
import com.radixdlt.consensus.simulation.SimulationTest;
import com.radixdlt.consensus.simulation.SimulationTest.Builder;
import com.radixdlt.consensus.simulation.TestInvariant.TestInvariantError;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.Condition;
import org.junit.Test;

public class RandomValidatorsTest {
	private static final int numNodes = 200;

	private final Builder bftTestBuilder = SimulationTest.builder()
		.pacemakerTimeout(1000)
		.numNodes(numNodes)
		.epochHighView(View.of(100))
		.checkEpochHighView("epochHighView", View.of(100))
		.checkSafety("safety")
		.checkLiveness("liveness", 1000, TimeUnit.MILLISECONDS)
		.checkNoTimeouts("noTimeouts")
		.checkAllProposalsHaveDirectParents("directParents");

	private static Function<Long, IntStream> randomEpochToNodesMapper(Function<Long, Random> randomSupplier) {
		return epoch -> {
			List<Integer> indices = IntStream.range(0, numNodes).boxed().collect(Collectors.toList());
			Random random = randomSupplier.apply(epoch);
			for (long i = 0; i < epoch; i++) {
				random.nextInt(numNodes);
			}
			return IntStream.range(0, random.nextInt(numNodes) + 1)
				.map(i -> indices.remove(random.nextInt(indices.size())));
		};
	}

	private static Function<Long, IntStream> goodRandomEpochToNodesMapper() {
		return randomEpochToNodesMapper(Random::new);
	}

	private static Function<Long, IntStream> badRandomEpochToNodesMapper() {
		Random random = new Random();
		return randomEpochToNodesMapper(l -> random);
	}

	@Test
	public void given_deterministic_randomized_validator_sets__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.epochToNodesMapper(goodRandomEpochToNodesMapper())
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

	@Test
	public void given_nondeterministic_randomized_validator_sets__then_should_fail() {
		SimulationTest bftTest = bftTestBuilder
			.epochToNodesMapper(badRandomEpochToNodesMapper())
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).hasValueSatisfying(new Condition<Optional>(Optional::isPresent, "Has error"));
	}

}
