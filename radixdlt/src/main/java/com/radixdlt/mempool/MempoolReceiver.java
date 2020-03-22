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

package com.radixdlt.mempool;

import com.radixdlt.consensus.MempoolNetworkRx;
import java.util.Objects;

import org.radix.logging.Logger;
import org.radix.logging.Logging;

import com.google.inject.Inject;
import com.radixdlt.common.Atom;

import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Network glue for SubmissionControl.
 */
public final class MempoolReceiver {
	private static final Logger log = Logging.getLogger("submission");

	private final MempoolNetworkRx mempoolRx;
	private final SubmissionControl submissionControl;

	private final Object startLock = new Object();
	private Disposable disposable;

	@Inject
	public MempoolReceiver(
		MempoolNetworkRx mempoolRx,
		SubmissionControl submissionControl
	) {
		this.mempoolRx = Objects.requireNonNull(mempoolRx);
		this.submissionControl = Objects.requireNonNull(submissionControl);
	}

	public void start() {
		synchronized (this.startLock) {
			if (this.disposable == null) {
				this.disposable = this.mempoolRx.atomMessages()
					.subscribe(this::processAtom);
			}
		}
	}

	void stop() {
		synchronized (this.startLock) {
			if (this.disposable != null) {
				// Try to do the sensible thing if disposable.dispose() throws
				Disposable d = this.disposable;
				this.disposable = null;
				d.dispose();
			}
		}
	}

	boolean running() {
		synchronized (this.startLock) {
			return this.disposable != null;
		}
	}

	private void processAtom(Atom atom) {
		try {
			this.submissionControl.submitAtom(atom);
		} catch (MempoolRejectedException ex) {
			log.info(String.format("Mempool rejected atom %s", atom.getAID()), ex);
		}
	}
}
