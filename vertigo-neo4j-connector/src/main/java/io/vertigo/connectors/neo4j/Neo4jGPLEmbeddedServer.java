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
package io.vertigo.connectors.neo4j;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Inject;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.io.ByteUnit;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.node.component.Component;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

public class Neo4jGPLEmbeddedServer implements Component, Activeable {
	private static final int DEFAULT_PORT = 7687;
	private final DatabaseManagementService managementService;
	protected final GraphDatabaseService graphDb;

	@Inject
	public Neo4jGPLEmbeddedServer(
			@ParamValue("home") final String home,
			final ResourceManager resourceManager) throws URISyntaxException {

		Assertion.check().isNotBlank(home);
		//---
		final Path homeFile = Paths.get(resourceManager.resolve(home).toURI());

		managementService = new DatabaseManagementServiceBuilder(homeFile)
				//.setConfig(GraphDatabaseSettings.pagecache_memory, "512M")
				.setConfig(GraphDatabaseSettings.pagecache_memory, ByteUnit.mebiBytes(512))
				.setConfig(BoltConnector.enabled, true)
				.setConfig(BoltConnector.listen_address, new SocketAddress("localhost", DEFAULT_PORT))
				.build();

		graphDb = managementService.database(DEFAULT_DATABASE_NAME);
		registerShutdownHook(managementService);
	}

	@Override
	public void start() {
		//
	}

	@Override
	public void stop() {
		managementService.shutdown();
	}

	private static void registerShutdownHook(final DatabaseManagementService managementService) {
		// Registers a shutdown hook for the Neo4j instance so that it
		// shuts down nicely when the VM exits (even if you "Ctrl-C" the
		// running application).
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				managementService.shutdown();
			}
		});
	}
}
