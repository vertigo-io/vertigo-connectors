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
package io.vertigo.connectors.ifttt;

import java.util.Optional;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;

/**
 *
 * @author dt
 */
public class IftttClient implements Connector<IftttClient> {

	private static final Logger LOGGER = LogManager.getLogger(IftttClient.class);
	private final String connectorName;
	private final String baseUrl;
	private final String apiKey;

	public IftttClient(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("baseUrl") final String baseUrl,
			@ParamValue("apiKey") final String apiKey,
			@ParamValue("proxyHost") final Optional<String> proxyHostOpt,
			@ParamValue("proxyPort") final Optional<String> proxyPortOpt) {
		Assertion.checkArgNotEmpty(apiKey, "Apikey must not be empty");
		Assertion.checkNotNull(proxyHostOpt);
		Assertion.checkNotNull(proxyPortOpt);
		Assertion.checkArgument(
				proxyHostOpt.isPresent() && proxyPortOpt.isPresent() || !proxyHostOpt.isPresent() && !proxyPortOpt.isPresent(),
				"les deux paramètres host et port doivent être tous les deux remplis ou vides");
		// ----
		connectorName = connectorNameOpt.orElse("main");
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;

		if (proxyHostOpt.isPresent()) {
			System.setProperty("https.proxyHost", proxyHostOpt.get()); // "172.20.0.9"
			System.setProperty("https.proxyPort", proxyPortOpt.get()); // "3128"
		}
	}

	@Override
	public String getName() {
		return connectorName;
	}

	@Override
	public IftttClient getClient() {
		return this;
	}

	public void sendMakerEvent(final MakerEvent event) {
		Assertion.checkNotNull(event);
		//---
		final String url = new StringBuilder(baseUrl)
				.append("/")
				.append(event.getEventName())
				.append("/with/key/")
				.append(apiKey)
				.toString();
		final WebTarget resource = ClientBuilder.newClient().target(url);
		final Builder request = resource.request().accept(MediaType.APPLICATION_JSON);
		final Response response = request.post(Entity.<MakerEventMetadatas> entity(event.getEventMetadatas(), MediaType.APPLICATION_JSON));

		if (response.getStatusInfo().getFamily() == Family.SUCCESSFUL) {
			LOGGER.info("Success! {}", response.getStatus());
		} else {
			LOGGER.error("Error! {}", response.getStatus());
			throw new VSystemException("Error while sending Ifttt maker event:" + response.getStatus());
		}
	}

}
