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

package org.radix.serialization;

import org.junit.Ignore;
import com.radixdlt.identifiers.AID;

public class AIDSerializeTest extends SerializeMessageObject<AID> {
	public AIDSerializeTest() {
		super(AID.class, AIDSerializeTest::getAID);
	}

	private static AID getAID() {
		byte[] bytes = new byte[AID.BYTES];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) i;
		}
		return AID.from(bytes);
	}

	@Override
	@Ignore("Not applicable to AIDs.")
	public void testNONEIsEmpty() {
	}
}
