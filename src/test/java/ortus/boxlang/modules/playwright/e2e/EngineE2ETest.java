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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import ortus.boxlang.modules.playwright.engine.NodeRuntime;
import ortus.boxlang.modules.playwright.engine.PlaywrightHome;

public class EngineE2ETest {

	/**
	 * The engine resolves a downloaded or bundled Node.js, launches Chromium and reads the text of a rendered heading.
	 */
	@DisplayName( "The engine downloads Node.js, installs Chromium and drives a page" )
	@Test
	public void testLaunchChromium() {
		PlaywrightHome home = E2E.home();
		assertThat( home.resolveNode().get().getSource() ).isAnyOf( NodeRuntime.Source.DOWNLOADED, NodeRuntime.Source.BUNDLED );

		try ( Playwright playwright = home.createPlaywright(); Browser browser = playwright.chromium().launch() ) {
			Page page = browser.newPage();
			page.setContent( "<h1>Hello BoxLang</h1>" );
			assertThat( page.locator( "h1" ).textContent() ).isEqualTo( "Hello BoxLang" );
		}
	}

}
