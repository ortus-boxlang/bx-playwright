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
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.microsoft.playwright.Playwright;

/**
 * The on-disk home of bx-playwright (by default {@code ~/.boxlang/playwright}).
 * <p>
 * It owns everything Playwright needs outside the JVM:
 * <ul>
 * <li>{@code driver/<playwrightVersion>/}: the Playwright driver (JavaScript), extracted once from the jars.
 * In the full distribution the Node.js runtime is extracted next to it.</li>
 * <li>{@code node/}: Node.js runtimes downloaded by {@code bxPlaywright install} (small distribution).</li>
 * <li>{@code browsers/}: the browsers installed by {@code bxPlaywright install}.</li>
 * </ul>
 * It also resolves which Node.js runtime to use and builds the environment passed to the driver.
 */
public class PlaywrightHome {

	/**
	 * The minimum Node.js major version required by the Playwright driver (from its package.json engines field).
	 */
	public static final int			MIN_NODE_MAJOR		= 20;

	private static final String		DRIVER_RESOURCE		= "driver/";
	private static final Pattern	NODE_VERSION		= Pattern.compile( "v?(\\d+)\\.(\\d+)\\.(\\d+)" );
	private static final long		NODE_PROBE_SECONDS	= 10;

	private final Path				home;
	private final Path				browsersPath;
	private final String			playwrightVersion;
	private final String			nodeVersion;
	private final Platform			platform;
	private final Path				explicitNodePath;

	/**
	 * Create a home.
	 *
	 * @param home              The home directory
	 * @param browsersPath      Where browsers are installed, or null for {@code <home>/browsers}
	 * @param playwrightVersion The Playwright version of the bundled jars
	 * @param nodeVersion       The Node.js version to download when no runtime is available
	 * @param explicitNodePath  An explicit Node.js executable to use, or null to auto-resolve
	 * @param platform          The platform, usually {@link Platform#current()}
	 */
	public PlaywrightHome(
	    Path home,
	    Path browsersPath,
	    String playwrightVersion,
	    String nodeVersion,
	    Path explicitNodePath,
	    Platform platform ) {
		this.home				= Objects.requireNonNull( home, "home must not be null" ).toAbsolutePath().normalize();
		this.browsersPath		= browsersPath == null ? this.home.resolve( "browsers" ) : browsersPath.toAbsolutePath().normalize();
		this.playwrightVersion	= Objects.requireNonNull( playwrightVersion, "playwrightVersion must not be null" );
		this.nodeVersion		= Objects.requireNonNull( nodeVersion, "nodeVersion must not be null" );
		this.explicitNodePath	= explicitNodePath;
		this.platform			= Objects.requireNonNull( platform, "platform must not be null" );
	}

	/**
	 * Create a home from string paths, which is friendlier to call from BoxLang.
	 * Empty strings are treated as not set.
	 *
	 * @param home              The home directory
	 * @param browsersPath      Where browsers are installed, empty for {@code <home>/browsers}
	 * @param playwrightVersion The Playwright version of the bundled jars
	 * @param nodeVersion       The Node.js version to download when no runtime is available
	 * @param explicitNodePath  An explicit Node.js executable, empty to auto-resolve
	 *
	 * @return The home for the current platform
	 */
	public static PlaywrightHome of( String home, String browsersPath, String playwrightVersion, String nodeVersion, String explicitNodePath ) {
		return new PlaywrightHome(
		    Paths.get( home ),
		    isBlank( browsersPath ) ? null : Paths.get( browsersPath ),
		    playwrightVersion,
		    nodeVersion,
		    isBlank( explicitNodePath ) ? null : Paths.get( explicitNodePath ),
		    // An explicit Node.js lets Windows ARM64 run with the x64 layout, see Platform.of()
		    Platform.current( !isBlank( explicitNodePath ) )
		);
	}

	/**
	 * --------------------------------------------------------------------------
	 * Paths
	 * --------------------------------------------------------------------------
	 */

	/**
	 * @return The home directory
	 */
	public Path getHome() {
		return home;
	}

	/**
	 * The driver directory for this home's Playwright version.
	 *
	 * @return The directory holding the extracted driver for this Playwright version
	 */
	public Path getDriverDir() {
		return home.resolve( "driver" ).resolve( playwrightVersion );
	}

