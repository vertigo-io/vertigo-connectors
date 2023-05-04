/**
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2023, Vertigo.io, team@vertigo.io
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
package io.vertigo.connectors.influxdb;

import java.util.Optional;

import javax.inject.Inject;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;

/**
 * An InfluxDb connector to the time series database.
 * @author mlaroche
 */
public class InfluxDbConnector implements Connector<InfluxDBClient>, Activeable {

	private final String connectorName;
	private final InfluxDBClient influxDB;
	private final String orgId;

	/**
	 * Constructor of an InfluxDb connector to the time series database
	 * @param connectorNameOpt
	 * @param host Host of the influxdb database
	 * @param token token to use for authentication
	 * @param org org to connect to
	 */
	@Inject
	public InfluxDbConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("host") final String host,
			@ParamValue("token") final String token,
			@ParamValue("org") final String org) {
		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotBlank(host)
				.isNotBlank(token)
				.isNotBlank(org);
		//---
		connectorName = connectorNameOpt.orElse("main");
		influxDB = InfluxDBClientFactory.create(host, token.toCharArray(), org);
		orgId = influxDB.getOrganizationsApi().findOrganizations().stream().filter(organization -> organization.getName().equals(org)).findFirst().get().getId();
	}

	@Override
	public String getName() {
		return connectorName;
	}

	/**
	 * @return the influxdb java client
	 */
	@Override
	public InfluxDBClient getClient() {
		return influxDB;
	}

	public String getOrgId() {
		return orgId;
	}

	/** {@inheritDoc} */
	@Override
	public void start() {
		//
	}

	/** {@inheritDoc} */
	@Override
	public void stop() {
		influxDB.close();
	}

}
