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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.playwright.BaseIntegrationTest;
import ortus.boxlang.runtime.scopes.Key;

public class CliE2ETest extends BaseIntegrationTest {

	private String cli;

	/**
	 * Prepare the end-to-end home and build the BoxLang snippet that creates a CLI pointed at it.
	 */
	@BeforeEach
	public void prepare() {
		E2E.home();
		String home = E2E.homeDir().toString().replace( "\\", "/" );
		cli = "cli = new models.cli.Cli@playwright( new models.Config@playwright( settings = { home : \"" + home
		    + "\", nodeVersion : \"" + E2E.NODE_VERSION + "\", nodeDownloadURL : \"\", profiles : {}, defaultProfile : \"default\" }, environment = {} ) )\n";
	}

	/**
	 * The doctor verb returns exit code 0, reports Node.js and Chromium as healthy, and reports ok as JSON with "--json".
	 */
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

	/**
	 * The devices verb returns exit code 0 and lists Playwright device descriptors such as iPhone 15 and Pixel 7.
	 */
	@DisplayName( "devices lists the Playwright device descriptors" )
	@Test
	public void testDevices() {
		String output = capture( cli + "result = cli.run( [ \"devices\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 0 );
		assertThat( output ).contains( "iPhone 15" );
		assertThat( output ).contains( "Pixel 7" );
	}

	/**
	 * Installing Chromium again returns exit code 0 and prints "Ready." when everything is already present.
	 */
	@DisplayName( "install is idempotent when everything is present" )
	@Test
	public void testInstall() {
		String output = capture( cli + "result = cli.run( [ \"install\", \"chromium\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 0 );
		assertThat( output ).contains( "Ready." );
	}

	/**
	 * With --json, install, install-node and doctor print one valid JSON document: progress goes to standard error.
	 */
	@DisplayName( "--json output is valid JSON" )
	@Test
	public void testJsonOutput() {
		for ( String verb : new String[] { "install", "install-node", "doctor", "version" } ) {
			String output = capture( cli + "result = cli.run( [ \"" + verb + "\", \"--json\" ] )" );
			assertThat( variables.get( result ) ).isEqualTo( 0 );
			variables.put( Key.of( "output" ), output );
			assertThat( run( "result = isJSON( output ) && isStruct( jsonDeserialize( output ) )" ) ).isEqualTo( true );
		}
	}

	/**
	 * version reports the Node.js runtime actually used and where it comes from.
	 */
	@DisplayName( "version reports the Node.js in use" )
	@Test
	public void testVersionNode() {
		String output = capture( cli + "result = cli.run( [ \"version\" ] )" );
		assertThat( output ).contains( "Node.js " + E2E.NODE_VERSION + " (downloaded)" );
	}

	/**
	 * install with an explicit Node.js path that does not exist fails with a typed error instead of reporting it as used.
	 */
	@DisplayName( "install fails on a missing explicit Node.js" )
	@Test
	public void testInstallMissingNode() {
		String	bad		= cli.replace( "nodeDownloadURL : \"\",", "nodeDownloadURL : \"\", nodePath : \"/nope/bin/node\"," );
		String	output	= capture( bad + "result = cli.run( [ \"install\", \"chromium\" ] )" );
		assertThat( variables.get( result ) ).isEqualTo( 1 );
		assertThat( output ).contains( "Error [Playwright.NotInstalled]" );
		assertThat( output.replace( '\\', '/' ) ).contains( "/nope/bin/node" );
		assertThat( output ).doesNotContain( "Ready." );
	}

	/**
	 * A recording in the format written by Playwright 1.63 codegen translates into BoxLang that runs against a real browser:
	 * strings with quotes and "#", regexes, enums, iframes, exact finders, popups and dialogs.
	 */
	@DisplayName( "A translated codegen recording runs" )
	@Test
	public void testTranslatedRecordingRuns() throws IOException {
		Path dir = Files.createTempDirectory( "bxplaywright-codegen" );
		Files.writeString( dir.resolve( "page.html" ),
		    """
		    <!doctype html><html><head><title>Login</title></head><body>
		    <h1>Welcome "friend"</h1>
		    <label for="email">Email</label><input id="email">
		    <label><input type="checkbox" id="remember"> Remember me</label>
		    <label for="country">Country</label><select id="country"><option value="us">US</option><option value="uk">UK</option></select>
		    <button type="button" onclick="document.getElementById('out').textContent='Signed in as #'+document.getElementById('email').value">Sign in</button>
		    <button type="button" onclick="alert('hi');this.textContent='Alerted'">Alert</button>
		    <button id="rc" oncontextmenu="this.textContent='right';return false">Menu</button>
		    <ul><li>One</li><li>Two</li><li>Three</li></ul>
		    <p id="out"></p>
		    <iframe name="inner" srcdoc="<button onclick=&quot;this.textContent='done'&quot;>Pay</button>"></iframe>
		    <a href="page2.html" target="_blank">Docs</a>
		    </body></html>
		    """ );
		Files.writeString( dir.resolve( "page2.html" ), "<!doctype html><html><head><title>Docs</title></head><body><h1>Hello Docs</h1></body></html>" );
		String	url			= dir.resolve( "page.html" ).toUri().toString();
		// @formatter:off
		String recording = """
			public class Example {
			  public static void main(String[] args) {
			    try (Playwright playwright = Playwright.create()) {
			      Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
			        .setHeadless(false));
			      BrowserContext context = browser.newContext();
			      Page page = context.newPage();
			      page.navigate("%s");
			      page.getByLabel("Email").fill("bob#1@example.com");
			      page.getByLabel("Remember me").check();
			      page.getByLabel("Country").selectOption("uk");
			      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
			      assertThat(page.locator("#out")).containsText("Signed in as #bob#1@example.com");
			      assertThat(page.getByRole(AriaRole.HEADING)).hasText("Welcome \\"friend\\"");
			      assertThat(page.locator("h1")).hasText(Pattern.compile("welcome\\\\s+", Pattern.CASE_INSENSITIVE));
			      page.getByRole(AriaRole.LISTITEM).filter(new Locator.FilterOptions().setHasText("Two")).click();
			      page.getByText("Three", new Page.GetByTextOptions().setExact(true)).click();
			      page.locator("iframe[name=\\"inner\\"]").contentFrame().getByRole(AriaRole.BUTTON, new FrameLocator.GetByRoleOptions().setName("Pay")).click();
			      assertThat(page.locator("iframe[name=\\"inner\\"]").contentFrame().getByRole(AriaRole.BUTTON)).hasText("done");
			      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Menu")).click(new Locator.ClickOptions()
			        .setButton(MouseButton.RIGHT));
			      assertThat(page.locator("#rc")).hasText("right");
			      page.onceDialog(dialog -> {
			        System.out.println(String.format("Dialog message: %%s", dialog.message()));
			        dialog.dismiss();
			      });
			      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Alert")).click();
			      Page page1 = page.waitForPopup(() -> {
			        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Docs")).click();
			      });
			      assertThat(page1.getByRole(AriaRole.HEADING)).hasText("Hello Docs");
			      assertThat(page1.locator("body")).matchesAriaSnapshot(\"""
			        - heading "Hello Docs" [level=1]
			        \""");
			      assertThat(page).hasTitle("Login");
			    }
			  }
			}
			""".formatted( url );
		// @formatter:on
		variables.put( Key.of( "recording" ), recording );
		String code = run( "result = new models.cli.CodegenTranslator@playwright().translate( recording )" ).toString();
		assertThat( code ).doesNotContain( "TODO translate: page" );
		run( code + "\nresult = \"ran\"" );
		assertThat( variables.get( result ) ).isEqualTo( "ran" );
	}

}