	/**
	 * The directory of downloaded Node.js runtimes.
	 *
	 * @return The directory where Node.js runtimes are downloaded
	 */
	public Path getNodeDir() {
		return home.resolve( "node" );
	}

	/**
	 * The directory where Playwright browsers are installed.
	 *
	 * @return The browsers directory
	 */
	public Path getBrowsersPath() {
		return browsersPath;
	}

	/**
	 * The Playwright version this home is set up for.
	 *
	 * @return The Playwright version
	 */
	public String getPlaywrightVersion() {
		return playwrightVersion;
	}

	/**
	 * The Node.js version to download.
	 *
	 * @return The Node.js version downloaded when no runtime is available
	 */
	public String getNodeVersion() {
		return nodeVersion;
	}

	/**
	 * The platform this home runs on.
	 *
	 * @return The platform
	 */
	public Platform getPlatform() {
		return platform;
	}

	/**
	 * The path of the node executable in the downloaded Node.js runtime.
	 *
	 * @return The Node.js executable of the downloaded runtime (it may not exist yet)
	 */
	public Path getDownloadedNodeExecutable() {
		return getNodeDir().resolve( platform.nodeFolderName( nodeVersion ) ).resolve( platform.getNodeExecutable() );
	}

	/**
	 * The path of the node executable bundled with the driver in the full distribution.
	 *
	 * @return The Node.js executable extracted from the full distribution (it may not exist)
	 */
	public Path getBundledNodeExecutable() {
		return getDriverDir().resolve( platform.getNodeFileName() );
	}

	/**
	 * --------------------------------------------------------------------------
	 * Driver
	 * --------------------------------------------------------------------------
	 */

	/**
	 * @return True if this distribution ships the Node.js runtime inside its jars (bx-playwright-full)
	 */
	public boolean hasBundledNode() {
		return resource( DRIVER_RESOURCE + platform.getDriverFolder() ) != null;
	}

	/**
	 * Whether the driver for this Playwright version has been extracted.
	 *
	 * @return True if the driver has been extracted into {@link #getDriverDir()}
	 */
	public boolean isDriverInstalled() {
		return Files.isRegularFile( getDriverDir().resolve( "package" ).resolve( "cli.js" ) );
	}

	/**
	 * Extract the driver from the jars into {@link #getDriverDir()}. When the jars bundle Node.js
	 * (full distribution), the runtime for this platform is extracted as well.
	 * <p>
	 * Installs are serialized per home (a JVM-wide lock plus a file lock in the home, so other processes wait too) and
	 * the installed state is checked again once the lock is held, so concurrent callers extract only once.
	 * Extraction happens in a temporary sibling folder that is then moved into place, so a
	 * half-extracted driver is never used, and an existing driver is only replaced when forced (or when the bundled
	 * Node.js is missing).
	 *
	 * @param force Re-extract even if the driver is already installed
	 *
	 * @return The driver directory
	 */
	public Path installDriver( boolean force ) {
		Path target = getDriverDir();
		if ( !needsDriverInstall( force ) ) {
			return target;
		}
		try {
			return InstallLock.with( home.resolve( "driver" ).resolve( ".install-" + playwrightVersion + ".lock" ), () -> {
				// Another thread or process may have installed it while this one waited for the lock
				if ( !force && !needsDriverInstall( false ) ) {
					return target;
				}
				Path staging = target.resolveSibling( target.getFileName() + ".tmp-" + UUID.randomUUID() );
				try {
					Files.createDirectories( staging );
					extractResource( DRIVER_RESOURCE + "package", staging.resolve( "package" ) );
					if ( hasBundledNode() ) {
						extractResource( DRIVER_RESOURCE + platform.getDriverFolder(), staging );
					}
					replaceWith( staging, target );
					return target;
				} finally {
					deleteQuietly( staging );
				}
			} );
		} catch ( IOException e ) {
			throw PlaywrightErrors.of(
			    PlaywrightErrors.NOT_INSTALLED,
			    "Failed to extract the Playwright driver into [" + target + "]: " + e.getMessage(),
			    "Check that the directory is writable, or change the 'home' setting of the playwright module.",
			    e
			);
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw PlaywrightErrors.of(
			    PlaywrightErrors.NOT_INSTALLED,
			    "Interrupted while extracting the Playwright driver into [" + target + "].",
			    "Run the command again."
			);
		}
	}

