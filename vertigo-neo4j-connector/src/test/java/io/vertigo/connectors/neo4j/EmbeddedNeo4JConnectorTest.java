/**
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2020, Vertigo.io, team@vertigo.io
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

import javax.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

import io.vertigo.core.node.AutoCloseableNode;
import io.vertigo.core.node.component.di.DIInjector;
import io.vertigo.core.node.config.BootConfig;
import io.vertigo.core.node.config.NodeConfig;
import io.vertigo.core.param.Param;
import io.vertigo.core.plugins.resource.classpath.ClassPathResourceResolverPlugin;

public class EmbeddedNeo4JConnectorTest {

	@Inject
	private Neo4JConnector neo4jConnector;
	private AutoCloseableNode node;

	@BeforeEach
	public final void setUp() throws Exception {
		node = new AutoCloseableNode(buildNodeConfig());
		DIInjector.injectMembers(this, node.getComponentSpace());
	}

	@AfterEach
	public final void tearDown() throws Exception {
		if (node != null) {
			node.close();
		}
	}

	@Test
	public void testConnection() {

		try (Session session = neo4jConnector.getClient().session()) {
			final String greeting = session.writeTransaction(tx -> {
				final Result result = tx.run("CREATE (a:Greeting) " +
						"SET a.message = $message " +
						"RETURN a.message + ', from node ' + id(a)",
						Values.parameters("message", "hello"));
				return result.single().get(0).asString();
			});
			System.out.println(greeting);
		}

		try (final Session session = neo4jConnector.getClient().session()) {
			session.writeTransaction(tx -> {
				final Result result = tx.run("MATCH (a:Greeting) " +
						"RETURN a.message");
				result.list().forEach(record -> System.out.println(record));
				return null;
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
