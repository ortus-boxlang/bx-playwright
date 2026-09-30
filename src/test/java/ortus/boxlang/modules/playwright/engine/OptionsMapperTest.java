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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitUntilState;

import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class OptionsMapperTest {

	@DisplayName( "It maps scalars and enums from a plain map" )
	@Test
	public void testScalarsAndEnums() {
		Page.NavigateOptions options = OptionsMapper.map( Page.NavigateOptions.class, Map.of( "timeout", 5000, "waitUntil", "networkidle" ) );
		assertThat( options.timeout ).isEqualTo( 5000d );
		assertThat( options.waitUntil ).isEqualTo( WaitUntilState.NETWORKIDLE );
	}

	@DisplayName( "It maps BoxLang structs with Key keys, ignoring case" )
	@Test
	public void testBoxLangStruct() {
		IStruct						struct	= Struct.of( "HEADLESS", false, "slowMo", 250, "channel", "chrome" );
		BrowserType.LaunchOptions	options	= OptionsMapper.map( BrowserType.LaunchOptions.class, struct );
		assertThat( options.headless ).isFalse();
		assertThat( options.slowMo ).isEqualTo( 250d );
		assertThat( options.channel ).isEqualTo( "chrome" );
	}

	@DisplayName( "It builds nested option objects, lists and maps" )
	@Test
	public void testNestedObjects() {
		IStruct						struct	= Struct.of(
		    "viewport", Struct.of( "width", 1280, "height", 720 ),
		    "colorScheme", "dark",
		    "permissions", Array.of( "geolocation" ),
		    "geolocation", Struct.of( "latitude", 40.4, "longitude", -3.7, "accuracy", 10 ),
		    "extraHTTPHeaders", Struct.of( "X-Test", "yes" ),
		    "timezone", "UTC",
		    "proxy", "http://proxy:8080"
		);
		Browser.NewContextOptions	options	= OptionsMapper.map( Browser.NewContextOptions.class, struct );
		assertThat( options.viewportSize.get().width ).isEqualTo( 1280 );
		assertThat( options.viewportSize.get().height ).isEqualTo( 720 );
		assertThat( options.colorScheme.get() ).isEqualTo( ColorScheme.DARK );
		assertThat( options.permissions ).containsExactly( "geolocation" );
		assertThat( options.geolocation.latitude ).isEqualTo( 40.4 );
		assertThat( options.geolocation.accuracy ).isEqualTo( 10d );
		assertThat( options.extraHTTPHeaders ).containsEntry( "X-Test", "yes" );
		assertThat( options.timezoneId ).isEqualTo( "UTC" );
		assertThat( options.proxy.server ).isEqualTo( "http://proxy:8080" );
	}

	@DisplayName( "It converts strings to paths and dashed values to enums" )
	@Test
	public void testPathsAndDashedEnums() {
		Page.ScreenshotOptions		screenshot	= OptionsMapper.map( Page.ScreenshotOptions.class, Map.of( "path", "shot.png", "fullPage", "true" ) );
		Browser.NewContextOptions	context		= OptionsMapper.map( Browser.NewContextOptions.class, Map.of( "colorScheme", "no-preference" ) );
		assertThat( screenshot.path ).isEqualTo( Paths.get( "shot.png" ) );
		assertThat( screenshot.fullPage ).isTrue();
		assertThat( context.colorScheme.get() ).isEqualTo( ColorScheme.NO_PREFERENCE );
	}

	@DisplayName( "It builds options by short class name and enums by name" )
	@Test
	public void testBuildByName() {
		Object	margin	= OptionsMapper.build( "options.Margin", Map.of( "top", "1cm" ) );
		Object	pdf		= OptionsMapper.build( "Page.PdfOptions", Map.of( "format", "A4", "margin", Map.of( "top", "1cm" ) ) );
		assertThat( margin ).isInstanceOf( Margin.class );
		assertThat( ( ( Margin ) margin ).top ).isEqualTo( "1cm" );
		assertThat( ( ( Page.PdfOptions ) pdf ).margin.top ).isEqualTo( "1cm" );
		assertThat( OptionsMapper.enumValue( "AriaRole", "button" ) ).isEqualTo( AriaRole.BUTTON );
	}

	@DisplayName( "It fails on unknown options with the list of valid ones" )
	@Test
	public void testUnknownOption() {
		BoxRuntimeException error = assertThrows(
		    BoxRuntimeException.class,
		    () -> OptionsMapper.map( Page.NavigateOptions.class, Map.of( "timeoutt", 1 ) )
		);
		assertThat( error.getType() ).isEqualTo( PlaywrightErrors.INVALID_OPTION );
		assertThat( error.getMessage() ).contains( "timeoutt" );
		assertThat( error.getDetail() ).contains( "waitUntil" );
	}

	@DisplayName( "It fails on invalid enum values with the valid values" )
	@Test
	public void testInvalidEnum() {
		BoxRuntimeException error = assertThrows(
		    BoxRuntimeException.class,
		    () -> OptionsMapper.map( Page.NavigateOptions.class, Map.of( "waitUntil", "sometime" ) )
		);
		assertThat( error.getType() ).isEqualTo( PlaywrightErrors.INVALID_OPTION );
		assertThat( error.getDetail() ).contains( "networkidle" );
	}

	@DisplayName( "It lists option names" )
	@Test
	public void testOptionNames() {
		List<String> names = OptionsMapper.optionNames( Page.NavigateOptions.class );
		assertThat( names ).containsAtLeast( "referer", "timeout", "waitUntil" );
	}

}
