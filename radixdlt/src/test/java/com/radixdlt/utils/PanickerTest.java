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

package com.radixdlt.utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.utils.Panicker.PanicError;
import com.radixdlt.utils.Panicker.PanicException;

import static org.assertj.core.api.Assertions.*;

/**
 * Basic tests for Panicker class.
 */
public class PanickerTest {
	private boolean enableExitOnPanic;

	@Before
	public void setup() {
		this.enableExitOnPanic = Panicker.setExitOnPanic(false);
	}

	@After
	public void teardown() {
		Panicker.setExitOnPanic(this.enableExitOnPanic);
	}

	@Test
	public void panicThrowsError() {
		assertThatThrownBy(() -> Panicker.panic("test %s", "message"))
			.isInstanceOf(PanicError.class)
			.hasMessageContaining("PANIC: test message")
			.hasCauseInstanceOf(PanicException.class);
	}

	static final class TestException extends Exception {
		// Default constructor is fine
	}

	@Test
	public void panicThrowableThrowsError() {
		assertThatThrownBy(() -> Panicker.panic(new TestException(), "test %s", "message"))
			.isInstanceOf(PanicError.class)
			.hasMessageContaining("PANIC: test message")
			.hasCauseInstanceOf(TestException.class);
	}
}
