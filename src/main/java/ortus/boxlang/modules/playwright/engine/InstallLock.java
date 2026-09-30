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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes installs (driver extraction, Node.js downloads) that write into the playwright home.
 * <p>
 * Two levels of locking are used: a JVM-wide lock per lock file, so threads of this JVM wait for each other, and an
 * OS file lock on the lock file, so other JVMs (another BoxLang process, the CLI) sharing the same home wait too.
 * File locks are advisory and not supported by every file system: when the OS lock cannot be taken the install still
 * runs under the JVM-wide lock.
 */
final class InstallLock {

	/**
	 * The JVM-wide locks, one per lock file.
	 */
	private static final ConcurrentHashMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

	/**
	 * Work done while holding the lock.
	 *
	 * @param <T> The result type
	 */
	@FunctionalInterface
	interface Action<T> {

		/**
		 * Run the work.
		 *
		 * @return The result
		 *
		 * @throws IOException          when a file operation fails
		 * @throws InterruptedException when interrupted
		 */
		T run() throws IOException, InterruptedException;
	}

	/**
	 * Static utility class, not instantiable.
	 */
	private InstallLock() {
	}

	/**
	 * Run an action while holding the JVM-wide lock and the OS file lock of a lock file.
	 *
	 * @param lockFile The lock file, created when missing (with its parent folders)
	 * @param action   The work to do
	 * @param <T>      The result type
	 *
	 * @return The result of the action
	 *
	 * @throws IOException          when the action fails or the lock file cannot be created
	 * @throws InterruptedException when interrupted
	 */
	static <T> T with( Path lockFile, Action<T> action ) throws IOException, InterruptedException {
		Path			key		= lockFile.toAbsolutePath().normalize();
		ReentrantLock	jvmLock	= LOCKS.computeIfAbsent( key, path -> new ReentrantLock() );
		jvmLock.lockInterruptibly();
		try {
			Files.createDirectories( key.getParent() );
			FileChannel	channel		= null;
			FileLock	fileLock	= null;
			try {
				try {
					channel		= FileChannel.open( key, StandardOpenOption.CREATE, StandardOpenOption.WRITE );
					fileLock	= channel.lock();
				} catch ( IOException | UnsupportedOperationException | OverlappingFileLockException e ) {
					// File locks are not available here (e.g. some network file systems), or this thread already holds
					// the lock (a nested install): rely on the JVM-wide lock
					fileLock = null;
				}
				return action.run();
			} finally {
				if ( fileLock != null && fileLock.isValid() ) {
					fileLock.release();
				}
				if ( channel != null ) {
					channel.close();
				}
			}
		} finally {
			jvmLock.unlock();
		}
	}

}
