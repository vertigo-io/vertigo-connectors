/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2024, Vertigo.io, team@vertigo.io
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
package io.vertigo.connectors.oidc;

import io.vertigo.core.node.config.Feature;
import io.vertigo.core.node.config.Features;
import io.vertigo.core.param.Param;

/**
 * Defines commons module.
 * @author skerdudou
 */
public class OIDCFeatures extends Features<OIDCFeatures> {

	/**
	 * Constructor.
	 */
	public OIDCFeatures() {
		super("vertigo-oidc-connector");
	}

	@Feature("oidc")
	public OIDCFeatures withConfig(final Param... params) {
		getModuleConfigBuilder()
				.addConnector(OIDCDeploymentConnector.class, params);
		return this;

	}

	/** {@inheritDoc} */
	@Override
	protected void buildFeatures() {
		//
	}
}
