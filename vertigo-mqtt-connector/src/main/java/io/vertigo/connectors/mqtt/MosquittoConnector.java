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
package io.vertigo.connectors.mqtt;

import java.util.Optional;

import javax.inject.Inject;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.node.Node;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;

/**
 * @author mlaroche
 */
public class MosquittoConnector implements Connector<MqttClient>, Activeable {

	private final MqttClient mqttClient;
	private final String connectionName;

	@Inject
	public MosquittoConnector(
			@ParamValue("name") final Optional<String> connectionNameOpt,
			@ParamValue("host") final String brokerHost,
			@ParamValue("clientId") final Optional<String> clientIdOpt) {
		Assertion.check().isNotBlank(brokerHost);
		//---
		connectionName = connectionNameOpt.orElse("main");
		final String clientId = clientIdOpt.orElseGet(() -> Node.getNode().getNodeConfig().getNodeId());
		try {
			mqttClient = new MqttClient(brokerHost, clientId + connectionName, new MemoryPersistence());
			final MqttConnectOptions connOpts = new MqttConnectOptions();
			connOpts.setCleanSession(true);
			//mqttClient.connect(connOpts);
		} catch (final MqttException e) {
			throw WrappedException.wrap(e);
		}
	}

	@Override
	public void start() {
		//nothing

	}

	@Override
	public void stop() {
		try {
			if (mqttClient.isConnected()) {
				mqttClient.disconnect();
			}
		} catch (final MqttException e) {
			throw WrappedException.wrap(e);
		}

	}

	@Override
	public MqttClient getClient() {
		return mqttClient;

	}

	@Override
	public String getName() {
		return connectionName;
	}

}
