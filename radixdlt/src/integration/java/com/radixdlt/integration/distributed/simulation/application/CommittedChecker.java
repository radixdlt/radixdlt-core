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

package com.radixdlt.integration.distributed.simulation.application;

import com.radixdlt.consensus.Command;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import io.reactivex.rxjava3.core.Observable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Checks to make sure that commands have been committed in a certain amount
 * of time
 */
public class CommittedChecker implements TestInvariant {
	private final Observable<Command> submittedCommands;

	public CommittedChecker(Observable<Command> submittedCommands) {
		this.submittedCommands = Objects.requireNonNull(submittedCommands);
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		return submittedCommands
			.flatMapMaybe(command -> network
					.committedCommands()
						.filter(cmd -> Objects.equals(cmd.getCommand(), command))
						.timeout(10, TimeUnit.SECONDS)
						.firstOrError()
						.ignoreElement()
						.onErrorReturn(e -> new TestInvariantError(e.getMessage() + " " + command))
			);
	}
}
