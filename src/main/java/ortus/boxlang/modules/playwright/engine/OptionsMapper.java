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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import ortus.boxlang.runtime.scopes.Key;

/**
 * Converts BoxLang structs into Playwright option objects, such as {@code Page.NavigateOptions}
 * or {@code Browser.NewContextOptions}, using their public setters.
 * <p>
 * This gives BoxLang code access to every Playwright option without hand-written glue:
 *
 * <pre>
 * { timeout: 5000, waitUntil: "networkidle" }         -> Page.NavigateOptions
 * { viewport: { width: 1280, height: 720 } }           -> Browser.NewContextOptions.setViewportSize( ViewportSize )
 * { colorScheme: "dark", permissions: [ "geolocation" ] }
 * </pre>
 *
 * Conversions: enums from strings (case and dash insensitive), paths from strings, nested option
 * objects from structs, lists and maps from arrays and structs, numbers and booleans from any
 * compatible value. Unknown keys fail with the list of valid option names.
 */
public final class OptionsMapper {

	private static final String										PLAYWRIGHT_PACKAGE	= "com.microsoft.playwright.";
	private static final String										OPTIONS_PACKAGE		= PLAYWRIGHT_PACKAGE + "options.";

	/**
	 * Friendly option names mapped to the Playwright setter names.
	 */
	private static final Map<String, String>						ALIASES				= Map.of(
	    "viewport", "viewportSize",
	    "timezone", "timezoneId",
	    "storagestatefile", "storageStatePath"
	);

	/**
	 * Constructor argument names for option classes that have no default constructor.
	 */
	private static final Map<String, String[]>						CONSTRUCTOR_ARGS	= Map.ofEntries(
	    Map.entry( "ViewportSize", new String[] { "width", "height" } ),
	    Map.entry( "RecordVideoSize", new String[] { "width", "height" } ),
	    Map.entry( "ScreenSize", new String[] { "width", "height" } ),
	    Map.entry( "Size", new String[] { "width", "height" } ),
	    Map.entry( "Geolocation", new String[] { "latitude", "longitude" } ),
	    Map.entry( "Position", new String[] { "x", "y" } ),
	    Map.entry( "Clip", new String[] { "x", "y", "width", "height" } ),
	    Map.entry( "Proxy", new String[] { "server" } ),
	    Map.entry( "HttpCredentials", new String[] { "username", "password" } ),
	    Map.entry( "Cookie", new String[] { "name", "value" } ),
	    Map.entry( "ClientCertificate", new String[] { "origin" } ),
	    Map.entry( "FilePayload", new String[] { "name", "mimeType", "buffer" } )
	);

	private static final Map<Class<?>, Map<String, List<Method>>>	SETTERS				= new ConcurrentHashMap<>();

	/**
	 * Static utility class, not instantiable.
	 */
	private OptionsMapper() {
	}

	/**
	 * Build an options object by its short Playwright name.
	 *
	 * @param className The class name relative to {@code com.microsoft.playwright}, e.g. {@code Page.NavigateOptions},
	 *                  {@code BrowserType.LaunchOptions} or {@code options.Margin}
	 * @param options   The options struct, may be null or empty
	 *
	 * @return The populated options object
	 */
	public static Object build( String className, Map<?, ?> options ) {
		return map( resolveClass( className ), options );
	}

	/**
	 * Build an options object of the given type.
	 *
	 * @param type    The options class
	 * @param options The options struct, may be null or empty
	 * @param <T>     The options type
	 *
	 * @return The populated options object
	 */
	public static <T> T map( Class<T> type, Map<?, ?> options ) {
		Map<String, Object>	values	= normalize( options );
		T					target	= instantiate( type, values );
		apply( target, values );
		return target;
	}

