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

package com.radixdlt.statecomputer;

import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;

/**
 * Serializes/deserializes a client atom
 */
public class ClientAtomToBinaryConverter {
	private final Serialization serializer;

	public ClientAtomToBinaryConverter(Serialization serializer) {
		this.serializer = serializer;
	}

	public byte[] toLedgerEntryContent(ClientAtom command) {
		try {
			return serializer.toDson(command, DsonOutput.Output.PERSIST);
		} catch (SerializationException e) {
			throw new RuntimeException(String.format("Serialization for Command %s failed", command));
		}
	}

	public ClientAtom toAtom(byte[] ledgerEntryContent) {
		try {
			return serializer.fromDson(ledgerEntryContent, ClientAtom.class);
		} catch (SerializationException e) {
			throw new IllegalStateException("Deserialization of Atom failed", e);
		}
	}
}