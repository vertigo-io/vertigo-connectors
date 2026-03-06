/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2025, Vertigo.io, team@vertigo.io
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

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.node.component.Component;
import io.vertigo.core.param.ParamValue;

//Vérifier
/**
 * Gestion de la connexion au serveur ElasticSearch en mode embarqué.
 *
 * @author pchretien, npiedeloup
 */
public final class EmbeddedElasticSearchServer implements Component, Activeable {
	private static final int DEFAULT_TRANSPORT_PORT = 9300;
	private static final int DEFAULT_HTTP_PORT = 9200;

	public static final String DEFAULT_VERTIGO_ES_CLUSTER_NAME = "vertigo-elasticsearch-test";
	//private static final String DEFAULT_ES_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:7.17.28";
	private static final String DEFAULT_ES_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:9.2.3";

	private final ElasticsearchContainer container;

	/**
	 * Constructor.
	 * 
	 * @param imageOpt Image Docker ES (ex: docker.elastic.co/elasticsearch/elasticsearch:9.2.3)
	 * @param clusterNameOpt Nom du cluster
	 * @param httpPortOpt Port HTTP
	 * @param transportPortOpt Port Transport (TCP)
	 */
	@Inject
	public EmbeddedElasticSearchServer(
			@ParamValue("esImage") final Optional<String> imageOpt,
			@ParamValue("cluster.name") final Optional<String> clusterNameOpt,
			@ParamValue("http.port") final Optional<Integer> httpPortOpt,
			@ParamValue("transport.tcp.port") final Optional<Integer> transportPortOpt) {

		final String imageName = imageOpt.orElse(DEFAULT_ES_IMAGE);
		final String clusterName = clusterNameOpt.orElse(DEFAULT_VERTIGO_ES_CLUSTER_NAME);
		final int httpPort = httpPortOpt.orElse(DEFAULT_HTTP_PORT);
		final int transportPort = transportPortOpt.orElse(DEFAULT_TRANSPORT_PORT);

		// Définition du conteneur
		container = new ElasticsearchContainer(DockerImageName.parse(imageName))
				.withEnv("cluster.name", clusterName)
				.withEnv("discovery.type", "single-node")
				// On désactive la sécurité (HTTPS, mots de passe) pour reproduire l'ancien comportement simple
				.withEnv("xpack.security.enabled", "false")
				// Limite la consommation RAM du conteneur pour ne pas tuer la CI
				.withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
				// --- Conf de Watermark (gestion de l'espace disque) ---
				.withEnv("cluster.routing.allocation.disk.watermark.low", "1000mb")
				.withEnv("cluster.routing.allocation.disk.watermark.high", "500mb")
				.withEnv("cluster.routing.allocation.disk.watermark.flood_stage", "250mb");

		// IMPORTANT : On force l'exposition des ports fixes demandés par la configuration Vertigo.
		// Cela permet au Client Vertigo qui démarre juste après de s'y connecter via "localhost:9200"
		container.setPortBindings(List.of(
				httpPort + ":9200",
				transportPort + ":9300"));
	}

	/** {@inheritDoc} */
	@Override
	public void start() {
		try {
			container.start();
		} catch (final Exception e) {
			throw WrappedException.wrap(e, "Error at Testcontainers ElasticSearch start");
		}
	}

	/** {@inheritDoc} */
	@Override
	public void stop() {
		try {
			container.stop();
		} catch (final Exception e) {
			throw WrappedException.wrap(e, "Error at Testcontainers ElasticSearch stop");
		}
	}
}
