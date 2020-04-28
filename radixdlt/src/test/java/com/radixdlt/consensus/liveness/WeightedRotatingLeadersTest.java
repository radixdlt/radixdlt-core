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

package com.radixdlt.consensus.liveness;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt128;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Test;

public class WeightedRotatingLeadersTest {
	private WeightedRotatingLeaders weightedRotatingLeaders;
	private WeightedRotatingLeaders weightedRotatingLeaders2;
	private ImmutableList<Validator> validatorsInOrder;

	private void setUp(int validatorSetSize, int sizeOfCache) {
		this.validatorsInOrder = Stream.generate(() -> mock(ECPublicKey.class))
			.limit(validatorSetSize)
			.map(pk -> Validator.from(pk, UInt128.ONE))
			.collect(ImmutableList.toImmutableList());

		ValidatorSet validatorSet = ValidatorSet.from(validatorsInOrder);
		this.weightedRotatingLeaders = new WeightedRotatingLeaders(validatorSet, Comparator.comparingInt(validatorsInOrder::indexOf), sizeOfCache);
		this.weightedRotatingLeaders2 = new WeightedRotatingLeaders(validatorSet, Comparator.comparingInt(validatorsInOrder::indexOf), sizeOfCache);
	}

	@Test
	public void when_equivalent_leaders__then_leaders_are_round_robined_deterministically() {
		for (int validatorSetSize = 1; validatorSetSize <= 128; validatorSetSize *= 2) {
			for (int sizeOfCache = 1; sizeOfCache <= 128; sizeOfCache *= 2) {
				setUp(validatorSetSize, sizeOfCache);

				// 2 round robins
				final int viewsToTest = 2 * validatorSetSize;

				for (int view = 0; view < viewsToTest; view++) {
					ECPublicKey expectedKeyForView = validatorsInOrder.get(validatorSetSize - (view % validatorSetSize) - 1).nodeKey();
					assertThat(weightedRotatingLeaders.getProposer(View.of(view))).isEqualTo(expectedKeyForView);
				}
			}
		}
	}


	@Test
	public void when_get_proposer_multiple_times__then_should_return_the_same_key() {
		for (int validatorSetSize = 1; validatorSetSize <= 128; validatorSetSize *= 2) {
			for (int sizeOfCache = 1; sizeOfCache <= 128; sizeOfCache *= 2) {
				setUp(validatorSetSize, sizeOfCache);

				// 2 * sizeOfCache so cache eviction occurs
				final int viewsToTest = 2 * sizeOfCache;

				ECPublicKey expectedKeyForView0 = weightedRotatingLeaders.getProposer(View.of(0));
				for (View view = View.of(1); view.compareTo(View.of(viewsToTest)) <= 0; view = view.next()) {
					weightedRotatingLeaders.getProposer(view);
				}
				assertThat(weightedRotatingLeaders.getProposer(View.of(0))).isEqualTo(expectedKeyForView0);
			}
		}
	}

	@Test
	public void when_get_proposer_skipping_views__then_should_return_same_result_as_in_order() {
		for (int validatorSetSize = 1; validatorSetSize <= 128; validatorSetSize *= 2) {
			for (int sizeOfCache = 1; sizeOfCache <= 128; sizeOfCache *= 2) {
				setUp(validatorSetSize, sizeOfCache);

				// 2 * sizeOfCache so cache eviction occurs
				final int viewsToTest = 2 * sizeOfCache;

				for (int view = 0; view < viewsToTest; view++) {
					weightedRotatingLeaders2.getProposer(View.of(view));
				}
				ECPublicKey pk1 = weightedRotatingLeaders.getProposer(View.of(viewsToTest - 1));
				ECPublicKey pk2 = weightedRotatingLeaders2.getProposer(View.of(viewsToTest - 1));
				assertThat(pk1).isEqualTo(pk2);
			}
		}
	}

	@Test
	public void when_validators_distributed_by_fibonacci__then_leaders_also_distributed_in_fibonacci() {
		// fibonacci sequence can quickly explode so keep sizes small
		final int validatorSetSize = 8;
		final int sizeOfCache = 4;
		final Supplier<IntStream> fibonacci = () -> Stream.iterate(new int[]{1, 1}, t -> new int[]{t[1], t[0] + t[1]})
			.mapToInt(t -> t[0])
			.limit(validatorSetSize);

		final int sumOfPower = fibonacci.get().sum();
		this.validatorsInOrder = fibonacci.get()
			.mapToObj(p -> Validator.from(mock(ECPublicKey.class), UInt128.from(p)))
			.collect(ImmutableList.toImmutableList());

		ValidatorSet validatorSet = ValidatorSet.from(validatorsInOrder);
		Comparator<Validator> validatorComparator = mock(Comparator.class);
		this.weightedRotatingLeaders = new WeightedRotatingLeaders(validatorSet, validatorComparator, sizeOfCache);

		Map<ECPublicKey, UInt128> proposerCounts = Stream.iterate(View.of(0), View::next)
			.limit(sumOfPower)
			.map(this.weightedRotatingLeaders::getProposer)
			.collect(groupingBy(p -> p, collectingAndThen(counting(), UInt128::from)));

		Map<ECPublicKey, UInt128> expected = validatorsInOrder.stream()
			.collect(toMap(Validator::nodeKey, Validator::getPower));

		assertThat(proposerCounts).isEqualTo(expected);
	}

}