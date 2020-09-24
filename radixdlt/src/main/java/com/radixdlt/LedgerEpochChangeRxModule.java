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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.radixdlt.epochs.EpochChangeManager.EpochsLedgerUpdateSender;
import com.radixdlt.epochs.EpochChangeRx;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.epochs.EpochChangeSender;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.utils.SenderToRx;
import io.reactivex.rxjava3.core.Observable;

/**
 * Epoch change messages
 */
public class LedgerEpochChangeRxModule extends AbstractModule {
	@Override
	protected void configure() {
		SenderToRx<EpochChange, EpochChange> epochChangeSenderToRx = new SenderToRx<>(e -> e);
		bind(EpochChangeRx.class).toInstance(epochChangeSenderToRx::rx);
		bind(EpochChangeSender.class).toInstance(epochChangeSenderToRx::send);

		SenderToRx<EpochsLedgerUpdate, EpochsLedgerUpdate> epochsLedgerUpdates = new SenderToRx<>(e -> e);
		bind(EpochsLedgerUpdateSender.class).toInstance(epochsLedgerUpdates::send);
		bind(Key.get(new TypeLiteral<Observable<EpochsLedgerUpdate>>() { })).toInstance(epochsLedgerUpdates.rx());
	}
}