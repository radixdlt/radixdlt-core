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

package org.radix.time;

import com.radixdlt.properties.RuntimeProperties;

public final class Time {
	public static final int MAXIMUM_DRIFT = 30;

	private static NtpService ntpServiceInstance;

	public static void start(RuntimeProperties properties) {
		if (properties.get("ntp", false)) {
			ntpServiceInstance = new NtpService(properties.get("ntp.pool"));
		}
	}

	public static long currentTimestamp() {
		return ntpServiceInstance != null ? ntpServiceInstance.getUTCTimeMS() : System.currentTimeMillis();
	}
}
