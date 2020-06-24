/**
 * vertigo - simple java starter
 *
 * Copyright (C) 2013-2019, vertigo-io, KleeGroup, direction.technique@kleegroup.com (http://www.kleegroup.com)
 * KleeGroup, Centre d'affaire la Boursidiere - BP 159 - 92357 Le Plessis Robinson Cedex - France
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

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;

/**
 * An InfluxDb connector to the time series database.
 * @author mlaroche
 */
public final class InfluxDbConnector implements Connector<InfluxDB>, Activeable {

	private final String connectorName;
	private final InfluxDB influxDB;

	/**
	 * Constructor of an InfluxDb connector to the time series database
	 * @param connectorNameOpt
	 * @param host Host of the influxdb database
	 * @param user user to user
	 * @param password password secret for connection
	 */
	@Inject
	public InfluxDbConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("host") final String host,
			@ParamValue("user") final String user,
			@ParamValue("password") final String password) {
		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotBlank(host)
				.isNotBlank(user)
				.isNotBlank(password);
		//---
		connectorName = connectorNameOpt.orElse("main");
		influxDB = InfluxDBFactory.connect(host, user, password);
	}

	@Override
	public String getName() {
		return connectorName;
	}

	/**
	 * @return the influxdb java client
	 */
	@Override
	public InfluxDB getClient() {
		return influxDB;
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
