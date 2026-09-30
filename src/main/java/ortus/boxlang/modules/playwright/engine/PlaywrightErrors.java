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

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Factory for the typed errors thrown by bx-playwright.
 * Every error carries a stable {@code type} so it can be caught by type in BoxLang,
 * and a {@code detail} that tells the developer (or an AI agent) how to fix it.
 */
public final class PlaywrightErrors {

	public static final String	NOT_INSTALLED			= "Playwright.NotInstalled";
	public static final String	INVALID_OPTION			= "Playwright.InvalidOption";
	public static final String	NODE_INSTALL_FAILED		= "Playwright.NodeInstallFailed";
	public static final String	UNSUPPORTED_PLATFORM	= "Playwright.UnsupportedPlatform";
	public static final String	INVALID_PROFILE			= "Playwright.InvalidProfile";

	private PlaywrightErrors() {
	}

	/**
	 * Build a typed error.
	 *
	 * @param type    The error type, one of the constants in this class
	 * @param message What went wrong
	 * @param hint    How to fix it
	 * @param cause   The underlying cause, or null
	 *
	 * @return The exception, ready to throw
	 */
	public static BoxRuntimeException of( String type, String message, String hint, Throwable cause ) {
		return new BoxRuntimeException( message, hint, type, null, cause );
	}

	/**
	 * Build a typed error without a cause.
	 *
	 * @param type    The error type, one of the constants in this class
	 * @param message What went wrong
	 * @param hint    How to fix it
	 *
	 * @return The exception, ready to throw
	 */
	public static BoxRuntimeException of( String type, String message, String hint ) {
		return of( type, message, hint, null );
	}

}
