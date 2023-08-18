/*
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
package io.vertigo.connectors.ifttt;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.lang.WrappedException;

/**
 *
 * @author dt
 */
public final class IftttClient {
	private static final Logger LOGGER = LogManager.getLogger(IftttClient.class);
	private static final Gson GSON = new Gson();
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

		final HttpClient client = HttpClient.newBuilder().build();
		final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.setHeader("Content-Type", "application/json;charset=UTF-8")
				.setHeader("Accept", "application/json")
				.POST(BodyPublishers.ofString(GSON.toJson(event.getEventMetadatas(), MakerEventMetadatas.class)))
				.build();

		try {
			final HttpResponse<?> response = client.send(request, BodyHandlers.discarding());
			if (response.statusCode() / 100 != 2) {
				LOGGER.info("Success! {}", response.statusCode());
			} else {
				LOGGER.error("Error! {}", response.statusCode());
				throw new VSystemException("Error while sending Ifttt maker event:" + response.statusCode());
			}
		} catch (IOException | InterruptedException e) {
			throw WrappedException.wrap(e);
		}

	}
}
