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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.message.FormattedMessage;

/**
 * System-level utility methods.
 */
public final class SystemUtils {

	/**
	 * We exit the application with this status code when we discover
	 * an logic or data corruption error that cannot be recovered
	 * without some intervention.
	 * <p>
	 * Note that the value here is {@code 128 + SIGABRT}, which is the
	 * usual {@code libc} exit code when calling {@code abort()}.
	 */
	public static final int PANIC_EXIT_STATUS = 128 + 6;

	private SystemUtils() {
		throw new IllegalStateException("Can't construct");
	}

	private static class PanicException extends RuntimeException {
		private PanicException() {
			super("panic");
		}
	}

	/**
	 * Log the specified throwable with the specified message and exit with
	 * exit status {@link #PANIC_EXIT_STATUS}.
	 * The specified message is formatted using {@link FormattedMessage}.
	 * <p>
	 * <b>Note that this method is intended to be called when a logic or data
	 * corruption error that cannot be recovered automatically is detected.</b>
	 *
	 * @param fmt the message format
	 * @param fmtargs the format arguments
	 * @return Dummy throwable so panic can be used in {@code Optional.orElseThrow(...)}
	 */
	public static RuntimeException panic(Throwable t, String fmt, Object... fmtargs) {
		// Not really worth the effort to make this more efficient, as we will be exiting here
		LogManager.getLogger().always().log(new FormattedMessage(fmt, fmtargs, t));
		System.exit(PANIC_EXIT_STATUS);
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
		// Include a dummy exception so that the stack trace is included
		return panic(new PanicException(), fmt, fmtargs);
	}
}
