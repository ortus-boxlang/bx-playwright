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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Resolves the single-string selectors of the bx-playwright DSL into Playwright locators.
 *
 * <ul>
 * <li>{@code @name}: the test id ({@code data-testid} by default, see the {@code testIdAttribute} setting)</li>
 * <li>{@code ref=e12}: an element ref from an ai mode snapshot</li>
 * <li>CSS, XPath and Playwright engine selectors pass through: {@code #id}, {@code .class}, {@code input[name=email]},
 * lowercase tag names,
 * {@code //div}, {@code css=...}, {@code text=...}, {@code role=button[name="Save"]}, {@code h1}, chains such as
 * {@code div >> text=Foo}</li>
 * <li>Anything else is human text, resolved by intent:
 * <ul>
 * <li>{@code fill}: exact label, label, exact placeholder, placeholder, then the name attribute (first match in that
 * order)</li>
 * <li>{@code click}: button or link by exact accessible name, then by partial name, then exact visible text</li>
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
	/**
	 * How long (ms) a smart text selector waits for its preferred element (a button or link for clicks, a field for
	 * fills) to be attached before falling back, so an element rendered a moment later still wins.
	 */
	private static final double			GRACE_MS		= 1000;
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
			case FILL -> {
				// Priority: exact label, label, exact placeholder, placeholder, then the name attribute.
				// A plain or() chain would return the first match in document order instead.
				List<Locator>	ordered		= List.of(
				    root.getByLabel( value, new Locator.GetByLabelOptions().setExact( true ) ),
				    root.getByLabel( value ),
				    root.getByPlaceholder( value, new Locator.GetByPlaceholderOptions().setExact( true ) ),
				    root.getByPlaceholder( value ),
				    root.locator( "[name=\"" + value.replace( "\"", "\\\"" ) + "\"]" )
				);
				Locator			anyField	= ordered.get( 1 ).or( ordered.get( 3 ) ).or( ordered.get( 4 ) );
				yield pick( ordered, anyField, anyField );
			}
			case CLICK -> {
				// A button or link wins over any other element with the same text (e.g. a "Sign in" heading
				// above a "Sign in" button), and an exact name wins over a longer one ("Save" over "Save draft").
				// Text is only the fallback when no button or link shows up within the grace period.
				Locator			exactButton	= root.getByRole( AriaRole.BUTTON, new Locator.GetByRoleOptions().setName( value ).setExact( true ) );
				Locator			exactLink	= root.getByRole( AriaRole.LINK, new Locator.GetByRoleOptions().setName( value ).setExact( true ) );
				Locator			button		= root.getByRole( AriaRole.BUTTON, new Locator.GetByRoleOptions().setName( value ) );
				Locator			link		= root.getByRole( AriaRole.LINK, new Locator.GetByRoleOptions().setName( value ) );
				Locator			control		= button.or( link );
				List<Locator>	ordered		= List.of( exactButton.or( exactLink ), control );
				yield pick( ordered, control, control.or( root.getByText( value, new Locator.GetByTextOptions().setExact( true ) ) ) );
			}
			default -> root.getByText( value ).first();
		};
	}

	/**
	 * Pick the first locator, in priority order, that matches at least one element. When none matches yet, wait up to
	 * {@link #GRACE_MS} for an element matching {@code waitOn} to be attached (it may still be rendering) and check the
	 * priority order again. If nothing shows up, return {@code fallback}, which then waits with the action timeout.
	 *
	 * @param ordered  The candidate locators, best first
	 * @param waitOn   The locator to wait for during the grace period
	 * @param fallback The locator to use when no candidate matches after the grace period
	 *
	 * @return The first element of the chosen locator
	 */
	private static Locator pick( List<Locator> ordered, Locator waitOn, Locator fallback ) {
		Locator found = firstPresent( ordered );
		if ( found != null ) {
			return found;
		}
		try {
			waitOn.first().waitFor( new Locator.WaitForOptions().setState( WaitForSelectorState.ATTACHED ).setTimeout( GRACE_MS ) );
		} catch ( TimeoutError e ) {
			return fallback.first();
		}
		found = firstPresent( ordered );
		return found != null ? found : fallback.first();
	}

	/**
	 * Find the first locator that currently matches at least one element.
	 *
	 * @param ordered The candidate locators, best first
	 *
	 * @return The first element of the first matching locator, or null when none matches
	 */
	private static Locator firstPresent( List<Locator> ordered ) {
		for ( Locator candidate : ordered ) {
			if ( candidate.count() > 0 ) {
				return candidate.first();
			}
		}
		return null;
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
		// Playwright chains ("div >> text=Foo", "nav >> nth=0"): every segment must be a selector
		List<String> chain = split( trimmed, true );
		if ( chain.size() > 1 ) {
			for ( String segment : chain ) {
				if ( segment.isBlank() || !isSelector( segment ) ) {
					return false;
				}
			}
			return true;
		}
		// Every whitespace separated part must look like CSS: "ul li", "div > span", "input[placeholder=\"Your email\"]"
		// while "Save changes" or "Sign in" are human text. Whitespace inside quotes, [] and () does not split.
		for ( String part : split( trimmed, false ) ) {
			if ( !isCssToken( part ) ) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Split a selector outside quotes, brackets and parentheses, either on whitespace or on the Playwright chain
	 * operator {@code >>}.
	 *
	 * @param value The selector
	 * @param chain True to split on {@code >>}, false to split on whitespace
	 *
	 * @return The parts, trimmed; empty parts are dropped when splitting on whitespace
	 */
	private static List<String> split( String value, boolean chain ) {
		List<String>	parts	= new ArrayList<>();
		StringBuilder	current	= new StringBuilder();
		char			quote	= 0;
		int				depth	= 0;
		for ( int i = 0; i < value.length(); i++ ) {
			char c = value.charAt( i );
			if ( quote != 0 ) {
				current.append( c );
				if ( c == '\\' && i + 1 < value.length() ) {
					current.append( value.charAt( ++i ) );
				} else if ( c == quote ) {
					quote = 0;
				}
				continue;
			}
			if ( c == '"' || c == '\'' ) {
				quote = c;
			} else if ( c == '[' || c == '(' ) {
				depth++;
			} else if ( ( c == ']' || c == ')' ) && depth > 0 ) {
				depth--;
			} else if ( depth == 0 && chain && c == '>' && i + 1 < value.length() && value.charAt( i + 1 ) == '>' ) {
				parts.add( current.toString().trim() );
				current.setLength( 0 );
				i++;
				continue;
			} else if ( depth == 0 && !chain && Character.isWhitespace( c ) ) {
				if ( !current.isEmpty() ) {
					parts.add( current.toString() );
					current.setLength( 0 );
				}
				continue;
			}
			current.append( c );
		}
		if ( chain || !current.isEmpty() ) {
			parts.add( chain ? current.toString().trim() : current.toString() );
		}
		return parts;
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
