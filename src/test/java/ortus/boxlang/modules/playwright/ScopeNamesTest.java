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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * On a web runtime an unqualified scope name (url, form, request, cookie, ...) resolves to that scope, even when a
 * closure parameter, a local variable or an argument has the same name. The module also runs inside web requests
 * (TestBox and ColdBox browser specs), so its BoxLang source must never rely on such a name.
 */
public class ScopeNamesTest {

	private static final String		SCOPES			= "url|form|cgi|cookie|session|request|server|client|application";

	/** A closure or lambda parameter named after a scope: ( url ) => or ( request, x ) -> */
	private static final Pattern	CLOSURE_PARAM	= Pattern.compile( "\\(\\s*(" + SCOPES + ")\\s*(,[^)]*)?\\)\\s*(=>|->)" );

	/** A local variable named after a scope: var form = */
	private static final Pattern	LOCAL_VAR		= Pattern.compile( "\\bvar\\s+(" + SCOPES + ")\\s*=" );

	/** A bare scope name used as a value, for example hasURL( url, o ), not url.x, url(), arguments.url, a url : key or an assignment */
	private static final Pattern	BARE_VALUE		= Pattern.compile( "(?<![\\w.#\"'])(" + SCOPES + ")(?![\\w(.:\"'])(?!\\s*:)(?!\\s*=[^=])" );

	/**
	 * No closure parameter or local variable in the module source is named after a scope, and no scope-named
	 * argument is used without the arguments. prefix.
	 */
	@DisplayName( "BoxLang source never relies on names that clash with scopes on a web runtime" )
	@Test
	public void testNoScopeNames() throws IOException {
		List<String> problems = new ArrayList<>();
		try ( Stream<Path> files = Files.walk( Path.of( "src", "main", "bx" ) ) ) {
			for ( Path file : files.filter( f -> f.toString().endsWith( ".bx" ) ).toList() ) {
				List<String> lines = Files.readAllLines( file );
				for ( int i = 0; i < lines.size(); i++ ) {
					String line = lines.get( i ).trim();
					if ( line.startsWith( "*" ) || line.startsWith( "/*" ) || line.startsWith( "//" ) ) {
						continue;
					}
					String	code	= line.replaceAll( "\"[^\"]*\"", "\"\"" );
					String	where	= file + ":" + ( i + 1 ) + ": " + line;
					if ( CLOSURE_PARAM.matcher( code ).find() || LOCAL_VAR.matcher( code ).find() ) {
						problems.add( where );
						continue;
					}
					// Declarations such as "required string url" or "string url = ''" name the argument, they do not use it
					if ( code.matches( ".*\\bfunction\\b.*" ) ) {
						continue;
					}
					Matcher bare = BARE_VALUE.matcher( code );
					if ( bare.find() ) {
						problems.add( where );
					}
				}
			}
		}
		assertThat( problems ).isEmpty();
	}

}
