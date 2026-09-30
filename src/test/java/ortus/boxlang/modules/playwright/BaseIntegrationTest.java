/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import ortus.boxlang.modules.playwright.util.KeyDictionary;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.services.ModuleService;

/**
 * Base integration test: boots a BoxLang runtime and loads the built module from build/module.
 */
public abstract class BaseIntegrationTest {

	protected static BoxRuntime				runtime;
	protected static ModuleService			moduleService;
	protected static ModuleRecord			moduleRecord;
	protected static Key					result		= new Key( "result" );
	protected static Key					moduleName	= KeyDictionary.moduleName;
	protected ScriptingRequestBoxContext	context;
	protected IScope						variables;

	/**
	 * Boot the BoxLang runtime with the test configuration and load the built module once for the whole test class.
	 */
	@BeforeAll
	public static void setup() {
		runtime			= BoxRuntime.getInstance( true, Path.of( "src/test/resources/boxlang.json" ).toString() );
		moduleService	= runtime.getModuleService();
		// Load the module
		loadModule( runtime.getRuntimeContext() );
	}

	/**
	 * Create a fresh scripting request context and grab its variables scope before each test.
	 */
	@BeforeEach
	public void setupEach() {
		// Create the mock contexts
		context		= new ScriptingRequestBoxContext();
		variables	= context.getScopeNearby( VariablesScope.name );
	}

	/**
	 * Register and activate the module from build/module, unless the module service already has it loaded.
	 *
	 * @param context The context used to load, register and activate the module
	 */
	protected static void loadModule( IBoxContext context ) {
		if ( !runtime.getModuleService().hasModule( moduleName ) ) {
			System.out.println( "Loading module: " + moduleName );
			String physicalPath = Paths.get( "./build/module" ).toAbsolutePath().toString();
			moduleRecord = new ModuleRecord( physicalPath );

			moduleService.getRegistry().put( moduleName, moduleRecord );

			moduleRecord
			    .loadDescriptor( context )
			    .register( context )
			    .activate( context );
		} else {
			// The runtime also auto-loads modules installed in the user's BoxLang home (e.g. by install-bx-module).
			// Testing such a copy instead of build/module silently ignores every source change, so fail loudly.
			Path	loaded	= moduleService.getModuleRecord( moduleName ).physicalPath.toAbsolutePath().normalize();
			Path	built	= Paths.get( "./build/module" ).toAbsolutePath().normalize();
			if ( !loaded.equals( built ) ) {
				throw new IllegalStateException(
				    "The tests loaded the playwright module from [" + loaded + "] instead of [" + built
				        + "]. Remove the installed copy (install-bx-module --remove bx-playwright) or move it out of the BoxLang modules folder."
				);
			}
			System.out.println( "Module already loaded: " + moduleName );
		}
	}

	/**
	 * Execute BoxLang code and return the value of the `result` variable.
	 *
	 * @param code The BoxLang code, which should set a `result` variable
	 *
	 * @return The value of `result`
	 */
	protected Object run( String code ) {
		runtime.executeSource( code, context );
		return variables.get( result );
	}

	/**
	 * Execute BoxLang code and capture what it prints to the console.
	 *
	 * @param code The BoxLang code
	 *
	 * @return The console output
	 */
	protected String capture( String code ) {
		PrintStream				original	= context.getOut();
		ByteArrayOutputStream	buffer		= new ByteArrayOutputStream();
		context.setOut( new PrintStream( buffer, true, StandardCharsets.UTF_8 ) );
		try {
			runtime.executeSource( code, context );
		} finally {
			context.getOut().flush();
			context.setOut( original );
		}
		return buffer.toString( StandardCharsets.UTF_8 );
	}

}
