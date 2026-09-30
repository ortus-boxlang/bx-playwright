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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ortus.boxlang.modules.playwright.BaseIntegrationTest;

/**
 * Runs every example in examples/ so the documentation and skills that copy them stay correct.
 */
public class ExamplesE2ETest extends BaseIntegrationTest {

	/**
	 * Prepare the end-to-end home and map /pages to the examples page objects.
	 */
	@BeforeEach
	public void prepare() {
		E2E.home();
		runtime.getConfiguration().registerMapping( "/pages", Path.of( "examples/pages" ).toAbsolutePath().toString() );
	}

	/**
	 * Each example script runs and prints its expected output.
	 *
	 * @param file     The example file name under examples/
	 * @param expected Text the example output must contain
	 */
	@ParameterizedTest( name = "{0}" )
	@CsvSource( {
	    "render-pdf-and-images.bxs, PDF:",
	    "login-flow.bxs, 'Users: Luis, Brad, Jon'",
	    "page-objects.bxs, Logged in as Luis",
	    "quality-checks.bxs, Accessibility issues found:",
	    "ai-agent-tools.bxs, browser_visit"
	} )
	public void testExample( String file, String expected ) throws IOException {
		String output = capture( Files.readString( Path.of( "examples", file ) ) );
		assertThat( output ).contains( expected );
	}

}