	/**
	 * Decide if the driver must be extracted.
	 *
	 * @param force True to extract even when it is installed
	 *
	 * @return True when forced, when the driver is missing, or when the jars bundle a Node.js runtime that is not extracted
	 *         yet (switching from the small to the full distribution on the same home)
	 */
	private boolean needsDriverInstall( boolean force ) {
		boolean bundledNodeMissing = hasBundledNode() && !Files.isRegularFile( getBundledNodeExecutable() );
		return force || !isDriverInstalled() || bundledNodeMissing;
	}

	/**
	 * Move a fully prepared folder into place. An existing target is first renamed aside, so the target path never
	 * holds a partial copy, then deleted.
	 *
	 * @param staging The prepared folder
	 * @param target  The final location
	 *
	 * @throws IOException when a move fails
	 */
	static void replaceWith( Path staging, Path target ) throws IOException {
		Path old = null;
		if ( Files.exists( target ) ) {
			old = target.resolveSibling( target.getFileName() + ".old-" + UUID.randomUUID() );
			Files.move( target, old, StandardCopyOption.ATOMIC_MOVE );
		}
		try {
			Files.move( staging, target, StandardCopyOption.ATOMIC_MOVE );
		} catch ( IOException e ) {
			if ( old != null ) {
				// Put the previous install back rather than leaving nothing
				Files.move( old, target, StandardCopyOption.ATOMIC_MOVE );
			}
			throw e;
		}
		if ( old != null ) {
			deleteQuietly( old );
		}
	}

	/**
	 * --------------------------------------------------------------------------
	 * Node.js
	 * --------------------------------------------------------------------------
	 */

	/**
	 * Resolve the Node.js runtime, first match wins:
	 * <ol>
	 * <li>The explicit path ({@code nodePath} setting or {@code PLAYWRIGHT_NODEJS_PATH})</li>
	 * <li>The runtime bundled with the full distribution</li>
	 * <li>A runtime previously downloaded into {@link #getNodeDir()}</li>
	 * <li>{@code node} on the system PATH, if its version is supported</li>
	 * </ol>
	 *
	 * @return The node executable, or empty if none is available (including an explicit path that does not exist, is
	 *         not executable and does not run as a command)
	 */
	public Optional<NodeRuntime> resolveNode() {
		if ( explicitNodePath != null ) {
			// An explicit path is used as is (no fallback to other runtimes), but only when it is an executable file or a
			// command that runs; otherwise nothing is resolved and requireNode() names the bad path
			String	version		= probeNodeVersion( explicitNodePath.toString() );
			boolean	executable	= Files.isRegularFile( explicitNodePath ) && Files.isExecutable( explicitNodePath );
			if ( !executable && version == null ) {
				return Optional.empty();
			}
			return Optional.of( new NodeRuntime( explicitNodePath, NodeRuntime.Source.EXPLICIT, version ) );
		}
		if ( Files.isRegularFile( getBundledNodeExecutable() ) ) {
			return Optional
			    .of( new NodeRuntime( getBundledNodeExecutable(), NodeRuntime.Source.BUNDLED, probeNodeVersion( getBundledNodeExecutable().toString() ) ) );
		}
		if ( Files.isRegularFile( getDownloadedNodeExecutable() ) ) {
			return Optional.of( new NodeRuntime( getDownloadedNodeExecutable(), NodeRuntime.Source.DOWNLOADED, nodeVersion ) );
		}
		String systemVersion = probeNodeVersion( platform.getNodeFileName() );
		if ( systemVersion != null && nodeMajor( systemVersion ) >= MIN_NODE_MAJOR ) {
			return Optional.of( new NodeRuntime( Paths.get( platform.getNodeFileName() ), NodeRuntime.Source.SYSTEM, systemVersion ) );
		}
		return Optional.empty();
	}

