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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class NodeInstallerTest {

	@TempDir
	Path tempDir;

	/**
	 * The Node.js archive and checksums URLs point at nodejs.org by default and at a mirror when one is set.
	 */
	@DisplayName( "It builds the download URLs, honoring a mirror" )
	@Test
	public void testUrls() {
		PlaywrightHome	home		= new PlaywrightHome( tempDir, null, "1.63.0", "24.21.0", null, Platform.LINUX_X64 );
		NodeInstaller	official	= new NodeInstaller( home, null, null );
		NodeInstaller	mirror		= new NodeInstaller( home, "https://mirror.example.com/node/", null );
		assertThat( official.archiveURL() ).isEqualTo( "https://nodejs.org/dist/v24.21.0/node-v24.21.0-linux-x64.tar.gz" );
		assertThat( official.checksumsURL() ).isEqualTo( "https://nodejs.org/dist/v24.21.0/SHASUMS256.txt" );
		assertThat( mirror.archiveURL() ).isEqualTo( "https://mirror.example.com/node/v24.21.0/node-v24.21.0-linux-x64.tar.gz" );
	}

	/**
	 * The checksum of an archive is found in a SHASUMS file, and a missing archive fails with the node install error type.
	 */
	@DisplayName( "It finds the checksum of an archive" )
	@Test
	public void testExpectedChecksum() {
		String checksums = """
		                   aaa111  node-v24.21.0-darwin-arm64.tar.gz
		                   bbb222  node-v24.21.0-linux-x64.tar.gz
		                   ccc333  node-v24.21.0-linux-x64.tar.xz
		                   """;
		assertThat( NodeInstaller.expectedChecksum( checksums, "node-v24.21.0-linux-x64.tar.gz" ) ).isEqualTo( "bbb222" );
		BoxRuntimeException error = assertThrows( BoxRuntimeException.class, () -> NodeInstaller.expectedChecksum( checksums, "missing.zip" ) );
		assertThat( error.getType() ).isEqualTo( PlaywrightErrors.NODE_INSTALL_FAILED );
	}

	/**
	 * The SHA-256 digest of a file is computed as lowercase hex.
	 */
	@DisplayName( "It computes SHA-256 digests" )
	@Test
	public void testSha256() throws IOException {
		Path file = Files.writeString( tempDir.resolve( "hello.txt" ), "hello" );
		assertThat( NodeInstaller.sha256( file ) ).isEqualTo( "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824" );
	}

	/**
	 * Node.js is extracted into a private staging folder and only moved into place once the executable is there and runs;
	 * no download or staging files are left behind.
	 */
	@DisplayName( "It installs Node.js through a staging folder" )
	@Test
	public void testInstallStaged() throws Exception {
		Assumptions.assumeFalse( Platform.current().isWindows(), "Builds a tar.gz with a shell script" );
		PlaywrightHome home = currentHome();
		try ( FakeMirror mirror = new FakeMirror( home, true ) ) {
			Path executable = new NodeInstaller( home, mirror.url(), null ).install( false );
			assertThat( executable ).isEqualTo( home.getDownloadedNodeExecutable() );
			assertThat( PlaywrightHome.probeNodeVersion( executable.toString() ) ).isEqualTo( "24.21.0" );
			assertThat( leftovers( home ) ).isEmpty();
		}
	}

	/**
	 * An archive without the node executable fails with the node install error type and leaves nothing that counts as installed.
	 */
	@DisplayName( "A broken archive never counts as installed" )
	@Test
	public void testBrokenArchive() throws Exception {
		Assumptions.assumeFalse( Platform.current().isWindows(), "Builds a tar.gz with a shell script" );
		PlaywrightHome home = currentHome();
		try ( FakeMirror mirror = new FakeMirror( home, false ) ) {
			BoxRuntimeException error = assertThrows( BoxRuntimeException.class, () -> new NodeInstaller( home, mirror.url(), null ).install( false ) );
			assertThat( error.getType() ).isEqualTo( PlaywrightErrors.NODE_INSTALL_FAILED );
			assertThat( Files.exists( home.getDownloadedNodeExecutable() ) ).isFalse();
			assertThat( Files.exists( home.getNodeDir().resolve( home.getPlatform().nodeFolderName( home.getNodeVersion() ) ) ) ).isFalse();
			assertThat( leftovers( home ) ).isEmpty();
		}
	}

	/**
	 * Concurrent installs into the same home are serialized: the archive is downloaded once and every caller gets the executable.
	 */
	@DisplayName( "Concurrent installs download once" )
	@Test
	public void testConcurrentInstall() throws Exception {
		Assumptions.assumeFalse( Platform.current().isWindows(), "Builds a tar.gz with a shell script" );
		PlaywrightHome	home		= currentHome();
		ExecutorService	executor	= Executors.newFixedThreadPool( 6 );
		try ( FakeMirror mirror = new FakeMirror( home, true ) ) {
			CountDownLatch		start	= new CountDownLatch( 1 );
			List<Future<Path>>	results	= new ArrayList<>();
			for ( int i = 0; i < 6; i++ ) {
				results.add( executor.submit( () -> {
					start.await();
					return new NodeInstaller( home, mirror.url(), null ).install( false );
				} ) );
			}
			start.countDown();
			for ( Future<Path> result : results ) {
				assertThat( result.get( 2, TimeUnit.MINUTES ) ).isEqualTo( home.getDownloadedNodeExecutable() );
			}
			assertThat( mirror.archiveDownloads.get() ).isEqualTo( 1 );
			assertThat( leftovers( home ) ).isEmpty();
		} finally {
			executor.shutdownNow();
		}
	}

	/**
	 * A home for the current platform in the temporary folder.
	 *
	 * @return The home
	 */
	private PlaywrightHome currentHome() {
		return new PlaywrightHome( tempDir.resolve( "home" ), null, "1.63.0", "24.21.0", null, Platform.current() );
	}

	/**
	 * List the temporary download and staging files left in the node folder.
	 *
	 * @param home The home
	 *
	 * @return The names of leftover files
	 *
	 * @throws IOException when the folder cannot be listed
	 */
	private static List<String> leftovers( PlaywrightHome home ) throws IOException {
		try ( Stream<Path> entries = Files.list( home.getNodeDir() ) ) {
			return entries.map( path -> path.getFileName().toString() )
			    .filter( name -> name.startsWith( ".tmp-" ) || name.endsWith( ".download" ) || name.contains( ".old-" ) )
			    .toList();
		}
	}

	/**
	 * A local Node.js mirror serving a fake runtime (a shell script that prints a version) and its SHASUMS256.txt.
	 */
	private final class FakeMirror implements AutoCloseable {

		private final HttpServer	server;
		private final AtomicInteger	archiveDownloads	= new AtomicInteger();

		/**
		 * Build the fake archive and start the server.
		 *
		 * @param home           The home, for the platform and Node.js version
		 * @param withExecutable False to build an archive without the node executable
		 *
		 * @throws Exception when the archive cannot be built or the server cannot start
		 */
		FakeMirror( PlaywrightHome home, boolean withExecutable ) throws Exception {
			Platform	platform	= home.getPlatform();
			String		version		= home.getNodeVersion();
			Path		build		= Files.createDirectories( tempDir.resolve( "mirror-src-" + System.nanoTime() ) );
			Path		root		= Files.createDirectories( build.resolve( platform.nodeFolderName( version ) ) );
			Path		node		= root.resolve( withExecutable ? platform.getNodeExecutable() : "README.md" );
			Files.createDirectories( node.getParent() );
			Files.writeString( node, "#!/bin/sh\necho v" + version + "\n" );
			node.toFile().setExecutable( true, true );
			Path	archive	= tempDir.resolve( platform.nodeArchiveName( version ) + "-" + System.nanoTime() );
			Process	tar		= new ProcessBuilder( "tar", "-czf", archive.toString(), "-C", build.toString(), platform.nodeFolderName( version ) )
			    .redirectErrorStream( true ).start();
			assertThat( tar.waitFor() ).isEqualTo( 0 );
			byte[]	archiveBytes	= Files.readAllBytes( archive );
			byte[]	checksums		= ( NodeInstaller.sha256( archive ) + "  " + platform.nodeArchiveName( version ) + "\n" )
			    .getBytes( StandardCharsets.UTF_8 );
			server = HttpServer.create( new InetSocketAddress( "127.0.0.1", 0 ), 0 );
			server.createContext( "/v" + version + "/" + platform.nodeArchiveName( version ), exchange -> {
				archiveDownloads.incrementAndGet();
				exchange.sendResponseHeaders( 200, archiveBytes.length );
				exchange.getResponseBody().write( archiveBytes );
				exchange.close();
			} );
			server.createContext( "/v" + version + "/SHASUMS256.txt", exchange -> {
				exchange.sendResponseHeaders( 200, checksums.length );
				exchange.getResponseBody().write( checksums );
				exchange.close();
			} );
			server.setExecutor( Executors.newCachedThreadPool() );
			server.start();
		}

		/**
		 * The mirror base URL.
		 *
		 * @return The URL to pass as the Node.js download URL
		 */
		String url() {
			return "http://127.0.0.1:" + server.getAddress().getPort();
		}

		/**
		 * Stop the server.
		 */
		@Override
		public void close() {
			server.stop( 0 );
		}

	}

}
