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

package com.radixdlt.consensus;

import java.util.Collection;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;
import java.util.stream.IntStream;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import nl.jqno.equalsverifier.EqualsVerifier;

public class QuorumCertificateTest {
	@Test
	public void when_create_genesis_qc_with_non_genesis_vertex__then_should_throw_exception() {
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(View.of(1));
		assertThatThrownBy(() -> QuorumCertificate.ofGenesis(vertex))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(QuorumCertificate.class)
			.verify();
	}

	@Test
	public void testQuorumTimeZeroWeight() {
		ImmutableList<ECPublicKey> keys = makeKeys(1);
		QuorumCertificate qc = makeQuorumCertificate(keys, n -> n);
		ValidatorSet validatorSet = makeValidatorSet(keys, n -> UInt256.ZERO);

		assertThatThrownBy(() -> qc.quorumTime(validatorSet))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void testQuorumTime1() {
		ImmutableList<ECPublicKey> keys = makeKeys(1);
		QuorumCertificate qc = makeQuorumCertificate(keys, n -> n);
		ValidatorSet validatorSet = makeValidatorSet(keys, n -> UInt256.ONE);

		assertEquals(0L, qc.quorumTime(validatorSet));
	}

	@Test
	public void testQuorumTime3Equal() {
		ImmutableList<ECPublicKey> keys = makeKeys(3);
		QuorumCertificate qc = makeQuorumCertificate(keys, n -> n);
		ValidatorSet validatorSet = makeValidatorSet(keys, n -> UInt256.ONE);

		assertEquals(1L, qc.quorumTime(validatorSet));
	}

	@Test
	public void testQuorumTime100NonEqual() {
		ImmutableList<ECPublicKey> keys = makeKeys(100);
		QuorumCertificate qc = makeQuorumCertificate(keys, n -> n);
		ValidatorSet validatorSet = makeValidatorSet(keys, UInt256::from);

		// Using \sum_{i=0}^{n}{i} = \frac{n^2 + n}{2}, we have
		// \sum_{i=0}^{99}{i} = 4,950, so median weight = 4,950 / 2 = 2,475
		// \sum_{i=0}^{69}{i} = 2,415
		// \sum_{i=0}^{70}{i} = 2,485, so median value = 70
		assertEquals(70L, qc.quorumTime(validatorSet));
	}

	private ValidatorSet makeValidatorSet(Collection<ECPublicKey> keys, LongFunction<UInt256> weightFunction) {
		ImmutableList.Builder<Validator> validators = ImmutableList.builder();
		long index = 0L;
		for (ECPublicKey pk : keys) {
			validators.add(Validator.from(pk, weightFunction.apply(index)));
			index += 1;
		}
		return ValidatorSet.from(validators.build());
	}

	private QuorumCertificate makeQuorumCertificate(Collection<ECPublicKey> keys, LongUnaryOperator timeFunction) {
		ImmutableMap.Builder<ECPublicKey, Pair<Long, ECDSASignature>> builder = ImmutableMap.builder();
		long index = 0L;
		for (ECPublicKey pk : keys) {
			builder.put(pk, Pair.of(timeFunction.applyAsLong(index), new ECDSASignature()));
			index += 1;
		}
		return new QuorumCertificate(mock(VoteData.class), new TimestampedECDSASignatures(builder.build()));
	}

	private ImmutableList<ECPublicKey> makeKeys(int n) {
		return IntStream.range(0, n).mapToObj(i -> ECKeyPair.generateNew().getPublicKey()).collect(ImmutableList.toImmutableList());
	}
}