	/**
	 * Apply the values of a struct to an existing options object through its setters.
	 *
	 * @param target  The options object
	 * @param options The options struct
	 * @param <T>     The options type
	 *
	 * @return The same options object
	 */
	public static <T> T apply( T target, Map<?, ?> options ) {
		Map<String, List<Method>> setters = setters( target.getClass() );
		for ( Map.Entry<String, Object> entry : normalize( options ).entrySet() ) {
			String			name		= entry.getKey().toLowerCase( Locale.ROOT );
			List<Method>	candidates	= setters.get( ALIASES.getOrDefault( name, name ).toLowerCase( Locale.ROOT ) );
			if ( candidates == null && entry.getValue() == null ) {
				// Unset (null) unknown keys were always ignored; keep structs built with optional arguments working
				continue;
			}
			if ( candidates == null ) {
				throw PlaywrightErrors.of(
				    PlaywrightErrors.INVALID_OPTION,
				    "Unknown option [" + entry.getKey() + "] for " + displayName( target.getClass() ) + ".",
				    "Valid options are: " + String.join( ", ", optionNames( target.getClass() ) )
				);
			}
			// null is passed on to object setters (e.g. viewport : null disables the fixed viewport) and
			// only skipped when every setter takes a primitive, which cannot hold null
			if ( entry.getValue() == null && candidates.stream().allMatch( method -> method.getParameterTypes()[ 0 ].isPrimitive() ) ) {
				continue;
			}
			invokeBest( target, candidates, entry.getKey(), entry.getValue() );
		}
		return target;
	}

	/**
	 * List the option names accepted by an options class.
	 *
	 * @param type The options class
	 *
	 * @return The option names, sorted
	 */
	public static List<String> optionNames( Class<?> type ) {
		TreeSet<String> names = new TreeSet<>( String.CASE_INSENSITIVE_ORDER );
		for ( List<Method> methods : setters( type ).values() ) {
			String name = methods.get( 0 ).getName().substring( 3 );
			names.add( Character.toLowerCase( name.charAt( 0 ) ) + name.substring( 1 ) );
		}
		return new ArrayList<>( names );
	}

	/**
	 * Convert a string to a Playwright enum constant, ignoring case, dashes and underscores.
	 *
	 * @param enumName The enum name relative to {@code com.microsoft.playwright.options}, e.g. {@code AriaRole}
	 * @param value    The value, e.g. {@code button} or {@code no-preference}
	 *
	 * @return The enum constant
	 */
	public static Object enumValue( String enumName, String value ) {
		return toEnum( resolveClass( "options." + enumName ), value, enumName );
	}

	/**
	 * --------------------------------------------------------------------------
	 * Conversion
	 * --------------------------------------------------------------------------
	 */

	/**
	 * Call the first setter overload that accepts the value, trying the most natural ones first.
	 *
	 * @param target     The options object
	 * @param candidates The setter overloads for the option
	 * @param key        The option name, for error messages
	 * @param value      The option value
	 *
	 * @throws ortus.boxlang.runtime.types.exceptions.BoxRuntimeException when no overload accepts the value or the setter fails
	 */
	private static void invokeBest( Object target, List<Method> candidates, String key, Object value ) {
		ConversionException last = null;
		for ( Method method : ranked( candidates, value ) ) {
			Object converted;
			try {
				converted = convert( value, method.getGenericParameterTypes()[ 0 ], key );
			} catch ( ConversionException e ) {
				last = e;
				continue;
			}
			try {
				method.invoke( target, converted );
				return;
			} catch ( IllegalAccessException | InvocationTargetException e ) {
				throw PlaywrightErrors.of(
				    PlaywrightErrors.INVALID_OPTION,
				    "Could not set option [" + key + "] on " + displayName( target.getClass() ) + ": " + rootMessage( e ),
				    "Check the value type of the option.",
				    e
				);
			}
		}
		throw PlaywrightErrors.of(
		    PlaywrightErrors.INVALID_OPTION,
		    "Invalid value for option [" + key + "] on " + displayName( target.getClass() ) + ": " + ( last == null ? value : last.getMessage() ),
		    "Accepted types: " + candidates.stream().map( m -> simpleTypeName( m.getGenericParameterTypes()[ 0 ] ) ).toList()
		);
	}

