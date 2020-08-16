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

package com.radixdlt.integration.distributed.simulation.invariants.mempool;

import static org.mockito.Mockito.mock;

import com.radixdlt.api.LedgerRx;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.syncer.SyncExecutor.StateComputerExecutedCommand;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SubmittedAndExecutedInvariant implements TestInvariant {

	private Maybe<TestInvariantError> submitAndExecuted(Mempool mempool, Set<Observable<StateComputerExecutedCommand>> allLedgers) {
		ClientAtom clientAtom = mock(ClientAtom.class);

		List<Maybe<TestInvariantError>> errors = allLedgers.stream()
			.map(cmds ->
				cmds
					.filter(cmd -> Objects.equals(cmd.getCommand().getClientAtom(), clientAtom))
					.timeout(10, TimeUnit.SECONDS)
					.firstOrError()
					.ignoreElement()
					.doOnComplete(() -> System.out.println("Executed " + clientAtom))
					.onErrorReturn(e -> new TestInvariantError(e.getMessage() + " " + clientAtom))
			)
			.collect(Collectors.toList());

		return Maybe.merge(errors).firstElement().doOnSubscribe(d -> {
			try {
				mempool.addAtom(clientAtom);
			} catch (MempoolDuplicateException | MempoolFullException e) {
				e.printStackTrace();
				return;
			}
			System.out.println("Submitted " + clientAtom);
		});
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		return Observable.interval(1, 4, TimeUnit.SECONDS)
			.scan(0, (a, b) -> a + 1)
			.map(i -> network.getNodes().get(i % network.getNodes().size()))
			.flatMapMaybe(node -> {
				Set<Observable<StateComputerExecutedCommand>> allLedgers
					= network.getNodes().stream().map(network::getLedger).map(LedgerRx::committed).collect(Collectors.toSet());
				return submitAndExecuted(network.getMempool(node), allLedgers);
			});
	}
}