/**
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2021, Vertigo.io, team@vertigo.io
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
package io.vertigo.connectors.mongodb;

import java.util.Optional;

import javax.inject.Inject;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;

/**
 * Connector to mongodb
 *
 * @author mlaroche
 */
public class MongoClientConnector implements Connector<MongoClient>, Activeable {
	private final String connectorName;
	private final MongoClient mongoClient;

	/**
	 * Connector to mongodb
	 * @param connectorNameOpt Optional name of the connector ("main" by default)
	 * @param connectionString connectionString to connect to Mongo DB or replica set
	 */
	@Inject
	public MongoClientConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("connectionString") final String connectionString) {
		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotBlank(connectionString);
		//---
		connectorName = connectorNameOpt.orElse(DEFAULT_CONNECTOR_NAME);
		mongoClient = MongoClients.create(connectionString);
	}

	@Override
	public String getName() {
		return connectorName;
	}

	@Override
	public MongoClient getClient() {
		return mongoClient;
	}

	@Override
	public void start() {
		// nothing

	}

	@Override
	public void stop() {
		mongoClient.close();
	}
}