	/**
	 * Order setter overloads so the most natural one for the value is tried first.
	 *
	 * @param candidates The setter overloads
	 * @param value      The option value
	 *
	 * @return A new list of the overloads, best match first
	 */
	private static List<Method> ranked( List<Method> candidates, Object value ) {
		List<Method> ranked = new ArrayList<>( candidates );
		ranked.sort( ( a, b ) -> Integer.compare( score( a.getParameterTypes()[ 0 ], value ), score( b.getParameterTypes()[ 0 ], value ) ) );
		return ranked;
	}

	/**
	 * Score how well a parameter type fits a value; lower is better.
	 *
	 * @param type  The setter parameter type
	 * @param value The option value
	 *
	 * @return The score, 0 for an exact instance match
	 */
	private static int score( Class<?> type, Object value ) {
		if ( type.isInstance( value ) ) {
			return 0;
		}
		if ( value instanceof Map ) {
			return Map.class.isAssignableFrom( type ) ? 2 : 1;
		}
		if ( value instanceof Collection || value instanceof Object[] ) {
			return List.class.isAssignableFrom( type ) ? 1 : 5;
		}
		if ( value instanceof CharSequence ) {
			if ( type == String.class ) {
				return 1;
			}
			return type.isEnum() || type == Path.class ? 2 : 4;
		}
		return 3;
	}

	/**
	 * Convert a BoxLang value to a setter or constructor parameter type, recursing into lists, maps and
	 * nested Playwright option classes.
	 *
	 * @param value      The value to convert
	 * @param targetType The target type, possibly generic
	 * @param key        The option name, for error messages
	 *
	 * @return The converted value
	 *
	 * @throws ConversionException when the value does not fit the type
	 */
	private static Object convert( Object value, Type targetType, String key ) {
		Class<?> raw = rawClass( targetType );

		if ( value == null ) {
			if ( raw.isPrimitive() ) {
				throw new ConversionException( "null is not allowed" );
			}
			return null;
		}
		if ( raw == Object.class ) {
			return value;
		}
		if ( !raw.isPrimitive() && raw.isInstance( value ) && ! ( value instanceof Map ) && ! ( value instanceof Collection ) ) {
			return value;
		}
		if ( raw == String.class ) {
			if ( value instanceof CharSequence || value instanceof Number || value instanceof Boolean ) {
				return value.toString();
			}
			throw new ConversionException( "expected a string" );
		}
		if ( raw == boolean.class || raw == Boolean.class ) {
			return toBoolean( value );
		}
		if ( raw == int.class || raw == Integer.class ) {
			return toNumber( value ).intValue();
		}
		if ( raw == double.class || raw == Double.class ) {
			return toNumber( value ).doubleValue();
		}
		if ( raw == long.class || raw == Long.class ) {
			return toNumber( value ).longValue();
		}
		if ( raw == Path.class ) {
			if ( value instanceof Path ) {
				return value;
			}
			if ( value instanceof CharSequence ) {
				return Paths.get( value.toString() );
			}
			throw new ConversionException( "expected a file path" );
		}
		if ( raw == Pattern.class ) {
			if ( value instanceof Pattern ) {
				return value;
			}
			throw new ConversionException( "expected a java.util.regex.Pattern" );
		}
		if ( raw == byte[].class ) {
			if ( value instanceof byte[] ) {
				return value;
			}
			if ( value instanceof CharSequence ) {
				return value.toString().getBytes( java.nio.charset.StandardCharsets.UTF_8 );
			}
			throw new ConversionException( "expected binary data" );
		}
		if ( raw.isEnum() ) {
			if ( ! ( value instanceof CharSequence ) ) {
				throw new ConversionException( "expected one of " + enumNames( raw ) );
			}
			return toEnum( raw, value.toString(), key );
		}
		if ( List.class.isAssignableFrom( raw ) ) {
			Type			elementType	= typeArgument( targetType, 0 );
			List<Object>	result		= new ArrayList<>();
			for ( Object item : toCollection( value ) ) {
				result.add( convert( item, elementType, key ) );
			}
			return result;
		}
		if ( Map.class.isAssignableFrom( raw ) ) {
			if ( ! ( value instanceof Map<?, ?> map ) ) {
				throw new ConversionException( "expected a struct" );
			}
			Type				valueType	= typeArgument( targetType, 1 );
			Map<String, Object>	result		= new LinkedHashMap<>();
			for ( Map.Entry<String, Object> entry : normalize( map ).entrySet() ) {
				result.put( entry.getKey(), convert( entry.getValue(), valueType, key ) );
			}
			return result;
		}
		if ( value instanceof Map<?, ?> map && raw.getName().startsWith( PLAYWRIGHT_PACKAGE ) ) {
			return map( raw, map );
		}
		throw new ConversionException( "cannot convert " + value.getClass().getSimpleName() + " to " + raw.getSimpleName() );
	}

