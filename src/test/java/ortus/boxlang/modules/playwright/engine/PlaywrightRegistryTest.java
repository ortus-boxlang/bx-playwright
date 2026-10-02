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
package ortus.boxlang.modules.playwright.engine;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PlaywrightRegistryTest {

	@AfterEach
	public void tearDown() {
		PlaywrightRegistry.closeAll();
	}

	@DisplayName( "closeAll() closes every registered instance once and forgets them" )
	@Test
	public void testCloseAll() {
		AtomicInteger	closes	= new AtomicInteger();
		AutoCloseable	first	= closes::incrementAndGet;
		AutoCloseable	second	= closes::incrementAndGet;
		assertThat( PlaywrightRegistry.register( first ) ).isSameInstanceAs( first );
		PlaywrightRegistry.register( second );
		assertThat( PlaywrightRegistry.openCount() ).isEqualTo( 2 );

		assertThat( PlaywrightRegistry.closeAll() ).isEqualTo( 2 );
		assertThat( closes.get() ).isEqualTo( 2 );
		assertThat( PlaywrightRegistry.openCount() ).isEqualTo( 0 );
		assertThat( PlaywrightRegistry.closeAll() ).isEqualTo( 0 );
		assertThat( closes.get() ).isEqualTo( 2 );
	}

	@DisplayName( "A released instance is not closed by closeAll()" )
	@Test
	public void testRelease() {
		AtomicInteger	closes		= new AtomicInteger();
		AutoCloseable	instance	= closes::incrementAndGet;
		PlaywrightRegistry.register( instance );
		PlaywrightRegistry.release( instance );
		assertThat( PlaywrightRegistry.openCount() ).isEqualTo( 0 );
		PlaywrightRegistry.closeAll();
		assertThat( closes.get() ).isEqualTo( 0 );
	}

	@DisplayName( "An instance that fails to close does not stop the others" )
	@Test
	public void testCloseFailure() {
		AtomicInteger	closes	= new AtomicInteger();
		AutoCloseable	broken	= () -> {
									throw new IllegalStateException( "driver gone" );
								};
		PlaywrightRegistry.register( broken );
		PlaywrightRegistry.register( ( AutoCloseable ) closes::incrementAndGet );
		assertThat( PlaywrightRegistry.closeAll() ).isEqualTo( 1 );
		assertThat( closes.get() ).isEqualTo( 1 );
		assertThat( PlaywrightRegistry.openCount() ).isEqualTo( 0 );
	}

}
