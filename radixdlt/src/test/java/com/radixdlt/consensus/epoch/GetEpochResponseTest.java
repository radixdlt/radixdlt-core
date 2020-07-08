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

package com.radixdlt.consensus.epoch;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.crypto.ECPublicKey;
import org.junit.Before;
import org.junit.Test;

public class GetEpochResponseTest {
	private ECPublicKey sender;
	private VertexMetadata ancestor;
	private GetEpochResponse response;

	@Before
	public void setUp() {
		this.sender = mock(ECPublicKey.class);
		this.ancestor = mock(VertexMetadata.class);
		this.response = new GetEpochResponse(this.sender, this.ancestor);
	}

	@Test
	public void testGetters() {
		assertThat(this.response.getEpochAncestor()).isEqualTo(this.ancestor);
		assertThat(this.response.getAuthor()).isEqualTo(this.sender);
	}

	@Test
	public void testToString() {
		assertThat(this.response.toString()).isNotNull();
	}

}