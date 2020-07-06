package io.vertigo.connectors.neo4j;

import javax.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.TransactionWork;
import org.neo4j.driver.v1.Values;

import io.vertigo.core.node.AutoCloseableApp;
import io.vertigo.core.node.component.di.DIInjector;
import io.vertigo.core.node.config.BootConfig;
import io.vertigo.core.node.config.NodeConfig;
import io.vertigo.core.param.Param;
import io.vertigo.core.plugins.resource.classpath.ClassPathResourceResolverPlugin;

public class TestNeo4jEmbedded {

	@Inject
	private Neo4JConnector neo4jConnector;
	private AutoCloseableApp app;

	@BeforeEach
	public final void setUp() throws Exception {
		app = new AutoCloseableApp(buildNodeConfig());
		DIInjector.injectMembers(this, app.getComponentSpace());
	}

	@AfterEach
	public final void tearDown() throws Exception {
		if (app != null) {
			app.close();
		}
	}

	@Test
	public void testConnection() {

		try (Session session = neo4jConnector.getClient().session()) {
			final String greeting = session.writeTransaction(new TransactionWork<String>() {
				@Override
				public String execute(final Transaction tx) {
					final StatementResult result = tx.run("CREATE (a:Greeting) " +
							"SET a.message = $message " +
							"RETURN a.message + ', from node ' + id(a)",
							Values.parameters("message", "hello"));
					return result.single().get(0).asString();
				}
			});
			System.out.println(greeting);
		}

		try (final Session session = neo4jConnector.getClient().session()) {
			session.writeTransaction(new TransactionWork<Void>() {

				@Override
				public Void execute(final Transaction tx) {
					final StatementResult result = tx.run("MATCH (a:Greeting) " +
							"RETURN a.message");
					result.list().forEach(record -> System.out.println(record));
					return null;
				}
			});

		}
	}

	protected NodeConfig buildNodeConfig() {
		return NodeConfig.builder()
				.withBoot(BootConfig.builder()
						.addPlugin(ClassPathResourceResolverPlugin.class)
						.build())
				.addModule(new Neo4jFeatures()
						.withGPLEmbeddedServer(
								Param.of("home", "io/vertigo/connectors/neo4j/home"))
						.withNeo4j(
								Param.of("uri", "bolt://localhost:7687"),
								Param.of("login", "neo4j"),
								Param.of("password", "neo4j"))
						.build())
				.build();
	}

}
