/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package ortus.boxlang.modules.playwright.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Every Playwright instance the module started and did not close yet. Closing a Playwright instance stops its
 * driver process and every browser it launched, so closing them all leaves no browser process behind.
 *
 * They are all closed when the module unloads and when the JVM shuts down (Ctrl+C, System.exit, the end of a
 * script), even when the code that started them never called close(): an aborted request, a failed test run, a
 * script that forgot. A JVM killed with SIGKILL cannot run anything.
 */
public final class PlaywrightRegistry {

	/** The open instances */
	private static final Set<AutoCloseable>	OPEN			= ConcurrentHashMap.newKeySet();

	/** Is the JVM shutdown hook installed? */
	private static final AtomicBoolean		HOOK_INSTALLED	= new AtomicBoolean( false );

	private PlaywrightRegistry() {
	}

	/**
	 * Track an open instance. The first call installs the JVM shutdown hook.
	 *
	 * @param instance The instance, usually a com.microsoft.playwright.Playwright
	 *
	 * @return The instance
	 */
	public static <T extends AutoCloseable> T register( T instance ) {
		if ( instance != null ) {
			installShutdownHook();
			OPEN.add( instance );
		}
		return instance;
	}

	/**
	 * Stop tracking an instance, because its owner closes it.
	 *
	 * @param instance The instance
	 */
	public static void release( AutoCloseable instance ) {
		if ( instance != null ) {
			OPEN.remove( instance );
		}
	}

	/**
	 * Close every open instance. An instance that fails to close does not stop the others.
	 *
	 * @return How many instances were closed
	 */
	public static int closeAll() {
		List<AutoCloseable> instances = new ArrayList<>( OPEN );
		OPEN.removeAll( instances );
		int closed = 0;
		for ( AutoCloseable instance : instances ) {
			try {
				instance.close();
				closed++;
			} catch ( Exception e ) {
				// Already closed or its driver is gone: keep closing the rest
			}
		}
		return closed;
	}

	/**
	 * @return How many instances are open
	 */
	public static int openCount() {
		return OPEN.size();
	}

	/**
	 * Install the JVM shutdown hook once.
	 */
	private static void installShutdownHook() {
		if ( HOOK_INSTALLED.compareAndSet( false, true ) ) {
			Runtime.getRuntime().addShutdownHook( new Thread( PlaywrightRegistry::closeAll, "bx-playwright-shutdown" ) );
		}
	}

}
