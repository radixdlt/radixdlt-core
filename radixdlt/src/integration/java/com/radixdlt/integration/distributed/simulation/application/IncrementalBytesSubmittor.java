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

import com.google.common.primitives.Longs;
import com.radixdlt.consensus.Command;

/**
 * Submits unique bytes (incrementally) to a network
 */
public class IncrementalBytesSubmittor extends LocalMempoolPeriodicSubmittor {
	private long commandId = 0;

	@Override
	Command nextCommand() {
		return new Command(Longs.toByteArray(commandId++));
	}
}