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
package ortus.boxlang.modules.playwright.e2e;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;

import ortus.boxlang.modules.playwright.engine.NodeInstaller;
import ortus.boxlang.modules.playwright.engine.Platform;
import ortus.boxlang.modules.playwright.engine.PlaywrightHome;

/**
 * Shared setup for end-to-end tests that drive a real browser.
 * <p>
 * They only run when {@code PLAYWRIGHT_E2E=true}. The first run downloads Node.js and Chromium
 * into {@code build/playwright-home} (or {@code BX_PLAYWRIGHT_HOME}), later runs reuse them.
 */
public final class E2E {

	public static final String		PLAYWRIGHT_VERSION	= "1.63.0";
	public static final String		NODE_VERSION		= "24.21.0";

	private static PlaywrightHome	home;

	/**
	 * Static helpers only, no instances.
	 */
	private E2E() {
	}

	/**
	 * Skip the calling test unless end-to-end tests are enabled.
	 */
	public static void assumeEnabled() {
		Assumptions.assumeTrue( "true".equalsIgnoreCase( System.getenv( "PLAYWRIGHT_E2E" ) ), "Set PLAYWRIGHT_E2E=true to run browser tests" );
	}

	/**
	 * The playwright home for end-to-end tests: BX_PLAYWRIGHT_HOME if set, otherwise build/playwright-home.
	 *
	 * @return The home directory used by end-to-end tests
	 */
	public static Path homeDir() {
		String custom = System.getenv( "BX_PLAYWRIGHT_HOME" );
		return custom == null || custom.isBlank() ? Path.of( "build", "playwright-home" ).toAbsolutePath() : Path.of( custom );
	}

	/**
	 * A home with Node.js and Chromium installed, ready to launch browsers.
	 *
	 * @return The prepared home
	 */
	public static synchronized PlaywrightHome home() {
		assumeEnabled();
		if ( home == null ) {
			PlaywrightHome candidate = new PlaywrightHome( homeDir(), null, PLAYWRIGHT_VERSION, NODE_VERSION, null, Platform.current() );
			if ( candidate.resolveNode().isEmpty()
			    || candidate.resolveNode().get().getSource() == ortus.boxlang.modules.playwright.engine.NodeRuntime.Source.SYSTEM ) {
				new NodeInstaller( candidate, null, System.out::println ).install( false );
			}
			if ( candidate.installedBrowsers().stream().noneMatch( name -> name.startsWith( "chromium" ) ) ) {
				int exitCode = candidate.runCli( List.of( "install", "chromium" ) );
				if ( exitCode != 0 ) {
					throw new IllegalStateException( "Failed to install Chromium, exit code " + exitCode );
				}
			}
			home = candidate;
		}
		return home;
	}

}
