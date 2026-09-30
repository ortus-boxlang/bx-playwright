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

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A resolved Node.js runtime used to run the Playwright driver.
 */
public class NodeRuntime {

	/**
	 * Where the runtime came from.
	 */
	public enum Source {
		/** The {@code nodePath} setting or the {@code PLAYWRIGHT_NODEJS_PATH} variable */
		EXPLICIT,
		/** Extracted from the jars of bx-playwright-full */
		BUNDLED,
		/** Downloaded by {@code bxPlaywright install} */
		DOWNLOADED,
		/** {@code node} found on the system PATH */
		SYSTEM
	}

	private final Path		executable;
	private final Source	source;
	private final String	version;

	/**
	 * @param executable The node executable
	 * @param source     Where it came from
	 * @param version    The version if known, or null
	 */
	public NodeRuntime( Path executable, Source source, String version ) {
		this.executable	= Objects.requireNonNull( executable, "executable must not be null" );
		this.source		= Objects.requireNonNull( source, "source must not be null" );
		this.version	= version;
	}

	/**
	 * @return The node executable
	 */
	public Path getExecutable() {
		return executable;
	}

	/**
	 * @return Where the runtime came from
	 */
	public Source getSource() {
		return source;
	}

	/**
	 * @return The version if known, or null
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * @return A map representation, useful for {@code doctor} and JSON output
	 */
	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put( "executable", executable.toString() );
		map.put( "source", source.name().toLowerCase() );
		map.put( "version", version == null ? "" : version );
		return map;
	}

}
