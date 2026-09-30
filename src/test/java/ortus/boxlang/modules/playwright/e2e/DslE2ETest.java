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

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.playwright.BaseIntegrationTest;

/**
 * Drives the DSL against a fake site (src/test/resources/site) served through request interception.
 */
public class DslE2ETest extends BaseIntegrationTest {

	private String setup;

	@BeforeEach
	public void prepare() {
		E2E.home();
		String site = Path.of( "src/test/resources/site" ).toAbsolutePath().toString().replace( "\\", "/" );
		// @formatter:off
		setup = """
			import java:ortus.boxlang.modules.playwright.engine.OptionsMapper@playwright;
			site = "%s"
			function serve( ctx ) {
				ctx.intercept( "http://app.test/**" ).handle( ( route ) => {
					var path = createObject( "java", "java.net.URI" ).init( route.request().url() ).getPath()
					var file = site & ( path == "/" ? "/login.html" : path & ".html" )
					if ( !fileExists( file ) ) {
						return route.fulfill( OptionsMapper.build( "Route.FulfillOptions", { status : 404, body : "Not found" } ) )
					}
					route.fulfill( OptionsMapper.build( "Route.FulfillOptions", { status : 200, contentType : "text/html", body : fileRead( file ) } ) )
				} )
				return ctx
			}
			pw = playwright( { baseURL : "http://app.test" } )
			""".formatted( site );
		// @formatter:on
	}

	private Object bx( String code ) {
		return run( setup + "\ntry {\n" + code + "\n} finally {\n pw.close()\n}" );
	}

	@DisplayName( "Fill a form with smart selectors, submit and assert" )
	@Test
	public void testLoginFlow() {
		// @formatter:off
		Object value = bx( """
			page = serve( pw.newContext() ).newPage()
			page.visit( "/login" )
				.assertTitle( "Login" )
				.assertSee( "Sign in to BoxLang" )
				.fill( "Email", "luis@ortus.com" )
				.fill( "Password", "secret" )
				.check( "Remember me" )
				.select( "@role", "editor" )
				.assertValue( "Email", "luis@ortus.com" )
				.assertChecked( "Remember me" )
				.click( "Sign in" )
				.assertPathIs( "/dashboard" )
				.assertUrlContains( "user=luis" )
				.assertTitleContains( "Dashboard" )
				.assertSee( "Welcome" )
			result = page.url()
		""" );
		// @formatter:on
		assertThat( value.toString() ).isEqualTo( "http://app.test/dashboard?user=luis%40ortus.com" );
	}

	@DisplayName( "Locators: count, texts, nth, roles, within and hidden elements" )
	@Test
	public void testLocators() {
		// @formatter:off
		Object value = bx( """
			page = serve( pw.newContext() ).newPage().visit( "/dashboard" )
			todos = page.locator( ".todos li" )
			page.assertCount( ".todos li", 3 )
				.within( "@cart", ( cart ) => cart.assertSee( "2 items" ).click( "Remove" ) )
				.assertVisible( "##welcome" )
			page.byRole( "heading", { name : "Welcome" } ).expect().toBeVisible()
			page.expect( ".todos li" ).toHaveCount( 3 )
			page.locator( ".todos li" ).filter( { hasText : "Ship" } ).expect().toHaveText( "Ship bx-playwright" )
			result = todos.count() & "|" & todos.texts().toList() & "|" & todos.nth( 2 ).text() & "|" & todos.last().text() & "|" & page.text( "h1" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "3|Write specs,Ship bx-playwright,Celebrate|Ship bx-playwright|Celebrate|Welcome" );
	}

	@DisplayName( "Mock the network and listen to console messages" )
	@Test
	public void testNetworkAndConsole() {
		// @formatter:off
		Object value = bx( """
			messages = []
			page = serve( pw.newContext() ).newPage()
			page.onConsole( ( message ) => messages.append( message.text ) )
				.intercept( "**/api/users" ).respondJson( { users : [ "Luis", "Brad" ] } )
				.visit( "/dashboard" )
				.assertText( "##users", "Luis, Brad" )
			result = messages.toList()
		""" );
		// @formatter:on
		assertThat( value.toString() ).contains( "dashboard loaded" );
	}

	@DisplayName( "Popups open as new pages" )
	@Test
	public void testPopup() {
		// @formatter:off
		Object value = bx( """
			page  = serve( pw.newContext() ).newPage().visit( "/login" )
			popup = page.waitForPopup( () => page.click( "About" ) )
			popup.assertTitle( "About" ).assertSee( "About BoxLang" )
			result = popup.title()
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "About" );
	}

