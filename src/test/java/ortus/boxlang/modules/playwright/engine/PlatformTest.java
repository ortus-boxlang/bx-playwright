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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PlatformTest {

	@DisplayName( "It resolves every supported OS and architecture" )
	@Test
	public void testResolvesPlatforms() {
		assertThat( Platform.of( "Linux", "amd64" ) ).isEqualTo( Platform.LINUX_X64 );
		assertThat( Platform.of( "Linux", "aarch64" ) ).isEqualTo( Platform.LINUX_ARM64 );
		assertThat( Platform.of( "Mac OS X", "x86_64" ) ).isEqualTo( Platform.MAC_X64 );
		assertThat( Platform.of( "Mac OS X", "aarch64" ) ).isEqualTo( Platform.MAC_ARM64 );
		assertThat( Platform.of( "Windows 11", "amd64" ) ).isEqualTo( Platform.WINDOWS_X64 );
	}

	@DisplayName( "It rejects unsupported platforms with a clear message" )
	@Test
	public void testRejectsUnsupportedPlatforms() {
		assertThrows( UnsupportedOperationException.class, () -> Platform.of( "Windows 11", "aarch64" ) );
		assertThrows( UnsupportedOperationException.class, () -> Platform.of( "SunOS", "sparc" ) );
	}

	@DisplayName( "It builds the Node.js archive and folder names" )
	@Test
	public void testNodeNaming() {
		assertThat( Platform.MAC_ARM64.nodeArchiveName( "24.21.0" ) ).isEqualTo( "node-v24.21.0-darwin-arm64.tar.gz" );
		assertThat( Platform.WINDOWS_X64.nodeArchiveName( "24.21.0" ) ).isEqualTo( "node-v24.21.0-win-x64.zip" );
		assertThat( Platform.LINUX_X64.nodeFolderName( "24.21.0" ) ).isEqualTo( "node-v24.21.0-linux-x64" );
		assertThat( Platform.WINDOWS_X64.getNodeExecutable() ).isEqualTo( "node.exe" );
		assertThat( Platform.LINUX_ARM64.getNodeExecutable() ).isEqualTo( "bin/node" );
		assertThat( Platform.MAC_ARM64.getDriverFolder() ).isEqualTo( "mac-arm64" );
	}

}
