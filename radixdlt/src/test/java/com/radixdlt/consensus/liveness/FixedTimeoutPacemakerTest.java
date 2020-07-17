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

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker.TimeoutSender;
import com.radixdlt.consensus.validators.ValidationState;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;

import org.junit.Before;
import com.radixdlt.utils.UInt256;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FixedTimeoutPacemakerTest {
	private FixedTimeoutPacemaker pacemaker;
	private FixedTimeoutPacemaker.TimeoutSender timeoutSender;
	private long timeout;

	@Before
	public void setUp() {
		this.timeout = 100;
		this.timeoutSender = mock(FixedTimeoutPacemaker.TimeoutSender.class);
		this.pacemaker = new FixedTimeoutPacemaker(timeout, this.timeoutSender, ECPublicKey::verify);
	}

	@Test
	public void when_creating_pacemaker_with_invalid_timeout__then_exception_is_thrown() {
		TimeoutSender timeoutSender = mock(TimeoutSender.class);
		assertThatThrownBy(() -> new FixedTimeoutPacemaker(0, timeoutSender, ECPublicKey::verify))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new FixedTimeoutPacemaker(-1, timeoutSender, ECPublicKey::verify))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new FixedTimeoutPacemaker(-100, timeoutSender, ECPublicKey::verify))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void when_view_0_processed_qc__then_current_view_should_be_1_and_next_timeout_should_be_scheduled() {
		pacemaker.processQC(View.of(0));
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(1));
		verify(timeoutSender, times(1)).scheduleTimeout(eq(View.of(1)), eq(timeout));
	}

	@Test
	public void when_view_0_processed_timeout__then_current_view_should_be_1_and_next_timeout_should_be_scheduled() {
		pacemaker.processLocalTimeout(View.of(0));
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(1));
		verify(timeoutSender, times(1)).scheduleTimeout(eq(View.of(1)), eq(timeout));
	}

	@Test
	public void when_process_timeout_twice__then_two_timeout_events_occur() {
		pacemaker.processLocalTimeout(View.of(0));
		pacemaker.processLocalTimeout(View.of(1));
		verify(timeoutSender, times(2)).scheduleTimeout(any(), eq(timeout));
	}

	@Test
	public void when_process_timeout_for_earlier_view__then_view_should_not_change() {
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(0L));
		Optional<View> newView = pacemaker.processQC(View.of(0L));
		assertThat(newView).isEqualTo(Optional.of(View.of(1L)));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
		pacemaker.processLocalTimeout(View.of(0L));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
	}

	@Test
	public void when_process_qc_twice_for_same_view__then_view_should_not_change() {
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(0L));
		Optional<View> newView = pacemaker.processQC(View.of(0L));
		assertThat(newView).isEqualTo(Optional.of(View.of(1L)));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
		newView = pacemaker.processQC(View.of(0L));
		assertThat(newView).isEmpty();
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
	}

	@Test
	public void when_inserting_a_new_view_without_signature__then_exception_is_thrown() {
		NewView newViewWithoutSignature = mock(NewView.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(1L));
		when(newViewWithoutSignature.getQC()).thenReturn(qc);
		when(newViewWithoutSignature.getView()).thenReturn(View.of(2L));
		when(newViewWithoutSignature.getSignature()).thenReturn(Optional.empty());
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		when(validatorSet.containsKey(any())).thenReturn(true);

		assertThatThrownBy(() -> pacemaker.processNewView(newViewWithoutSignature, validatorSet))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void when_inserting_valid_but_unaccepted_new_views__then_no_new_view_is_returned() {
		View view = View.of(2);
		NewView newView1 = makeNewViewFor(view);
		NewView newView2 = makeNewViewFor(view);
		ValidatorSet validatorSet = ValidatorSet.from(Collections.singleton(Validator.from(newView1.getAuthor(), UInt256.ONE)));
		assertThat(pacemaker.processNewView(newView2, validatorSet)).isEmpty();
	}

	@Test
	public void when_inserting_valid_but_old_new_views__then_no_new_view_is_returned() {
		View view = View.of(0);
		NewView newView = makeNewViewFor(view);
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		pacemaker.processQC(View.of(0));
		assertThat(pacemaker.processNewView(newView, validatorSet)).isEmpty();
	}

	@Test
	public void when_inserting_current_and_accepted_new_views__then_qc_is_formed_and_current_view_has_changed() {
		View view = View.of(1);
		NewView newView = makeNewViewFor(view);
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		when(validationState.addSignature(any(), anyLong(), any())).thenReturn(true);
		when(validationState.complete()).thenReturn(true);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsKey(any())).thenReturn(true);
		pacemaker.processQC(View.of(0));

		assertThat(pacemaker.processNewView(newView, validatorSet)).isPresent().get().isEqualTo(View.of(1));
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(1));
	}

	@Test
	public void when_inserting_new_view_with_qc_from_previous_view__then_new_synced_view_is_returned() {
		View view = View.of(2);
		ECPublicKey author = ECKeyPair.generateNew().getPublicKey();

		NewView newView = mock(NewView.class);
		when(newView.getView()).thenReturn(view);
		when(newView.getSignature()).thenReturn(Optional.of(new ECDSASignature()));
		when(newView.getAuthor()).thenReturn(author);

		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(1));
		when(newView.getQC()).thenReturn(qc);

		ValidatorSet validatorSet = mock(ValidatorSet.class);
		pacemaker.processQC(View.of(1));

		assertThat(pacemaker.processNewView(newView, validatorSet))
			.isPresent().get().isEqualTo(View.of(2));
		assertThat(pacemaker.getCurrentView())
			.isEqualTo(View.of(2));
	}

	@Test
	public void when_inserting_new_views_with_non_current_view_qc__then_current_view_has_not_changed_and_no_new_timeout() {
		View view = View.of(2);
		NewView newView = makeNewViewFor(view);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.genesis());
		when(newView.getQC()).thenReturn(qc);
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		when(validationState.complete()).thenReturn(true);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsKey(any())).thenReturn(true);

		pacemaker.processQC(View.genesis());
		verify(timeoutSender, times(1)).scheduleTimeout(any(), anyLong());
		assertThat(pacemaker.processNewView(newView, validatorSet)).isEmpty();
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(1));
		verify(timeoutSender, times(1)).scheduleTimeout(any(), anyLong());
	}

	private NewView makeNewViewFor(View view) {
		NewView newView = mock(NewView.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		if (!view.isGenesis()) {
			when(qc.getView()).thenReturn(view.previous());
		}
		when(newView.getQC()).thenReturn(qc);
		when(newView.getView()).thenReturn(view);
		when(newView.getSignature()).thenReturn(Optional.of(new ECDSASignature()));
		ECPublicKey author = mock(ECPublicKey.class);
		when(author.verify(any(Hash.class), any())).thenReturn(true);
		when(newView.getAuthor()).thenReturn(author);
		return newView;
	}
}