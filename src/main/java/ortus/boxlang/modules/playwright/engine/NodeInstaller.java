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
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Downloads and installs the official Node.js runtime for the current platform into the
 * playwright home. Used by the small distribution (bx-playwright), which does not bundle Node.js.
 * <p>
 * The archive is verified against the {@code SHASUMS256.txt} published with every Node.js release,
 * then extracted with the {@code tar} command (available on Linux, macOS and Windows 10+).
 */
public class NodeInstaller {

	/**
	 * The official Node.js distribution site. It can be replaced with a mirror through the {@code nodeDownloadURL} setting.
	 */
	public static final String		DEFAULT_DOWNLOAD_URL	= "https://nodejs.org/dist";

	private static final Duration	CONNECT_TIMEOUT			= Duration.ofSeconds( 30 );
	private static final Duration	DOWNLOAD_TIMEOUT		= Duration.ofMinutes( 10 );
	private static final long		EXTRACT_TIMEOUT_MINUTES	= 5;

	private final PlaywrightHome	home;
	private final String			downloadURL;
	private final Consumer<String>	logger;
	private final HttpClient		httpClient;

	/**
	 * Create an installer.
	 *
	 * @param home        The playwright home
	 * @param downloadURL The Node.js distribution base URL, or null/empty for {@link #DEFAULT_DOWNLOAD_URL}
	 * @param logger      Receives progress messages, may be null
	 */
	public NodeInstaller( PlaywrightHome home, String downloadURL, Consumer<String> logger ) {
		this.home			= Objects.requireNonNull( home, "home must not be null" );
		this.downloadURL	= downloadURL == null || downloadURL.isBlank() ? DEFAULT_DOWNLOAD_URL : stripTrailingSlash( downloadURL );
		this.logger			= logger == null ? message -> {
							} : logger;
		this.httpClient		= HttpClient.newBuilder()
		    .connectTimeout( CONNECT_TIMEOUT )
		    // Honor the JVM proxy settings (https.proxyHost, etc.)
		    .proxy( ProxySelector.getDefault() )
		    .followRedirects( HttpClient.Redirect.NORMAL )
		    .build();
	}

	/**
	 * The download URL of the Node.js archive.
	 *
	 * @return The URL of the Node.js archive for this home's platform and Node.js version
	 */
	public String archiveURL() {
		return releaseURL() + "/" + home.getPlatform().nodeArchiveName( home.getNodeVersion() );
	}

	/**
	 * The download URL of the checksums file used to verify the Node.js archive.
	 *
	 * @return The URL of the SHASUMS256.txt file of the Node.js release
	 */
	public String checksumsURL() {
		return releaseURL() + "/SHASUMS256.txt";
	}

	/**
	 * Download, verify and extract Node.js into the playwright home.
	 * <p>
	 * Installs are serialized per Node.js version (a JVM-wide lock plus a file lock in the node folder, so other
	 * processes wait too), and every call uses its own download file and staging folder. The archive is extracted into
	 * the staging folder, the node executable is checked (it must exist and answer {@code --version}), and only then is
	 * the runtime moved into place, so an interrupted or failed install never leaves a partial runtime that looks
	 * installed.
	 *
	 * @param force Reinstall even if the runtime is already there
	 *
	 * @return The node executable
	 */
	public Path install( boolean force ) {
		Path	executable	= home.getDownloadedNodeExecutable();
		Path	nodeDir		= home.getNodeDir();
		if ( Files.isRegularFile( executable ) && !force ) {
			logger.accept( "Node.js " + home.getNodeVersion() + " is already installed at " + executable );
			return executable;
		}
		try {
			Files.createDirectories( nodeDir );
			Path lockFile = nodeDir.resolve( ".install-" + home.getPlatform().nodeFolderName( home.getNodeVersion() ) + ".lock" );
			return InstallLock.with( lockFile, () -> {
				// Another thread or process may have installed it while this one waited for the lock
				if ( Files.isRegularFile( executable ) && !force ) {
					logger.accept( "Node.js " + home.getNodeVersion() + " is already installed at " + executable );
					return executable;
				}
				return downloadAndInstall( executable );
			} );
		} catch ( IOException e ) {
			throw PlaywrightErrors.of(
			    PlaywrightErrors.NODE_INSTALL_FAILED,
			    "Failed to install Node.js: " + e.getMessage(),
			    "Check your network or proxy, set the 'nodeDownloadURL' setting to a mirror, or set 'nodePath' to an existing Node.js "
			        + PlaywrightHome.MIN_NODE_MAJOR + "+ executable.",
			    e
			);
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw PlaywrightErrors.of( PlaywrightErrors.NODE_INSTALL_FAILED, "Node.js installation was interrupted.", "Run the install again." );
		}
	}

