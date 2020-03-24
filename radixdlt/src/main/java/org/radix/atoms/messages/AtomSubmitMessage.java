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

package org.radix.atoms.messages;

import com.radixdlt.atommodel.Atom;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("atom.submit")
public final class AtomSubmitMessage extends Message {
	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private Atom atom;

	AtomSubmitMessage() {
		// For serializer only
		super(0);
	}

	public AtomSubmitMessage(Atom atom, int magic) {
		super(magic);
		this.atom = atom;
	}

	@Override
	public String getCommand()
	{
		return "atom.submit";
	}

	public Atom getAtom() { return atom; }
}
