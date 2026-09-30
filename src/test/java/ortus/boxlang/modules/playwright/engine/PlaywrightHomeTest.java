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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PlaywrightHomeTest {

	private static final String	PLAYWRIGHT_VERSION	= "1.63.0";
	private static final String	NODE_VERSION		= "24.21.0";

	@TempDir
	Path						tempDir;

	private PlaywrightHome home( Path explicitNode ) {
		return new PlaywrightHome( tempDir, null, PLAYWRIGHT_VERSION, NODE_VERSION, explicitNode, Platform.current() );
	}

	@DisplayName( "It lays out the home folders" )
	@Test
	public void testPaths() {
		PlaywrightHome home = home( null );
		assertThat( home.getDriverDir() ).isEqualTo( tempDir.toAbsolutePath().resolve( "driver" ).resolve( PLAYWRIGHT_VERSION ) );
		assertThat( home.getBrowsersPath() ).isEqualTo( tempDir.toAbsolutePath().resolve( "browsers" ) );
		assertThat( home.getDownloadedNodeExecutable().toString() ).contains( "node-v" + NODE_VERSION );
	}

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

	@DisplayName( "The small distribution does not bundle Node.js" )
	@Test
	public void testNoBundledNodeInSmallFlavor() {
		if ( "full".equals( System.getProperty( "bx.playwright.flavor", "small" ) ) ) {
			return;
		}
		assertThat( home( null ).hasBundledNode() ).isFalse();
	}

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

	@DisplayName( "An explicit Node.js path wins" )
	@Test
	public void testExplicitNode() throws IOException {
		Path			fakeNode	= Files.createFile( tempDir.resolve( "my-node" ) );
		PlaywrightHome	home		= home( fakeNode );
		assertThat( home.resolveNode().get().getSource() ).isEqualTo( NodeRuntime.Source.EXPLICIT );
		assertThat( home.resolveNode().get().getExecutable() ).isEqualTo( fakeNode );
	}

	@DisplayName( "A downloaded Node.js runtime is found" )
	@Test
	public void testDownloadedNode() throws IOException {
		PlaywrightHome	home		= home( null );
		Path			executable	= home.getDownloadedNodeExecutable();
		Files.createDirectories( executable.getParent() );
		Files.createFile( executable );
		assertThat( home.resolveNode().get().getSource() ).isEqualTo( NodeRuntime.Source.DOWNLOADED );
	}

	@DisplayName( "It builds the driver environment" )
	@Test
	public void testDriverEnvironment() {
		PlaywrightHome		home	= home( null );
		Map<String, String>	env		= home.driverEnvironment( new NodeRuntime( Path.of( "/opt/node" ), NodeRuntime.Source.EXPLICIT, null ) );
		assertThat( env ).containsEntry( "PLAYWRIGHT_BROWSERS_PATH", home.getBrowsersPath().toString() );
		assertThat( env ).containsEntry( "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1" );
		assertThat( env ).containsEntry( "PLAYWRIGHT_NODEJS_PATH", Path.of( "/opt/node" ).toString() );
	}

	@DisplayName( "It parses Node.js versions" )
	@Test
	public void testNodeMajor() {
		assertThat( PlaywrightHome.nodeMajor( "v24.21.0" ) ).isEqualTo( 24 );
		assertThat( PlaywrightHome.nodeMajor( "18.1.0" ) ).isEqualTo( 18 );
		assertThat( PlaywrightHome.nodeMajor( "nope" ) ).isEqualTo( -1 );
	}

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

}
