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

package com.radixdlt.network;

import java.util.Objects;
import java.util.function.Consumer;

import com.radixdlt.atommodel.Atom;

/**
 * Callback for receiving atoms from mempool gossip.
 */
public interface MempoolSubmissionCallback extends Consumer<Atom> {
	/**
	 * Callback with received atom.
	 *
	 * @param The received atom
	 */
	@Override
	void accept(Atom atom);

	/**
	 * Returns a composed {@code MempoolSubmissionCallback} that performs, in
	 * sequence, this operation followed by the {@code after} operation. If
	 * performing either operation throws an exception, it is relayed to the caller
	 * of the composed operation. If performing this operation throws an exception,
	 * the {@code after} operation will not be performed.
	 *
	 * @param after the operation to perform after this operation
	 * @return a composed {@code MempoolSubmissionCallback} that performs in
	 *         sequence this operation followed by the {@code after} operation
	 * @throws NullPointerException if {@code after} is null
	 */
	@Override
	default MempoolSubmissionCallback andThen(Consumer<? super Atom> after) {
		Objects.requireNonNull(after);
		return (Atom atom) -> {
			accept(atom);
			after.accept(atom);
		};
	}
}
