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

package com.radixdlt.consensus.liveness;

import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.validators.ValidationState;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.Longs;

/**
 * Manages pending {@link NewView} items.
 * <p>
 * This class is NOT thread-safe.
 * <p>
 * This class is security critical (signature checks, validator set membership checks).
 */
@NotThreadSafe
@SecurityCritical({ SecurityKind.SIG_VERIFY, SecurityKind.GENERAL })
public final class PendingNewViews {
	private static final Logger log = LogManager.getLogger();

	private final Configuration configuration;

	public PendingNewViews(Configuration config) {
		this.configuration = config;
	}

	public PendingNewViews() {
		this(new Configuration());
		assert(configuration.shouldVerifySignatures());
	}

	private final Map<View, ValidationState> newViewState = Maps.newHashMap();
	private final Map<ECPublicKey, View> previousNewView = Maps.newHashMap();

	public static final class Configuration {
		private final boolean shouldVerifySignatures;
		public Configuration(boolean shouldVerifySignatures) {
			this.shouldVerifySignatures = shouldVerifySignatures;
		}
		public Configuration() {
			this(true);
		}

		public boolean shouldVerifySignatures() {
			return shouldVerifySignatures;
		}
	}

	private boolean shouldVerifySignatures() {
		return configuration.shouldVerifySignatures();
	}

	/**
	 * Inserts a {@link NewView}, attempting to form a quorum certificate.
	 * <p>
	 * A QC will only be formed if permitted by the {@link ValidatorSet}.
	 *
	 * @param newView The {@link NewView} to be inserted
	 * @param validatorSet The validator set to form a quorum with
	 * @return The generated QC, if any
	 */
	public Optional<View> insertNewView(NewView newView, ValidatorSet validatorSet) {
		final ECPublicKey newViewAuthor = newView.getAuthor();
		final Hash newViewId = Hash.of(Longs.toByteArray(newView.getView().number()));
		final ECDSASignature signature = newView.getSignature().orElseThrow(() -> new IllegalArgumentException("new-view is missing signature"));
		if (!validatorSet.containsKey(newViewAuthor)) {
			// Not a valid validator
			log.info("Ignoring new view from invalid author {}", () -> hostId(newViewAuthor));
			return Optional.empty();
		}

		if (shouldVerifySignatures()) {
			if (!newViewAuthor.verify(newViewId, signature)) {
				// Signature not valid, just ignore
				log.info("Ignoring invalid signature from author {}", () -> hostId(newViewAuthor));
				return Optional.empty();
			}
		}

		View thisView = newView.getView();
		if (!replacePreviousNewView(newViewAuthor, thisView)) {
			return Optional.empty();
		}

		// Process if signature
		ValidationState validationState = this.newViewState.computeIfAbsent(thisView, k -> validatorSet.newValidationState());

		// check if we have gotten enough new-views to proceed
		if (!(validationState.addSignature(newViewAuthor, signature) && validationState.complete())) {
			return Optional.empty();
		}

		// if we have enough new-views, return view
		return Optional.of(thisView);
	}

	private boolean replacePreviousNewView(ECPublicKey author, View thisView) {
		View previousView = this.previousNewView.put(author, thisView);
		if (previousView == null) {
			// No previous NewView for this author, all good here
			return true;
		}

		if (thisView.equals(previousView)) {
			// Just going to ignore this duplicate NewView for now.
			// However, we can't count duplicates multiple times.
			return false;
		}

		// Prune last pending NewView from pending.
		// This limits the number of pending new views that are in the pipeline.
		ValidationState validationState = this.newViewState.get(previousView);
		if (validationState != null) {
			validationState.removeSignature(author);
			if (validationState.isEmpty()) {
				this.newViewState.remove(previousView);
			}
		}
		return true;
	}

	private static String hostId(ECPublicKey author) {
		// Try and make this as NPE resistant as practical, especially for mocks
		return author == null || author.euid() == null ? "null" : author.euid().toString().substring(0, 6);
	}

	@VisibleForTesting
	// Greybox stuff for testing
	int newViewStateSize() {
		return this.newViewState.size();
	}

	@VisibleForTesting
	// Greybox stuff for testing
	int previousNewViewsSize() {
		return this.previousNewView.size();
	}
}
