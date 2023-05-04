/**
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2023, Vertigo.io, team@vertigo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.vertigo.connectors.httpclient;

import io.vertigo.core.node.config.Feature;
import io.vertigo.core.node.config.Features;
import io.vertigo.core.param.Param;

/**
 * Defines commons module.
 * @author npiedeloup
 */
public final class HttpClientFeatures extends Features<HttpClientFeatures> {

	/**
	 * Constructor.
	 */
	public HttpClientFeatures() {
		super("vertigo-httpClient-connector");
	}

	@Feature("httpclient")
	public HttpClientFeatures withHttpClient(final Param... params) {
		getModuleConfigBuilder()
				.addConnector(HttpClientConnector.class, params);
		return this;

	}

	/** {@inheritDoc} */
	@Override
	protected void buildFeatures() {
		//
	}
}