	@DisplayName( "Assertion failures and timeouts are typed errors with Playwright's message" )
	@Test
	public void testTypedErrors() {
		// @formatter:off
		Object value = bx( """
			page = pw.newPage( { timeouts : { action : 500, assertion : 500 } } )
			page.setContent( "<h1>Hello</h1>" )
			errors = []
			try {
				page.assertSee( "Goodbye" )
			} catch ( "Playwright.AssertionFailed" e ) {
				errors.append( "assert:" & ( e.message contains "Goodbye" ) )
			}
			try {
				page.click( "##missing" )
			} catch ( "Playwright.Timeout" e ) {
				errors.append( "timeout:" & ( e.detail contains "timed out" ) )
			}
			try {
				page.getJava()
				playwright( "nope" )
			} catch ( "Playwright.InvalidProfile" e ) {
				errors.append( "profile" )
			}
			result = errors.toList()
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "assert:true,timeout:true,profile" );
	}

	@DisplayName( "Screenshots, PDFs, content and rendering HTML" )
	@Test
	public void testOutputs() {
		String	dir		= Path.of( "build", "e2e-output" ).toAbsolutePath().toString().replace( "\\", "/" );
		// @formatter:off
		Object value = bx( """
			dir = "%s"
			directoryCreate( dir, true, true )
			page  = pw.newPage()
			page.setContent( "<h1>Report</h1>" )
			shot  = page.screenshot( dir & "/page.png" )
			bytes = page.screenshot()
			pdf   = page.pdf( dir & "/page.pdf", { format : "A4" } )
			html  = page.content()
			rendered = playwright().render( "<h1>Invoice 42</h1>", { type : "pdf" } )
			image    = playwright().render( "<h1>Card</h1>", { type : "png", viewport : { width : 600, height : 315 } } )
			result = fileExists( shot ) & "|" & ( arrayLen( bytes ) > 100 ) & "|" & fileExists( pdf ) & "|" & ( html contains "Report" )
				& "|" & ( charsetEncode( arraySlice( rendered, 1, 4 ), "utf-8" ) ) & "|" & ( arrayLen( image ) > 100 )
		""".formatted( dir ) );
		// @formatter:on
		assertThat( value ).isEqualTo( "true|true|true|true|%PDF|true" );
	}

	@DisplayName( "browse() gives each argument an isolated page and cleans up" )
	@Test
	public void testBrowseMultiUser() {
		// @formatter:off
		Object value = run( """
			manager = playwright()
			result = manager.browse( ( alice, bob ) => {
				alice.setContent( "<p>alice</p>" )
				bob.setContent( "<p>bob</p>" )
				return alice.text( "p" ) & "," & bob.text( "p" ) & "," & ( alice.context() != bob.context() )
			} )
			result &= "," & manager.isStarted()
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "alice,bob,true,false" );
	}

	@DisplayName( "Profiles and devices shape the browser context" )
	@Test
	public void testDeviceProfile() {
		// @formatter:off
		Object value = run( """
			result = playwright( "android" ).browse( ( page ) => {
				page.setContent( "<meta name='viewport' content='width=device-width'><p>x</p>" )
				return page.evaluate( "navigator.userAgent.includes( 'Android' ) + '|' + window.innerWidth + '|' + matchMedia( '(prefers-color-scheme: light)' ).matches" )
			} )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "true|412|true" );
	}

	@DisplayName( "Artifacts are kept on failure and removed on success" )
	@Test
	public void testArtifacts() {
		String	dir		= Path.of( "build", "e2e-artifacts" ).toAbsolutePath().toString().replace( "\\", "/" );
		// @formatter:off
		Object value = bx( """
			options  = { artifacts : { directory : "%s", screenshot : "only-on-failure", trace : "retain-on-failure", video : "retain-on-failure" } }
			failed   = pw.newContext( options )
			failed.newPage().setContent( "<h1>Oops</h1>" )
			kept     = failed.close( true )
			passed   = pw.newContext( options )
			passed.newPage().setContent( "<h1>Fine</h1>" )
			dropped  = passed.close( false )
			result = kept.screenshots.len() & "|" & fileExists( kept.trace ) & "|" & kept.videos.len() & "|" & fileExists( kept.videos[ 1 ] )
				& "|" & dropped.screenshots.len() & "|" & len( dropped.trace ) & "|" & dropped.videos.len()
		""".formatted( dir ) );
		// @formatter:on
		assertThat( value ).isEqualTo( "1|true|1|true|0|0|0" );
	}

	@DisplayName( "The accessibility snapshot is a compact page view" )
	@Test
	public void testSnapshot() {
		// @formatter:off
		Object value = bx( """
			page = pw.newPage()
			page.setContent( "<h1>Hello</h1><button>Save</button>" )
			result = page.snapshot()
			page.expect().not().toHaveTitle( "Nope" )
		""" );
		// @formatter:on
		assertThat( value.toString() ).contains( "heading \"Hello\"" );
		assertThat( value.toString() ).contains( "button \"Save\"" );
	}

}
