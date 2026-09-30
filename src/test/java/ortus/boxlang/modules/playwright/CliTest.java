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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CliTest extends BaseIntegrationTest {

	/**
	 * Running the CLI without a verb returns exit code 1 and prints the usage, including the install verb.
	 */
	@DisplayName( "No verb prints usage and fails" )
	@Test
	public void testNoVerb() {
		String output = capture( "result = new models.cli.Cli@playwright().run( [] )" );
		assertThat( variables.get( result ) ).isEqualTo( 1 );
		assertThat( output ).contains( "Usage: bxPlaywright <verb>" );
		assertThat( output ).contains( "install" );
	}

	/**
	 * An unknown verb returns exit code 1 and prints an "Unknown verb [fly]" message.
	 */
	@DisplayName( "Unknown verbs fail and print usage" )
	@Test
	public void testUnknownVerb() {
		String output = capture( "result = new models.cli.Cli@playwright().run( [ \"fly\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 1 );
		assertThat( output ).contains( "Unknown verb [fly]" );
	}

	/**
	 * Help for a single verb, "help --json" and "<verb> --help" print the verb usage or the JSON description of the verbs.
	 */
	@DisplayName( "Help for a verb and machine readable help" )
	@Test
	public void testHelp() {
		String verbHelp = capture( "result = new models.cli.Cli@playwright().run( [ \"help\", \"install\" ] )" );
		assertThat( verbHelp ).contains( "bxPlaywright install [browsers...]" );
		String json = capture( "result = new models.cli.Cli@playwright().run( [ \"help\", \"--json\" ] )" );
		assertThat( json ).contains( "\"doctor\"" );
		assertThat( json ).contains( "\"usage\"" );
		String dashHelp = capture( "result = new models.cli.Cli@playwright().run( [ \"install\", \"--help\" ] )" );
		assertThat( dashHelp ).contains( "bxPlaywright install [browsers...]" );
	}

	/**
	 * parseOptions() separates positionals from options, parses flags, quoted values and "--no-" negations.
	 */
	@DisplayName( "It parses BoxLang style options" )
	@Test
	public void testParseOptions() {
		// @formatter:off
		Object value = run( """
			options = new models.cli.Cli@playwright().parseOptions( [ "firefox", "--with-deps", '--device="iPhone 15"', "--no-headless", "webkit" ] )
			result  = options._positionals.toList() & "|" & options[ "with-deps" ] & "|" & options.device & "|" & options.headless
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "firefox,webkit|true|iPhone 15|false" );
	}

	/**
	 * "--version" returns exit code 0 and prints the Playwright version, and "version --json" prints it as JSON.
	 */
	@DisplayName( "Version prints versions and supports --json" )
	@Test
	public void testVersion() {
		String output = capture( "result = new models.cli.Cli@playwright().run( [ \"--version\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 0 );
		assertThat( output ).contains( "Playwright 1.63.0" );
		String json = capture( "result = new models.cli.Cli@playwright().run( [ \"version\", \"--json\" ] )" );
		assertThat( json ).contains( "\"playwright\":\"1.63.0\"" );
	}

	/**
	 * The profiles verb lists the built-in profiles and resolves a single profile as JSON.
	 */
	@DisplayName( "Profiles lists and resolves profiles" )
	@Test
	public void testProfiles() {
		String list = capture( "result = new models.cli.Cli@playwright().run( [ \"profiles\" ] )" );
		assertThat( list ).contains( "mobile" );
		assertThat( list ).contains( "android-tablet" );
		String one = capture( "result = new models.cli.Cli@playwright().run( [ \"profiles\", \"tablet\", \"--json\" ] )" );
		assertThat( one ).contains( "iPad Pro 11" );
	}

	/**
	 * Errors return exit code 1 and print their type and fix, or the error type as JSON with "--json".
	 */
	@DisplayName( "Errors print their type and fix, or JSON" )
	@Test
	public void testErrors() {
		String output = capture( "result = new models.cli.Cli@playwright().run( [ \"profiles\", \"nope\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 1 );
		assertThat( output ).contains( "Error [Playwright.InvalidProfile]" );
		assertThat( output ).contains( "Fix: Available profiles" );
		String json = capture( "result = new models.cli.Cli@playwright().run( [ \"profiles\", \"nope\", \"--json\" ] )" );
		assertThat( json ).contains( "\"type\":\"Playwright.InvalidProfile\"" );
	}

	/**
	 * The completion script is generated from the verb registry. After changing verbs, regenerate it with:
	 * {@code UPDATE_COMPLETIONS=true ./gradlew test --tests '*CliTest'}
	 */
	@DisplayName( "The shipped completion script matches the verb registry" )
	@Test
	public void testCompletionsInSync() throws IOException {
		Object	generated	= run( "result = new models.cli.Cli@playwright().completionScript()" );
		Path	file		= Path.of( "src/main/bx/completions/bxPlaywright.bash" );
		if ( "true".equalsIgnoreCase( System.getenv( "UPDATE_COMPLETIONS" ) ) ) {
			Files.createDirectories( file.getParent() );
			Files.writeString( file, generated.toString() );
		}
		assertThat( Files.readString( file ) ).isEqualTo( generated );
	}

}