	/**
	 * Resolve the Node.js runtime or fail with an actionable error.
	 *
	 * @return The runtime
	 */
	public NodeRuntime requireNode() {
		if ( explicitNodePath != null ) {
			return resolveNode().orElseThrow( () -> PlaywrightErrors.of(
			    PlaywrightErrors.NOT_INSTALLED,
			    "The configured Node.js executable [" + explicitNodePath + "] does not exist or is not executable.",
			    "Point the 'nodePath' setting (or PLAYWRIGHT_NODEJS_PATH) at a Node.js " + MIN_NODE_MAJOR
			        + "+ executable, or unset it to use the runtime downloaded by [bxPlaywright install]."
			) );
		}
		return resolveNode().orElseThrow( () -> PlaywrightErrors.of(
		    PlaywrightErrors.NOT_INSTALLED,
		    "No Node.js runtime is available for Playwright.",
		    "Run [bxPlaywright install] to download it (or install bx-playwright-full), or set the 'nodePath' setting to a Node.js "
		        + MIN_NODE_MAJOR + "+ executable."
		) );
	}

	/**
	 * Run {@code <executable> --version} and return the version, or null if it cannot run.
	 *
	 * @param executable The node executable or command name
	 *
	 * @return The version without the leading {@code v}, or null
	 */
	public static String probeNodeVersion( String executable ) {
		return probeNodeVersion( executable, TimeUnit.SECONDS.toMillis( NODE_PROBE_SECONDS ) );
	}

	/**
	 * Run {@code <executable> --version} and return the version, or null if it cannot run or does not finish in time.
	 * The output goes to a temporary file, so a process that never writes or never exits cannot block the caller:
	 * it is killed when the timeout expires.
	 *
	 * @param executable    The node executable or command name
	 * @param timeoutMillis How long to wait for the process to exit
	 *
	 * @return The version without the leading {@code v}, or null
	 */
	static String probeNodeVersion( String executable, long timeoutMillis ) {
		Path output = null;
		try {
			output = Files.createTempFile( "bx-playwright-node-", ".txt" );
			Process process = new ProcessBuilder( executable, "--version" )
			    .redirectErrorStream( true )
			    .redirectOutput( output.toFile() )
			    .start();
			process.getOutputStream().close();
			if ( !process.waitFor( timeoutMillis, TimeUnit.MILLISECONDS ) ) {
				process.destroyForcibly();
				return null;
			}
			if ( process.exitValue() != 0 ) {
				return null;
			}
			String	line	= Files.readString( output, StandardCharsets.UTF_8 ).trim().lines().findFirst().orElse( "" );
			Matcher	matcher	= NODE_VERSION.matcher( line.trim() );
			return matcher.find() ? matcher.group( 1 ) + "." + matcher.group( 2 ) + "." + matcher.group( 3 ) : null;
		} catch ( IOException e ) {
			return null;
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			return null;
		} finally {
			if ( output != null ) {
				deleteQuietly( output );
			}
		}
	}

	/**
	 * Parse the major version from a Node.js version string.
	 *
	 * @param version A version such as {@code 24.21.0} or {@code v24.21.0}
	 *
	 * @return The major version, or -1 if it cannot be parsed
	 */
	public static int nodeMajor( String version ) {
		if ( version == null ) {
			return -1;
		}
		Matcher matcher = NODE_VERSION.matcher( version );
		return matcher.find() ? Integer.parseInt( matcher.group( 1 ) ) : -1;
	}

	/**
	 * --------------------------------------------------------------------------
	 * Running Playwright
	 * --------------------------------------------------------------------------
	 */

	/**
	 * The environment passed to the Playwright driver process.
	 * Browsers are only installed explicitly through the CLI, never on {@code Playwright.create()}.
	 *
	 * @param node The Node.js runtime
	 *
	 * @return The environment variables
	 */
	public Map<String, String> driverEnvironment( NodeRuntime node ) {
		Map<String, String> env = new LinkedHashMap<>();
		env.put( "PLAYWRIGHT_BROWSERS_PATH", browsersPath.toString() );
		env.put( "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1" );
		if ( node != null ) {
			env.put( "PLAYWRIGHT_NODEJS_PATH", node.getExecutable().toString() );
		}
		return env;
	}

	/**
	 * Make sure the driver is extracted and a Node.js runtime is available, then point the
	 * Playwright Java driver at them. This is idempotent.
	 *
	 * @return The environment to pass to the driver
	 */
	public Map<String, String> prepare() {
		installDriver( false );
		NodeRuntime node = requireNode();
		System.setProperty( "playwright.cli.dir", getDriverDir().toString() );
		return driverEnvironment( node );
	}

