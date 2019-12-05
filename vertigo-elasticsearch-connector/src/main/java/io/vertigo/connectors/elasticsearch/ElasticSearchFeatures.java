/**
 * vertigo - simple java starter
 *
 * Copyright (C) 2013-2019, vertigo-io, KleeGroup, direction.technique@kleegroup.com (http://www.kleegroup.com)
 * KleeGroup, Centre d'affaire la Boursidiere - BP 159 - 92357 Le Plessis Robinson Cedex - France
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
package io.vertigo.connectors.elasticsearch;

import io.vertigo.core.node.config.Feature;
import io.vertigo.core.node.config.Features;
import io.vertigo.core.param.Param;

/**
 * Defines ElasticSearchConnector module.
 * @author npiedeloup
 */
public final class ElasticSearchFeatures extends Features<ElasticSearchFeatures> {

	/**
	 * Constructor.
	 */
	public ElasticSearchFeatures() {
		super("vertigo-elasticsearch-connector");
	}

	@Feature("embedded")
	public ElasticSearchFeatures withEmbedded(final Param... params) {
		getModuleConfigBuilder()
				.addConnector(EmbeddedElasticSearchConnector.class, params);
		return this;

	}

	@Feature("transport")
	public ElasticSearchFeatures withTransport(final Param... params) {
		getModuleConfigBuilder()
				.addConnector(TransportElasticSearchConnector.class, params);
		return this;
	}

	@Feature("node")
	public ElasticSearchFeatures withNode(final Param... params) {
		getModuleConfigBuilder()
				.addConnector(NodeElasticSearchConnector.class, params);
		return this;
	}

	/** {@inheritDoc} */
	@Override
	protected void buildFeatures() {
		//
	}
}