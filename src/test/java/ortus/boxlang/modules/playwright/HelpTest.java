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

public class HelpTest extends BaseIntegrationTest {

	/**
	 * help() describes the public methods from their docblocks, with descriptions and arguments, and leaves out private methods and init().
	 */
	@DisplayName( "help() describes the public API from the docblocks" )
	@Test
	public void testHelp() {
		// @formatter:off
		Object value = run( """
			all    = playwright().help()
			visit  = all.visit
			browse = playwright().help( "browse" ).browse
			result = all.keyExists( "screenshot" ) & "|" & all.keyExists( "tryAction" ) & "|" & all.keyExists( "init" )
				& "|" & ( visit.description contains "Open a page" ) & "|" & visit.arguments[ 1 ].name & "|" & visit.arguments[ 1 ].required
				& "|" & ( browse.arguments[ 1 ].description contains "page per declared argument" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "true|false|false|true|url|true|true" );
	}

	/**
	 * help() for an unknown method throws Playwright.InvalidOption whose detail lists the available methods.
	 */
	@DisplayName( "help() for an unknown method lists the available ones" )
	@Test
	public void testUnknown() {
		// @formatter:off
		Object value = run( """
			try {
				playwright().help( "nope" )
			} catch ( "Playwright.InvalidOption" e ) {
				result = e.detail contains "visit"
			}
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( true );
	}

	/**
	 * aiToolDefinitions() returns the browser tool definitions without bx-ai, while aiTools() throws Playwright.NotInstalled mentioning bx-ai.
	 */
	@DisplayName( "aiTools() explains that bx-ai is needed; aiToolDefinitions() works without it" )
	@Test
	public void testAiTools() {
		// @formatter:off
		Object value = run( """
			definitions = playwright().aiToolDefinitions()
			try {
				playwright().aiTools()
				missing = "installed"
			} catch ( "Playwright.NotInstalled" e ) {
				missing = e.detail contains "bx-ai"
			}
			result = definitions.map( ( d ) -> d.name ).toList() & "|" & definitions[ 4 ].arguments.keyExists( "value" ) & "|" & missing
		""" );
		// @formatter:on
		assertThat( value.toString() ).startsWith( "browser_visit,browser_snapshot,browser_click,browser_fill,browser_select" );
		assertThat( value.toString() ).endsWith( "|true|true" );
	}

}
