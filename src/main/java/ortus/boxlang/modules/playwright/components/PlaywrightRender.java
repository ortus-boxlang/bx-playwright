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
package ortus.boxlang.modules.playwright.components;

import java.util.List;
import java.util.Set;

import ortus.boxlang.modules.playwright.engine.PlaywrightErrors;
import ortus.boxlang.runtime.bifs.BIFDescriptor;
import ortus.boxlang.runtime.components.Attribute;
import ortus.boxlang.runtime.components.BoxComponent;
import ortus.boxlang.runtime.components.Component;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.ExpressionInterpreter;
import ortus.boxlang.runtime.dynamic.Referencer;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.validation.Validator;

/**
 * Render the body HTML with a real browser (Chromium) to a PDF or an image.
 *
 * <pre>
 * &lt;bx:playwrightRender type="pdf" path="invoice.pdf" format="A4"&gt;
 *     &lt;h1&gt;Invoice ##invoice.id##&lt;/h1&gt;
 * &lt;/bx:playwrightRender&gt;
 *
 * bx:playwrightRender type="png" variable="card" viewport="1200x630" {
 *     writeOutput( socialCardHtml )
 * }
 * </pre>
 *
 * Either {@code path} (write a file) or {@code variable} (store the bytes, or the path when both are given) is required.
 * Defaults come from the {@code render} module setting.
 */
@BoxComponent( requiresBody = true )
public class PlaywrightRender extends Component {

	private static final Key			PLAYWRIGHT	= Key.of( "playwright" );
	private static final Key			RENDER		= Key.of( "render" );
	private static final Key			TYPE		= Key.of( "type" );
	private static final Key			PATH		= Key.of( "path" );
	private static final Key			VARIABLE	= Key.of( "variable" );
	private static final Key			PROFILE		= Key.of( "profile" );
	private static final Key			OPTIONS		= Key.of( "options" );
	private static final Key			VIEWPORT	= Key.of( "viewport" );
	private static final Key			MARGIN		= Key.of( "margin" );

	/**
	 * Attributes copied as-is into the render() options when present.
	 */
	private static final List<String>	PASSTHROUGH	= List.of(
	    "baseURL", "waitFor", "waitUntil", "device", "colorScheme", "locale", "timezone",
	    "format", "landscape", "printBackground", "headerTemplate", "footerTemplate", "displayHeaderFooter", "scale", "pageRanges",
	    "width", "height", "preferCSSPageSize",
	    "fullPage", "omitBackground", "quality"
	);

	private static final Set<String>	TYPES		= Set.of( "pdf", "png", "jpeg", "webp" );

	public PlaywrightRender() {
		super();
		declaredAttributes = new Attribute[] {
		    new Attribute( TYPE, "string", "pdf", Set.of( Validator.valueOneOf( TYPES.toArray( new String[ 0 ] ) ) ) ),
		    new Attribute( PATH, "string" ),
		    new Attribute( VARIABLE, "string" ),
		    new Attribute( PROFILE, "any" ),
		    new Attribute( OPTIONS, "struct" ),
		    new Attribute( VIEWPORT, "any" ),
		    new Attribute( MARGIN, "any" ),
		    new Attribute( Key.of( "baseURL" ), "string" ),
		    new Attribute( Key.of( "waitFor" ), "string" ),
		    new Attribute( Key.of( "waitUntil" ), "string" ),
		    new Attribute( Key.of( "device" ), "string" ),
		    new Attribute( Key.of( "colorScheme" ), "string" ),
		    new Attribute( Key.of( "locale" ), "string" ),
		    new Attribute( Key.of( "timezone" ), "string" ),
		    new Attribute( Key.of( "format" ), "string" ),
		    new Attribute( Key.of( "landscape" ), "boolean" ),
		    new Attribute( Key.of( "printBackground" ), "boolean" ),
		    new Attribute( Key.of( "headerTemplate" ), "string" ),
		    new Attribute( Key.of( "footerTemplate" ), "string" ),
		    new Attribute( Key.of( "displayHeaderFooter" ), "boolean" ),
		    new Attribute( Key.of( "scale" ), "numeric" ),
		    new Attribute( Key.of( "pageRanges" ), "string" ),
		    new Attribute( Key.of( "width" ), "string" ),
		    new Attribute( Key.of( "height" ), "string" ),
		    new Attribute( Key.of( "preferCSSPageSize" ), "boolean" ),
		    new Attribute( Key.of( "fullPage" ), "boolean" ),
		    new Attribute( Key.of( "omitBackground" ), "boolean" ),
		    new Attribute( Key.of( "quality" ), "numeric" )
		};
	}

