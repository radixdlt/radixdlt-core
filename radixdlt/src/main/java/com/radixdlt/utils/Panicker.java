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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.message.FormattedMessage;

import com.google.common.annotations.VisibleForTesting;

/**
 * Panic methods for exiting system abruptly when invariants fail.
 */
public final class Panicker {

	/**
	 * We exit the application with this status code when we discover
	 * an logic or data corruption error that cannot be recovered
	 * without some intervention.
	 * <p>
	 * Note that the value here is {@code 128 + SIGABRT}, which is the
	 * usual {@code libc} exit code when calling {@code abort()}.
	 */
	public static final int PANIC_EXIT_STATUS = 128 + 6;

	// Allow panicking without exiting for tests
	private static final AtomicBoolean exitOnPanic = new AtomicBoolean(true);

	private Panicker() {
		throw new IllegalStateException("Can't construct");
	}

	@VisibleForTesting
	static final class PanicException extends RuntimeException {
		private PanicException() {
			super("panic");
		}
	}

	/**
	 * An error thrown by {@code Panicker.panic(...)} methods when a
	 * panic occurs and the {@code exitOnPanic} flag is set to false.
	 *
	 * @see Panicker#setExitOnPanic(boolean)
	 */
	@VisibleForTesting
	public static final class PanicError extends Error {
		private PanicError(String msg, Throwable cause) {
			super(msg, cause);
		}
	}

	/**
	 * Sets the state of the internal {@code exitOnPanic} flag.
	 * <p>
	 * Note that a running system should <b>never</b> have this flag set to
	 * {@code false}, it is only here to support testing.  A panic occurs
	 * when the likelihood of data corruption is high if the system continues
	 * operating, and the system will stop processing data by design.
	 * <p>
	 * However, for unit tests we would like to test the logic that leads to a
	 * panic, but we need the tests to continue.  In this case, tests can use
	 * an approach similar to:
	 * <pre>
	 *   {@code @Before}
	 *   public void setup() {
	 *     this.previousPanicState = Panicker.setExitOnPanic(false);
	 *   }
	 *
	 *   {@code @After}
	 *   public void tearDown() {
	 *     Panicker.setExitOnPanic(this.previousPanicState);
	 *   }
	 *
	 *   {@code @Test}
	 *   public void testCausingPanic() {
	 *     assertThatThrownBy(() -> somePanickingMethod()).isInstanceOf(PanicError.class);
	 *   }
	 * </pre>
	 * <p>
	 * When the {@code exitOnPanic} flag is set to {@code true}, calling any
	 * of the {@code panic(...)} methods in this class will result in a call
	 * to {@link System#exit(int)} with an exit status of
	 * {@link #PANIC_EXIT_STATUS}.
	 * <p>
	 * When the {@code exitOnPanic} flag is set to {@code false}, calling any
	 * of the {@code panic(...)} methods in this class will result in a
	 * {@link PanicError} being thrown, which can be caught in tests.
	 *
	 * @param exitOnPanic sets whether calls to panic methods should cause the
	 * 		system to exit
	 * @return the old value of the {@code exitOnPanic} flag
	 */
	@VisibleForTesting
	public static boolean setExitOnPanic(boolean exitOnPanic) {
		return Panicker.exitOnPanic.getAndSet(exitOnPanic);
	}

	/**
	 * Log the specified throwable with the specified message and exit with
	 * exit status {@link #PANIC_EXIT_STATUS}.
	 * The specified message is formatted using {@link FormattedMessage}.
	 * <p>
	 * <b>Note that this method is intended to be called when a logic or data
	 * corruption error that cannot be recovered automatically is detected.</b>
	 *
	 * @param t A {@link Throwable} with the cause of the panic
	 * @param fmt the message format
	 * @param fmtargs the format arguments
	 * @return Dummy throwable so panic can be used in {@code Optional.orElseThrow(...)}
	 */
	public static RuntimeException panic(Throwable t, String fmt, Object... fmtargs) {
		// Not really worth the effort to make this more efficient, as we will be exiting here
		String message = new FormattedMessage("PANIC: " + fmt, fmtargs).getFormattedMessage();
		LogManager.getLogger().atFatal().withThrowable(t).log(message);
		if (Panicker.exitOnPanic.get()) {
			System.exit(PANIC_EXIT_STATUS);
		} else {
			throw new PanicError(message, t);
		}
		return null;
	}

	/**
	 * Log the the specified message and exit with exit status
	 * {@link #PANIC_EXIT_STATUS}.
	 * The specified message is formatted using {@link FormattedMessage}.
	 * <p>
	 * Note that a dummy exception of private type {@code PanicException}
	 * is logged with the log message, so that the stack trace is visible.
	 * <p>
	 * <b>Note that this method is intended to be called when a logic or data
	 * corruption error that cannot be recovered automatically is detected.</b>
	 *
	 * @param fmt the message format
	 * @param fmtargs the format arguments
	 * @return Dummy throwable so panic can be used in {@code Optional.orElseThrow(...)}
	 */
	public static RuntimeException panic(String fmt, Object... fmtargs) {
		// Include a dummy exception so that the stack trace is logged
		return panic(new PanicException(), fmt, fmtargs);
	}
}
