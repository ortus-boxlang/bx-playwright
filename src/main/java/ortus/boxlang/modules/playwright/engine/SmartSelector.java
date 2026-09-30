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

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;

/**
 * Resolves the single-string selectors of the bx-playwright DSL into Playwright locators.
 *
 * <ul>
 * <li>{@code @name}: the test id ({@code data-testid} by default, see the {@code testIdAttribute} setting)</li>
 * <li>{@code ref=e12}: an element ref from an ai mode snapshot</li>
 * <li>CSS, XPath and Playwright engine selectors pass through: {@code #id}, {@code .class}, {@code input[name=email]},
 * lowercase tag names,
 * {@code //div}, {@code css=...}, {@code text=...}, {@code role=button[name="Save"]}, {@code h1}</li>
 * <li>Anything else is human text, resolved by intent:
 * <ul>
 * <li>{@code fill}: label, then placeholder, then the name attribute</li>
 * <li>{@code click}: button or link by accessible name, then exact visible text</li>
 * <li>{@code any}: visible text</li>
 * </ul>
 * </li>
 * </ul>
 * Text matches return the first element, so {@code click( "Save" )} works even if the text appears twice.
 */
public final class SmartSelector {

	/**
	 * How the selector will be used.
	 */
	public enum Intent {
		ANY,
		CLICK,
		FILL
	}

	private static final Pattern		ENGINE_PREFIX	= Pattern.compile( "^(css|xpath|text|role|id|data-testid|internal:[a-z-]+|nth|visible)=.*",
	    Pattern.DOTALL );
	private static final Set<String>	HTML_TAGS		= Set.of(
	    "a", "abbr", "article", "aside", "audio", "b", "blockquote", "body", "button", "canvas", "caption", "code", "dd", "details",
	    "dialog", "div", "dl", "dt", "em", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6",
	    "header", "hr", "html", "i", "iframe", "img", "input", "label", "legend", "li", "main", "menu", "nav", "ol", "optgroup",
	    "option", "output", "p", "picture", "pre", "progress", "section", "select", "small", "span", "strong", "sub", "summary",
	    "sup", "svg", "table", "tbody", "td", "template", "textarea", "tfoot", "th", "thead", "time", "tr", "u", "ul", "video"
	);

	/**
	 * Static utility class, not instantiable.
	 */
	private SmartSelector() {
	}

	/**
	 * Resolve a selector inside a root locator.
	 *
	 * @param root     The scope, e.g. {@code page.locator( ":root" )} or a {@code within()} locator
	 * @param selector The DSL selector
	 * @param intent   The intent: any, click or fill
	 *
	 * @return The Playwright locator
	 */
	public static Locator locate( Locator root, String selector, String intent ) {
		String	value	= selector == null ? "" : selector.trim();
		Intent	mode	= intent == null ? Intent.ANY : Intent.valueOf( intent.toUpperCase( Locale.ROOT ) );
		if ( value.isEmpty() ) {
			throw PlaywrightErrors.of( PlaywrightErrors.INVALID_OPTION, "An empty selector was given.", "Use CSS (#id, .class), @testId, or visible text." );
		}
		if ( value.startsWith( "@" ) && value.length() > 1 ) {
			return root.getByTestId( value.substring( 1 ) );
		}
		if ( value.startsWith( "ref=" ) ) {
			// Element refs from ai mode snapshots, e.g. ref=e12
			return root.page().locator( "aria-ref=" + value.substring( 4 ) );
		}
		if ( isSelector( value ) ) {
			return root.locator( value );
		}
		return switch ( mode ) {
			case FILL -> root.getByLabel( value )
			    .or( root.getByPlaceholder( value ) )
			    .or( root.locator( "[name=\"" + value.replace( "\"", "\\\"" ) + "\"]" ) )
			    .first();
			case CLICK -> root.getByRole( AriaRole.BUTTON, new Locator.GetByRoleOptions().setName( value ) )
			    .or( root.getByRole( AriaRole.LINK, new Locator.GetByRoleOptions().setName( value ) ) )
			    .or( root.getByText( value, new Locator.GetByTextOptions().setExact( true ) ) )
			    .first();
			default -> root.getByText( value ).first();
		};
	}

	/**
	 * Decide if a string is a CSS, XPath or Playwright engine selector rather than human text.
	 *
	 * @param value The selector
	 *
	 * @return True for selectors, false for human text
	 */
	public static boolean isSelector( String value ) {
		String trimmed = value.trim();
		if ( trimmed.isEmpty() ) {
			return false;
		}
		char first = trimmed.charAt( 0 );
		if ( first == '#' || first == '.' || first == '[' || first == '/' || first == '*' || first == '(' || first == ':' ) {
			return true;
		}
		if ( ENGINE_PREFIX.matcher( trimmed ).matches() ) {
			return true;
		}
		// Every whitespace separated part must look like CSS: "ul li", "div > span", "input[name=x]"
		// while "Save changes" or "Sign in" are human text
		for ( String part : trimmed.split( "\\s+" ) ) {
			if ( !isCssToken( part ) ) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Decide if a whitespace separated part of a selector looks like CSS: a combinator, a token starting
	 * with a CSS character, or a known HTML tag name (case sensitive).
	 *
	 * @param part One part of the selector, not empty
	 *
	 * @return True when the part looks like CSS
	 */
	private static boolean isCssToken( String part ) {
		if ( part.equals( ">" ) || part.equals( "~" ) || part.equals( "+" ) ) {
			return true;
		}
		char first = part.charAt( 0 );
		if ( first == '#' || first == '.' || first == '[' || first == ':' || first == '*' ) {
			return true;
		}
		// Tags are matched case sensitively: "menu" is the tag, "Menu" is text
		String tag = part.split( "[#.\\[:]", 2 )[ 0 ];
		return HTML_TAGS.contains( tag );
	}

}