	/**
	 * Create a Playwright instance wired to this home.
	 * The instance is not thread safe: use it only from the thread that created it.
	 *
	 * @return A new Playwright instance
	 */
	public Playwright createPlaywright() {
		Map<String, String> env = prepare();
		return Playwright.create( new Playwright.CreateOptions().setEnv( env ) );
	}

	/**
	 * Build a process that runs the Playwright CLI ({@code node cli.js <args>}) with this home's environment.
	 *
	 * @param args The CLI arguments, e.g. {@code install chromium}
	 *
	 * @return The process builder, not started
	 */
	public ProcessBuilder cliProcess( List<String> args ) {
		Map<String, String>	env		= prepare();
		List<String>		command	= new ArrayList<>();
		command.add( env.get( "PLAYWRIGHT_NODEJS_PATH" ) );
		command.add( getDriverDir().resolve( "package" ).resolve( "cli.js" ).toString() );
		command.addAll( args );
		ProcessBuilder builder = new ProcessBuilder( command );
		// PW_LANG_NAME is deliberately not set: Playwright would then print Java/Maven commands ("mvn exec:java ...") in
		// its help and hints. The driver used by the Java API gets it from Playwright Java itself, and codegen passes --target.
		builder.environment().putAll( env );
		builder.environment().putIfAbsent( "PLAYWRIGHT_DOWNLOAD_CONNECTION_TIMEOUT", "120000" );
		return builder;
	}

	/**
	 * Run the Playwright CLI with the console attached and wait for it to finish.
	 *
	 * @param args The CLI arguments
	 *
	 * @return The process exit code
	 */
	public int runCli( List<String> args ) {
		try {
			Process process = cliProcess( args ).inheritIO().start();
			return process.waitFor();
		} catch ( IOException e ) {
			throw PlaywrightErrors.of( PlaywrightErrors.NOT_INSTALLED, "Failed to run the Playwright CLI: " + e.getMessage(), "Run [bxPlaywright doctor].", e );
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			return 130;
		}
	}

	/**
	 * --------------------------------------------------------------------------
	 * Maintenance
	 * --------------------------------------------------------------------------
	 */

	/**
	 * List the browsers installed in {@link #getBrowsersPath()}, e.g. {@code chromium-1234}.
	 *
	 * @return The installed browser folder names, sorted
	 */
	public List<String> installedBrowsers() {
		if ( !Files.isDirectory( browsersPath ) ) {
			return Collections.emptyList();
		}
		try ( Stream<Path> entries = Files.list( browsersPath ) ) {
			return entries
			    .filter( Files::isDirectory )
			    .map( path -> path.getFileName().toString() )
			    .filter( name -> !name.startsWith( "." ) )
			    .sorted()
			    .toList();
		} catch ( IOException e ) {
			return Collections.emptyList();
		}
	}

	/**
	 * Delete extracted drivers (all versions) and downloaded Node.js runtimes. Browsers are kept.
	 */
	public void clean() {
		deleteQuietly( home.resolve( "driver" ) );
		deleteQuietly( getNodeDir() );
	}

	/**
	 * --------------------------------------------------------------------------
	 * Helpers
	 * --------------------------------------------------------------------------
	 */

	/**
	 * Find a resource through the Playwright class loader.
	 *
	 * @param path The resource path
	 *
	 * @return The resource URL, or null when not found
	 */
	private static URL resource( String path ) {
		return Playwright.class.getClassLoader().getResource( path );
	}

