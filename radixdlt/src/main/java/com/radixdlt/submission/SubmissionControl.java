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
package com.radixdlt.submission;

import java.util.function.Consumer;

import org.json.JSONObject;

import com.radixdlt.identifiers.AID;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;

/**
 * Handle atom submission.
 * <p>
 * FIXME: In the longer term, it would be nice if the submit method here returned
 * an {@code Observable<AtomStatusEvent>} or similar.
 */
public interface SubmissionControl {
	/**
	 * Handle atom submission from API or network as an {@link Atom}.
	 *
	 * @param atom the {@link Atom} for the atom
	 * @throws MempoolFullException if the underlying mempool is too full to accept new submissions
	 * @throws MempoolDuplicateException if the underlying mempool already has the specified atom
	 */
	public void submitAtom(Atom atom) throws MempoolFullException, MempoolDuplicateException;

	/**
	 * Handle atom submission from API or network as an {@link JSONObject}.
	 *
	 * @param atomJson the {@link JSONObject} to deserialise for the atom
	 * @param deserialisationCallback the callback to call after deserialisation has occurred
	 * @return The {@link AID} of the submitted atom
	 * @throws MempoolFullException if the underlying mempool is too full to accept new submissions
	 * @throws MempoolDuplicateException if the underlying mempool already has the specified atom
	 */
	public AID submitAtom(JSONObject atomJson, Consumer<Atom> deserialisationCallback)
		throws MempoolFullException, MempoolDuplicateException;
}
