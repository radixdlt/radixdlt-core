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

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;


/**
 * Represents a validator and their Proof-of-Stake status.
 */
@Immutable
public final class Validator {
	// Power associated with each validator, could e.g. be based on staked tokens
	private final UInt256 power;

    // Public key for consensus
	private final ECPublicKey nodeKey;

	private Validator(
		ECPublicKey nodeKey,
		UInt256 power
	) {
		this.nodeKey = Objects.requireNonNull(nodeKey);
		this.power = Objects.requireNonNull(power);
	}

	public static Validator from(ECPublicKey nodeKey, UInt256 power) {
		return new Validator(nodeKey, power);
	}

	public ECPublicKey nodeKey() {
		return this.nodeKey;
	}

	public UInt256 getPower() {
		return power;
	}

	@Override
	public int hashCode() {
		return this.nodeKey.hashCode() * 31 + this.power.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof Validator) {
			Validator other = (Validator) obj;
			return Objects.equals(this.nodeKey, other.nodeKey)
				&& Objects.equals(this.power, other.power);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{nodeKey=%s power=%s}", getClass().getSimpleName(), this.nodeKey, this.power);
	}
}
