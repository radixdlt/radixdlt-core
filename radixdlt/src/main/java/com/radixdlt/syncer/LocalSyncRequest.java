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

package com.radixdlt.syncer;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.BFTNode;
import java.util.Objects;

/**
 * A request to sync to a given version
 */
public final class LocalSyncRequest {
	private final long targetVersion;
	private final long currentVersion;
	private final ImmutableList<BFTNode> target;

	public LocalSyncRequest(long targetVersion, long currentVersion, ImmutableList<BFTNode> target) {
		this.targetVersion = targetVersion;
		this.currentVersion = currentVersion;
		this.target = Objects.requireNonNull(target);
	}

	public long getTargetVersion() {
		return targetVersion;
	}

	public long getCurrentVersion() {
		return currentVersion;
	}

	public ImmutableList<BFTNode> getTarget() {
		return target;
	}
}