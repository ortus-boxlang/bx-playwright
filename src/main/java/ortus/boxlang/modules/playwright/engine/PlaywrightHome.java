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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
		    Platform.current()
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
	 * @return The directory holding the extracted driver for this Playwright version
	 */
	public Path getDriverDir() {
		return home.resolve( "driver" ).resolve( playwrightVersion );
	}

	/**
	 * @return The directory where Node.js runtimes are downloaded
	 */
	public Path getNodeDir() {
		return home.resolve( "node" );
	}

	/**
	 * @return The browsers directory
	 */
	public Path getBrowsersPath() {
		return browsersPath;
	}

	/**
	 * @return The Playwright version
	 */
	public String getPlaywrightVersion() {
		return playwrightVersion;
	}

	/**
	 * @return The Node.js version downloaded when no runtime is available
	 */
	public String getNodeVersion() {
		return nodeVersion;
	}

	/**
	 * @return The platform
	 */
	public Platform getPlatform() {
		return platform;
	}

	/**
	 * @return The Node.js executable of the downloaded runtime (it may not exist yet)
	 */
	public Path getDownloadedNodeExecutable() {
		return getNodeDir().resolve( platform.nodeFolderName( nodeVersion ) ).resolve( platform.getNodeExecutable() );
	}

	/**
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
	 * @return True if the driver has been extracted into {@link #getDriverDir()}
	 */
	public boolean isDriverInstalled() {
		return Files.isRegularFile( getDriverDir().resolve( "package" ).resolve( "cli.js" ) );
	}

	/**
	 * Extract the driver from the jars into {@link #getDriverDir()}. When the jars bundle Node.js
	 * (full distribution), the runtime for this platform is extracted as well.
	 * <p>
	 * Extraction happens in a temporary sibling folder that is then moved into place, so a
	 * half-extracted driver is never used.
	 *
	 * @param force Re-extract even if the driver is already installed
	 *
	 * @return The driver directory
	 */
	public Path installDriver( boolean force ) {
		Path target = getDriverDir();
		if ( isDriverInstalled() && !force ) {
			return target;
		}
		Path staging = target.resolveSibling( target.getFileName() + ".tmp-" + UUID.randomUUID() );
		try {
			Files.createDirectories( staging );
			extractResource( DRIVER_RESOURCE + "package", staging.resolve( "package" ) );
			if ( hasBundledNode() ) {
				extractResource( DRIVER_RESOURCE + platform.getDriverFolder(), staging );
			}
			if ( Files.exists( target ) ) {
				deleteRecursively( target );
			}
			Files.move( staging, target, StandardCopyOption.ATOMIC_MOVE );
			return target;
		} catch ( IOException | URISyntaxException e ) {
			throw PlaywrightErrors.of(
			    PlaywrightErrors.NOT_INSTALLED,
			    "Failed to extract the Playwright driver into [" + target + "]: " + e.getMessage(),
			    "Check that the directory is writable, or change the 'home' setting of the playwright module.",
			    e
			);
		} finally {
			deleteQuietly( staging );
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
	 * @return The node executable, or empty if none is available
	 */
	public Optional<NodeRuntime> resolveNode() {
		if ( explicitNodePath != null ) {
			return Optional.of( new NodeRuntime( explicitNodePath, NodeRuntime.Source.EXPLICIT, probeNodeVersion( explicitNodePath.toString() ) ) );
		}
		if ( Files.isRegularFile( getBundledNodeExecutable() ) ) {
			return Optional.of( new NodeRuntime( getBundledNodeExecutable(), NodeRuntime.Source.BUNDLED, null ) );
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
		try {
			Process	process	= new ProcessBuilder( executable, "--version" ).redirectErrorStream( true ).start();
			String	output;
			try ( BufferedReader reader = new BufferedReader( new InputStreamReader( process.getInputStream(), StandardCharsets.UTF_8 ) ) ) {
				output = reader.readLine();
			}
			if ( !process.waitFor( NODE_PROBE_SECONDS, TimeUnit.SECONDS ) ) {
				process.destroyForcibly();
				return null;
			}
			if ( process.exitValue() != 0 || output == null ) {
				return null;
			}
			Matcher matcher = NODE_VERSION.matcher( output.trim() );
			return matcher.find() ? matcher.group( 1 ) + "." + matcher.group( 2 ) + "." + matcher.group( 3 ) : null;
		} catch ( IOException e ) {
			return null;
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			return null;
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
		builder.environment().putAll( env );
		builder.environment().put( "PW_LANG_NAME", "java" );
		builder.environment().put( "PW_LANG_NAME_VERSION", String.valueOf( Runtime.version().feature() ) );
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

	private static URL resource( String path ) {
		return Playwright.class.getClassLoader().getResource( path );
	}

	/**
	 * Copy a resource folder from the jars (or the file system during development) into a directory.
	 */
	private static void extractResource( String resourcePath, Path destination ) throws IOException, URISyntaxException {
		URL url = resource( resourcePath );
		if ( url == null ) {
			throw new IOException( "Resource not found in the Playwright jars: " + resourcePath );
		}
		URI			uri			= url.toURI();
		FileSystem	fileSystem	= null;
		try {
			if ( "jar".equals( uri.getScheme() ) ) {
				try {
					fileSystem = FileSystems.newFileSystem( uri, Collections.emptyMap() );
				} catch ( FileSystemAlreadyExistsException e ) {
					fileSystem = null;
				}
			}
			Path source = Paths.get( uri );
			try ( Stream<Path> paths = Files.walk( source ) ) {
				for ( Path from : paths.toList() ) {
					Path to = destination.resolve( source.relativize( from ).toString() );
					if ( Files.isDirectory( from ) ) {
						Files.createDirectories( to );
					} else {
						Files.createDirectories( to.getParent() );
						Files.copy( from, to, StandardCopyOption.REPLACE_EXISTING );
						if ( isExecutable( to ) ) {
							to.toFile().setExecutable( true, true );
						}
					}
				}
			}
		} finally {
			if ( fileSystem != null ) {
				fileSystem.close();
			}
		}
	}

	private static boolean isExecutable( Path file ) {
		String name = file.getFileName().toString();
		return name.endsWith( ".sh" ) || name.endsWith( ".exe" ) || !name.contains( "." );
	}

	private static void deleteRecursively( Path path ) throws IOException {
		if ( !Files.exists( path ) ) {
			return;
		}
		try ( Stream<Path> paths = Files.walk( path ) ) {
			for ( Path entry : paths.sorted( Comparator.reverseOrder() ).toList() ) {
				Files.deleteIfExists( entry );
			}
		}
	}

	private static void deleteQuietly( Path path ) {
		try {
			deleteRecursively( path );
		} catch ( IOException e ) {
			// Best effort cleanup of temporary or disposable folders
		}
	}

	private static boolean isBlank( String value ) {
		return value == null || value.isBlank();
	}

}
