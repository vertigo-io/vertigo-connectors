package io.vertigo.connectors.neo4j;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

import java.io.File;
import java.net.URISyntaxException;

import javax.inject.Inject;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.node.component.Component;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

public class Neo4jGPLEmbeddedServer implements Component, Activeable {
	private final DatabaseManagementService managementService;
	protected final GraphDatabaseService graphDb;

	@Inject
	public Neo4jGPLEmbeddedServer(
			@ParamValue("home") final String home,
			final ResourceManager resourceManager) throws URISyntaxException {

		Assertion.check().isNotBlank(home);
		//---
		final File homeFile = new File(resourceManager.resolve(home).toURI());

		managementService = new DatabaseManagementServiceBuilder(homeFile)
				.setConfig(GraphDatabaseSettings.pagecache_memory, "512M")
				.setConfig(BoltConnector.enabled, true)
				.setConfig(BoltConnector.listen_address, new SocketAddress("localhost", 7687))
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
