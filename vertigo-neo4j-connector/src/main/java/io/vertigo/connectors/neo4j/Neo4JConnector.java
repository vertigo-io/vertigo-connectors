/**
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2022, Vertigo.io, team@vertigo.io
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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;

public class Neo4JConnector implements Connector<Driver>, Activeable {
	private final Driver neo4jDriver;
	private final String connectionName;

	@Inject
	public Neo4JConnector(
			@ParamValue("uri") final String uri,
			@ParamValue("login") final String login,
			@ParamValue("password") final String password,
			@ParamValue("name") final Optional<String> connectionNameOpt,
			@ParamValue("connectionTimeout") final Optional<Long> connectionTimeoutOpt,
			@ParamValue("connectionLivenessCheckTimeout") final Optional<Long> connectionLivenessCheckTimeoutOpt,
			@ParamValue("connectionAcquisitionTimeout") final Optional<Long> connectionAcquisitionTimeoutOpt,
			@ParamValue("connectionPoolSize") final Optional<Integer> connectionPoolSizeOpt) {
		Assertion.check()
				.isNotBlank(uri)
				.isNotBlank(login)
				.isNotBlank(password);
		//---
		connectionName = connectionNameOpt.orElse("main");
		//---
		neo4jDriver = GraphDatabase.driver(
				uri,
				AuthTokens.basic(login, password),
				getConfig(connectionTimeoutOpt, connectionLivenessCheckTimeoutOpt, connectionAcquisitionTimeoutOpt, connectionPoolSizeOpt));
	}

	protected Config getConfig(final Optional<Long> connectionTimeoutOpt,
			final Optional<Long> connectionLivenessCheckTimeoutOpt,
			final Optional<Long> connectionAcquisitionTimeoutOpt,
			final Optional<Integer> connectionPoolSizeOpt) {
		return Config.builder()
				.withConnectionTimeout(connectionTimeoutOpt.orElse(1L), TimeUnit.MINUTES)
				.withConnectionLivenessCheckTimeout(connectionLivenessCheckTimeoutOpt.orElse(10L), TimeUnit.MINUTES)
				.withConnectionAcquisitionTimeout(connectionAcquisitionTimeoutOpt.orElse(1L), TimeUnit.MINUTES)
				.withMaxConnectionPoolSize(connectionPoolSizeOpt.orElse(100))
				.build();
	}

	@Override
	public Driver getClient() {
		return neo4jDriver;
	}

	@Override
	public void start() {
		// nothing to do

	}

	@Override
	public void stop() {
		neo4jDriver.close();

	}

	@Override
	public String getName() {
		return connectionName;
	}

}
