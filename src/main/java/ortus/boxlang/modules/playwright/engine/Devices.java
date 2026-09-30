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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.impl.PlaywrightImpl;

/**
 * Access to the device descriptors (iPhone 15, Pixel 7, iPad Pro 11, ...) that ship with the Playwright driver.
 * The Java API does not expose them publicly, so they are read from the driver connection.
 */
public final class Devices {

	/**
	 * Static utility class, not instantiable.
	 */
	private Devices() {
	}

	/**
	 * List every device descriptor.
	 *
	 * @param playwright A running Playwright instance
	 *
	 * @return Device maps with {@code name} and {@code descriptor} ({@code userAgent}, {@code viewport},
	 *         {@code deviceScaleFactor}, {@code isMobile}, {@code hasTouch}, {@code defaultBrowserType})
	 */
	public static List<Map<String, Object>> list( Playwright playwright ) {
		JsonArray					descriptors	= ( ( PlaywrightImpl ) playwright ).deviceDescriptors();
		List<Map<String, Object>>	devices		= new ArrayList<>();
		for ( JsonElement element : descriptors ) {
			@SuppressWarnings( "unchecked" )
			Map<String, Object> device = ( Map<String, Object> ) toJava( element );
			devices.add( device );
		}
		return devices;
	}

	/**
	 * Find a device descriptor by name, ignoring case.
	 *
	 * @param playwright A running Playwright instance
	 * @param name       The device name, e.g. {@code iPhone 15}
	 *
	 * @return The descriptor ({@code userAgent}, {@code viewport}, ...)
	 */
	@SuppressWarnings( "unchecked" )
	public static Map<String, Object> get( Playwright playwright, String name ) {
		List<String> names = new ArrayList<>();
		for ( Map<String, Object> device : list( playwright ) ) {
			String deviceName = String.valueOf( device.get( "name" ) );
			if ( deviceName.equalsIgnoreCase( name ) ) {
				return ( Map<String, Object> ) device.get( "descriptor" );
			}
			names.add( deviceName );
		}
		throw PlaywrightErrors.of(
		    PlaywrightErrors.INVALID_OPTION,
		    "Unknown device [" + name + "].",
		    "Run [bxPlaywright devices] to list them. Examples: iPhone 15, Pixel 7, iPad Pro 11, Desktop Chrome."
		);
	}

	/**
	 * Convert a Gson element to plain Java maps, lists, strings, numbers and booleans.
	 *
	 * @param element The Gson element, may be null
	 *
	 * @return The plain Java value, or null for a JSON null
	 */
	private static Object toJava( JsonElement element ) {
		if ( element == null || element.isJsonNull() ) {
			return null;
		}
		if ( element.isJsonObject() ) {
			Map<String, Object> map = new LinkedHashMap<>();
			for ( Map.Entry<String, JsonElement> entry : ( ( JsonObject ) element ).entrySet() ) {
				map.put( entry.getKey(), toJava( entry.getValue() ) );
			}
			return map;
		}
		if ( element.isJsonArray() ) {
			List<Object> list = new ArrayList<>();
			for ( JsonElement item : element.getAsJsonArray() ) {
				list.add( toJava( item ) );
			}
			return list;
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if ( primitive.isBoolean() ) {
			return primitive.getAsBoolean();
		}
		if ( primitive.isNumber() ) {
			double value = primitive.getAsDouble();
			return value == Math.rint( value ) ? ( Object ) Integer.valueOf( ( int ) value ) : ( Object ) Double.valueOf( value );
		}
		return primitive.getAsString();
	}

}
