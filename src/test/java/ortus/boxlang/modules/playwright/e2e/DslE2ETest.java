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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

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

	/**
	 * Prepare the end-to-end home, map the fixtures folder and build the BoxLang preamble that serves the fake site through request interception.
	 */
	@BeforeEach
	public void prepare() {
		E2E.home();
		runtime.getConfiguration().registerMapping( "/fixtures", Path.of( "src/test/resources/fixtures" ).toAbsolutePath().toString() );
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

	/**
	 * Run BoxLang code after the site preamble, closing the Playwright manager afterwards.
	 *
	 * @param code The BoxLang code to run, which should set a `result` variable
	 *
	 * @return The value of `result`
	 */
	private Object bx( String code ) {
		return run( setup + "\ntry {\n" + code + "\n} finally {\n pw.close()\n}" );
	}

	/**
	 * A manager registers its Playwright instance when it starts one and releases it on close(), so the module only
	 * closes instances that are still open when it unloads or the JVM stops.
	 */
	@DisplayName( "close() releases the Playwright instance from the registry" )
	@Test
	public void testCloseReleasesRegistry() {
		// @formatter:off
		Object value = run( "import java:ortus.boxlang.modules.playwright.engine.PlaywrightRegistry@playwright;\n" + setup + """
			PlaywrightRegistry.closeAll()
			pw.newPage().setContent( "<h1>Hi</h1>" )
			open = PlaywrightRegistry.openCount()
			pw.close()
			result = open & "|" & PlaywrightRegistry.openCount()
			""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "1|0" );
	}

	/**
	 * click( "Sign in" ) clicks the button even when a heading with the same text comes first in the page, and
	 * assertSee() only counts rendered text: text inside a hidden element fails assertSee() and passes assertDontSee().
	 */
	@DisplayName( "click() prefers buttons over same text, assertSee() ignores hidden text" )
	@Test
	public void testClickIntentAndHiddenText() {
		// @formatter:off
		Object value = bx( """
			page = pw.newPage()
			page.setContent( "<form onsubmit=""event.preventDefault();this.hidden=true;document.getElementById('dash').hidden=false""><h2>Sign in</h2><button>Sign in</button></form><section id=dash hidden><h1>Welcome back</h1></section>" )
			page.assertDontSee( "Welcome back" )
			hiddenFailed = false
			try {
				page.assertSee( "Welcome back" )
			} catch ( "Playwright.AssertionFailed" e ) {
				hiddenFailed = true
			}
			page.click( "Sign in" ).assertSee( "Welcome back" ).assertDontSee( "Sign in" )
			result = hiddenFailed
			""" );
		// @formatter:on
		assertThat( value ).isEqualTo( true );
	}

	/**
	 * Smart selectors: a button rendered a moment later still wins over a same text heading, an exact button name wins
	 * over a longer one ("Save" over "Save draft"), fill() follows the label, placeholder, name priority instead of
	 * document order, and selectors with spaces inside quotes or with the >> chain operator are used as selectors.
	 */
	@DisplayName( "Smart selectors: delayed buttons, exact names, fill priority and selectors with spaces" )
	@Test
	public void testSmartSelectorPriorities() {
		// @formatter:off
		Object value = bx( """
			results = []
			page = pw.newPage()
			page.setContent( "<h2>Sign in</h2><div id=out></div><script>setTimeout( () => { const b = document.createElement( 'button' ); b.textContent = 'Sign in'; b.onclick = () => out.textContent = 'button'; document.body.append( b ) }, 300 )</script>" )
			results.append( page.click( "Sign in" ).text( "##out" ) )

			page.setContent( "<button>Save draft</button><button>Save</button><div id=out></div><script>document.querySelectorAll( 'button' ).forEach( b => b.onclick = () => out.textContent = 'clicked ' + b.textContent )</script>" )
			results.append( page.click( "Save" ).text( "##out" ) )

			page.setContent( "<label>Backup email <input id=a></label><label>Email <input id=b></label>" )
			page.fill( "Email", "x" )
			results.append( page.value( "##a" ) & "|" & page.value( "##b" ) )

			page.setContent( "<input id=p placeholder='Email'><label>Email <input id=l></label>" )
			page.fill( "Email", "y" )
			results.append( page.value( "##p" ) & "|" & page.value( "##l" ) )

			page.setContent( "<input placeholder='Your email'><div><span>Foo</span></div><nav>first</nav><nav>second</nav>" )
			page.fill( 'input[placeholder="Your email"]', "z" )
			results.append( page.value( "input" ) )
			results.append( page.text( "div >> text=Foo" ) )
			results.append( page.text( "nav >> nth=0" ) )
			result = results.toList( ";" )
			""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "button;clicked Save;|x;|y;z;Foo;first" );
	}

	/**
	 * A login form is filled with smart selectors, submitted and asserted through the fluent page API, ending on the dashboard URL.
	 */
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

	/**
	 * Locators count, list texts, pick nth and last elements, find by role, filter, scope with within() and assert visibility.
	 */
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

	/**
	 * A mocked JSON API response is rendered on the page and console messages reach the onConsole() listener.
	 */
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

	/**
	 * waitForPopup() returns the page opened by a click as a new page that can be asserted.
	 */
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

	/**
	 * Failed assertions, action timeouts and invalid profiles throw typed Playwright errors that carry the Playwright message.
	 */
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

	/**
	 * Pages write screenshots and PDFs to files or bytes, return their content, and render() turns HTML into PDF or PNG bytes.
	 */
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

	/**
	 * browse() gives each closure argument a page in its own context and stops the manager afterwards.
	 */
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

	/**
	 * The android profile emulates an Android user agent, a 412px wide viewport and the light color scheme.
	 */
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

	/**
	 * A context closed as failed keeps its screenshot, trace and video, while a context closed as passed removes them.
	 */
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

	/**
	 * onPlaywrightArtifact uses singular types for every artifact, the same as page.screenshot() and page.pdf().
	 */
	@DisplayName( "onPlaywrightArtifact announces singular artifact types" )
	@Test
	public void testArtifactEventTypes() {
		String	dir		= Path.of( "build", "e2e-artifact-events" ).toAbsolutePath().toString().replace( "\\", "/" );
		// @formatter:off
		Object value = bx( """
			request.pwArtifactTypes = []
			listener = ( data ) => request.pwArtifactTypes.append( data.type )
			boxRegisterInterceptor( listener, "onPlaywrightArtifact" )
			try {
				context = pw.newContext( { artifacts : { directory : "%s", screenshot : "on", trace : "on", video : "on" } } )
				page    = context.newPage()
				page.setContent( "<h1>Artifacts</h1>" ).screenshot( "%s/page.png" )
				context.close( true )
			} finally {
				boxUnregisterInterceptor( listener, "onPlaywrightArtifact" )
			}
			result = request.pwArtifactTypes.toList()
		""".formatted( dir, dir ) );
		// @formatter:on
		assertThat( value ).isEqualTo( "screenshot,screenshot,video,trace" );
	}

	/**
	 * snapshot() returns a compact accessibility view that lists the heading and button with their names.
	 */
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

	/**
	 * bx:playwrightRender writes its body to a PDF file or stores image bytes in a variable, and fails without a target.
	 */
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
			bx:playwrightRender options={ type : "png" } variable="fromOptions" {
				writeOutput( "<h1>Options</h1>" )
			}
			try {
				bx:playwrightRender type="png" variable="bad" viewport="1200xabc" {
					writeOutput( "<h1>Bad viewport</h1>" )
				}
			} catch ( "Playwright.InvalidOption" e ) {
				errors.append( "bad viewport" )
			}
			// PNG files start with 0x89 'P' 'N' 'G'
			isPng = fromOptions[ 2 ] == 80 && fromOptions[ 3 ] == 78 && fromOptions[ 4 ] == 71
			result = fileExists( dir & "/component.pdf" ) & "|" & ( arrayLen( card ) > 100 ) & "|" & isPng & "|" & errors.toList()
		""".formatted( dir ) );
		// @formatter:on
		assertThat( value ).isEqualTo( "true|true|true|missing target,bad viewport" );
	}

	/**
	 * request() sends API calls with JSON bodies, headers and query params to a local server and reports the status of each response.
	 */
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

	/**
	 * Creating a context and a page announces the onContextCreate and onPageCreate interception points.
	 */
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

	/**
	 * Screenshot matching creates a baseline, matches it, honors masks, fails on changes with diff and actual images, and updates the baseline on
	 * request.
	 */
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

	/**
	 * Console errors can be listed, asserted and ignored, smoke tests report failing paths, and accessibility checks filter by impact or rule.
	 */
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

	/**
	 * Page objects are visited, checked with at(), use element aliases and chain into other page objects.
	 */
	@DisplayName( "Page objects: visit, at() checks, element aliases and chaining" )
	@Test
	public void testPageObjects() {
		// @formatter:off
		Object value = bx( """
			page      = serve( pw.newContext() ).newPage()
			dashboard = page.visit( new fixtures.LoginPage() )
				.assertSee( "Sign in to BoxLang" )
				.loginAs( "luis@ortus.com" )
			dashboard.assertSee( "Welcome" ).assertVisible( "@welcome" )
			wrongPage = ""
			try {
				page.on( new fixtures.LoginPage() )
			} catch ( "Playwright.AssertionFailed" e ) {
				wrongPage = "not at login"
			}
			result = dashboard.todoCount() & "|" & dashboard.element( "welcome" ).text() & "|" & wrongPage & "|" & getMetadata( dashboard ).name
		""" );
		// @formatter:on
		assertThat( value.toString() ).startsWith( "3|Welcome|not at login|" );
		assertThat( value.toString() ).endsWith( "DashboardPage" );
	}

	/**
	 * Page components scope actions, assertions and aliases to their root element through within() and component().
	 */
	@DisplayName( "Page components scope actions and aliases to their root" )
	@Test
	public void testComponents() {
		// @formatter:off
		Object value = bx( """
			page = serve( pw.newContext() ).newPage().visit( "/dashboard" )
			page.evaluate( "() => { document.querySelector( '[data-testid=cart] button' ).onclick = () => document.querySelector( '[data-testid=cart] span' ).textContent = 'Empty' }" )
			page.within( new fixtures.CartComponent(), ( cart ) => cart.assertSee( "2 items" ).empty().assertSee( "Empty" ) )
			cart = page.component( new fixtures.CartComponent() )
			result = cart.text( "span" ) & "|" & cart.count( "button" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "Empty|1" );
	}

	/**
	 * Macros add methods to pages and locators, unknown methods list the available macros, and macros can be removed.
	 */
	@DisplayName( "Macros add methods to pages and locators" )
	@Test
	public void testMacros() {
		// @formatter:off
		Object value = bx( """
			pw.macro( "fillLogin", ( page, email ) => page.fill( "Email", email ).fill( "Password", "secret" ) )
			pw.macro( "shout", ( locator ) => uCase( locator.text() ), "locator" )
			page = serve( pw.newContext() ).newPage().visit( "/login" )
			page.fillLogin( "a@b.com" ).assertValue( "Email", "a@b.com" )
			shouted = page.locator( "h1" ).shout()
			unknown = ""
			try {
				page.flyAway()
			} catch ( "Playwright.InvalidOption" e ) {
				unknown = e.detail contains "fillLogin"
			}
			pw.removeMacro( "fillLogin" ).removeMacro( "shout", "locator" )
			result = shouted & "|" & unknown
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "SIGN IN TO BOXLANG|true" );
	}

	/**
	 * soft() runs every assertion and then throws one error that counts and lists all the failures.
	 */
	@DisplayName( "Soft assertions collect every failure and fail once" )
	@Test
	public void testSoftAssertions() {
		// @formatter:off
		Object value = bx( """
			page = pw.newPage( { timeouts : { assertion : 300 } } )
			page.setContent( "<title>Home</title><h1>Hello</h1>" )
			message = ""
			try {
				page.soft( ( p ) => {
					p.assertSee( "Hello" )
					p.assertSee( "Missing one" )
					p.assertTitle( "Other" )
					p.expect( "h1" ).toHaveText( "Nope" )
				} )
			} catch ( "Playwright.AssertionFailed" e ) {
				message = e.message
			}
			page.assertSee( "Hello" )
			result = listFirst( message, ":" ) & "|" & ( message contains "Missing one" ) & "|" & ( message contains "Nope" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "3 soft assertion(s) failed|true|true" );
	}

	/**
	 * A saved session runs its setup once, is reused by later calls and new pages, and a missing session fails.
	 */
	@DisplayName( "Saved sessions are created once and reused" )
	@Test
	public void testSessions() {
		// @formatter:off
		Object value = bx( """
			runs = 0
			setup = ( page ) => {
				runs++
				page.context().addCookies( [ { name : "auth", value : "admin-token", url : "http://app.test/" } ] )
			}
			file1 = pw.session( "admin-test", setup, { refresh : true } )
			file2 = pw.session( "admin-test", setup )
			page  = pw.newPage( { session : "admin-test" } )
			token = page.context().cookies().filter( ( c ) -> c.name == "auth" )[ 1 ].value
			missing = ""
			try {
				pw.newPage( { session : "nobody-yet" } )
			} catch ( "Playwright.InvalidOption" e ) {
				missing = "missing"
			}
			result = runs & "|" & ( file1 == file2 ) & "|" & token & "|" & missing
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "1|true|admin-token|missing" );
	}

	/**
	 * freezeTime() fixes the page clock, and setViewport() and emulate() change the viewport, color scheme and media.
	 */
	@DisplayName( "Emulation and time helpers" )
	@Test
	public void testEmulationAndTime() {
		// @formatter:off
		Object value = bx( """
			page = pw.newPage()
			page.freezeTime( "2030-05-01T10:00:00" )
			page.setContent( "<p>x</p>" )
			year = page.evaluate( "new Date().getFullYear()" )
			page.setViewport( 500, 400 ).emulate( { colorScheme : "dark", media : "print" } )
			result = year & "|" & page.evaluate( "window.innerWidth + '|' + matchMedia( '(prefers-color-scheme: dark)' ).matches + '|' + matchMedia( 'print' ).matches" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "2030|500|true|true" );
	}

	/**
	 * The AI browser returns snapshots with element refs, acts by ref or by text, and reports failures and closing as text.
	 */
	@DisplayName( "AI browser: snapshots with refs, acting by ref or text, errors as text" )
	@Test
	public void testAiBrowser() {
		// @formatter:off
		Object value = bx( """
			ctx     = serve( pw.newContext() )
			browser = new models.AiBrowser@playwright( { newPage : () => ctx.newPage() } )
			state   = browser.visit( "http://app.test/login" )
			found    = reFind( "textbox ""Email"" \\[ref=(e[0-9]+)\\]", state, 1, true )
			emailRef = found.pos[ 1 ] ? found.match[ 2 ] : ""
			browser.fill( emailRef, "ai@ortus.com" )
			browser.fill( "Password", "secret" )
			after   = browser.click( "Sign in" )
			failure = browser.click( "Does not exist here" )
			result  = len( emailRef ) & "|" & ( state contains "Title: Login" ) & "|" & ( after contains "/dashboard?user=ai%40ortus.com" )
				& "|" & ( failure contains "Error [Playwright." ) & "|" & browser.close()
		""" );
		// @formatter:on
		assertThat( value.toString() ).matches( "[23]\\|true\\|true\\|true\\|Closed\\." );
	}

	/**
	 * Locator fill, type, select and press act on the located element itself.
	 */
	@DisplayName( "Locator shortcuts: fill, type, select and press act on the locator itself" )
	@Test
	public void testLocatorShortcuts() {
		// @formatter:off
		Object value = bx( """
			page = serve( pw.newContext() ).newPage().visit( "/login" )
			page.byLabel( "Email" ).fill( "a@b.com" )
			page.byPlaceholder( "Password" ).type( "xyz" )
			page.byTestId( "role" ).select( "editor" )
			page.byLabel( "Email" ).press( "End" )
			result = page.value( "Email" ) & "|" & page.value( "Password" ) & "|" & page.value( "@role" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "a@b.com|xyz|editor" );
	}

	/**
	 * Visibility checks look at every match: a hidden element with the same text does not hide a visible one, and
	 * assertMissing() passes only when no match is visible, without strict mode errors for several hidden matches.
	 */
	@DisplayName( "Visibility checks judge every match, not only the first one" )
	@Test
	public void testVisibilityChecksEveryMatch() {
		// @formatter:off
		Object value = bx( """
			page = pw.newPage( { timeouts : { action : 1000, assertion : 1000 } } )
			page.setContent( "<span style='display:none'>Menu</span><span>Menu</span><p class=t style='display:none'>a</p><p class=t hidden>b</p>" )
			page.assertVisible( "Menu" ).waitForText( "Menu", 1000 ).waitFor( "Menu", "visible", 1000 ).assertMissing( ".t" )
			missingFailed = false
			try {
				page.assertMissing( "Menu" )
			} catch ( "Playwright.AssertionFailed" e ) {
				missingFailed = true
			}
			result = page.isVisible( "Menu" ) & "|" & page.locator( "span" ).isVisible() & "|" & page.isVisible( ".t" ) & "|" & missingFailed
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "true|true|false|true" );
	}

	/**
	 * count( text ) and expect( text ).toHaveCount() count every element with the text, not only the first one.
	 */
	@DisplayName( "count() and toHaveCount() count every text match" )
	@Test
	public void testCountEveryTextMatch() {
		// @formatter:off
		Object value = bx( """
			page = pw.newPage( { timeouts : { assertion : 1000 } } )
			page.setContent( "<p>Apple pie</p><p>Apple tart</p><div>Pear</div>" )
			page.expect( "Apple" ).toHaveCount( 2 ).toHaveText( "Apple pie" )
			result = page.count( "Apple" ) & "|" & page.locator( "body" ).count( "Apple" ) & "|" & page.count( "p" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "2|2|2" );
	}

	/**
	 * assertCount() resolves @alias selectors registered by page objects and components.
	 */
	@DisplayName( "assertCount() honors element aliases" )
	@Test
	public void testAssertCountAliases() {
		// @formatter:off
		Object value = bx( """
			page = pw.newPage( { timeouts : { assertion : 1000 } } )
			page.setContent( "<div id=cart>2 items</div><p>Apple pie</p><p>Apple tart</p><b data-testid=total>3</b>" )
			page.useElements( { cartBox : "##cart", fruit : "Apple" } )
				.assertCount( "@cartBox", 1 )
				.assertCount( "@fruit", 2 )
				.assertCount( "@total", 1 )
			result = page.count( "@fruit" )
		""" );
		// @formatter:on
		assertThat( value.toString() ).isEqualTo( "2" );
	}

	/**
	 * assertPathIs() is case sensitive and works for file URLs, which have no host.
	 */
	@DisplayName( "assertPathIs() is case sensitive and supports file URLs" )
	@Test
	public void testAssertPathIsCaseAndFileUrls() {
		// @formatter:off
		Object value = bx( """
			page = serve( pw.newContext( { timeouts : { assertion : 500 } } ) ).newPage()
			page.visit( "/dashboard" ).assertPathIs( "/dashboard" )
			wrongCase = false
			try {
				page.assertPathIs( "/Dashboard" )
			} catch ( "Playwright.AssertionFailed" e ) {
				wrongCase = true
			}
			tempFile = createObject( "java", "java.io.File" ).createTempFile( "bxpw-path", ".html" )
			tempFile.deleteOnExit()
			fileWrite( tempFile.getAbsolutePath(), "<p>on disk</p>" )
			pw.newPage().visit( tempFile.toURI().toString() ).assertPathIs( tempFile.toURI().getPath() )
			result = wrongCase
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( true );
	}

	/**
	 * assertNoSmoke() reports JavaScript errors on every visited URL, also after a page that had errors of its own.
	 */
	@DisplayName( "assertNoSmoke() catches errors on every visited URL" )
	@Test
	public void testSmokeCatchesErrorsOnEveryUrl() {
		// @formatter:off
		Object value = bx( """
			page = pw.newPage()
			page.setContent( "<p>start</p>" )
			failure = ""
			try {
				page.assertNoSmoke( [
					"data:text/html,<script>console.error( 'first-boom' )</script>",
					"data:text/html,<script>console.error( 'second-boom' )</script>",
					"data:text/html,<p>clean</p>"
				] )
			} catch ( "Playwright.AssertionFailed" e ) {
				failure = e.message
			}
			result = ( failure contains "2 problem(s)" ) & "|" & ( failure contains "first-boom" ) & "|" & ( failure contains "second-boom" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "true|true|true" );
	}

	/**
	 * freezeTime() accepts BoxLang dates as well as ISO strings.
	 */
	@DisplayName( "freezeTime() accepts BoxLang dates" )
	@Test
	public void testFreezeTimeWithBoxLangDate() {
		// @formatter:off
		Object value = bx( """
			page = pw.newPage()
			page.freezeTime( createDateTime( 2031, 6, 15, 12, 0, 0 ) )
			page.setContent( "<p>x</p>" )
			result = page.evaluate( "new Date().getFullYear()" )
		""" );
		// @formatter:on
		assertThat( value.toString() ).matches( "2031(\\.0)?" );
	}

	/**
	 * filter( { has, hasNot } ) works with locators built from the page, and screenshots accept Locators and selectors as masks.
	 */
	@DisplayName( "filter( has/hasNot ) with page locators, screenshot masks with Locators" )
	@Test
	public void testFilterHasAndScreenshotMasks() {
		// @formatter:off
		Object value = bx( """
			page = pw.newPage()
			page.setContent( "<ul><li>One <b>x</b></li><li>Two</li></ul>" )
			items   = page.locator( "li" )
			has     = items.filter( { has : page.locator( "b" ) } ).texts()
			hasNot  = items.filter( { hasNot : page.locator( "b" ) } ).texts()
			shot    = page.screenshot( "", { mask : [ page.locator( "b" ), "Two" ] } )
			element = items.first().screenshot( "", { mask : page.locator( "b" ) } )
			result  = has.toList() & "|" & hasNot.toList() & "|" & ( arrayLen( shot ) > 0 ) & "|" & ( arrayLen( element ) > 0 ) & "|" & page.count( "b" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "One x|Two|true|true|1" );
	}

	/**
	 * assertScreenshotMatches() resolves a relative directory against the working directory, not the installed module.
	 */
	@DisplayName( "assertScreenshotMatches() resolves a relative directory against the working directory" )
	@Test
	public void testScreenshotRelativeDirectory() {
		String	relative	= "build/e2e-relative-snapshots-" + System.nanoTime();
		Path	expected	= Path.of( System.getProperty( "user.dir" ), relative, "relative.png" );
		// @formatter:off
		bx( """
			page = pw.newPage()
			page.setContent( "<h1>Relative</h1>" )
			page.assertScreenshotMatches( "relative", { directory : "%s" } )
			result = true
		""".formatted( relative ) );
		// @formatter:on
		assertThat( expected.toFile().exists() ).isTrue();
	}

	/**
	 * A nested soft() adds its failures to the outer soft(), which fails once with all of them.
	 */
	@DisplayName( "Nested soft() keeps the outer failures" )
	@Test
	public void testNestedSoftAssertions() {
		// @formatter:off
		Object value = bx( """
			page = pw.newPage( { timeouts : { assertion : 300 } } )
			page.setContent( "<h1>Home</h1>" )
			failure = ""
			try {
				page.soft( ( p ) => {
					p.assertSee( "outer-missing" )
					p.soft( ( q ) => q.assertSee( "inner-missing" ) )
					p.assertSee( "Home" )
				} )
			} catch ( "Playwright.AssertionFailed" e ) {
				failure = e.message
			}
			result = ( failure contains "2 soft assertion(s)" ) & "|" & ( failure contains "outer-missing" ) & "|" & ( failure contains "inner-missing" ) & "|" & isNull( page.softCollector() )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "true|true|true|true" );
	}

	/**
	 * Locator.texts() skips hidden elements, and nth( 0 ) throws a typed error instead of returning the last element.
	 */
	@DisplayName( "texts() returns visible text only, nth( 0 ) throws" )
	@Test
	public void testTextsVisibleAndNthZero() {
		// @formatter:off
		Object value = bx( """
			page = pw.newPage()
			page.setContent( "<ul><li>one</li><li style='display:none'>secret</li><li>three</li></ul>" )
			items = page.locator( "li" )
			error = ""
			try {
				items.nth( 0 )
			} catch ( "Playwright.InvalidOption" e ) {
				error = e.message
			}
			result = items.texts().toList() & "|" & items.nth( 3 ).getJava().textContent() & "|" & ( error contains "1-based" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "one,three|three|true" );
	}

	/**
	 * upload( files ) on a locator sets the files of the locator itself, like fill( value ) and select( value ).
	 */
	@DisplayName( "Locator upload( files ) uploads to the locator itself" )
	@Test
	public void testLocatorUploadShortcut() {
		// @formatter:off
		Object value = bx( """
			one = createObject( "java", "java.io.File" ).createTempFile( "bxpw-cv", ".pdf" )
			two = createObject( "java", "java.io.File" ).createTempFile( "bxpw-cover", ".txt" )
			one.deleteOnExit()
			two.deleteOnExit()
			page = pw.newPage()
			page.setContent( "<label>Resume <input type=file id=cv></label><label>Docs <input type=file id=docs multiple></label>" )
			page.byLabel( "Resume" ).upload( one.getAbsolutePath() )
			page.byLabel( "Docs" ).upload( [ one.getAbsolutePath(), two.getAbsolutePath() ] )
			result = page.evaluate( "document.getElementById( 'cv' ).files[ 0 ].name.endsWith( '.pdf' ) + '|' + document.getElementById( 'docs' ).files.length" )
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "true|2" );
	}

	/**
	 * The artifacts folder of a context is named after the current date and time (yyyyMMdd-HHmmss) plus a short unique id.
	 */
	@DisplayName( "Artifact folders are named after today's date and time" )
	@Test
	public void testArtifactFolderName() {
		String	dir		= Path.of( "build", "e2e-artifacts-names" ).toAbsolutePath().toString().replace( "\\", "/" );
		// @formatter:off
		Object value = bx( """
			ctx    = pw.newContext( { artifacts : { directory : "%s", screenshot : "on" } } )
			folder = listLast( ctx.artifacts().directory, "/" )
			ctx.close()
			result = folder
		""".formatted( dir ) );
		// @formatter:on
		assertThat( value.toString() ).matches( "[0-9]{8}-[0-9]{6}-[0-9a-f]{8}" );
		assertThat( value.toString() ).startsWith( LocalDate.now().format( DateTimeFormatter.BASIC_ISO_DATE ) );
	}

	/**
	 * render() resolves relative assets against the baseURL option (with or without a head element) and accepts a WIDTHxHEIGHT viewport
	 * string, while an invalid viewport fails with Playwright.InvalidOption.
	 *
	 * @throws IOException if the local server cannot start
	 */
	@DisplayName( "render(): baseURL resolves relative assets, WxH viewports" )
	@Test
	public void testRenderBaseURLAndViewport() throws IOException {
		BufferedImage			dot		= new BufferedImage( 4, 4, BufferedImage.TYPE_INT_RGB );
		ByteArrayOutputStream	png		= new ByteArrayOutputStream();
		AtomicInteger			hits	= new AtomicInteger();
		ImageIO.write( dot, "png", png );
		HttpServer server = HttpServer.create( new InetSocketAddress( "127.0.0.1", 0 ), 0 );
		server.createContext( "/assets/dot.png", exchange -> {
			hits.incrementAndGet();
			byte[] out = png.toByteArray();
			exchange.getResponseHeaders().add( "Content-Type", "image/png" );
			exchange.sendResponseHeaders( 200, out.length );
			exchange.getResponseBody().write( out );
			exchange.close();
		} );
		server.start();
		try {
			// @formatter:off
			Object value = run( """
				base    = "http://127.0.0.1:%d/assets/"
				image   = playwright().render( "<html><head><title>x</title></head><body><img src=""dot.png""></body></html>", { type : "png", baseURL : base, viewport : "300x200" } )
				plain   = playwright().render( "<p>no head</p><img src=""dot.png"">", { type : "png", baseURL : base } )
				picture = createObject( "java", "javax.imageio.ImageIO" ).read( createObject( "java", "java.io.ByteArrayInputStream" ).init( image ) )
				invalid = ""
				try {
					playwright().render( "<p>x</p>", { type : "png", viewport : "big" } )
				} catch ( "Playwright.InvalidOption" e ) {
					invalid = "invalid"
				}
				result = picture.getWidth() & "x" & picture.getHeight() & "|" & invalid
			""".formatted( server.getAddress().getPort() ) );
			// @formatter:on
			assertThat( value ).isEqualTo( "300x200|invalid" );
			assertThat( hits.get() ).isAtLeast( 2 );
		} finally {
			server.stop( 0 );
		}
	}

	/**
	 * Closing a client from playwright().request() stops the driver that request() started, and leaves a manager that was already running alone.
	 */
	@DisplayName( "request().close() stops the driver it started" )
	@Test
	public void testRequestCloseStopsDriver() {
		// @formatter:off
		Object value = run( """
			fresh = playwright()
			api   = fresh.request()
			api.close()
			running = playwright()
			running.getJava()
			try {
				other = running.request()
				other.close()
				stillRunning = running.isStarted()
			} finally {
				running.close()
			}
			result = fresh.isStarted() & "|" & stillRunning
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "false|true" );
	}

	/**
	 * visit() on a URL that fails closes the context and the manager it started, then rethrows the navigation error.
	 */
	@DisplayName( "visit() cleans up when the navigation fails" )
	@Test
	public void testVisitFailureCleansUp() {
		// @formatter:off
		Object value = run( """
			manager = playwright()
			failure = ""
			try {
				manager.visit( "http://127.0.0.1:1/" )
			} catch ( any e ) {
				failure = e.type
			}
			result = failure.left( 11 ) & "|" & manager.isStarted()
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "Playwright.|false" );
	}

	/**
	 * session() on a manager configured with the same session works before the session exists, and a refresh starts from a clean page
	 * instead of the stale saved state.
	 */
	@DisplayName( "session() setup never loads the configured session" )
	@Test
	public void testSessionOnConfiguredManager() {
		// @formatter:off
		Object value = bx( """
			name = "configured-session-test"
			file = pw.getConfigService().sessionPath( name )
			if ( fileExists( file ) ) {
				fileDelete( file )
			}
			seen  = []
			setup = ( page ) => {
				seen.append( page.context().cookies().len() )
				page.context().addCookies( [ { name : "auth", value : "v" & seen.len(), url : "http://app.test/" } ] )
			}
			configured = playwright( { session : name } )
			try {
				configured.session( name, setup )
				configured.session( name, setup, { refresh : true } )
				token = configured.newPage().context().cookies().filter( ( c ) -> c.name == "auth" )[ 1 ].value
			} finally {
				configured.close()
			}
			result = seen.toList() & "|" & token
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( "0,0|v2" );
	}

	/**
	 * The AI browser acts on refs of elements inside iframes, which ai snapshots label like f1e2.
	 */
	@DisplayName( "AI browser: refs inside iframes" )
	@Test
	public void testAiBrowserIframeRefs() {
		// @formatter:off
		Object value = bx( """
			html = '<h1>Outer</h1><iframe srcdoc=''<button onclick="this.textContent=`Done`">Inner</button>''></iframe>'
			ctx  = pw.newContext()
			ctx.intercept( "http://frame.test/**" ).handle( ( route ) => {
				route.fulfill( OptionsMapper.build( "Route.FulfillOptions", { status : 200, contentType : "text/html", body : html } ) )
			} )
			browser = new models.AiBrowser@playwright( { newPage : () => ctx.newPage() } )
			state   = browser.visit( "http://frame.test/" )
			found   = reFind( "button ""Inner"" \\[ref=(f[0-9]+e[0-9]+)\\]", state, 1, true )
			ref     = found.pos[ 1 ] ? found.match[ 2 ] : ""
			after   = len( ref ) ? browser.click( ref ) : state
			browser.close()
			result  = ref & "|" & ( after contains 'button "Done"' )
		""" );
		// @formatter:on
		assertThat( value.toString() ).matches( "f[0-9]+e[0-9]+\\|true" );
	}

}
