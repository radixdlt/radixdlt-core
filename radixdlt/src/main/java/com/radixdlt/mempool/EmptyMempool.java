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

package com.radixdlt.mempool;

import com.radixdlt.identifiers.AID;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Mempool which is always empty
 */
public class EmptyMempool implements Mempool {

	@Override
	public void addAtom(SimpleRadixEngineAtom atom) {
		// No-op
	}

	@Override
	public void removeCommittedAtom(AID aid) {
		// No-op
	}

	@Override
	public void removeRejectedAtom(AID aid) {
		// No-op
	}

	@Override
	public List<SimpleRadixEngineAtom> getAtoms(int count, Set<AID> seen) {
		return Collections.emptyList();
	}

	@Override
	public int atomCount() {
		return 0;
	}
}
