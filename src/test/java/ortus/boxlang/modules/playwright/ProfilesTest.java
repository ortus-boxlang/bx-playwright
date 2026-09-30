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

public class ProfilesTest extends BaseIntegrationTest {

	/**
	 * A built-in profile resolves by following its extends chain, and the extends key is removed from the result.
	 */
	@DisplayName( "Built-in profiles resolve, following extends" )
	@Test
	public void testExtends() {
		// @formatter:off
		Object value = run( """
			profile = new models.Profiles@playwright().resolve( "iphone" )
			result  = profile.browser & "|" & profile.device & "|" & profile.keyExists( "extends" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "webkit|iPhone 15|false" );
	}

	/**
	 * Several profiles merge left to right, with nested structs merged deeply.
	 */
	@DisplayName( "Profiles merge left to right, deeply" )
	@Test
	public void testMerge() {
		// @formatter:off
		Object value = run( """
			profile = new models.Profiles@playwright().resolve( [ "ci", "dark", "debug" ] )
			result  = profile.colorScheme & "|" & profile.headless & "|" & profile.artifacts.screenshot & "|" & profile.artifacts.trace
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "dark|false|on|on" );
	}

	/**
	 * User profiles can extend built-in profiles and replace a built-in profile with the same name.
	 */
	@DisplayName( "User profiles can extend built-in ones and replace them" )
	@Test
	public void testUserProfiles() {
		// @formatter:off
		Object value = run( """
			user    = { staging : { extends : "desktop", baseURL : "https://staging.test" }, dark : { colorScheme : "light" } }
			profile = new models.Profiles@playwright().resolve( "staging,dark", user )
			result  = profile.viewport.width & "|" & profile.baseURL & "|" & profile.colorScheme
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "1920|https://staging.test|light" );
	}

	/**
	 * Resolving an unknown profile throws Playwright.InvalidProfile listing the available profiles.
	 */
	@DisplayName( "Unknown profiles fail with the list of profiles" )
	@Test
	public void testUnknownProfile() {
		// @formatter:off
		Object value = run( """
			try {
				new models.Profiles@playwright().resolve( "nope" )
				result = "no error"
			} catch ( "Playwright.InvalidProfile" e ) {
				result = e.message & "|" & e.detail
			}
		""" );
		// @formatter:on
		assertThat( value.toString() ).contains( "Unknown profile [nope]" );
		assertThat( value.toString() ).contains( "mobile" );
	}

	/**
	 * A profile whose extends chain loops back to itself throws Playwright.InvalidProfile.
	 */
	@DisplayName( "Extends loops are detected" )
	@Test
	public void testLoop() {
		// @formatter:off
		Object value = run( """
			try {
				new models.Profiles@playwright().resolve( "a", { a : { extends : "b" }, b : { extends : "a" } } )
				result = "no error"
			} catch ( "Playwright.InvalidProfile" e ) {
				result = e.message
			}
		""" );
		// @formatter:on
		assertThat( value.toString() ).contains( "extends itself" );
	}

}
