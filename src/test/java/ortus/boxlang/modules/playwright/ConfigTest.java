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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ConfigTest extends BaseIntegrationTest {

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

	@DisplayName( "It builds launch and context options" )
	@Test
	public void testOptions() {
		// @formatter:off
		Object value = run( """
			cfg     = new models.Config@playwright( environment = {} )
			config  = cfg.resolve( [ "chrome", "dark" ], { slowMo : 100, timezone : "UTC" } )
			launch  = cfg.launchOptions( config )
			context = cfg.contextOptions( config, { viewport : { width : 390, height : 844 }, isMobile : true } )
			result  = launch.channel & "|" & launch.slowMo & "|" & context.colorScheme & "|" & context.timezoneId & "|" & context.viewport.width & "|" & context.isMobile
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "chrome|100|dark|UTC|390|true" );
	}

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
		// Windows paths use backslashes and a drive letter
		assertThat( value.toString().replace( '\\', '/' ) ).endsWith( ".boxlang/playwright|/tmp/pw/browsers" );
	}

}