	/**
	 * Copy a resource folder from the jars (or the file system during development) into a directory.
	 * <p>
	 * Jar resources are read through a private, uncached {@link JarFile}: a shared zip {@code FileSystem} is never
	 * created or closed here, so concurrent extractions (and other code reading the same jar) cannot close it under
	 * each other.
	 *
	 * @param resourcePath The resource folder path
	 * @param destination  The directory to copy into
	 *
	 * @throws IOException when the resource is missing or a file cannot be copied
	 */
	private static void extractResource( String resourcePath, Path destination ) throws IOException {
		URL url = resource( resourcePath );
		if ( url == null ) {
			throw new IOException( "Resource not found in the Playwright jars: " + resourcePath );
		}
		if ( "jar".equals( url.getProtocol() ) ) {
			extractFromJar( url, destination );
			return;
		}
		Path source;
		try {
			source = Paths.get( url.toURI() );
		} catch ( URISyntaxException e ) {
			throw new IOException( "Invalid resource URL: " + url, e );
		}
		try ( Stream<Path> paths = Files.walk( source ) ) {
			for ( Path from : paths.toList() ) {
				Path to = destination.resolve( source.relativize( from ).toString() );
				if ( Files.isDirectory( from ) ) {
					Files.createDirectories( to );
				} else {
					copyFile( Files.newInputStream( from ), to );
				}
			}
		}
	}

	/**
	 * Copy the entries under a jar folder URL ({@code jar:file:...!/folder}) into a directory.
	 *
	 * @param url         The jar URL of the folder
	 * @param destination The directory to copy into
	 *
	 * @throws IOException when the jar cannot be read or a file cannot be written
	 */
	private static void extractFromJar( URL url, Path destination ) throws IOException {
		JarURLConnection connection = ( JarURLConnection ) url.openConnection();
		// A private JarFile: the cached one is shared by the class loader and must not be closed
		connection.setUseCaches( false );
		String	entryName	= connection.getEntryName();
		String	prefix		= entryName.endsWith( "/" ) ? entryName : entryName + "/";
		Path	root		= destination.toAbsolutePath().normalize();
		try ( JarFile jar = connection.getJarFile() ) {
			Enumeration<JarEntry> entries = jar.entries();
			while ( entries.hasMoreElements() ) {
				JarEntry entry = entries.nextElement();
				if ( !entry.getName().startsWith( prefix ) || entry.getName().length() == prefix.length() ) {
					continue;
				}
				Path to = root.resolve( entry.getName().substring( prefix.length() ) ).normalize();
				if ( !to.startsWith( root ) ) {
					throw new IOException( "Refusing to extract an entry outside the destination: " + entry.getName() );
				}
				if ( entry.isDirectory() ) {
					Files.createDirectories( to );
				} else {
					copyFile( jar.getInputStream( entry ), to );
				}
			}
		}
	}

	/**
	 * Write a stream to a file, creating its folders, and mark it executable when it looks like one.
	 *
	 * @param input The content, closed when done
	 * @param to    The file to write
	 *
	 * @throws IOException when the file cannot be written
	 */
	private static void copyFile( InputStream input, Path to ) throws IOException {
		Files.createDirectories( to.getParent() );
		try ( InputStream in = input ) {
			Files.copy( in, to, StandardCopyOption.REPLACE_EXISTING );
		}
		if ( isExecutable( to ) ) {
			to.toFile().setExecutable( true, true );
		}
	}

	/**
	 * Guess if an extracted file should be executable: {@code .sh}, {@code .exe} or no extension.
	 *
	 * @param file The file
	 *
	 * @return True when the file should be marked executable
	 */
	private static boolean isExecutable( Path file ) {
		String name = file.getFileName().toString();
		return name.endsWith( ".sh" ) || name.endsWith( ".exe" ) || !name.contains( "." );
	}

	/**
	 * Delete a file or directory tree, doing nothing when it does not exist.
	 *
	 * @param path The file or directory to delete
	 *
	 * @throws IOException when a file cannot be deleted
	 */
	static void deleteRecursively( Path path ) throws IOException {
		if ( !Files.exists( path ) ) {
			return;
		}
		try ( Stream<Path> paths = Files.walk( path ) ) {
			for ( Path entry : paths.sorted( Comparator.reverseOrder() ).toList() ) {
				Files.deleteIfExists( entry );
			}
		}
	}

	/**
	 * Delete a file or directory tree, ignoring errors.
	 *
	 * @param path The file or directory to delete
	 */
	static void deleteQuietly( Path path ) {
		try {
			deleteRecursively( path );
		} catch ( IOException e ) {
			// Best effort cleanup of temporary or disposable folders
		}
	}

	/**
	 * Check if a string is null or only whitespace.
	 *
	 * @param value The string to check
	 *
	 * @return True when null or blank
	 */
	private static boolean isBlank( String value ) {
		return value == null || value.isBlank();
	}

}
