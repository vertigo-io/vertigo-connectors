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
package io.vertigo.connectors.ifttt;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.VSystemException;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

/**
 *
 * @author dt
 */
public final class IftttClient {
	private static final Logger LOGGER = LogManager.getLogger(IftttClient.class);
	private final String baseUrl;
	private final String apiKey;

	IftttClient(
			final String baseUrl,
			final String apiKey,
			final Optional<String> proxyHostOpt,
			final Optional<String> proxyPortOpt) {
		Assertion.check()
				.isNotBlank(baseUrl, "baseUrl must not be empty")
				.isNotBlank(apiKey, "Apikey must not be empty")
				.isNotNull(proxyHostOpt)
				.isNotNull(proxyPortOpt)
				.isFalse(proxyHostOpt.isPresent() ^ proxyPortOpt.isPresent(),
						"les deux paramètres host et port doivent être tous les deux remplis ou vides");
		// ----
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;

		if (proxyHostOpt.isPresent()) {
			System.setProperty("https.proxyHost", proxyHostOpt.get()); // "172.20.0.9"
			System.setProperty("https.proxyPort", proxyPortOpt.get()); // "3128"
		}
	}

	public void sendMakerEvent(final MakerEvent event) {
		Assertion.check().isNotNull(event);
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
