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

package com.radixdlt.consensus.sync;

import com.google.common.hash.HashCode;
import java.util.Objects;

/**
 * Parameters for a local get vertices request
 */
public final class LocalGetVerticesRequest {
	private final HashCode vertexId;
	private final int count;

	public LocalGetVerticesRequest(HashCode vertexId, int count) {
		this.vertexId = Objects.requireNonNull(vertexId);
		this.count = count;
	}

	public HashCode getVertexId() {
		return vertexId;
	}

	public int getCount() {
		return count;
	}

	@Override
	public int hashCode() {
		return Objects.hash(vertexId, count);
	}

	@Override
	public boolean equals(Object o)  {
		if (!(o instanceof LocalGetVerticesRequest)) {
			return false;
		}
		LocalGetVerticesRequest other = (LocalGetVerticesRequest) o;

		return Objects.equals(other.vertexId, this.vertexId)
			&& other.count == this.count;
	}

	@Override
	public String toString() {
		return String.format("%s{id=%s count=%s}", this.getClass().getSimpleName(), this.vertexId, this.count);
	}
}