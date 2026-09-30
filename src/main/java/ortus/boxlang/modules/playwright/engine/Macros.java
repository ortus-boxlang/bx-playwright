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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The global registry of DSL macros: custom methods added to pages and locators.
 *
 * <pre>
 * playwright().macro( "loginAs", ( page, email ) => page.visit( "/login" ).fill( "Email", email ).click( "Sign in" ) )
 * page.loginAs( "luis@ortus.com" )
 * </pre>
 */
public final class Macros {

	/**
	 * The objects macros can be attached to.
	 */
	public static final List<String>						TARGETS		= List.of( "page", "locator" );

	private static final Map<String, Map<String, Object>>	REGISTRY	= new ConcurrentHashMap<>();

	private Macros() {
	}

	/**
	 * Register a macro, replacing any macro with the same name.
	 *
	 * @param target   page or locator
	 * @param name     The method name
	 * @param function The BoxLang function; it receives the page or locator first, then the call arguments
	 */
	public static void register( String target, String name, Object function ) {
		REGISTRY.computeIfAbsent( checkTarget( target ), key -> new ConcurrentHashMap<>() ).put( name.toLowerCase( Locale.ROOT ), function );
	}

	/**
	 * Find a macro.
	 *
	 * @param target page or locator
	 * @param name   The method name, case insensitive
	 *
	 * @return The function, or null
	 */
	public static Object get( String target, String name ) {
		Map<String, Object> macros = REGISTRY.get( checkTarget( target ) );
		return macros == null ? null : macros.get( name.toLowerCase( Locale.ROOT ) );
	}

	/**
	 * @param target page or locator
	 *
	 * @return The registered macro names, sorted
	 */
	public static List<String> names( String target ) {
		Map<String, Object> macros = REGISTRY.get( checkTarget( target ) );
		return macros == null ? List.of() : List.copyOf( new TreeSet<>( macros.keySet() ) );
	}

	/**
	 * Remove a macro, or all macros of a target when the name is null or empty.
	 *
	 * @param target page or locator
	 * @param name   The macro name, or null
	 */
	public static void remove( String target, String name ) {
		Map<String, Object> macros = REGISTRY.get( checkTarget( target ) );
		if ( macros == null ) {
			return;
		}
		if ( name == null || name.isBlank() ) {
			macros.clear();
		} else {
			macros.remove( name.toLowerCase( Locale.ROOT ) );
		}
	}

	private static String checkTarget( String target ) {
		String value = target == null ? "page" : target.toLowerCase( Locale.ROOT );
		if ( !TARGETS.contains( value ) ) {
			throw PlaywrightErrors.of( PlaywrightErrors.INVALID_OPTION, "Unknown macro target [" + target + "].", "Use page or locator." );
		}
		return value;
	}

}