	/**
	 * Render the body.
	 *
	 * @param context        The context
	 * @param attributes     The attributes
	 * @param body           The body
	 * @param executionState The execution state
	 *
	 * @return The body result
	 */
	@Override
	public BodyResult _invoke( IBoxContext context, IStruct attributes, ComponentBody body, IStruct executionState ) {
		String	path		= attributes.getAsString( PATH );
		String	variable	= attributes.getAsString( VARIABLE );
		if ( isBlank( path ) && isBlank( variable ) ) {
			throw PlaywrightErrors.of(
			    PlaywrightErrors.INVALID_OPTION,
			    "bx:playwrightRender needs a [path] or a [variable] attribute.",
			    "Example: <bx:playwrightRender type=\"pdf\" path=\"report.pdf\">...</bx:playwrightRender>"
			);
		}

		StringBuffer	buffer	= new StringBuffer();
		BodyResult		result	= processBody( context, body, buffer );
		if ( result.isEarlyExit() ) {
			return result;
		}

		IStruct			options		= buildOptions( attributes );
		Object			profile		= attributes.get( PROFILE );
		BIFDescriptor	playwright	= context.getRuntime().getFunctionService().getGlobalFunction( PLAYWRIGHT );
		Object			manager		= playwright.invoke( context, new Object[] { profile == null ? "" : profile }, false, PLAYWRIGHT );
		Object			output		= Referencer.getAndInvoke( context, manager, RENDER, new Object[] { buffer.toString(), options }, false );

		if ( !isBlank( variable ) ) {
			ExpressionInterpreter.setVariable( context, variable, output );
		}
		return DEFAULT_RETURN;
	}

	/**
	 * Build the options struct passed to render().
	 */
	private IStruct buildOptions( IStruct attributes ) {
		IStruct options = new Struct();
		if ( attributes.get( OPTIONS ) instanceof IStruct extra ) {
			options.putAll( extra );
		}
		options.put( TYPE, attributes.getAsString( TYPE ) );
		String path = attributes.getAsString( PATH );
		if ( !isBlank( path ) ) {
			options.put( PATH, path );
		}
		for ( String name : PASSTHROUGH ) {
			Object value = attributes.get( Key.of( name ) );
			if ( value != null && ! ( value instanceof String text && text.isBlank() ) ) {
				options.put( Key.of( name ), value );
			}
		}
		Object viewport = attributes.get( VIEWPORT );
		if ( viewport instanceof String size && !size.isBlank() ) {
			String[] parts = size.toLowerCase().split( "x" );
			if ( parts.length != 2 ) {
				throw PlaywrightErrors.of( PlaywrightErrors.INVALID_OPTION, "Invalid viewport [" + size + "].", "Use WIDTHxHEIGHT, e.g. 1200x630." );
			}
			options.put( VIEWPORT, Struct.of( "width", Integer.parseInt( parts[ 0 ].trim() ), "height", Integer.parseInt( parts[ 1 ].trim() ) ) );
		} else if ( viewport instanceof IStruct ) {
			options.put( VIEWPORT, viewport );
		}
		Object margin = attributes.get( MARGIN );
		if ( margin instanceof String all && !all.isBlank() ) {
			options.put( MARGIN, Struct.of( "top", all, "right", all, "bottom", all, "left", all ) );
		} else if ( margin instanceof IStruct ) {
			options.put( MARGIN, margin );
		}
		return options;
	}

	private static boolean isBlank( String value ) {
		return value == null || value.isBlank();
	}

}
