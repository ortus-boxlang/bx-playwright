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
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

}
