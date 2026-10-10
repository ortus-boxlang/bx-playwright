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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class PlaywrightHomeTest {

	private static final String	PLAYWRIGHT_VERSION	= "1.63.0";
	private static final String	NODE_VERSION		= "24.21.0";

	@TempDir
	Path						tempDir;

	/**
	 * Create a home in the temporary folder for the current platform.
	 *
	 * @param explicitNode An explicit Node.js executable, or null to resolve one
	 *
	 * @return The home
	 */
	private PlaywrightHome home( Path explicitNode ) {
		return new PlaywrightHome( tempDir, null, PLAYWRIGHT_VERSION, NODE_VERSION, explicitNode, Platform.current() );
	}

	/**
	 * The driver, browsers and downloaded Node.js paths are laid out under the home folder.
	 */
	@DisplayName( "It lays out the home folders" )
	@Test
	public void testPaths() {
		PlaywrightHome home = home( null );
		assertThat( home.getDriverDir() ).isEqualTo( tempDir.toAbsolutePath().resolve( "driver" ).resolve( PLAYWRIGHT_VERSION ) );
		assertThat( home.getBrowsersPath() ).isEqualTo( tempDir.toAbsolutePath().resolve( "browsers" ) );
		assertThat( home.getDownloadedNodeExecutable().toString() ).contains( "node-v" + NODE_VERSION );
	}

	/**
	 * The driver is extracted once, reported installed, and installing again without force returns the same folder.
	 */
	@DisplayName( "It extracts the driver once and reports it installed" )
	@Test
	public void testInstallDriver() {
		PlaywrightHome home = home( null );
		assertThat( home.isDriverInstalled() ).isFalse();
		Path driverDir = home.installDriver( false );
		assertThat( home.isDriverInstalled() ).isTrue();
		assertThat( Files.isRegularFile( driverDir.resolve( "package" ).resolve( "package.json" ) ) ).isTrue();
		// Re-installing without force is a no-op
		assertThat( home.installDriver( false ) ).isEqualTo( driverDir );
	}

	/**
	 * The small distribution has no bundled Node.js; skipped for the full flavor.
	 */
	@DisplayName( "The small distribution does not bundle Node.js" )
	@Test
	public void testNoBundledNodeInSmallFlavor() {
		if ( "full".equals( System.getProperty( "bx.playwright.flavor", "small" ) ) ) {
			return;
		}
		assertThat( home( null ).hasBundledNode() ).isFalse();
	}

	/**
	 * The full distribution extracts its bundled Node.js with the driver and resolves it; skipped when none is bundled.
	 */
	@DisplayName( "The full distribution extracts and uses its bundled Node.js" )
	@Test
	public void testBundledNodeInFullFlavor() {
		PlaywrightHome home = home( null );
		if ( !home.hasBundledNode() ) {
			return;
		}
		home.installDriver( false );
		assertThat( Files.isRegularFile( home.getBundledNodeExecutable() ) ).isTrue();
		assertThat( home.resolveNode().get().getSource() ).isEqualTo( NodeRuntime.Source.BUNDLED );
	}

	/**
	 * An explicit Node.js path is resolved as the explicit source with that executable.
	 */
	@DisplayName( "An explicit Node.js path wins" )
	@Test
	public void testExplicitNode() throws IOException {
		Path fakeNode = Files.createFile( tempDir.resolve( "my-node" ) );
		fakeNode.toFile().setExecutable( true, true );
		PlaywrightHome home = home( fakeNode );
		assertThat( home.resolveNode().get().getSource() ).isEqualTo( NodeRuntime.Source.EXPLICIT );
		assertThat( home.resolveNode().get().getExecutable() ).isEqualTo( fakeNode );
	}

	/**
	 * A Node.js executable in the download folder is resolved as the downloaded source.
	 */
	@DisplayName( "A downloaded Node.js runtime is found" )
	@Test
	public void testDownloadedNode() throws IOException {
		PlaywrightHome	home		= home( null );
		Path			executable	= home.getDownloadedNodeExecutable();
		Files.createDirectories( executable.getParent() );
		Files.createFile( executable );
		assertThat( home.resolveNode().get().getSource() ).isEqualTo( NodeRuntime.Source.DOWNLOADED );
	}

	/**
	 * The driver environment sets the browsers path, skips browser downloads and points at the Node.js executable.
	 */
	@DisplayName( "It builds the driver environment" )
	@Test
	public void testDriverEnvironment() {
		PlaywrightHome		home	= home( null );
		Map<String, String>	env		= home.driverEnvironment( new NodeRuntime( Path.of( "/opt/node" ), NodeRuntime.Source.EXPLICIT, null ) );
		assertThat( env ).containsEntry( "PLAYWRIGHT_BROWSERS_PATH", home.getBrowsersPath().toString() );
		assertThat( env ).containsEntry( "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1" );
		assertThat( env ).containsEntry( "PLAYWRIGHT_NODEJS_PATH", Path.of( "/opt/node" ).toString() );
	}

	/**
	 * nodeMajor() parses the major version with or without a leading v, and returns -1 for invalid versions.
	 */
	@DisplayName( "It parses Node.js versions" )
	@Test
	public void testNodeMajor() {
		assertThat( PlaywrightHome.nodeMajor( "v24.21.0" ) ).isEqualTo( 24 );
		assertThat( PlaywrightHome.nodeMajor( "18.1.0" ) ).isEqualTo( 18 );
		assertThat( PlaywrightHome.nodeMajor( "nope" ) ).isEqualTo( -1 );
	}

	/**
	 * Installed browsers are listed without hidden folders, and clean() removes the driver but keeps the browsers.
	 */
	@DisplayName( "It lists installed browsers and cleans drivers" )
	@Test
	public void testBrowsersAndClean() throws IOException {
		PlaywrightHome home = home( null );
		Files.createDirectories( home.getBrowsersPath().resolve( "chromium-1200" ) );
		Files.createDirectories( home.getBrowsersPath().resolve( ".links" ) );
		home.installDriver( false );
		assertThat( home.installedBrowsers() ).containsExactly( "chromium-1200" );
		home.clean();
		assertThat( home.isDriverInstalled() ).isFalse();
		assertThat( home.installedBrowsers() ).containsExactly( "chromium-1200" );
	}

	/**
	 * An explicit Node.js path that does not exist resolves to nothing (no silent fallback), and requireNode() fails with
	 * the not installed type naming the bad path.
	 */
	@DisplayName( "A missing explicit Node.js path is reported" )
	@Test
	public void testMissingExplicitNode() {
		Path			missing	= tempDir.resolve( "nope" ).resolve( "node" );
		PlaywrightHome	home	= home( missing );
		assertThat( home.resolveNode().isPresent() ).isFalse();
		BoxRuntimeException error = assertThrows( BoxRuntimeException.class, home::requireNode );
		assertThat( error.getType() ).isEqualTo( PlaywrightErrors.NOT_INSTALLED );
		assertThat( error.getMessage() ).contains( missing.toString() );
		assertThat( error.getDetail() ).contains( "nodePath" );
	}

	/**
	 * The version probe reads the version of a working executable, and gives up (killing the process) when the
	 * executable never exits instead of hanging the caller.
	 */
	@DisplayName( "The Node.js version probe reads versions and times out on hanging executables" )
	@Test
	public void testProbeNodeVersion() throws IOException {
		Assumptions.assumeFalse( Platform.current().isWindows(), "Uses shell scripts" );
		Path	working	= script( "working-node", "echo v22.3.1" );
		Path	hanging	= script( "hanging-node", "sleep 60" );
		assertThat( PlaywrightHome.probeNodeVersion( working.toString() ) ).isEqualTo( "22.3.1" );
		long start = System.nanoTime();
		assertThat( PlaywrightHome.probeNodeVersion( hanging.toString(), 500 ) ).isNull();
		assertThat( TimeUnit.NANOSECONDS.toSeconds( System.nanoTime() - start ) ).isLessThan( 10L );
		assertThat( PlaywrightHome.probeNodeVersion( tempDir.resolve( "missing" ).toString() ) ).isNull();
	}

	/**
	 * CLI passthrough processes do not claim to be Playwright Java, so Playwright's own help and hints do not print Maven commands.
	 */
	@DisplayName( "CLI processes do not set PW_LANG_NAME" )
	@Test
	public void testCliProcessEnvironment() throws IOException {
		Assumptions.assumeFalse( Platform.current().isWindows(), "Uses shell scripts" );
		String previous = System.getProperty( "playwright.cli.dir" );
		try {
			PlaywrightHome	home	= home( script( "fake-node", "echo v24.21.0" ) );
			ProcessBuilder	builder	= home.cliProcess( List.of( "--help" ) );
			assertThat( builder.environment() ).doesNotContainKey( "PW_LANG_NAME" );
			assertThat( builder.environment() ).doesNotContainKey( "PW_LANG_NAME_VERSION" );
			assertThat( builder.environment() ).containsKey( "PLAYWRIGHT_BROWSERS_PATH" );
			assertThat( builder.command() ).contains( "--dns-result-order=ipv4first" );
		} finally {
			if ( previous == null ) {
				System.clearProperty( "playwright.cli.dir" );
			} else {
				System.setProperty( "playwright.cli.dir", previous );
			}
		}
	}

	/**
	 * The install runner prints the CLI output as it comes and keeps its last lines, stderr included, so a failed
	 * install can say why.
	 */
	@DisplayName( "runCliTee prints the output and keeps its tail" )
	@Test
	public void testRunCliTee() throws IOException {
		Assumptions.assumeFalse( Platform.current().isWindows(), "Uses shell scripts" );
		Path					node	= script(
		    "fake-node",
		    "if [ \"$1\" = \"--version\" ]; then echo v24.21.0; exit 0; fi\n"
		        + "i=1; while [ $i -le 30 ]; do echo \"line $i\"; i=$((i+1)); done\n"
		        + "printf \"\\033[2mError: Download failure\\033[22m\\n\" >&2\nexit 3"
		);
		ByteArrayOutputStream	printed	= new ByteArrayOutputStream();
		PlaywrightHome.CliRun	run		= home( node ).runCliTee( List.of( "install", "chromium" ), new PrintStream( printed, true, StandardCharsets.UTF_8 ),
		    5 );
		assertThat( run.exitCode() ).isEqualTo( 3 );
		assertThat( run.tail().lines().toList() ).containsExactly( "line 27", "line 28", "line 29", "line 30", "Error: Download failure" ).inOrder();
		assertThat( printed.toString( StandardCharsets.UTF_8 ) ).contains( "line 1" + System.lineSeparator() );
	}

	/**
	 * Concurrent driver installs on the same fresh home extract once and all succeed (no closed jar file system, no
	 * driver deleted under another thread), and installs on separate homes run side by side.
	 */
	@DisplayName( "Concurrent driver installs are safe" )
	@Test
	public void testConcurrentInstallDriver() throws Exception {
		int				threads		= 6;
		PlaywrightHome	shared		= home( null );
		ExecutorService	executor	= Executors.newFixedThreadPool( threads * 2 );
		try {
			CountDownLatch			start	= new CountDownLatch( 1 );
			List<Future<Path>>		results	= new ArrayList<>();
			List<PlaywrightHome>	homes	= new ArrayList<>();
			for ( int i = 0; i < threads; i++ ) {
				PlaywrightHome separate = new PlaywrightHome( tempDir.resolve( "home-" + i ), null, PLAYWRIGHT_VERSION, NODE_VERSION, null,
				    Platform.current() );
				homes.add( separate );
				results.add( executor.submit( () -> {
					start.await();
					return shared.installDriver( false );
				} ) );
				results.add( executor.submit( () -> {
					start.await();
					return separate.installDriver( false );
				} ) );
			}
			start.countDown();
			for ( Future<Path> result : results ) {
				Path driverDir = result.get( 5, TimeUnit.MINUTES );
				assertThat( Files.isRegularFile( driverDir.resolve( "package" ).resolve( "cli.js" ) ) ).isTrue();
			}
			assertThat( shared.isDriverInstalled() ).isTrue();
			for ( PlaywrightHome separate : homes ) {
				assertThat( separate.isDriverInstalled() ).isTrue();
			}
			// No staging or replaced folders are left behind
			try ( Stream<Path> entries = Files.list( shared.getDriverDir().getParent() ) ) {
				assertThat( entries.map( path -> path.getFileName().toString() ).filter( name -> !name.startsWith( "." ) ).toList() )
				    .containsExactly( PLAYWRIGHT_VERSION );
			}
		} finally {
			executor.shutdownNow();
		}
	}

	/**
	 * Write an executable shell script in the temporary folder.
	 *
	 * @param name The file name
	 * @param body The script body, after the shebang line
	 *
	 * @return The script path
	 *
	 * @throws IOException when the file cannot be written
	 */
	private Path script( String name, String body ) throws IOException {
		Path file = Files.writeString( tempDir.resolve( name ), "#!/bin/sh\n" + body + "\n" );
		file.toFile().setExecutable( true, true );
		return file;
	}

}
