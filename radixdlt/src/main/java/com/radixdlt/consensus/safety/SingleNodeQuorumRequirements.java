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

package com.radixdlt.consensus.safety;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.common.EUID;

import java.util.Objects;

public class SingleNodeQuorumRequirements implements QuorumRequirements {
	private final EUID self;

	@Inject
	public SingleNodeQuorumRequirements(@Named("self") EUID self) {
		this.self = Objects.requireNonNull(self);
	}

	@Override
	public int numRequiredVotes() {
		return 1;
	}

	@Override
	public boolean accepts(EUID author) {
		return self.equals(author);
	}
}
