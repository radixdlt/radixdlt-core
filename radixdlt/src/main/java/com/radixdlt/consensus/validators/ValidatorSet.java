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

package com.radixdlt.consensus.validators;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.utils.UInt256;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import com.radixdlt.crypto.ECPublicKey;

/**
 * Set of validators for consensus. Only validators with power >= 1 will
 * be part of the set.
 * <p>
 * Note that this set will validate for set sizes less than 4,
 * as long as all validators sign.
 */
public final class ValidatorSet {
	private final ImmutableBiMap<ECPublicKey, Validator> validators;

	// Because we will base power on tokens and because tokens have a max limit
	// of 2^256 this should never overflow
	private final transient UInt256 totalPower;

	private ValidatorSet(Collection<Validator> validators) {
		this.validators = validators.stream()
			.filter(v -> !v.getPower().isZero())
			.collect(ImmutableBiMap.toImmutableBiMap(Validator::nodeKey, Function.identity()));
		this.totalPower = validators.stream()
			.map(Validator::getPower)
			.reduce(UInt256::add)
			.orElse(UInt256.ZERO);
	}

	/**
	 * Create a validator set from a collection of validators. The sum
	 * of power of all validator should not exceed UInt256.MAX_VALUE otherwise
	 * the resulting ValidatorSet will perform in an undefined way.
	 * This invariant should be upheld within the system due to max number of
	 * tokens being constrained to UInt256.MAX_VALUE.
	 *
	 * @param validators the collection of validators
	 * @return The new {@code ValidatorSet}.
	 */
	public static ValidatorSet from(Collection<Validator> validators) {
		return new ValidatorSet(validators);
	}

	/**
	 * Create an initial validation state with no signatures for this validator set.
	 *
	 * @return An initial validation state with no signatures
	 */
	public ValidationState newValidationState() {
		return ValidationState.forValidatorSet(this);
	}

	public boolean containsKey(ECPublicKey key) {
		return validators.containsKey(key);
	}

	public UInt256 getPower(ECPublicKey key) {
		return validators.get(key).getPower();
	}

	public UInt256 getTotalPower() {
		return totalPower;
	}

	public ImmutableSet<Validator> getValidators() {
		return validators.values();
	}

	public ImmutableMap<ECPublicKey, Validator> validatorsByKey() {
		return validators;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.validators);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof ValidatorSet) {
			ValidatorSet other = (ValidatorSet) obj;
			return Objects.equals(this.validators, other.validators);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), this.validators.keySet());
	}
}