	/**
	 * Find an enum constant by name, ignoring case, dashes and underscores.
	 *
	 * @param enumType The enum class
	 * @param value    The value, e.g. {@code no-preference}
	 * @param key      The option name, for error messages
	 *
	 * @return The enum constant
	 *
	 * @throws ortus.boxlang.runtime.types.exceptions.BoxRuntimeException when no constant matches
	 */
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	private static Object toEnum( Class<?> enumType, String value, String key ) {
		String wanted = normalizeEnumName( value );
		for ( Object constant : enumType.getEnumConstants() ) {
			if ( normalizeEnumName( ( ( Enum ) constant ).name() ).equals( wanted ) ) {
				return constant;
			}
		}
		throw PlaywrightErrors.of(
		    PlaywrightErrors.INVALID_OPTION,
		    "Invalid value [" + value + "] for [" + key + "].",
		    "Valid values are: " + String.join( ", ", enumNames( enumType ) )
		);
	}

	/**
	 * List the constants of an enum as lower case, dashed names.
	 *
	 * @param enumType The enum class
	 *
	 * @return The names, e.g. {@code no-preference}
	 */
	private static List<String> enumNames( Class<?> enumType ) {
		List<String> names = new ArrayList<>();
		for ( Object constant : enumType.getEnumConstants() ) {
			names.add( ( ( Enum<?> ) constant ).name().toLowerCase( Locale.ROOT ).replace( '_', '-' ) );
		}
		return names;
	}

	/**
	 * Normalize an enum name for comparison: keep letters and digits only, upper cased.
	 *
	 * @param value The name
	 *
	 * @return The normalized name
	 */
	private static String normalizeEnumName( String value ) {
		return value.replaceAll( "[^A-Za-z0-9]", "" ).toUpperCase( Locale.ROOT );
	}

	/**
	 * Convert a boolean, number or true/false/yes/no string to a boolean.
	 *
	 * @param value The value
	 *
	 * @return The boolean
	 *
	 * @throws ConversionException when the value is not a boolean
	 */
	private static boolean toBoolean( Object value ) {
		if ( value instanceof Boolean bool ) {
			return bool;
		}
		if ( value instanceof Number number ) {
			return number.doubleValue() != 0;
		}
		if ( value instanceof CharSequence ) {
			String text = value.toString().trim().toLowerCase( Locale.ROOT );
			if ( text.equals( "true" ) || text.equals( "yes" ) ) {
				return true;
			}
			if ( text.equals( "false" ) || text.equals( "no" ) ) {
				return false;
			}
		}
		throw new ConversionException( "expected a boolean" );
	}

	/**
	 * Convert a number or numeric string to a number.
	 *
	 * @param value The value
	 *
	 * @return The number
	 *
	 * @throws ConversionException when the value is not numeric
	 */
	private static Number toNumber( Object value ) {
		if ( value instanceof Number number ) {
			return number;
		}
		if ( value instanceof CharSequence ) {
			try {
				return Double.valueOf( value.toString().trim() );
			} catch ( NumberFormatException e ) {
				throw new ConversionException( "expected a number" );
			}
		}
		throw new ConversionException( "expected a number" );
	}

