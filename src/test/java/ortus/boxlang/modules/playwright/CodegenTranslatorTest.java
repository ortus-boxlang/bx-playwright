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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;

public class CodegenTranslatorTest extends BaseIntegrationTest {

	// A recording in the format written by `playwright codegen --target java`
	private static final String RECORDING = """
	                                        import com.microsoft.playwright.*;
	                                        import com.microsoft.playwright.options.*;
	                                        import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
	                                        import java.util.*;

	                                        public class Example {
	                                          public static void main(String[] args) {
	                                            try (Playwright playwright = Playwright.create()) {
	                                              Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
	                                                .setHeadless(false));
	                                              BrowserContext context = browser.newContext();
	                                              Page page = context.newPage();
	                                              page.navigate("http://localhost:8080/login");
	                                              page.getByLabel("Email").click();
	                                              page.getByLabel("Email").fill("luis@ortus.com");
	                                              page.getByPlaceholder("Password").fill("secret");
	                                              page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in").setExact(true)).click();
	                                              page.locator("#menu li").nth(1).click();
	                                              page.getByTestId("role").selectOption("editor");
	                                              page.getByText("Remember me").press("Enter");
	                                              assertThat(page.getByRole(AriaRole.HEADING)).containsText("Welcome");
	                                              assertThat(page.getByText("Error")).not().isVisible();
	                                              page.close();
	                                            }
	                                          }
	                                        }
	                                        """;

	/**
	 * Put the sample codegen recording in the variables scope as "recording" before each test.
	 */
	@BeforeEach
	public void loadRecording() {
		variables.put( Key.of( "recording" ), RECORDING );
	}

	/**
	 * A Java codegen recording is translated into a browse() script: navigation, locators, actions and assertions map to the DSL, nth() becomes 1-based
	 * and the browser boilerplate is dropped.
	 */
	@DisplayName( "Recorded Java becomes the BoxLang DSL" )
	@Test
	public void testTranslate() {
		String code = run( "result = new models.cli.CodegenTranslator@playwright().translate( recording )" ).toString();
		assertThat( code ).startsWith( "playwright().browse( ( page ) => {\n" );
		assertThat( code ).contains( "\tpage.visit( \"http://localhost:8080/login\" )\n" );
		assertThat( code ).contains( "\tpage.byLabel( \"Email\" ).fill( \"luis@ortus.com\" )\n" );
		assertThat( code ).contains( "\tpage.byPlaceholder( \"Password\" ).fill( \"secret\" )\n" );
		assertThat( code ).contains( "\tpage.byRole( \"button\", { name : \"Sign in\", exact : true } ).click()\n" );
		assertThat( code ).contains( "\tpage.locator( \"##menu li\" ).nth( 2 ).click()\n" );
		assertThat( code ).contains( "\tpage.byTestId( \"role\" ).select( \"editor\" )\n" );
		assertThat( code ).contains( "\tpage.byText( \"Remember me\" ).press( \"Enter\" )\n" );
		assertThat( code ).contains( "\tpage.byRole( \"heading\" ).expect().toContainText( \"Welcome\" )\n" );
		assertThat( code ).contains( "\tpage.byText( \"Error\" ).expect().not().toBeVisible()\n" );
		assertThat( code ).contains( "\tpage.close()\n" );
		assertThat( code ).doesNotContain( "Browser browser" );
		assertThat( code ).endsWith( "} )\n" );
	}

	/**
	 * A statement the translator does not understand is kept as a "// TODO translate:" comment.
	 */
	@DisplayName( "Unknown statements are kept as comments" )
	@Test
	public void testUnknown() {
		Object value = run( "result = new models.cli.CodegenTranslator@playwright().translateStatement( \"context.close()\" )" );
		assertThat( value ).isEqualTo( "// TODO translate: context.close()" );
	}

	/**
	 * Translate a Java snippet wrapped in the codegen boilerplate and return the BoxLang script.
	 *
	 * @param body The recorded statements, one per line
	 *
	 * @return The translated BoxLang script
	 */
	private String translate( String body ) {
		String java = "public class Example {\n  public static void main(String[] args) {\n    try (Playwright playwright = Playwright.create()) {\n"
		    + "      Page page = context.newPage();\n" + body + "\n    }\n  }\n}\n";
		variables.put( Key.of( "java" ), java );
		return run( "result = new models.cli.CodegenTranslator@playwright().translate( java )" ).toString();
	}