	/**
	 * Download, verify, extract into a private staging folder, check the executable and move the runtime into place.
	 * Called while holding the install lock.
	 *
	 * @param executable The final node executable path
	 *
	 * @return The node executable
	 *
	 * @throws IOException          when downloading, extracting or moving fails
	 * @throws InterruptedException when interrupted
	 */
	private Path downloadAndInstall( Path executable ) throws IOException, InterruptedException {
		Platform	platform	= home.getPlatform();
		String		archiveName	= platform.nodeArchiveName( home.getNodeVersion() );
		String		folderName	= platform.nodeFolderName( home.getNodeVersion() );
		Path		nodeDir		= home.getNodeDir();
		String		id			= UUID.randomUUID().toString();
		Path		archive		= nodeDir.resolve( archiveName + "." + id + ".download" );
		Path		staging		= nodeDir.resolve( ".tmp-" + id );
		try {
			logger.accept( "Downloading Node.js " + home.getNodeVersion() + " (" + platform.getNodeDistribution() + ") from " + archiveURL() );
			download( archiveURL(), archive );

			String	expected	= expectedChecksum( fetchText( checksumsURL() ), archiveName );
			String	actual		= sha256( archive );
			if ( !expected.equalsIgnoreCase( actual ) ) {
				throw PlaywrightErrors.of(
				    PlaywrightErrors.NODE_INSTALL_FAILED,
				    "Checksum mismatch for " + archiveName + ": expected " + expected + " but got " + actual,
				    "Retry [bxPlaywright install-node --force]. If you use a mirror (nodeDownloadURL), make sure it serves the official files."
				);
			}

			Files.createDirectories( staging );
			extract( archive, staging );
			Path	extracted			= staging.resolve( folderName );
			Path	stagedExecutable	= extracted.resolve( platform.getNodeExecutable() );
			if ( !Files.isRegularFile( stagedExecutable ) ) {
				throw PlaywrightErrors.of(
				    PlaywrightErrors.NODE_INSTALL_FAILED,
				    "Node.js was extracted but the executable was not found at " + folderName + "/" + platform.getNodeExecutable(),
				    "Run [bxPlaywright install-node --force], or set the 'nodePath' setting to an existing Node.js executable."
				);
			}
			stagedExecutable.toFile().setExecutable( true, true );
			if ( PlaywrightHome.probeNodeVersion( stagedExecutable.toString() ) == null ) {
				throw PlaywrightErrors.of(
				    PlaywrightErrors.NODE_INSTALL_FAILED,
				    "The downloaded Node.js does not run: [" + stagedExecutable.getFileName() + " --version] failed.",
				    "Run [bxPlaywright install-node --force]. If it keeps failing, set the 'nodePath' setting to an existing Node.js "
				        + PlaywrightHome.MIN_NODE_MAJOR + "+ executable."
				);
			}
			PlaywrightHome.replaceWith( extracted, nodeDir.resolve( folderName ) );
			logger.accept( "Node.js installed at " + executable );
			return executable;
		} finally {
			Files.deleteIfExists( archive );
			PlaywrightHome.deleteQuietly( staging );
		}
	}

	/**
	 * Find the checksum of a file in the contents of a SHASUMS256.txt file.
	 *
	 * @param checksums The SHASUMS256.txt contents
	 * @param fileName  The archive file name
	 *
	 * @return The hex checksum
	 */
	public static String expectedChecksum( String checksums, String fileName ) {
		for ( String line : checksums.split( "\\R" ) ) {
			String[] parts = line.trim().split( "\\s+" );
			if ( parts.length == 2 && parts[ 1 ].equals( fileName ) ) {
				return parts[ 0 ];
			}
		}
		throw PlaywrightErrors.of(
		    PlaywrightErrors.NODE_INSTALL_FAILED,
		    "No checksum published for " + fileName,
		    "The Node.js version or platform may not exist on the download site. Check the 'nodeDownloadURL' setting."
		);
	}

