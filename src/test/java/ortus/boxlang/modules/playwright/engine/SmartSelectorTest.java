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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class SmartSelectorTest {

	/**
	 * CSS, XPath and engine prefixed values are detected as selectors.
	 *
	 * @param value The value to classify
	 */
	@ParameterizedTest( name = "[{0}] is a selector" )
	@ValueSource( strings = {
	    "#email", ".btn", "[data-x=1]", "//div", "(//a)[2]", "*", "h1", "button", "input[name=email]", "div > span", "a:has-text('x')",
	    "ul li", "css=.x", "text=Hello", "role=button[name=\"Save\"]", "xpath=//a", "div.card", "form#login"
	} )
	public void testSelectors( String value ) {
		assertThat( SmartSelector.isSelector( value ) ).isTrue();
	}

	/**
	 * Plain words and phrases are detected as text, not selectors.
	 *
	 * @param value The value to classify
	 */
	@ParameterizedTest( name = "[{0}] is text" )
	@ValueSource( strings = { "Save", "Sign in", "Email", "Save changes", "Remember me", "Welcome back!", "Continue to checkout", "Menu", "Summary",
	    "Button" } )
	public void testText( String value ) {
		assertThat( SmartSelector.isSelector( value ) ).isFalse();
	}

}
