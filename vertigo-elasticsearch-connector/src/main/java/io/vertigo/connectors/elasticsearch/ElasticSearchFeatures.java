/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2026, Vertigo.io, team@vertigo.io
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
 *
 * @author npiedeloup
 */
public final class ElasticSearchFeatures extends Features<ElasticSearchFeatures> {

	/**
	 * Constructor.
	 */
	public ElasticSearchFeatures() {
		super("vertigo-elasticsearch-connector");
	}

	@Feature("rest")
	public ElasticSearchFeatures withRest(final Param... params) {
		getModuleConfigBuilder()
				.addConnector(RestElasticSearchConnector.class, params);
		return this;
	}

	@Feature("embeddedServer")
	public ElasticSearchFeatures withEmbeddedServer(final Param... params) {
		/**
		 * To use EmbeddedServer with org.testcontainers, we need to declare a varEnv DOCKER_HOST to point on docker socket (like DOCKER_HOST=tcp://localhost:2375)
		 * To active socket on a docket with WSL, you shoud edit "/lib/systemd/system/docker.service" and add this line in [Service] section:
		 * ExecStart=/usr/bin/dockerd -H fd:// -H tcp://127.0.0.1:2375 --containerd=/run/containerd/containerd.sock
		 */
		getModuleConfigBuilder()
				.addComponent(EmbeddedElasticSearchServer.class, params);
		return this;
	}

	/** {@inheritDoc} */
	@Override
	protected void buildFeatures() {
		//
	}
}