	/**
	 * Treat a value as a collection: collections and arrays as-is, anything else as a single item list.
	 *
	 * @param value The value
	 *
	 * @return The collection
	 */
	private static Collection<?> toCollection( Object value ) {
		if ( value instanceof Collection<?> collection ) {
			return collection;
		}
		if ( value instanceof Object[] array ) {
			return List.of( array );
		}
		// A single value where a list is expected, e.g. permissions: "geolocation"
		return List.of( value );
	}

	/**
	 * --------------------------------------------------------------------------
	 * Reflection helpers
	 * --------------------------------------------------------------------------
	 */

	/**
	 * Create an options object with its no-arg constructor, or with the required constructor arguments
	 * listed in {@code CONSTRUCTOR_ARGS}, which are removed from the values.
	 *
	 * @param type   The options class
	 * @param values The normalized option values; constructor arguments are removed
	 * @param <T>    The options type
	 *
	 * @return The new options object
	 *
	 * @throws ortus.boxlang.runtime.types.exceptions.BoxRuntimeException when the object cannot be built
	 */
	private static <T> T instantiate( Class<T> type, Map<String, Object> values ) {
		try {
			try {
				return type.getConstructor().newInstance();
			} catch ( NoSuchMethodException e ) {
				String[] argNames = CONSTRUCTOR_ARGS.get( type.getSimpleName() );
				if ( argNames == null ) {
					throw PlaywrightErrors.of( PlaywrightErrors.INVALID_OPTION, "Cannot build " + displayName( type ) + " from a struct.",
					    "Pass the object itself." );
				}
				for ( Constructor<?> constructor : type.getConstructors() ) {
					if ( constructor.getParameterCount() == argNames.length ) {
						Object[]	args	= new Object[ argNames.length ];
						Type[]		types	= constructor.getGenericParameterTypes();
						for ( int i = 0; i < argNames.length; i++ ) {
							Object arg = removeIgnoreCase( values, argNames[ i ] );
							if ( arg == null ) {
								throw PlaywrightErrors.of(
								    PlaywrightErrors.INVALID_OPTION,
								    displayName( type ) + " requires [" + String.join( ", ", argNames ) + "].",
								    "Missing [" + argNames[ i ] + "]."
								);
							}
							args[ i ] = convert( arg, types[ i ], argNames[ i ] );
						}
						return type.cast( constructor.newInstance( args ) );
					}
				}
				throw PlaywrightErrors.of( PlaywrightErrors.INVALID_OPTION, "Cannot build " + displayName( type ) + " from a struct.",
				    "Pass the object itself." );
			}
		} catch ( InstantiationException | IllegalAccessException | InvocationTargetException e ) {
			throw PlaywrightErrors.of( PlaywrightErrors.INVALID_OPTION, "Cannot create " + displayName( type ) + ": " + rootMessage( e ), "", e );
		}
	}

	/**
	 * Find the single argument instance setters of a class, cached per class.
	 *
	 * @param type The class
	 *
	 * @return The setter overloads keyed by lower cased property name
	 */
	private static Map<String, List<Method>> setters( Class<?> type ) {
		return SETTERS.computeIfAbsent( type, key -> {
			Map<String, List<Method>> result = new LinkedHashMap<>();
			for ( Method method : key.getMethods() ) {
				if ( method.getName().startsWith( "set" )
				    && method.getName().length() > 3
				    && method.getParameterCount() == 1
				    && !Modifier.isStatic( method.getModifiers() ) ) {
					result.computeIfAbsent( method.getName().substring( 3 ).toLowerCase( Locale.ROOT ), k -> new ArrayList<>() ).add( method );
				}
			}
			return result;
		} );
	}

	/**
	 * Copy a struct or map into a map with plain string keys.
	 *
	 * @param options The struct or map, may be null
	 *
	 * @return A new ordered map
	 */
	private static Map<String, Object> normalize( Map<?, ?> options ) {
		Map<String, Object> result = new LinkedHashMap<>();
		if ( options == null ) {
			return result;
		}
		for ( Map.Entry<?, ?> entry : options.entrySet() ) {
			Object	key		= entry.getKey();
			String	name	= key instanceof Key boxKey ? boxKey.getName() : String.valueOf( key );
			result.put( name, entry.getValue() );
		}
		return result;
	}