	/**
	 * Java string literals become BoxLang strings: "#" is doubled (no interpolation), \" becomes "" and Java escapes are resolved.
	 */
	@DisplayName( "Java strings become valid BoxLang strings" )
	@Test
	public void testStrings() {
		String code = translate( """
		                               page.locator("#out").click();
		                               assertThat(page.getByRole(AriaRole.HEADING)).hasText("Welcome \\"friend\\"");
		                               page.locator("iframe[name=\\"a\\\\b\\"]").fill("tab\\there");
		                               page.getByText("50% #1 \\u00e9").click();
		                         """ );
		assertThat( code ).contains( "\tpage.locator( \"##out\" ).click()\n" );
		assertThat( code ).contains( "\tpage.byRole( \"heading\" ).expect().toHaveText( \"Welcome \"\"friend\"\"\" )\n" );
		assertThat( code ).contains( "\tpage.locator( \"iframe[name=\"\"a\\b\"\"]\" ).fill( \"tab\there\" )\n" );
		assertThat( code ).contains( "\tpage.byText( \"50% ##1 é\" ).click()\n" );
	}

	/**
	 * Pattern.compile() becomes page.regex() with the Java escapes resolved and the flags mapped to "i", "m" and "s".
	 */
	@DisplayName( "Patterns become page.regex() with flags" )
	@Test
	public void testPatterns() {
		String code = translate(
		    """
		          assertThat(page.locator("h1")).hasText(Pattern.compile("Welcome\\\\s+#"));
		          assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("hello", Pattern.CASE_INSENSITIVE)))).isVisible();
		    """ );
		assertThat( code ).contains( "\tpage.locator( \"h1\" ).expect().toHaveText( page.regex( \"Welcome\\s+##\" ) )\n" );
		assertThat( code ).contains( "\tpage.byRole( \"heading\", { name : page.regex( \"hello\", \"i\" ) } ).expect().toBeVisible()\n" );
	}

	/**
	 * Any enum constant argument becomes a lower case string, and click options are passed by name on a locator.
	 */
	@DisplayName( "Enum constants become strings" )
	@Test
	public void testEnums() {
		String code = translate( """
		                               page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Menu")).click(new Locator.ClickOptions()
		                                 .setButton(MouseButton.RIGHT));
		                               page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		                               page.getByText("Row").click(new Locator.ClickOptions().setModifiers(Arrays.asList(KeyboardModifier.CONTROL_OR_META)));
		                         """ );
		assertThat( code ).contains( "\tpage.byRole( \"button\", { name : \"Menu\" } ).click( options = { button : \"right\" } )\n" );
		assertThat( code ).contains( "\tpage.waitForLoadState( \"domcontentloaded\" )\n" );
		assertThat( code ).contains( "\tpage.byText( \"Row\" ).click( options = { modifiers : [ \"control-or-meta\" ] } )\n" );
		assertThat( code ).doesNotContain( "MouseButton" );
	}

	/**
	 * locator( selector ).contentFrame() becomes frame( selector ), whose finders work inside the iframe.
	 */
	@DisplayName( "contentFrame() becomes frame()" )
	@Test
	public void testFrames() {
		String code = translate(
		    """
		          page.locator("iframe[name=\\"inner\\"]").contentFrame().getByRole(AriaRole.BUTTON, new FrameLocator.GetByRoleOptions().setName("Pay")).click();
		          page.getByTitle("Map").contentFrame().getByText("Zoom").click();
		    """ );
		assertThat( code ).contains( "\tpage.frame( \"iframe[name=\"\"inner\"\"]\" ).byRole( \"button\", { name : \"Pay\" } ).click()\n" );
		assertThat( code ).contains( "\t// TODO translate: page.getByTitle(\"Map\").contentFrame()" );
	}

	/**
	 * setInputFiles() becomes upload() with a path, or an array of paths for several files.
	 */
	@DisplayName( "setInputFiles() becomes upload()" )
	@Test
	public void testUploads() {
		String code = translate( """
		                               page.getByLabel("Upload file").setInputFiles(Paths.get("page.html"));
		                               page.getByLabel("Files").setInputFiles(new Path[] {Paths.get("a.txt"), Paths.get("b.txt")});
		                               page.getByLabel("Files").setInputFiles(new Path[0]);
		                         """ );
		assertThat( code ).contains( "\tpage.byLabel( \"Upload file\" ).upload( \"page.html\" )\n" );
		assertThat( code ).contains( "\tpage.byLabel( \"Files\" ).upload( [ \"a.txt\", \"b.txt\" ] )\n" );
		assertThat( code ).contains( "\tpage.byLabel( \"Files\" ).upload( [] )\n" );
	}

