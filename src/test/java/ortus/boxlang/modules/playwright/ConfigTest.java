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
package ortus.boxlang.modules.playwright;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ConfigTest extends BaseIntegrationTest {

	/**
	 * Without arguments or environment the config resolves the default profile, chromium, headless mode, a 5000ms assertion timeout and no home key.
	 */
	@DisplayName( "Without arguments the default profile and module settings apply" )
	@Test
	public void testDefaults() {
		// @formatter:off
		Object value = run( """
			config = new models.Config@playwright( environment = {} ).resolve()
			result = config.profiles.toList() & "|" & config.browser & "|" & config.headless & "|" & config.timeouts.assertion & "|" & config.keyExists( "home" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "default|chromium|true|5000|false" );
	}

	/**
	 * Environment variables override the profile settings, and profiles and options passed to resolve() override the environment.
	 */
	@DisplayName( "Environment overrides win over profiles, call options win over everything" )
	@Test
	public void testResolutionOrder() {
		// @formatter:off
		Object value = run( """
			env    = { BX_PLAYWRIGHT_PROFILE : "mobile", BX_PLAYWRIGHT_HEADLESS : "false", BX_PLAYWRIGHT_BASEURL : "http://env.test" }
			cfg    = new models.Config@playwright( environment = env )
			a      = cfg.resolve()
			b      = cfg.resolve( "firefox", { baseURL : "http://call.test" } )
			result = a.profiles.toList() & "|" & a.browser & "|" & a.headless & "|" & a.baseURL & "|" & b.browser & "|" & b.baseURL
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "mobile|webkit|false|http://env.test|firefox|http://call.test" );
	}

	/**
	 * launchOptions() and contextOptions() build the launch and context options from the resolved profiles, call options and extra context options.
	 */
	@DisplayName( "It builds launch and context options" )
	@Test
	public void testOptions() {
		// @formatter:off
		Object value = run( """
			cfg     = new models.Config@playwright( environment = {} )
			config  = cfg.resolve( [ "chrome", "dark" ], { slowMo : 100, timezone : "UTC", device : "Pixel 7" } )
			launch  = cfg.launchOptions( config )
			context = cfg.contextOptions( config, { viewport : { width : 390, height : 844 }, isMobile : true } )
			result  = launch.channel & "|" & launch.slowMo & "|" & context.colorScheme & "|" & context.timezoneId & "|" & context.viewport.width & "|" & context.isMobile
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "chrome|100|dark|UTC|390|true" );
	}

	/**
	 * An empty home setting defaults to .boxlang/playwright under the user home, and a configured home is used for the browsers path.
	 */
	@DisplayName( "The home defaults under the user home and honors settings" )
	@Test
	public void testHome() {
		// @formatter:off
		Object value = run( """
			a      = new models.Config@playwright( settings = { home : "", nodeVersion : "24.21.0", profiles : {} }, environment = {} ).homePath()
			b      = new models.Config@playwright( settings = { home : "/tmp/pw", nodeVersion : "24.21.0", profiles : {} }, environment = {} ).home()
			result = a & "|" & b.getBrowsersPath().toString()
		""" );
		// @formatter:on
		// Windows paths use backslashes and a drive letter, so compare each path's tail
		String[]	paths	= value.toString().replace( '\\', '/' ).split( "\\|" );
		assertThat( paths[ 0 ] ).endsWith( ".boxlang/playwright" );
		assertThat( paths[ 1 ] ).endsWith( "/tmp/pw/browsers" );
	}

	/**
	 * The default profile adds nothing, so the browser, headless and viewport module settings apply as configured.
	 */
	@DisplayName( "The default profile keeps the module settings" )
	@Test
	public void testDefaultProfileKeepsSettings() {
		// @formatter:off
		Object value = run( """
			settings = { browser : "firefox", headless : false, viewport : { width : 800, height : 600 }, defaultProfile : "default", profiles : {} }
			config   = new models.Config@playwright( settings = settings, environment = {} ).resolve()
			result   = config.browser & "|" & config.headless & "|" & config.viewport.width & "x" & config.viewport.height
				& "|" & new models.Profiles@playwright().resolve( "default" ).isEmpty()
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "firefox|false|800x600|true" );
	}

	/**
	 * playwright( struct, struct ) merges the second struct over the first one.
	 */
	@DisplayName( "playwright() merges two option structs" )
	@Test
	public void testBifMergesTwoStructs() {
		// @formatter:off
		Object value = run( """
			config = playwright( { locale : "de-DE", timezone : "America/Chicago" }, { timezone : "UTC" } ).getConfig()
			result = config.locale & "|" & config.timezone
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "de-DE|UTC" );
	}

	/**
	 * Devices and viewports follow "last wins": a viewport set after a device replaces the device screen (keeping the rest of the device),
	 * and a device set after a viewport (or in the module settings) replaces the viewport.
	 */
	@DisplayName( "Devices and viewports: the last one set wins" )
	@Test
	public void testDeviceAndViewportOrder() {
		// @formatter:off
		Object value = run( """
			cfg      = new models.Config@playwright( environment = {} )
			device   = { viewport : { width : 412, height : 839 }, userAgent : "Pixel UA", isMobile : true, hasTouch : true }
			screenOf = ( config ) => {
				var options = cfg.contextOptions( config, device )
				return options.viewport.width & "x" & options.viewport.height & "/" & options.isMobile & "/" & options.userAgent
			}
			settings = new models.Config@playwright( settings = { device : "Pixel 7", viewport : { width : 1280, height : 720 }, profiles : {} }, environment = {} )
			result   = [
				screenOf( cfg.resolve( [ "android", "desktop" ] ) ),
				screenOf( cfg.resolve( "android", { viewport : { width : 500, height : 500 } } ) ),
				screenOf( cfg.resolve( [ "desktop", "android" ] ) ),
				screenOf( cfg.resolve( "desktop", { device : "Pixel 7" } ) ),
				screenOf( cfg.resolve( "android" ) ),
				screenOf( settings.resolve() )
			].toList( "," )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo(
		    "1920x1080/true/Pixel UA,500x500/true/Pixel UA,412x839/true/Pixel UA,412x839/true/Pixel UA,412x839/true/Pixel UA,412x839/true/Pixel UA" );
	}

	/**
	 * Session names map to distinct files: simple names keep their file name, other characters are encoded instead of collapsed.
	 */
	@DisplayName( "Session names map to distinct files" )
	@Test
	public void testSessionPath() {
		// @formatter:off
		Object value = run( """
			cfg    = new models.Config@playwright( settings = { home : "/tmp/pw", profiles : {} }, environment = {} )
			result = [ "admin", "admin-test", "a b", "a-b", "a~20b" ].map( ( name ) => listLast( cfg.sessionPath( name ), "/\\" ) ).toList( "," )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "admin.json,admin-test.json,a~20b.json,a-b.json,a~7E20b.json" );
	}

	/**
	 * A relative artifacts directory resolves against the current directory, an absolute one is kept.
	 */
	@DisplayName( "A relative artifacts directory resolves against the current directory" )
	@Test
	public void testRelativeArtifactsPath() {
		// @formatter:off
		Object value = run( """
			cfg      = new models.Config@playwright( settings = { artifacts : { directory : "relartifacts" }, profiles : {} }, environment = {} )
			absolute = createObject( "java", "java.io.File" ).init( createObject( "java", "java.lang.System" ).getProperty( "java.io.tmpdir" ), "abs-artifacts" ).getAbsolutePath()
			result   = cfg.artifactsPath() & "|" & cfg.artifactsPath( { artifacts : { directory : absolute } } ) & "|" & absolute
		""" );
		// @formatter:on
		String[]	parts	= value.toString().split( "\\|" );
		assertThat( parts[ 0 ] ).isEqualTo( Path.of( System.getProperty( "user.dir" ), "relartifacts" ).toString() );
		assertThat( parts[ 1 ] ).isEqualTo( parts[ 2 ] );
	}

	/**
	 * A relative snapshots directory resolves against the current directory, an absolute one is kept.
	 */
	@DisplayName( "A relative snapshots directory resolves against the current directory" )
	@Test
	public void testRelativeSnapshotsPath() {
		// @formatter:off
		Object value = run( """
			cfg      = new models.Config@playwright( settings = { snapshots : { directory : "relsnaps" }, profiles : {} }, environment = {} )
			absolute = createObject( "java", "java.io.File" ).init( createObject( "java", "java.lang.System" ).getProperty( "java.io.tmpdir" ), "abs-snaps" ).getAbsolutePath()
			result   = cfg.snapshotsPath() & "|" & cfg.snapshotsPath( { snapshots : { directory : absolute } } ) & "|" & absolute
		""" );
		// @formatter:on
		String[]	parts	= value.toString().split( "\\|" );
		assertThat( parts[ 0 ] ).isEqualTo( Path.of( System.getProperty( "user.dir" ), "relsnaps" ).toString() );
		assertThat( parts[ 1 ] ).isEqualTo( parts[ 2 ] );
	}

}
