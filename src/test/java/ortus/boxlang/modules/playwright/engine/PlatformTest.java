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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class PlatformTest {

	/**
	 * Linux, macOS and Windows names with their architectures resolve to the matching platform.
	 */
	@DisplayName( "It resolves every supported OS and architecture" )
	@Test
	public void testResolvesPlatforms() {
		assertThat( Platform.of( "Linux", "amd64" ) ).isEqualTo( Platform.LINUX_X64 );
		assertThat( Platform.of( "Linux", "aarch64" ) ).isEqualTo( Platform.LINUX_ARM64 );
		assertThat( Platform.of( "Mac OS X", "x86_64" ) ).isEqualTo( Platform.MAC_X64 );
		assertThat( Platform.of( "Mac OS X", "aarch64" ) ).isEqualTo( Platform.MAC_ARM64 );
		assertThat( Platform.of( "Windows 11", "amd64" ) ).isEqualTo( Platform.WINDOWS_X64 );
	}

	/**
	 * Unsupported OS and architecture combinations throw the typed unsupported platform error with a helpful detail,
	 * including 32-bit and exotic architectures that used to fall back to x64.
	 *
	 * @param arch The CPU architecture reported by the JVM
	 */
	@DisplayName( "It rejects unsupported architectures with a typed error" )
	@ParameterizedTest( name = "Linux [{0}] is unsupported" )
	@ValueSource( strings = { "arm", "x86", "i386", "ppc64le", "s390x", "riscv64", "" } )
	public void testRejectsUnsupportedArchitectures( String arch ) {
		BoxRuntimeException error = assertThrows( BoxRuntimeException.class, () -> Platform.of( "Linux", arch ) );
		assertThat( error.getType() ).isEqualTo( PlaywrightErrors.UNSUPPORTED_PLATFORM );
		assertThat( error.getDetail() ).contains( "x64" );
	}

	/**
	 * Unsupported operating systems, and Windows ARM64 without an explicit Node.js, throw the typed unsupported platform error.
	 */
	@DisplayName( "It rejects unsupported platforms with a typed error" )
	@Test
	public void testRejectsUnsupportedPlatforms() {
		BoxRuntimeException windowsArm = assertThrows( BoxRuntimeException.class, () -> Platform.of( "Windows 11", "aarch64" ) );
		assertThat( windowsArm.getType() ).isEqualTo( PlaywrightErrors.UNSUPPORTED_PLATFORM );
		assertThat( windowsArm.getDetail() ).contains( "nodePath" );
		BoxRuntimeException sunos = assertThrows( BoxRuntimeException.class, () -> Platform.of( "SunOS", "sparc" ) );
		assertThat( sunos.getType() ).isEqualTo( PlaywrightErrors.UNSUPPORTED_PLATFORM );
	}

	/**
	 * With an explicit Node.js executable, Windows ARM64 uses the Windows x64 layout, as the nodePath hint promises.
	 */
	@DisplayName( "Windows ARM64 works with an explicit Node.js" )
	@Test
	public void testWindowsArmWithExplicitNode() {
		assertThat( Platform.of( "Windows 11", "aarch64", true ) ).isEqualTo( Platform.WINDOWS_X64 );
		assertThat( Platform.of( "Windows 11", "arm64", true ) ).isEqualTo( Platform.WINDOWS_X64 );
		// An explicit Node.js does not make unsupported architectures work
		assertThrows( BoxRuntimeException.class, () -> Platform.of( "Windows 11", "x86", true ) );
	}

	/**
	 * Each platform builds its Node.js archive and folder names, Node.js executable path and driver folder.
	 */
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
