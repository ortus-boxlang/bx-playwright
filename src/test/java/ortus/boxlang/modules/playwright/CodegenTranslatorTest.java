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
		assertThat( code ).contains( "\tpage.locator( \"#menu li\" ).nth( 2 ).click()\n" );
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

}