	/**
	 * Popups, downloads and dialog handlers become DSL callbacks; statements on the popup page are translated and
	 * unknown lambda blocks are kept as comments.
	 */
	@DisplayName( "Popups, downloads and dialogs become callbacks" )
	@Test
	public void testCallbacks() {
		String code = translate( """
		                               page.onceDialog(dialog -> {
		                                 System.out.println(String.format("Dialog message: %s", dialog.message()));
		                                 dialog.dismiss();
		                               });
		                               page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Alert")).click();
		                               Page page1 = page.waitForPopup(() -> {
		                                 page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Docs")).click();
		                               });
		                               page1.getByRole(AriaRole.HEADING).click();
		                               Download download = page.waitForDownload(() -> {
		                                 page.getByText("Get file").click();
		                               });
		                               page.waitForRequest("**/api", () -> {
		                                 page.getByText("Load").click();
		                               });
		                         """ );
		// @formatter:off
		assertThat( code ).contains(
			"\tpage.getJava().onceDialog( ( dialog ) => {\n"
			+ "\t\t// TODO translate: System.out.println(String.format(\"Dialog message: %s\", dialog.message()))\n"
			+ "\t\tdialog.dismiss()\n"
			+ "\t} )\n"
			+ "\tpage.byRole( \"button\", { name : \"Alert\" } ).click()\n"
			+ "\tvar page1 = page.waitForPopup( () => {\n"
			+ "\t\tpage.byRole( \"link\", { name : \"Docs\" } ).click()\n"
			+ "\t} )\n"
			+ "\tpage1.byRole( \"heading\" ).click()\n"
			+ "\tvar download = page.waitForDownload( () => {\n"
			+ "\t\tpage.byText( \"Get file\" ).click()\n"
			+ "\t} )\n"
			+ "\t// TODO translate: page.waitForRequest(\"**/api\", () -> {\n"
			+ "\t\tpage.byText( \"Load\" ).click()\n"
			+ "\t// TODO translate: })\n"
		);
		// @formatter:on
	}

	/**
	 * Text blocks (multi-line aria snapshots) become strings, exact options become the exact argument and locator options a filter().
	 */
	@DisplayName( "Text blocks, exact finders and locator options" )
	@Test
	public void testTextBlocksAndOptions() {
		String code = translate( "      assertThat(page.locator(\"body\")).matchesAriaSnapshot(\"\"\"\n"
		    + "        - heading \"Hello\" [level=1]\n"
		    + "        - list:\n"
		    + "          - listitem: One\n"
		    + "        \"\"\");\n"
		    + "      page.getByText(\"Three\", new Page.GetByTextOptions().setExact(true)).click();\n"
		    + "      page.locator(\"li\", new Page.LocatorOptions().setHasText(\"Two\")).click();\n"
		    + "      page.getByLabel(\"Country\").selectOption(new String[] {\"uk\", \"us\"});" );
		assertThat( code ).contains(
		    "\tpage.locator( \"body\" ).expect().toMatchAriaSnapshot( \"- heading \"\"Hello\"\" [level=1]\n- list:\n  - listitem: One\n\" )\n" );
		assertThat( code ).contains( "\tpage.byText( \"Three\", true ).click()\n" );
		assertThat( code ).contains( "\tpage.locator( \"li\" ).filter( { hasText : \"Two\" } ).click()\n" );
		assertThat( code ).contains( "\tpage.byLabel( \"Country\" ).select( [ \"uk\", \"us\" ] )\n" );
	}

	/**
	 * A full recording (the one written by Playwright 1.63 codegen) translates into BoxLang that compiles.
	 */
	@DisplayName( "A translated recording compiles" )
	@Test
	public void testTranslatedCodeParses() {
		String	code	= translate( """
		                                   page.navigate("file:///tmp/page.html");
		                                   page.getByLabel("Email").fill("bob@example.com");
		                                   assertThat(page.locator("#out")).containsText("Signed in as bob@example.com");
		                                   assertThat(page.getByRole(AriaRole.HEADING)).hasText("Welcome \\"friend\\"");
		                                   assertThat(page.locator("h1")).hasText(Pattern.compile("Welcome\\\\s+"));
		                                   page.locator("iframe[name=\\"inner\\"]").contentFrame().getByRole(AriaRole.BUTTON).click();
		                                   page.getByRole(AriaRole.BUTTON).click(new Locator.ClickOptions().setButton(MouseButton.RIGHT));
		                                   Page page1 = page.waitForPopup(() -> {
		                                     page.getByText("Docs").click();
		                                   });
		                             """ );
		// Wrapping the script in a function compiles it without running the browser
		Object	value	= run( "function recorded() {\n" + code + "}\nresult = \"compiled\"" );
		assertThat( value ).isEqualTo( "compiled" );
	}

}
