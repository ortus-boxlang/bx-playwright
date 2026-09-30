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
package ortus.boxlang.modules.playwright.e2e;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.playwright.BaseIntegrationTest;

public class CliE2ETest extends BaseIntegrationTest {

	private String cli;

	@BeforeEach
	public void prepare() {
		E2E.home();
		String home = E2E.homeDir().toString().replace( "\\", "/" );
		cli = "cli = new models.cli.Cli@playwright( new models.Config@playwright( settings = { home : \"" + home
		    + "\", nodeVersion : \"" + E2E.NODE_VERSION + "\", nodeDownloadURL : \"\", profiles : {}, defaultProfile : \"default\" }, environment = {} ) )\n";
	}

	@DisplayName( "doctor reports a healthy installation" )
	@Test
	public void testDoctor() {
		String output = capture( cli + "result = cli.run( [ \"doctor\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 0 );
		assertThat( output ).contains( "[ok]   node" );
		assertThat( output ).contains( "chromium" );
		String json = capture( cli + "result = cli.run( [ \"doctor\", \"--json\" ] )" );
		assertThat( json ).contains( "\"ok\":true" );
	}

	@DisplayName( "devices lists the Playwright device descriptors" )
	@Test
	public void testDevices() {
		String output = capture( cli + "result = cli.run( [ \"devices\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 0 );
		assertThat( output ).contains( "iPhone 15" );
		assertThat( output ).contains( "Pixel 7" );
	}

	@DisplayName( "install is idempotent when everything is present" )
	@Test
	public void testInstall() {
		String output = capture( cli + "result = cli.run( [ \"install\", \"chromium\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 0 );
		assertThat( output ).contains( "Ready." );
	}

}