	/**
	 * Remove an entry by key, ignoring case.
	 *
	 * @param values The map
	 * @param name   The key
	 *
	 * @return The removed value, or null when not found
	 */
	private static Object removeIgnoreCase( Map<String, Object> values, String name ) {
		for ( String key : new ArrayList<>( values.keySet() ) ) {
			if ( key.equalsIgnoreCase( name ) ) {
				return values.remove( key );
			}
		}
		return null;
	}

	/**
	 * Load a Playwright class by its short name, turning nested names into binary names,
	 * e.g. {@code Page.NavigateOptions} into {@code com.microsoft.playwright.Page$NavigateOptions}.
	 *
	 * @param className The class name, relative to {@code com.microsoft.playwright} or fully qualified
	 *
	 * @return The class
	 *
	 * @throws ortus.boxlang.runtime.types.exceptions.BoxRuntimeException when the class does not exist
	 */
	private static Class<?> resolveClass( String className ) {
		String	name		= className.startsWith( PLAYWRIGHT_PACKAGE ) ? className : PLAYWRIGHT_PACKAGE + className;
		int		lastDot		= name.lastIndexOf( '.' );
		String	binaryName	= name;
		// Page.NavigateOptions -> com.microsoft.playwright.Page$NavigateOptions
		if ( !name.startsWith( OPTIONS_PACKAGE ) && name.substring( PLAYWRIGHT_PACKAGE.length() ).contains( "." ) ) {
			binaryName = name.substring( 0, lastDot ) + "$" + name.substring( lastDot + 1 );
		}
		try {
			return Class.forName( binaryName, true, OptionsMapper.class.getClassLoader() );
		} catch ( ClassNotFoundException e ) {
			throw PlaywrightErrors.of( PlaywrightErrors.INVALID_OPTION, "Unknown Playwright options class [" + className + "].",
			    "Examples: Page.NavigateOptions, Browser.NewContextOptions.", e );
		}
	}

	/**
	 * The raw class of a type.
	 *
	 * @param type A class or parameterized type
	 *
	 * @return The raw class, or Object for other types
	 */
	private static Class<?> rawClass( Type type ) {
		if ( type instanceof Class<?> clazz ) {
			return clazz;
		}
		if ( type instanceof ParameterizedType parameterized ) {
			return ( Class<?> ) parameterized.getRawType();
		}
		return Object.class;
	}

	/**
	 * A type argument of a parameterized type.
	 *
	 * @param type  The type
	 * @param index The type argument position
	 *
	 * @return The type argument, or Object when unavailable
	 */
	private static Type typeArgument( Type type, int index ) {
		if ( type instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length > index ) {
			return parameterized.getActualTypeArguments()[ index ];
		}
		return Object.class;
	}

	/**
	 * A short type name for messages, without the Playwright, java.lang and java.util packages.
	 *
	 * @param type The type
	 *
	 * @return The short name
	 */
	private static String simpleTypeName( Type type ) {
		return type.getTypeName().replace( PLAYWRIGHT_PACKAGE, "" ).replace( "java.lang.", "" ).replace( "java.util.", "" );
	}

	/**
	 * A short class name for messages, e.g. {@code Page.NavigateOptions}.
	 *
	 * @param type The class
	 *
	 * @return The short name
	 */
	private static String displayName( Class<?> type ) {
		return type.getName().replace( PLAYWRIGHT_PACKAGE, "" ).replace( '$', '.' );
	}

	/**
	 * The message of the deepest cause of an exception.
	 *
	 * @param e The exception
	 *
	 * @return The root cause message
	 */
	private static String rootMessage( Throwable e ) {
		Throwable root = e;
		while ( root.getCause() != null ) {
			root = root.getCause();
		}
		return root.getMessage();
	}

	/**
	 * Internal signal that a value cannot be converted to one setter overload, so the next one is tried.
	 */
	private static final class ConversionException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		/**
		 * Create the signal without a stack trace.
		 *
		 * @param message Why the conversion failed
		 */
		ConversionException( String message ) {
			super( message, null, false, false );
		}

	}

}
