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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

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

	@DisplayName( "bx:playwrightRender renders its body to a PDF file or image bytes" )
	@Test
	public void testRenderComponent() {
		String	dir		= Path.of( "build", "e2e-output" ).toAbsolutePath().toString().replace( "\\", "/" );
		// @formatter:off
		Object value = run( """
			dir = "%s"
			directoryCreate( dir, true, true )
			invoice = 42
			bx:playwrightRender type="pdf" path="#dir#/component.pdf" format="A4" margin="1cm" {
				writeOutput( "<h1>Invoice #invoice#</h1>" )
			}
			bx:playwrightRender type="png" variable="card" viewport="600x315" {
				writeOutput( "<h1>Card</h1>" )
			}
			errors = []
			try {
				bx:playwrightRender type="pdf" {
					writeOutput( "<h1>No target</h1>" )
				}
			} catch ( "Playwright.InvalidOption" e ) {
				errors.append( "missing target" )
			}
			result = fileExists( dir & "/component.pdf" ) & "|" & ( arrayLen( card ) > 100 ) & "|" & errors.toList()
		""".formatted( dir ) );
		// @formatter:on
		assertThat( value ).isEqualTo( "true|true|missing target" );
	}

	@DisplayName( "API testing with request(): verbs, JSON, headers and status" )
	@Test
	public void testRequest() throws IOException {
		HttpServer server = HttpServer.create( new InetSocketAddress( "127.0.0.1", 0 ), 0 );
		server.createContext( "/api/echo", exchange -> {
			byte[]	body	= exchange.getRequestBody().readAllBytes();
			String	json	= "{\"method\":\"" + exchange.getRequestMethod() + "\",\"query\":\"" + exchange.getRequestURI().getQuery()
			    + "\",\"token\":\"" + exchange.getRequestHeaders().getFirst( "X-Token" ) + "\",\"body\":"
			    + ( body.length == 0 ? "null" : new String( body, StandardCharsets.UTF_8 ) ) + "}";
			byte[]	out		= json.getBytes( StandardCharsets.UTF_8 );
			exchange.getResponseHeaders().add( "Content-Type", "application/json" );
			exchange.sendResponseHeaders( 200, out.length );
			exchange.getResponseBody().write( out );
			exchange.close();
		} );
		server.createContext( "/api/missing", exchange -> {
			exchange.sendResponseHeaders( 404, -1 );
			exchange.close();
		} );
		server.start();
		try {
			// @formatter:off
			Object value = run( """
				api  = playwright().request( { baseURL : "http://127.0.0.1:%d" } )
				try {
					a = api.post( "/api/echo", { json : { name : "Luis" }, headers : { "X-Token" : "abc" }, params : { page : 2 } } )
					api.expect( a ).toBeOK()
					data = a.json()
					b = api.get( "/api/missing" )
					result = a.status() & "|" & data.method & "|" & data.body.name & "|" & data.token & "|" & data.query & "|" & b.status() & "|" & b.ok()
				} finally {
					api.close()
				}
			""".formatted( server.getAddress().getPort() ) );
			// @formatter:on
			assertThat( value ).isEqualTo( "200|POST|Luis|abc|page=2|404|false" );
		} finally {
			server.stop( 0 );
		}
	}

	@DisplayName( "Interception points are announced" )
	@Test
	public void testInterceptors() {
		// @formatter:off
		Object value = run( """
			events = []
			BoxRegisterInterceptor( ( data ) => events.append( "page" ), "onPageCreate" )
			BoxRegisterInterceptor( ( data ) => events.append( "context" ), "onContextCreate" )
			playwright().browse( ( page ) => page.setContent( "<p>x</p>" ) )
			result = events.toList()
		""" );
		// @formatter:on
		assertThat( value.toString() ).contains( "context" );
		assertThat( value.toString() ).contains( "page" );
	}

	@DisplayName( "Visual regression: baseline, match, mismatch with diff image, update" )
	@Test
	public void testScreenshotMatches() {
		String	dir		= Path.of( "build", "e2e-snapshots-" + System.nanoTime() ).toAbsolutePath().toString().replace( "\\", "/" );
		// @formatter:off
		Object value = bx( """
			dir  = "%s"
			opts = { directory : dir }
			page = pw.newPage()
			page.setContent( "<h1 style='color:black'>Hello</h1><p id='clock'>12:00</p>" )
			page.assertScreenshotMatches( "hello", opts )
			created = fileExists( dir & "/hello.png" )
			page.assertScreenshotMatches( "hello", opts )
			page.locator( "h1" ).assertScreenshotMatches( "title", opts )
			page.assertScreenshotMatches( "masked", { directory : dir, mask : [ "##clock" ] } )
			page.setContent( "<h1 style='color:black'>Hello</h1><p id='clock'>12:01</p>" )
			page.assertScreenshotMatches( "masked", { directory : dir, mask : [ "##clock" ] } )
			page.setContent( "<h1 style='color:red'>Hello world</h1><p id='clock'>12:00</p>" )
			failure = ""
			try {
				page.assertScreenshotMatches( "hello", opts )
			} catch ( "Playwright.AssertionFailed" e ) {
				failure = e.message
			}
			diffWritten = fileExists( dir & "/hello-diff.png" ) && fileExists( dir & "/hello-actual.png" )
			page.assertScreenshotMatches( "hello", { directory : dir, update : true } )
			page.assertScreenshotMatches( "hello", opts )
			cleaned = !fileExists( dir & "/hello-diff.png" )
			result = created & "|" & ( failure contains "differs from its baseline" ) & "|" & diffWritten & "|" & cleaned
		""".formatted( dir ) );
		// @formatter:on
		assertThat( value ).isEqualTo( "true|true|true|true" );
	}

	@DisplayName( "Quality checks: console errors, smoke test and accessibility" )
	@Test
	public void testQualityChecks() {
		// @formatter:off
		Object value = bx( """
			results = []
			page = serve( pw.newContext() ).newPage()
			page.setContent( "<script>console.error( 'boom' ); console.error( 'favicon.ico missing' )</script><p>x</p>" )
			results.append( page.consoleErrors().len() )
			try {
				page.assertNoConsoleErrors()
			} catch ( "Playwright.AssertionFailed" e ) {
				results.append( e.message contains "boom" )
			}
			try {
				page.assertNoConsoleErrors( [ "boom", "favicon" ] )
				results.append( "ignored" )
			} catch ( any e ) {
				results.append( "not ignored" )
			}

			try {
				page.assertNoSmoke( [ "/login", "/nope" ] )
			} catch ( "Playwright.AssertionFailed" e ) {
				results.append( ( e.message contains "/nope: HTTP 404" ) && !( e.message contains "/login:" ) )
			}

			page.setContent( "<html lang='en'><head><title>ok</title></head><body><main><h1>Title</h1><img src='a.png'><button></button></main></body></html>" )
			violations = page.accessibility()
			results.append( violations.map( ( v ) -> v.id ).sort( "text" ).toList() )
			try {
				page.assertNoAccessibilityIssues( { impact : "critical" } )
			} catch ( "Playwright.AssertionFailed" e ) {
				results.append( e.message contains "image-alt" )
			}
			page.assertNoAccessibilityIssues( { exclude : [ "image-alt", "button-name" ] } )
			result = results.toList( "|" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "2|true|ignored|true|button-name,image-alt|true" );
	}

}
