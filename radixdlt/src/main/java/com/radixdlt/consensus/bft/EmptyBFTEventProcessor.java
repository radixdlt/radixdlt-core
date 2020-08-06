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

package com.radixdlt.consensus.bft;

import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.Hash;

/**
 * An empty BFT event processor
 */
public enum EmptyBFTEventProcessor implements BFTEventProcessor {
	INSTANCE;

	@Override
	public void processVote(Vote vote) {
		// No-op
	}

	@Override
	public void processNewView(NewView newView) {
		// No-op
	}

	@Override
	public void processProposal(Proposal proposal) {
		// No-op
	}

	@Override
	public void processLocalTimeout(View view) {
		// No-op
	}

	@Override
	public void processLocalSync(Hash vertexId) {
		// No-op
	}

	@Override
	public void start() {
		// No-op
	}
}
