package io.vertigo.connectors.neo4j;

import java.io.File;
import java.net.URISyntaxException;

import javax.inject.Inject;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.configuration.BoltConnector;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.node.component.Component;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

public class Neo4jGPLEmbeddedServer implements Component, Activeable {
	GraphDatabaseService graphDb;

	@Inject
	public Neo4jGPLEmbeddedServer(
			@ParamValue("home") final String home,
			final ResourceManager resourceManager) throws URISyntaxException {

		Assertion.check().isNotBlank(home);
		//---
		final File homeFile = new File(resourceManager.resolve(home).toURI());

		final BoltConnector bolt = new BoltConnector("0");
		graphDb = new GraphDatabaseFactory()
				.newEmbeddedDatabaseBuilder(homeFile)
				.setConfig(GraphDatabaseSettings.pagecache_memory, "512M")
				.setConfig(GraphDatabaseSettings.string_block_size, "60")
				.setConfig(GraphDatabaseSettings.array_block_size, "300")
				.setConfig(bolt.type, "BOLT")
				.setConfig(bolt.enabled, "true")
				.setConfig(bolt.listen_address, "localhost:7687")
				.newGraphDatabase();

	}

	@Override
	public void start() {
		//
	}

	@Override
	public void stop() {
		graphDb.shutdown();
	}

}
