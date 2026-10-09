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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

public class IntegrationTest extends BaseIntegrationTest {

	/**
	 * The module is registered and its settings include the default profile and a Node.js version.
	 */
	@DisplayName( "The module loads with its settings" )
	@Test
	public void testModuleLoads() {
		assertThat( moduleService.getRegistry().containsKey( moduleName ) ).isTrue();
		IStruct settings = moduleService.getModuleSettings( moduleName );
		assertThat( settings.getAsString( Key.of( "defaultProfile" ) ) ).isEqualTo( "default" );
		assertThat( settings.getAsString( Key.of( "nodeVersion" ) ) ).isNotEmpty();
	}

	/**
	 * The in-process browser installer BIF is registered and rejects unsupported browser names before setup.
	 */
	@DisplayName( "The browser installer BIF validates browser names" )
	@Test
	public void testEnsureBrowserRejectsUnsupportedBrowser() {
		Object value = run( """
		                    	try {
		                    		playwrightEnsureBrowser( "safari" )
		                    		result = "not rejected"
		                    	} catch ( any e ) {
		                    		result = e.type
		                    	}
		                    """ );
		assertThat( value ).isEqualTo( "Playwright.InvalidOption" );
	}

}
