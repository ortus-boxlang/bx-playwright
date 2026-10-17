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
package ortus.boxlang.modules.bxplaywright;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.Key;

/**
 * With the bx-ai module loaded, the browser tools register themselves in aiToolRegistry() as browser_*@bxplaywright.
 * Needs bx-ai unpacked in build/test-modules/bxai (./gradlew extractBxAi); the tests skip themselves without it.
 */
public class AiRegistrationTest extends BaseIntegrationTest {

	private static final Path BX_AI = Path.of( "build/test-modules/bxai" );

	/**
	 * Load bx-ai into the shared runtime, after bx-playwright, so the tests also cover the module that loads second.
	 */
	@BeforeAll
	public static void loadBxAi() {
		assumeTrue( Files.isDirectory( BX_AI ), "bx-ai not found in build/test-modules/bxai - run `./gradlew extractBxAi` first" );
		Key name = Key.of( "bxai" );
		if ( !moduleService.hasModule( name ) ) {
			ModuleRecord record = new ModuleRecord( BX_AI.toAbsolutePath().toString() );
			moduleService.getRegistry().put( name, record );
			record.loadDescriptor( runtime.getRuntimeContext() ).register( runtime.getRuntimeContext() ).activate( runtime.getRuntimeContext() );
		}
	}

	/**
	 * Registering adds the ten browser tools under the bxplaywright module and nothing under a bare name.
	 */
	@DisplayName( "The browser tools register as browser_*@bxplaywright, not under bare names" )
	@Test
	public void testRegisters() {
		// @formatter:off
		Object value = run( """
			registration = new models.AiRegistration@bxplaywright()
			registration.unregister()
			added  = registration.register()
			keys   = aiToolRegistry().getKeys().filter( ( k ) -> k contains "browser_" ).sort( "text" )
			result = added & "|" & keys.len() & "|" & keys.filter( ( k ) -> k.endsWith( "@bxplaywright" ) ).len() & "|" & aiToolRegistry().getByModule( "bxplaywright" ).len()
		""" );
		// @formatter:on
		assertThat( value.toString() ).isEqualTo( "10|10|10|10" );
	}

	/**
	 * Registering again changes nothing.
	 */
	@DisplayName( "Registering twice registers once" )
	@Test
	public void testIdempotent() {
		// @formatter:off
		Object value = run( """
			registration = new models.AiRegistration@bxplaywright()
			registration.unregister()
			first  = registration.register()
			second = registration.register()
			result = first & "|" & second & "|" & aiToolRegistry().getByModule( "bxplaywright" ).len()
		""" );
		// @formatter:on
		assertThat( value.toString() ).isEqualTo( "10|0|10" );
	}

	/**
	 * Unregistering removes them again.
	 */
	@DisplayName( "Unregistering removes the tools" )
	@Test
	public void testUnregisters() {
		// @formatter:off
		Object value = run( """
			registration = new models.AiRegistration@bxplaywright()
			registration.register()
			registration.unregister()
			result = aiToolRegistry().getByModule( "bxplaywright" ).len()
		""" );
		// @formatter:on
		assertThat( value ).isEqualTo( 0 );
	}

	/**
	 * aiTools() hands out tools without adding them to the registry under bare names.
	 */
	@DisplayName( "aiTools() does not leak bare-named tools into the registry" )
	@Test
	public void testAiToolsDoNotLeak() {
		// @formatter:off
		Object value = run( """
			registration = new models.AiRegistration@bxplaywright()
			registration.unregister()
			tools  = playwright().aiTools()
			result = tools.len() & "|" & aiToolRegistry().getKeys().filter( ( k ) -> k == "browser_visit" ).len()
		""" );
		// @formatter:on
		assertThat( value.toString() ).isEqualTo( "10|0" );
	}

}
