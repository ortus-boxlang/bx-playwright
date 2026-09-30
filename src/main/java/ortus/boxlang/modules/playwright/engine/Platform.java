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

import java.util.Locale;

/**
 * The operating system and CPU architecture combinations supported by Playwright.
 * Each platform knows the folder name Playwright uses inside its driver bundle and
 * the naming used by the official Node.js distributions.
 */
public enum Platform {

	LINUX_X64( "linux", "linux-x64", "tar.gz", "bin/node" ),
	LINUX_ARM64( "linux-arm64", "linux-arm64", "tar.gz", "bin/node" ),
	MAC_X64( "mac", "darwin-x64", "tar.gz", "bin/node" ),
	MAC_ARM64( "mac-arm64", "darwin-arm64", "tar.gz", "bin/node" ),
	WINDOWS_X64( "win32_x64", "win-x64", "zip", "node.exe" );

	private final String	driverFolder;
	private final String	nodeDistribution;
	private final String	nodeArchiveExtension;
	private final String	nodeExecutable;

	/**
	 * Describe a platform.
	 *
	 * @param driverFolder         The Playwright driver folder name for this platform
	 * @param nodeDistribution     The Node.js distribution name, e.g. {@code linux-x64}
	 * @param nodeArchiveExtension The Node.js archive extension, {@code tar.gz} or {@code zip}
	 * @param nodeExecutable       The node executable path inside the Node.js distribution
	 */
	Platform( String driverFolder, String nodeDistribution, String nodeArchiveExtension, String nodeExecutable ) {
		this.driverFolder			= driverFolder;
		this.nodeDistribution		= nodeDistribution;
		this.nodeArchiveExtension	= nodeArchiveExtension;
		this.nodeExecutable			= nodeExecutable;
	}

	/**
	 * Detect the platform of the running JVM.
	 *
	 * @return The current platform
	 *
	 * @throws UnsupportedOperationException if the OS or architecture is not supported by Playwright
	 */
	public static Platform current() {
		return of( System.getProperty( "os.name" ), System.getProperty( "os.arch" ) );
	}

	/**
	 * Resolve a platform from an OS name and an architecture, using the same values as the
	 * {@code os.name} and {@code os.arch} system properties.
	 *
	 * @param osName The operating system name
	 * @param osArch The CPU architecture
	 *
	 * @return The matching platform
	 *
	 * @throws UnsupportedOperationException if the OS or architecture is not supported by Playwright
	 */
	public static Platform of( String osName, String osArch ) {
		String	name	= osName == null ? "" : osName.toLowerCase( Locale.ROOT );
		String	arch	= osArch == null ? "" : osArch.toLowerCase( Locale.ROOT );
		boolean	isArm	= arch.equals( "aarch64" ) || arch.equals( "arm64" );

		if ( name.contains( "windows" ) ) {
			if ( isArm ) {
				throw new UnsupportedOperationException(
				    "Playwright does not ship a Windows ARM64 driver. Use an x64 JVM (emulated) or set a Node.js path with the 'nodePath' setting."
				);
			}
			return WINDOWS_X64;
		}
		if ( name.contains( "linux" ) ) {
			return isArm ? LINUX_ARM64 : LINUX_X64;
		}
		if ( name.contains( "mac" ) || name.contains( "darwin" ) ) {
			return isArm ? MAC_ARM64 : MAC_X64;
		}
		throw new UnsupportedOperationException( "Unsupported operating system for Playwright: [" + osName + " / " + osArch + "]" );
	}

	/**
	 * The Playwright driver folder name for this platform.
	 *
	 * @return The folder name Playwright uses for this platform inside the driver bundle (e.g. {@code mac-arm64})
	 */
	public String getDriverFolder() {
		return driverFolder;
	}

	/**
	 * The Node.js distribution suffix for this platform.
	 *
	 * @return The Node.js distribution suffix (e.g. {@code darwin-arm64})
	 */
	public String getNodeDistribution() {
		return nodeDistribution;
	}

	/**
	 * The archive extension of the Node.js distribution for this platform.
	 *
	 * @return The archive extension of the Node.js distribution ({@code tar.gz} or {@code zip})
	 */
	public String getNodeArchiveExtension() {
		return nodeArchiveExtension;
	}

	/**
	 * The relative path of the node executable inside a Node.js distribution for this platform.
	 *
	 * @return The path of the node executable relative to the root of an extracted Node.js distribution
	 */
	public String getNodeExecutable() {
		return nodeExecutable;
	}

	/**
	 * The file name of the node executable for this platform.
	 *
	 * @return The node executable file name for this platform ({@code node} or {@code node.exe})
	 */
	public String getNodeFileName() {
		return this == WINDOWS_X64 ? "node.exe" : "node";
	}

	/**
	 * Whether this platform is Windows.
	 *
	 * @return True if this is a Windows platform
	 */
	public boolean isWindows() {
		return this == WINDOWS_X64;
	}

	/**
	 * The file name of the Node.js archive for a version, e.g. {@code node-v24.21.0-linux-x64.tar.gz}.
	 *
	 * @param nodeVersion The Node.js version without the leading {@code v}
	 *
	 * @return The archive file name
	 */
	public String nodeArchiveName( String nodeVersion ) {
		return nodeFolderName( nodeVersion ) + "." + nodeArchiveExtension;
	}

	/**
	 * The root folder name inside the Node.js archive, e.g. {@code node-v24.21.0-linux-x64}.
	 *
	 * @param nodeVersion The Node.js version without the leading {@code v}
	 *
	 * @return The folder name
	 */
	public String nodeFolderName( String nodeVersion ) {
		return "node-v" + nodeVersion + "-" + nodeDistribution;
	}

}
