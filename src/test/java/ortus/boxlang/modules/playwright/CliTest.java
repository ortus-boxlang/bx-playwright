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

	/**
	 * Build a BoxLang snippet creating a CLI with a Config in a temporary home and the given extra settings.
	 *
	 * @param settings Extra settings as a BoxLang struct body, e.g. {@code browser : "firefox"}
	 *
	 * @return BoxLang code setting `config` and `cli`
	 */
	private String cliWith( String settings ) {
		String home = Path.of( "build", "cli-test-home" ).toAbsolutePath().toString().replace( "\\", "/" );
		return "config = new models.Config@playwright( settings = { home : \"" + home + "\", nodeVersion : \"24.21.0\", nodeDownloadURL : \"\", "
		    + "profiles : {}, defaultProfile : \"default\"" + ( settings.isEmpty() ? "" : ", " + settings ) + " }, environment = {} )\n"
		    + "cli = new models.cli.Cli@playwright( config )\n";
	}

	/**
	 * Value flags take the next argument ("--output file", "-o file", "-o=file"), "--key=value" keeps working and a value flag
	 * without a value is a typed error.
	 */
	@DisplayName( "Value flags take the next argument" )
	@Test
	public void testValueFlags() {
		// @formatter:off
		Object value = run( """
			cli     = new models.cli.Cli@playwright()
			verb    = cli.getVerbs().codegen
			a       = cli.parseOptions( [ "--output", "a.bxs", "https://example.com" ], verb )
			b       = cli.parseOptions( [ "-o", "b.bxs", "--target=boxlang" ], verb )
			c       = cli.parseOptions( [ "-o=c.bxs" ], verb )
			missing = ""
			try {
				cli.parseOptions( [ "--output" ], verb )
			} catch ( "Playwright.InvalidOption" e ) {
				missing = e.message
			}
			result = [ a.output, a._positionals.toList(), b.output, b.target, b._positionals.len(), c.output, missing ].toList( "|" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "a.bxs|https://example.com|b.bxs|boxlang|0|c.bxs|The [--output] option needs a value." );
	}

	/**
	 * Codegen forwards everything but its own options (and their values) to Playwright, and resolves --output against the
	 * working directory instead of the module folder.
	 */
	@DisplayName( "Codegen strips its own options and resolves the output path" )
	@Test
	public void testCodegenArguments() {
		// @formatter:off
		Object value = run( """
			cli    = new models.cli.Cli@playwright()
			kept   = cli.withoutOptions( [ "https://example.com", "--output", "a.bxs", "-o", "b.bxs", "--target=boxlang", "--json", "--device", "iPhone 15" ], [ "target", "output", "json" ], cli.getVerbs().codegen )
			result = kept.toList( "|" ) & "@" & new models.cli.Codegen@playwright().resolveOutput( "real.bxs" )
		""" );
		// @formatter:on
		String	expected	= Path.of( System.getProperty( "user.dir" ), "real.bxs" ).toAbsolutePath().toString();
		assertThat( value ).isEqualTo( "https://example.com|--device|iPhone 15@" + expected );
	}

	/**
	 * Passthrough verbs never forward --json to Playwright, and mcp gets the configured browser unless one is chosen.
	 */
	@DisplayName( "Passthrough strips --json and mcp gets a browser" )
	@Test
	public void testPassthroughArguments() {
		// @formatter:off
		Object value = run( cliWith( "" ) + """
			verbs   = cli.getVerbs()
			pass    = new models.cli.Passthrough@playwright()
			shot    = pass.buildArgs( { _raw : [ "https://example.com", "shot.png", "--json" ], json : true }, config, verbs.screenshot )
			mcp     = pass.buildArgs( { _raw : [ "--port", "8931" ], port : "8931" }, config, verbs.mcp )
			chosen  = pass.buildArgs( { _raw : [ "--browser", "webkit" ], browser : "webkit" }, config, verbs.mcp )
			firefox = pass.buildArgs( { _raw : [] }, new models.Config@playwright( settings = { profiles : {}, defaultProfile : "firefox" }, environment = {} ), verbs.mcp )
			edge    = pass.buildArgs( { _raw : [] }, new models.Config@playwright( settings = { channel : "msedge", profiles : {}, defaultProfile : "default" }, environment = {} ), verbs.mcp )
			result  = [ shot.toList( " " ), mcp.toList( " " ), chosen.toList( " " ), firefox.toList( " " ), edge.toList( " " ) ].toList( "|" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo(
		    "screenshot https://example.com shot.png|mcp --port 8931 --browser=chromium|mcp --browser webkit|mcp --browser=firefox|mcp --browser=msedge" );
	}

	/**
	 * A quoted value is only unquoted when it ends with the same quote it starts with.
	 */
	@DisplayName( "Only matching quotes are stripped" )
	@Test
	public void testStripQuotes() {
		// @formatter:off
		Object value = run( """
			options = new models.cli.Cli@playwright().parseOptions( [ '--a="abc', "--b='abc'", '--c="abc"', '--d=''abc"' ] )
			result  = [ options.a, options.b, options.c, options.d ].toList( "|" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "\"abc|abc|abc|'abc\"" );
	}

	/**
	 * Unknown verbs and help for an unknown verb print a typed error as JSON with --json and exit with 1.
	 */
	@DisplayName( "Unknown verbs are JSON errors with --json" )
	@Test
	public void testUnknownVerbJson() {
		String	expected	= "{\"error\":{\"type\":\"Playwright.InvalidOption\",\"message\":\"Unknown verb [nope].\"";
		String	help		= capture( "result = new models.cli.Cli@playwright().run( [ \"help\", \"nope\", \"--json\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 1 );
		assertThat( help.trim() ).startsWith( expected );
		String verb = capture( "result = new models.cli.Cli@playwright().run( [ \"nope\", \"--json\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 1 );
		assertThat( verb.trim() ).startsWith( expected );
		assertThat( verb ).doesNotContain( "Usage:" );
	}

	/**
	 * Built-in verbs reject flags they do not declare (a typo such as --with-dep) and list the valid ones, as text or JSON.
	 */
	@DisplayName( "Unknown flags on built-in verbs fail" )
	@Test
	public void testUnknownFlags() {
		String output = capture( "result = new models.cli.Cli@playwright().run( [ \"install\", \"--with-dep\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 1 );
		assertThat( output ).contains( "Error [Playwright.InvalidOption]: Unknown option [--with-dep] for [install]." );
		assertThat( output ).contains( "--with-deps" );
		String json = capture( "result = new models.cli.Cli@playwright().run( [ \"version\", \"-x\", \"--json\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 1 );
		assertThat( json ).contains( "\"type\":\"Playwright.InvalidOption\"" );
		assertThat( json ).contains( "Unknown option [-x] for [version]." );
	}

	/**
	 * An explicit Node.js path that does not exist fails doctor with a fix hint, and version reports no Node.js.
	 */
	@DisplayName( "A missing explicit Node.js fails doctor and shows in version" )
	@Test
	public void testMissingExplicitNode() {
		String	setup	= cliWith( "nodePath : \"/nope/bin/node\"" );
		String	doctor	= capture( setup + "result = cli.run( [ \"doctor\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 1 );
		assertThat( doctor ).contains( "[fail] node" );
		// Windows prints the path with backslashes
		assertThat( doctor.replace( '\\', '/' ) ).contains( "/nope/bin/node" );
		assertThat( doctor ).contains( "nodePath" );
		assertThat( doctor ).doesNotContain( "[ok]   node" );
		String json = capture( setup + "result = cli.run( [ \"doctor\", \"--json\" ] )" );
		assertThat( json ).contains( "\"ok\":false" );
		String version = capture( setup + "result = cli.run( [ \"version\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 0 );
		assertThat( version ).contains( "Node.js none" );
	}

}
