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

package com.radixdlt.consensus;

import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import io.reactivex.rxjava3.core.Observable;

/**
 * Provider of GetVertices RPC request/response events
 */
public interface SyncVerticesRPCRx {

	/**
	 * Retrieve a never-ending stream of requests
	 * @return a never-ending stream of requests
	 */
	Observable<GetVerticesRequest> requests();

	/**
	 * Retrieve a never-ending stream of responses
	 * @return a never-ending stream of responses
	 */
	Observable<GetVerticesResponse> responses();

	/**
	 * Retrieve a never-ending stream of error responses
	 * @return a never-ending stream of error responses
	 */
	Observable<GetVerticesErrorResponse> errorResponses();
}