	/**
	 * Compute the SHA-256 of a file.
	 *
	 * @param file The file
	 *
	 * @return The lowercase hex digest
	 *
	 * @throws IOException if the file cannot be read
	 */
	public static String sha256( Path file ) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance( "SHA-256" );
			try ( InputStream input = Files.newInputStream( file ) ) {
				byte[]	buffer	= new byte[ 64 * 1024 ];
				int		read;
				while ( ( read = input.read( buffer ) ) != -1 ) {
					digest.update( buffer, 0, read );
				}
			}
			return HexFormat.of().formatHex( digest.digest() );
		} catch ( NoSuchAlgorithmException e ) {
			throw new IllegalStateException( "SHA-256 is not available", e );
		}
	}

	/**
	 * The base URL of the configured Node.js release.
	 *
	 * @return The download URL followed by the version folder, e.g. {@code .../v22.0.0}
	 */
	private String releaseURL() {
		return downloadURL + "/v" + home.getNodeVersion();
	}

	/**
	 * Download a URL into a file.
	 *
	 * @param url    The URL to download
	 * @param target The file to write
	 *
	 * @throws IOException          when the request fails or the status is not 200
	 * @throws InterruptedException when interrupted while waiting for the response
	 */
	private void download( String url, Path target ) throws IOException, InterruptedException {
		HttpRequest			request		= HttpRequest.newBuilder( URI.create( url ) ).timeout( DOWNLOAD_TIMEOUT ).GET().build();
		HttpResponse<Path>	response	= httpClient.send( request, HttpResponse.BodyHandlers.ofFile( target ) );
		if ( response.statusCode() != 200 ) {
			throw new IOException( "HTTP " + response.statusCode() + " downloading " + url );
		}
	}

	/**
	 * Download a URL as UTF-8 text.
	 *
	 * @param url The URL to download
	 *
	 * @return The response body
	 *
	 * @throws IOException          when the request fails or the status is not 200
	 * @throws InterruptedException when interrupted while waiting for the response
	 */
	private String fetchText( String url ) throws IOException, InterruptedException {
		HttpRequest				request		= HttpRequest.newBuilder( URI.create( url ) ).timeout( CONNECT_TIMEOUT ).GET().build();
		HttpResponse<String>	response	= httpClient.send( request, HttpResponse.BodyHandlers.ofString( StandardCharsets.UTF_8 ) );
		if ( response.statusCode() != 200 ) {
			throw new IOException( "HTTP " + response.statusCode() + " downloading " + url );
		}
		return response.body();
	}

	/**
	 * Extract a zip or tar.gz archive with the system {@code tar} command.
	 *
	 * @param archive     The archive file
	 * @param destination The directory to extract into
	 *
	 * @throws IOException          when tar fails or times out
	 * @throws InterruptedException when interrupted while waiting for tar
	 */
	private static void extract( Path archive, Path destination ) throws IOException, InterruptedException {
		// The archive keeps its '.download' suffix, so tell tar the compression explicitly
		boolean			isZip	= archive.getFileName().toString().contains( ".zip" );
		List<String>	command	= isZip
		    ? List.of( "tar", "-xf", archive.toString(), "-C", destination.toString() )
		    : List.of( "tar", "-xzf", archive.toString(), "-C", destination.toString() );
		Process			process	= new ProcessBuilder( command ).redirectErrorStream( true ).start();
		String			output	= new String( process.getInputStream().readAllBytes(), StandardCharsets.UTF_8 );
		if ( !process.waitFor( EXTRACT_TIMEOUT_MINUTES, TimeUnit.MINUTES ) ) {
			process.destroyForcibly();
			throw new IOException( "Timed out extracting " + archive );
		}
		if ( process.exitValue() != 0 ) {
			throw new IOException( "tar failed (" + process.exitValue() + "): " + output.trim() );
		}
	}

	/**
	 * Remove one trailing slash from a string.
	 *
	 * @param value The string
	 *
	 * @return The string without its trailing slash
	 */
	private static String stripTrailingSlash( String value ) {
		return value.endsWith( "/" ) ? value.substring( 0, value.length() - 1 ) : value;
	}

}
